package com.aaf.link.core.mq.producer;

import com.aaf.link.core.config.LinkCoreProperties;
import com.aaf.link.core.mq.message.VisitMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka 发布实现测试（P5）：topic/key 正确、异常与异步失败都不影响调用方。
 */
@ExtendWith(MockitoExtension.class)
class KafkaVisitMessagePublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaVisitMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaVisitMessagePublisher(kafkaTemplate, new LinkCoreProperties());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishUsesConfiguredTopicAndVisitIdAsKey() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));
        VisitMessage message = VisitMessage.create("abc123", "1.2.3.4", "UA", null);

        publisher.publish(message);

        verify(kafkaTemplate).send("link.visit", message.getVisitId(), message);
    }

    @Test
    void publishSwallowsSynchronousException() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("broker down"));
        VisitMessage message = VisitMessage.create("abc123", "1.2.3.4", "UA", null);

        assertDoesNotThrow(() -> publisher.publish(message));
    }
}
