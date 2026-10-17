package com.aaf.link.core.mq.consumer;

import com.aaf.link.core.mq.IdempotentChecker;
import com.aaf.link.core.mq.message.VisitMessage;
import com.aaf.link.core.service.StatsService;
import com.aaf.link.core.service.StatsSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 访问消息的<b>消费业务处理器</b>（P5 从 {@code VisitStatsConsumer} 抽出）。
 *
 * <p>为什么单独一层：幂等、日期口径、{@code markDirty}/{@code recordVisitStrict} 的顺序、
 * 失败时先释放幂等标记再抛出——这些"值钱逻辑"与具体 MQ 无关。
 * 抽出来后，RabbitMQ 的 {@code @RabbitListener} 与 Kafka 的 {@code @KafkaListener}
 * 都退化成"取消息 → 交 handler"的薄壳，<b>业务逻辑只写一份</b>。
 *
 * <p><b>顺序不可颠倒</b>：先 {@code markDirty}（内存操作、重复无害）再写 Redis 统计；
 * 反过来在重试时会把 PV 重复累加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitMessageHandler {

    private final IdempotentChecker idempotentChecker;
    private final StatsService statsService;
    private final StatsSnapshotService statsSnapshotService;

    /**
     * 处理一条访问消息。失败时抛出异常，由各 MQ 的失败策略（重试/死信）接手。
     */
    public void handle(VisitMessage message) {
        if (message == null) {
            // 例如 payload 为空导致转换结果为空：直接丢弃，不必走重试/死信（重投也无法处理）
            log.warn("收到空的统计消息，已丢弃");
            return;
        }
        String visitId = message.getVisitId();
        if (!idempotentChecker.tryConsume(visitId)) {
            // 重复消息（重投/重试）：跳过，避免 PV 被多加
            log.debug("重复的统计消息，已跳过: visitId={}", visitId);
            return;
        }
        LocalDate visitDate = resolveVisitDate(message);
        try {
            // 顺序很重要：先标记脏（内存操作、重复无害），再写 Redis 统计。
            // 若写统计失败触发重试，脏标记重复不会造成任何影响；
            // 反过来（先写统计后标记）则可能在重试时把 PV 重复累加。
            statsSnapshotService.markDirty(message.getCode(), visitDate);
            statsService.recordVisitStrict(message.getCode(), message.getClientIp(),
                    message.getUserAgent(), message.getReferer(), visitDate);
        } catch (Exception e) {
            // 重试前必须释放幂等标记，否则第二次消费会被自己挡掉
            log.error("统计消息处理失败，将按重试策略重投: visitId={}, code={}", visitId, message.getCode(), e);
            idempotentChecker.release(visitId);
            throw e;
        }
    }

    /**
     * 统计日期取消息发生时间，而不是消费时刻：跨零点时消息仍归入访问当天
     */
    private LocalDate resolveVisitDate(VisitMessage message) {
        return message.getVisitTime() == null ? LocalDate.now() : message.getVisitTime().toLocalDate();
    }
}
