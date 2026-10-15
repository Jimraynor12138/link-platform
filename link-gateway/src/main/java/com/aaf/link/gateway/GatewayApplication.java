package com.aaf.link.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类（P0 骨架）。
 *
 * P3 再接入：路由、JWT 前置校验、限流、白名单。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
