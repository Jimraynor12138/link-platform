package com.aaf.link.core.mq.producer;

import com.aaf.link.core.config.LinkCoreProperties;
import com.aaf.link.core.mq.VisitMessagePublisher;
import com.aaf.link.core.mq.config.MqConditions;
import com.aaf.link.core.mq.message.VisitMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 消息发布实现（P5，{@code link.core.mq.type=kafka} 时装配）。
 *
 * <p>与 Rabbit 实现同一契约：<b>投递失败只记日志，绝不影响跳转</b>。
 * 差异在于 Kafka 发送是异步的——同步异常（如序列化失败）用 try/catch 兜住，
 * broker 侧的失败用 {@code whenComplete} 回调兜住。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(MqConditions.KafkaMqCondition.class)
public class KafkaVisitMessagePublisher implements VisitMessagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final LinkCoreProperties properties;

    @Override
    public void publish(VisitMessage message) {
        try {
            // 用 visitId 作 key：同一消息落到同一分区，便于排查与保证局部有序
            kafkaTemplate.send(properties.getMq().getTopic(), message.getVisitId(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("统计消息投递失败（Kafka 异步回调），已忽略（不影响跳转）: visitId={}, code={}",
                                    message.getVisitId(), message.getCode(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("统计消息投递失败（Kafka 同步异常），已忽略（不影响跳转）: visitId={}, code={}",
                    message.getVisitId(), message.getCode(), e);
        }
    }
}
