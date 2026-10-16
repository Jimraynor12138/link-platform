package com.aaf.link.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证授权服务启动类（link-auth）。
 *
 * scanBasePackages：公共模块在 com.aaf.link.common 下，默认扫描（com.aaf.link.auth）扫不到它，
 *                   因此显式指定 com.aaf.link —— 否则统一返回、全局异常处理器、租户上下文等 Bean 会失效
 */
@SpringBootApplication(scanBasePackages = "com.aaf.link")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
