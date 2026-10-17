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

    @RabbitListener(queues = "${link.core.mq.queue}")
    public void onVisit(VisitMessage message) {
        handler.handle(message);
    }
}
