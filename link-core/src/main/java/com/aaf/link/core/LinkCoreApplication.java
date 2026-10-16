package com.aaf.link.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 短链核心服务启动类（link-core）。
 *
 * EnableScheduling：统计快照按固定间隔批量落库（见 StatsSnapshotServiceImpl）
 * scanBasePackages：公共模块在 com.aaf.link.common 下，默认扫描（com.aaf.link.core）扫不到它，
 *                   因此显式指定 com.aaf.link —— 否则全局异常处理器、租户上下文等 Bean 会失效
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.aaf.link")
public class LinkCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkCoreApplication.class, args);
    }
}
