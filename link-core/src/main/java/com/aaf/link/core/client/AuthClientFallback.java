package com.aaf.link.core.client;

import com.aaf.link.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * link-auth 调用失败的降级实现（Sentinel Feign fallback）。
 *
 * <p>当调用 link-auth 超时/异常/被熔断时，返回<b>明确的失败结果</b>（而非"成功+降级标记"），
 * 让调用方能识别"降级"并决定如何兜底。
 *
 * <p><b>为什么是 fail-closed（返回失败）</b>：
 * <ul>
 *   <li>对验 API Key / 查租户配额这类<b>鉴权/配额</b>调用，下游不可用时若"假装成功"会放行未经验证的请求，
 *       属安全问题 —— 必须失败，宁可不可用也不能不安全。</li>
 *   <li>对探活这类读调用，失败结果也能让调用方立刻知道下游状态。</li>
 * </ul>
 *
 * <p>注意：这里演示的是 <b>fallback（降级）</b>；"熔断打开后快速失败"由 Sentinel 的
 * DegradeRule（慢调用/异常比例）驱动，可在 Dashboard 或代码里额外配置，二者不冲突。
 */
@Slf4j
@Component
public class AuthClientFallback implements AuthClient {

    @Override
    public Result<Map<String, Object>> ping() {
        log.warn("link-auth 调用降级（下游不可用），返回失败结果");
        return Result.failure("A0500", "link-auth 暂不可用，已降级");
    }
}
