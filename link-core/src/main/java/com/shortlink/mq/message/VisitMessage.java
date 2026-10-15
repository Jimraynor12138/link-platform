package com.shortlink.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访问统计消息（V2）。消息只放原始事实（IP/UA/Referer），解析放在消费端。
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
    /** 访问时间 */
    private LocalDateTime visitTime;
}
