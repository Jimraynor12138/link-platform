package com.aaf.link.core.mq.producer;

import com.aaf.link.core.config.LinkCoreProperties;
import com.aaf.link.core.mq.message.VisitMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * RabbitMQ 发布实现测试（P5：send → publish，实现 VisitMessagePublisher）。
 *
 * <p>两条断言与原测试一致：交换机/路由键取配置、投递失败吞异常（不影响跳转）。
 * 消息构造断言已迁移到 {@code VisitMessageTest}（buildMessage 上移为静态工厂）。
 */
@ExtendWith(MockitoExtension.class)
class VisitMessageProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private VisitMessageProducer producer;

    @BeforeEach
    void setUp() {
        LinkCoreProperties properties = new LinkCoreProperties();
        producer = new VisitMessageProducer(rabbitTemplate, properties);
    }

    @Test
    void publishUsesConfiguredExchangeAndRoutingKey() {
        VisitMessage message = VisitMessage.create("abc123", "1.2.3.4", "UA", null);

        producer.publish(message);

        verify(rabbitTemplate).convertAndSend("link.visit.exchange", "link.visit", message);
    }

    @Test
    void publishFailureDoesNotThrow() {
        VisitMessage message = VisitMessage.create("abc123", "1.2.3.4", "UA", null);
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> producer.publish(message));
    }
}
