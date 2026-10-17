package com.aaf.link.core.mq;

import com.aaf.link.core.config.LinkCoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

import java.nio.ByteBuffer;

/**
 * Kafka 的「重试主题 + 死信主题」路由（P5）。
 *
 * <p><b>为什么需要它</b>：Kafka 没有 RabbitMQ 那种原生死信队列（DLX）。Spring Kafka 的
 * {@code DeadLetterPublishingRecoverer} 默认只会发到 {@code <原topic>.DLT}，做不出
 * "先重试几次、再进死信"的分级。这里手写 recoverer，把分级逻辑显式写清楚。
 *
 * <p>路由规则（{@code x-retry-count} 头记录已重试次数）：
 * <pre>
 * retryCount < maxRetry  → 发到 retry topic，头里 retryCount+1
 * retryCount >= maxRetry → 发到 dlt topic（终态）
 * </pre>
 * 例：maxRetry=3 时，初次消费 + 3 次重试 = 4 次机会，之后进死信。
 *
 * <p>与 RabbitMQ 的对应关系：Rabbit 的 {@code x-dead-letter-exchange} + 队列重试次数，
 * 在这里换成"显式转投 topic + header 计数"。
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaRetryRecoverer implements ConsumerRecordRecoverer {

    /** 重试次数头（int，大端） */
    public static final String RETRY_COUNT_HEADER = "x-retry-count";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final LinkCoreProperties properties;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception ex) {
        int retryCount = retryCount(record);
        int maxRetry = properties.getMq().getMaxRetry();

        if (retryCount < maxRetry) {
            int next = retryCount + 1;
            log.warn("Kafka 消费失败，转投重试主题({}/{}): key={}, err={}",
                    next, maxRetry, record.key(), ex.getMessage());
            republish(properties.getMq().getRetryTopic(), record, next);
        } else {
            log.error("Kafka 消费重试超限，转投死信主题: key={}, err={}",
                    record.key(), ex.getMessage());
            republish(properties.getMq().getDltTopic(), record, retryCount);
        }
    }

    /**
     * 读取当前已重试次数（无头视为 0）
     */
    static int retryCount(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(RETRY_COUNT_HEADER);
        if (header == null || header.value() == null) {
            return 0;
        }
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private void republish(String topic, ConsumerRecord<?, ?> record, int retryCount) {
        try {
            Object key = record.key();
            ProducerRecord<String, Object> out = new ProducerRecord<>(topic,
                    key == null ? null : String.valueOf(key), record.value());
            out.headers().add(new RecordHeader(RETRY_COUNT_HEADER,
                    ByteBuffer.allocate(Integer.BYTES).putInt(retryCount).array()));
            kafkaTemplate.send(out);
        } catch (Exception e) {
            // 转投失败意味着消息可能丢失：这里没有更好的兜底（无本地消息表），必须打 error 便于人工介入
            log.error("Kafka 重试/死信转投失败，消息可能丢失: topic={}, key={}", topic, record.key(), e);
        }
    }
}
