package com.aaf.link.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密钥配置的启动期校验（fail-fast）单测。
 */
class JwtPropertiesTest {

    @Test
    @DisplayName("未配置密钥：拒绝启动")
    void rejectsMissingSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(null);

        assertThatThrownBy(properties::validateJwtSecret)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("占位符未被解析（Spring 会原样保留）：拒绝启动")
    void rejectsUnresolvedPlaceholder() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("${LINK_AUTH_JWT_SECRET}");

        assertThatThrownBy(properties::validateJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("密钥强度不足 32 字节：拒绝启动")
    void rejectsTooShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("short-secret");

        assertThatThrownBy(properties::validateJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("合规密钥：通过校验")
    void acceptsStrongSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789abcdefghijklmn");

        assertThatCode(properties::validateJwtSecret).doesNotThrowAnyException();
    }
}
