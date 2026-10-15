package com.aaf.link.stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统计服务启动类（P0 骨架）。
 *
 * P5 再接入：统计消费与看板查询（统计代码届时从 link-core 抽出）。
 */
@SpringBootApplication
public class StatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatsApplication.class, args);
    }
}
