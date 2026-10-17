package com.aaf.link.core.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 访问统计消息（V2）。消息只放原始事实（IP/UA/Referer），解析放在消费端。
 *
 * <p><b>P5 说明</b>：消息构造抽成静态工厂 {@link #create}，让 RabbitMQ 与 Kafka 两套发布实现
 * 共享同一份"visitId 生成 + visitTime 取值"逻辑，避免两处各写一份而漂移。
 */
@Data
public class VisitMessage implements Serializable {

    /** 消息唯一 ID，消费端幂等去重依据 */
    private String visitId;
    /** 短链码 */
    private String code;
    /** 访问者 IP */
    private String clientIp;
    /** 客户端 UA */
    private String userAgent;
    /** 访问来源 */
    private String referer;
    /** 访问时间（消费端按它归入统计日期，跨零点仍归访问当天） */
    private LocalDateTime visitTime;

    /**
     * 构造一条访问消息（与 MQ 实现无关）
     *
     * @param code      短码
     * @param clientIp  访问者 IP
     * @param userAgent 客户端 UA
     * @param referer   来源
     */
    public static VisitMessage create(String code, String clientIp, String userAgent, String referer) {
        VisitMessage message = new VisitMessage();
        message.setVisitId(UUID.randomUUID().toString());
        message.setCode(code);
        message.setClientIp(clientIp);
        message.setUserAgent(userAgent);
        message.setReferer(referer);
        message.setVisitTime(LocalDateTime.now());
        return message;
    }
}
