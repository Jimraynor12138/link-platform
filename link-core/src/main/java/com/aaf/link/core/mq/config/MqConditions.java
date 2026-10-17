package com.aaf.link.core.mq.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;

/**
 * MQ 装配条件（P5）：两条正交维度用 AND 组合。
 *
 * <p>为什么需要组合：`@ConditionalOnProperty` 一次只能表达一个属性，而"装配哪种 MQ"需要
 * <b>enabled=true 且 type=xxx</b> 两个条件同时成立，所以用 {@link AllNestedConditions} 包一层。
 *
 * <pre>
 * enabled=false                → 只有 SyncVisitRecorder（同步直写）
 * enabled=true, type=rabbit    → RabbitMQ 拓扑/生产者/监听器（默认）
 * enabled=true, type=kafka     → Kafka 生产者/监听器/错误处理，Rabbit 侧不装配
 * </pre>
 */
public final class MqConditions {

    private MqConditions() {
    }

    /** RabbitMQ 侧：mq.enabled=true（缺省 true）且 mq.type=rabbit（缺省即 rabbit） */
    public static class RabbitMqCondition extends AllNestedConditions {

        public RabbitMqCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "link.core.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
        static class MqEnabled {
        }

        @ConditionalOnProperty(prefix = "link.core.mq", name = "type", havingValue = "rabbit", matchIfMissing = true)
        static class TypeRabbit {
        }
    }

    /** Kafka 侧：mq.enabled=true（缺省 true）且 mq.type=kafka（<b>无</b> matchIfMissing，必须显式指定） */
    public static class KafkaMqCondition extends AllNestedConditions {

        public KafkaMqCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "link.core.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
        static class MqEnabled {
        }

        @ConditionalOnProperty(prefix = "link.core.mq", name = "type", havingValue = "kafka")
        static class TypeKafka {
        }
    }
}
