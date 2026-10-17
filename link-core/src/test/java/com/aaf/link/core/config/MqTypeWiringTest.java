package com.aaf.link.core.config;

import com.aaf.link.core.mq.VisitMessagePublisher;
import com.aaf.link.core.mq.producer.KafkaVisitMessagePublisher;
import com.aaf.link.core.mq.producer.VisitMessageProducer;
import com.aaf.link.core.service.StatsService;
import com.aaf.link.core.service.StatsSnapshotService;
import com.aaf.link.core.service.VisitRecorder;
import com.aaf.link.core.service.impl.AsyncVisitRecorder;
import com.aaf.link.core.service.impl.SyncVisitRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * MQ 可切换的装配矩阵测试（P5）。
 *
 * <p>为什么必须测装配：条件注解写错（比如 Kafka 侧误加 matchIfMissing）会导致
 * 两套实现同时装配 → {@code NoUniqueBeanDefinitionException}，而普通单测（手工 new）完全测不出来。
 *
 * <p>矩阵：{@code enabled=false} 走同步；{@code enabled=true,type=rabbit}（默认）走 Rabbit。
 * Kafka 组合在 Kafka 实现落地后补充。
 */
class MqTypeWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(LinkCoreProperties.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void rabbitTypeWiresRabbitPublisherAndAsyncRecorder() {
        runner.withPropertyValues("link.core.mq.enabled=true", "link.core.mq.type=rabbit")
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(VisitMessageProducer.class)
                .withBean(AsyncVisitRecorder.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(VisitMessagePublisher.class);
                    assertThat(ctx.getBean(VisitMessagePublisher.class)).isInstanceOf(VisitMessageProducer.class);
                    assertThat(ctx).hasSingleBean(VisitRecorder.class);
                    assertThat(ctx.getBean(VisitRecorder.class)).isInstanceOf(AsyncVisitRecorder.class);
                });
    }

    @Test
    void defaultTypeIsRabbit() {
        // 不显式给 type，默认也应是 rabbit（matchIfMissing），保证与 P4 行为一致
        runner.withPropertyValues("link.core.mq.enabled=true")
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(VisitMessageProducer.class)
                .withBean(AsyncVisitRecorder.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(VisitMessagePublisher.class);
                    assertThat(ctx.getBean(VisitMessagePublisher.class)).isInstanceOf(VisitMessageProducer.class);
                });
    }

    @Test
    void disabledWiresSyncRecorderAndNoPublisher() {
        runner.withPropertyValues("link.core.mq.enabled=false")
                .withBean(StatsService.class, () -> mock(StatsService.class))
                .withBean(StatsSnapshotService.class, () -> mock(StatsSnapshotService.class))
                .withBean(SyncVisitRecorder.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(VisitRecorder.class);
                    assertThat(ctx.getBean(VisitRecorder.class)).isInstanceOf(SyncVisitRecorder.class);
                    // 同步路径不该装配任何 MQ 发布实现
                    assertThat(ctx).doesNotHaveBean(VisitMessagePublisher.class);
                });
    }

    @Test
    void kafkaTypeWiresKafkaPublisherAndAsyncRecorder() {
        runner.withPropertyValues("link.core.mq.enabled=true", "link.core.mq.type=kafka")
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(KafkaVisitMessagePublisher.class)
                .withBean(AsyncVisitRecorder.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(VisitMessagePublisher.class);
                    assertThat(ctx.getBean(VisitMessagePublisher.class)).isInstanceOf(KafkaVisitMessagePublisher.class);
                    assertThat(ctx).hasSingleBean(VisitRecorder.class);
                });
    }

    @Test
    void invalidMqTypeFailsFast() {
        // 直接验证校验逻辑（ApplicationContextRunner 的 withBean(LinkCoreProperties.class) 不做属性绑定）
        LinkCoreProperties properties = new LinkCoreProperties();
        properties.getMq().setType("rocketmq");

        assertThatThrownBy(properties::validateMqType)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("link.core.mq.type");
    }
}
