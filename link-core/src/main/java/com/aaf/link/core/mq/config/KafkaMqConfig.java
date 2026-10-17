package com.aaf.link.core.mq.config;

import com.aaf.link.core.config.LinkCoreProperties;
import com.aaf.link.core.mq.KafkaRetryRecoverer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 拓扑与错误处理（P5，{@code link.core.mq.type=kafka} 时装配）。
 *
 * <p>三件事：
 * <ol>
 *   <li><b>声明 topic</b>：主 / retry / dlt。broker 关掉了自动建 topic（见 deploy/kafka），
 *       拼错名字会直接报错，而不是静默建一个错的。</li>
 *   <li><b>错误处理</b>：不在原地反复重试，而是把失败记录交给 {@link KafkaRetryRecoverer}
 *       转投 retry topic / dlt——这对应 RabbitMQ 的"重试次数 + 死信队列"语义。</li>
 *   <li>处理完即 ack，避免原 topic 无限重投。</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
@Conditional(MqConditions.KafkaMqCondition.class)
public class KafkaMqConfig {

    private final LinkCoreProperties properties;

    @Bean
    public NewTopic visitTopic() {
        return TopicBuilder.name(properties.getMq().getTopic())
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic visitRetryTopic() {
        return TopicBuilder.name(properties.getMq().getRetryTopic())
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic visitDltTopic() {
        return TopicBuilder.name(properties.getMq().getDltTopic())
                .partitions(3).replicas(1).build();
    }

    /**
     * 消费失败策略：{@code FixedBackOff(0, 0)} 表示"不原地重试"，直接交给 recoverer 决定
     * 是转投 retry topic 还是 dlt（真正的重试发生在 retry topic 的下一轮消费里）。
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new KafkaRetryRecoverer(kafkaTemplate, properties),
                new FixedBackOff(0L, 0L));
        handler.setAckAfterHandle(true);
        return handler;
    }
}
