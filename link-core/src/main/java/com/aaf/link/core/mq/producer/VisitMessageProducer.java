package com.aaf.link.core.mq.producer;

import com.aaf.link.core.config.LinkCoreProperties;
import com.aaf.link.core.mq.VisitMessagePublisher;
import com.aaf.link.core.mq.config.MqConditions;
import com.aaf.link.core.mq.message.VisitMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 消息发布实现（V2 基线，P5 改为 {@link VisitMessagePublisher} 的一种实现）。
 *
 * <p>可靠性取舍：统计不是钱，允许偶发丢一条 —— 投递失败只记 error 日志，绝不影响跳转。
 * 若要求不丢，需要 publisher confirm + 本地消息表，代价是跳转 RT 变长，本项目不采用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(MqConditions.RabbitMqCondition.class)
public class VisitMessageProducer implements VisitMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final LinkCoreProperties properties;

    /**
     * 发送消息：吞掉所有异常，保证跳转链路不受 MQ 可用性影响
     */
    @Override
    public void publish(VisitMessage message) {
        try {
            rabbitTemplate.convertAndSend(properties.getMq().getExchange(),
                    properties.getMq().getRoutingKey(), message);
        } catch (Exception e) {
            log.error("统计消息投递失败，已忽略（不影响跳转）: visitId={}, code={}",
                    message.getVisitId(), message.getCode(), e);
        }
    }
}
