package com.aaf.link.core.open;

import com.aaf.link.common.result.Result;
import com.aaf.link.core.dto.LinkCreateReqDTO;
import com.aaf.link.core.dto.LinkRespDTO;
import com.aaf.link.core.service.LinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 入口（P4）：供第三方系统通过 API Key + 签名调用。
 *
 * <p>鉴权由 {@link OpenApiAuthInterceptor} 完成（签名 + 防重放 + 配额），并注入租户上下文；
 * 本控制器只做业务转发。租户归属 = API Key 的 tenant_id（多租户拦截器自动过滤）。
 */
@RestController
@RequestMapping("/open/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final LinkService linkService;

    /**
     * 创建短链（第三方 API）
     */
    @PostMapping("/link/create")
    public Result<LinkRespDTO> createLink(@RequestBody @Valid LinkCreateReqDTO reqDTO) {
        return Result.success(linkService.createLink(reqDTO));
    }
}
