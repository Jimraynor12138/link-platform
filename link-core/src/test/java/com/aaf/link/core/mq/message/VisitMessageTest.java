package com.aaf.link.core.mq.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 消息构造测试（P5 从生产者测试迁移而来：buildMessage 已上移为 MQ 无关的静态工厂）。
 * 断言与原 {@code VisitMessageProducerTest.buildMessageFillsAllFields} 完全一致，未削弱。
 */
class VisitMessageTest {

    @Test
    void createFillsAllFields() {
        VisitMessage message = VisitMessage.create("abc123", "1.2.3.4", "UA", "https://www.baidu.com");

        assertNotNull(message.getVisitId());
        assertEquals("abc123", message.getCode());
        assertEquals("1.2.3.4", message.getClientIp());
        assertEquals("UA", message.getUserAgent());
        assertEquals("https://www.baidu.com", message.getReferer());
        assertNotNull(message.getVisitTime());
    }

    @Test
    void createGeneratesDistinctVisitIds() {
        VisitMessage a = VisitMessage.create("abc123", "1.2.3.4", "UA", null);
        VisitMessage b = VisitMessage.create("abc123", "1.2.3.4", "UA", null);

        assertNotEquals(a.getVisitId(), b.getVisitId());
    }
}
