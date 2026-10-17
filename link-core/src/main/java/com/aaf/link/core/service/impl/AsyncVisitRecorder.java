package com.aaf.link.core.service.impl;

import com.aaf.link.core.mq.VisitMessagePublisher;
import com.aaf.link.core.mq.message.VisitMessage;
import com.aaf.link.core.service.VisitRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MQ 异步记录（V2 正常路径，P5 改为依赖发布抽象）。
 *
 * <p>跳转链路只投递消息，UA/Referer 解析与统计写入都在消费端完成。
 * 具体走 RabbitMQ 还是 Kafka 由 {@code link.core.mq.type} 决定（本类不感知）。
 *
 * <p>收益：跳转 RT 不再包含统计开销（V1 压测显示同步统计让 QPS 掉了 30%+）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "link.core.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncVisitRecorder implements VisitRecorder {

    private final VisitMessagePublisher visitMessagePublisher;

    @Override
    public void record(String code, String clientIp, String userAgent, String referer) {
        visitMessagePublisher.publish(VisitMessage.create(code, clientIp, userAgent, referer));
    }
}
