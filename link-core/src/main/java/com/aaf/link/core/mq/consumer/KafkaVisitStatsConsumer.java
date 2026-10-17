package com.aaf.link.core.mq.consumer;

import com.aaf.link.core.mq.config.MqConditions;
import com.aaf.link.core.mq.message.VisitMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 访问统计消费者（P5，{@code link.core.mq.type=kafka} 时装配）。
 *
 * <p>与 Rabbit 版一样是<b>薄壳</b>：业务逻辑全在 {@link VisitMessageHandler}。
 *
 * <p>topic 与 group 用 SpEL 从 {@code LinkCoreProperties} 取（而不是
 * {@code ${link.core.mq.topic}} 占位符）——避免"Nacos 没配这个 key 时占位符解析失败"，
 * 同时保证与 {@code link.core.mq.*} 单一配置源一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(MqConditions.KafkaMqCondition.class)
public class KafkaVisitStatsConsumer {

    private final VisitMessageHandler handler;

    @KafkaListener(
            topics = "#{@linkCoreProperties.mq.topic}",
            groupId = "#{@linkCoreProperties.mq.consumerGroup}")
    public void onVisit(VisitMessage message) {
        handler.handle(message);
    }
}
