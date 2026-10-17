package com.aaf.link.core.mq.consumer;

import com.aaf.link.core.mq.config.MqConditions;
import com.aaf.link.core.mq.message.VisitMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 访问统计消费者（V2，P5 改为薄壳）。
 *
 * <p>业务逻辑全部在 {@link VisitMessageHandler}；本类只负责"从 RabbitMQ 取消息 → 交 handler"。
 *
 * <p>失败处理（沿用 V2 语义）：handler 抛出异常 → Spring AMQP 重试（最多 3 次）；
 * 重试用尽后因 {@code default-requeue-rejected=false} 进入死信队列，而不是无限重投。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(MqConditions.RabbitMqCondition.class)
public class VisitStatsConsumer {

    private final VisitMessageHandler handler;

    // 必须给默认值：optional:nacos 的容错承诺是"Nacos 不可用也能启动"，
    // 而 @RabbitListener 的占位符是从 Environment 解析的（拿不到 LinkCoreProperties 的字段默认值），
    // 没配 link-core.yaml 时会直接 IllegalArgumentException 启动失败（P7 容器化时踩到）
    @RabbitListener(queues = "${link.core.mq.queue:link.visit.queue}")
    public void onVisit(VisitMessage message) {
        handler.handle(message);
    }
}
