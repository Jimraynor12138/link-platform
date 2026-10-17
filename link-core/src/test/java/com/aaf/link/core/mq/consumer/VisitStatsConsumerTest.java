package com.aaf.link.core.mq.consumer;

import com.aaf.link.core.mq.message.VisitMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * RabbitMQ 消费者薄壳测试（P5）：只验证"取到消息 → 原样交给 handler"。
 *
 * <p>业务逻辑的 6 条断言已完整迁移到 {@link VisitMessageHandlerTest}，此处不重复、也不削弱。
 * 这里补的是"转发"本身——避免薄壳改错导致消息根本没进 handler。
 */
@ExtendWith(MockitoExtension.class)
class VisitStatsConsumerTest {

    @Mock
    private VisitMessageHandler handler;

    private VisitStatsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new VisitStatsConsumer(handler);
    }

    @Test
    void delegatesMessageToHandler() {
        VisitMessage message = VisitMessage.create("abc123", "1.2.3.4", "UA", null);

        consumer.onVisit(message);

        verify(handler).handle(message);
    }

    @Test
    void delegatesNullMessageToHandler() {
        // 空消息的"丢弃"语义在 handler 里，薄壳应原样透传
        consumer.onVisit(null);

        verify(handler).handle(null);
    }
}
