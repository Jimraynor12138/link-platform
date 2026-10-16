package com.aaf.link.core.client;

import com.aaf.link.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * link-auth 调用失败的降级实现（Sentinel Feign fallback）。
 *
 * <p>当调用 link-auth 超时/异常/被熔断时，返回兜底结果而不是把异常抛给调用方——
 * 这是"服务间调用的优雅降级"：<b>下游挂了，本服务仍可用</b>。
 *
 * <p>注意：这里演示的是 <b>fallback（降级）</b>；"熔断打开后快速失败"由 Sentinel 的
 * DegradeRule（慢调用/异常比例）驱动，可在 Dashboard 或代码里额外配置，二者不冲突。
 */
@Slf4j
@Component
public class AuthClientFallback implements AuthClient {

    @Override
    public Result<Map<String, Object>> ping() {
        log.warn("link-auth 调用降级（下游不可用），返回兜底结果");
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("service", "link-auth");
        fallback.put("instance", "UNAVAILABLE");
        fallback.put("degraded", true);
        return Result.success(fallback);
    }
}
