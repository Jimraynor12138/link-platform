package com.aaf.link.core.client;

import com.aaf.link.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 调用 link-auth 的 Feign 客户端。
 *
 * <p><b>P2 阶段说明</b>：这里只有探活接口，<b>不是业务功能</b>。
 * 它存在的意义是验证"注册发现 + 按服务名调用 + 超时/重试/日志/错误解码"这条通路是通的。
 * 真实的业务调用（如校验租户配额、查询租户状态）在 P4 才会长出来。
 *
 * <p>用 {@code name = "link-auth"}（服务名）而不是 {@code url}，才会走注册发现 + 负载均衡。
 */
@FeignClient(name = "link-auth", path = "/internal")
public interface AuthClient {

    /**
     * 探测下游是否可达
     */
    @GetMapping("/ping")
    Result<Map<String, Object>> ping();
}
