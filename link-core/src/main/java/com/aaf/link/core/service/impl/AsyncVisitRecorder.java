package com.aaf.link.core.service.impl;

import com.aaf.link.core.mq.producer.VisitMessageProducer;
import com.aaf.link.core.service.VisitRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MQ 异步记录（V2 正常路径）：跳转链路只投递消息，UA/Referer 解析与统计写入都在消费端完成。
 *
 * 收益：跳转 RT 不再包含统计开销（V1 压测显示同步统计让 QPS 掉了 30%+）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "link.core.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncVisitRecorder implements VisitRecorder {

    private final VisitMessageProducer visitMessageProducer;
//    visitRecorder 是访问记录器，职责是采集这次访问的信息，并交给下游处理。它通常会：
//
//    从 request 里提取 IP、User-Agent、Referer、Cookie 等
//
//    组装成一条访问记录对象（比如 VisitMessage）
//
//    调用 producer.send(...) 把这条记录发到 MQ

    @Override
    public void record(String code, String clientIp, String userAgent, String referer) {
//        producer.send(...)这是消息生产者，职责是把消息封装好，决定发到哪个交换机、用什么路由键。
        visitMessageProducer.send(visitMessageProducer.buildMessage(code, clientIp, userAgent, referer));
    }
}
