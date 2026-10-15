package com.aaf.link.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证授权启动类（P0 骨架）。
 *
 * P1 再接入：租户 / 用户 / 角色 / 权限、双 Token、API Key。
 */
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
