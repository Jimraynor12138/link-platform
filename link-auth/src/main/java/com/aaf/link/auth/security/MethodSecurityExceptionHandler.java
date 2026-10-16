package com.aaf.link.auth.security;

import com.aaf.link.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级鉴权拒绝异常的处理（补 {@link RestAccessDeniedHandler} 覆盖不到的场景）。
 *
 * <p><b>为什么必须有这个类</b>：{@code @PreAuthorize} 抛出的 {@link AccessDeniedException}
 * 发生在 DispatcherServlet 内部 —— 比 Spring Security 的 ExceptionTranslationFilter 更靠里，
 * 因此不会被 {@link RestAccessDeniedHandler} 捕获。若不单独处理，它会被兜底的
 * {@code @ExceptionHandler(Exception.class)} 吃掉，返回 HTTP 200 + 业务错误码，
 * 前端会误判为"成功"，形成事实上的鉴权失效。
 *
 * <p>放在 auth 模块而非 common：common 的依赖白名单里没有 spring-security，不应为它引入。
 */
@Slf4j
@RestControllerAdvice
public class MethodSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("方法级鉴权拒绝: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.failure("A0403", "无权限访问"));
    }
}
