package com.aaf.link.core.mq;

import com.aaf.link.core.mq.message.VisitMessage;

/**
 * 访问消息发布抽象（P5：MQ 可切换的接缝）。
 *
 * <p>RabbitMQ 与 Kafka 各一套实现，由 {@code link.core.mq.type} 选择装配哪一个：
 * <ul>
 *   <li>{@code com.aaf.link.core.mq.producer.VisitMessageProducer}（RabbitMQ，基线）</li>
 *   <li>{@code com.aaf.link.core.mq.producer.KafkaVisitMessagePublisher}（Kafka）</li>
 * </ul>
 *
 * <p><b>契约</b>：实现<b>必须自行吞掉所有异常</b>——统计不是钱，MQ 故障绝不能让跳转失败。
 */
public interface VisitMessagePublisher {

    /**
     * 投递一条访问消息（实现内部保证不向调用方抛异常）
     */
    void publish(VisitMessage message);
}
