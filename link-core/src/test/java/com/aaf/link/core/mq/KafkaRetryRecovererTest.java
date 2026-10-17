package com.aaf.link.core.mq;

import com.aaf.link.core.config.LinkCoreProperties;
import com.aaf.link.core.mq.message.VisitMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Kafka 重试/死信路由测试（P5）：未超限进 retry topic 且计数+1；超限进 DLT。
 */
@ExtendWith(MockitoExtension.class)
class KafkaRetryRecovererTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaRetryRecoverer recoverer;

    @BeforeEach
    void setUp() {
        LinkCoreProperties properties = new LinkCoreProperties();
        properties.getMq().setMaxRetry(3);
        recoverer = new KafkaRetryRecoverer(kafkaTemplate, properties);
    }

    @Test
    void firstFailureGoesToRetryTopicWithIncrementedCount() {
        ConsumerRecord<String, Object> record = record(0);

        recoverer.accept(record, new RuntimeException("boom"));

        ProducerRecord<String, Object> out = captureSent();
        assertThat(out.topic()).isEqualTo("link.visit.retry");
        assertThat(retryCountOf(out)).isEqualTo(1);
    }

    @Test
    void lastRetryStillGoesToRetryTopic() {
        // 已重试 2 次，maxRetry=3 → 仍应重试（第 3 次）
        ConsumerRecord<String, Object> record = record(2);

        recoverer.accept(record, new RuntimeException("boom"));

        ProducerRecord<String, Object> out = captureSent();
        assertThat(out.topic()).isEqualTo("link.visit.retry");
        assertThat(retryCountOf(out)).isEqualTo(3);
    }

    @Test
    void overLimitGoesToDlt() {
        // 已重试 3 次 = maxRetry → 进死信
        ConsumerRecord<String, Object> record = record(3);

        recoverer.accept(record, new RuntimeException("boom"));

        ProducerRecord<String, Object> out = captureSent();
        assertThat(out.topic()).isEqualTo("link.visit.dlt");
        assertThat(retryCountOf(out)).isEqualTo(3);
    }

    private ConsumerRecord<String, Object> record(int retryCount) {
        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>("link.visit", 0, 0L, "visit-1", new VisitMessage());
        if (retryCount > 0) {
            record.headers().add(new RecordHeader(KafkaRetryRecoverer.RETRY_COUNT_HEADER,
                    ByteBuffer.allocate(Integer.BYTES).putInt(retryCount).array()));
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, Object> captureSent() {
        ArgumentCaptor<ProducerRecord<String, Object>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private int retryCountOf(ProducerRecord<String, Object> record) {
        return ByteBuffer.wrap(record.headers().lastHeader(KafkaRetryRecoverer.RETRY_COUNT_HEADER).value()).getInt();
    }
}
