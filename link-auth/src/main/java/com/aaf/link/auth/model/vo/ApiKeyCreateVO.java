package com.aaf.link.auth.model.vo;

import lombok.Data;

/**
 * 生成 API Key 的返回（P4）。secret 仅在此次返回中可见，之后无法再取回。
 */
@Data
public class ApiKeyCreateVO {

    private String accessKey;

    private String secret;

    /** 提示语 */
    private String notice;
}
