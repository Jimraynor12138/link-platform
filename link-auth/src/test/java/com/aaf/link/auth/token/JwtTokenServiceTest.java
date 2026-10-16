package com.aaf.link.auth.token;

import com.aaf.link.auth.config.JwtProperties;
import com.aaf.link.auth.model.dto.LoginUser;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 令牌服务单测：证明"租户标识与 jti 在签发/解析两端一致"。
 */
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private LoginUser user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789abcdefghijklmn");
        properties.setExpiration(1800L);
        properties.setRefreshExpiration(604800L);
        properties.setTokenHeader("Authorization");
        properties.setTokenHead("Bearer ");
        jwtTokenService = new JwtTokenService(properties);

        user = new LoginUser();
        user.setId(7L);
        user.setTenantId(3L);
        user.setUsername("tenantadmin");
    }

    @Test
    void accessTokenCarriesIdentityTenantAndJti() {
        String token = jwtTokenService.generateAccessToken(user);

        assertThat(jwtTokenService.validateAccessToken(token)).isTrue();
        assertThat(jwtTokenService.validateRefreshToken(token)).isFalse();
        assertThat(jwtTokenService.getUsername(token)).isEqualTo("tenantadmin");
        assertThat(jwtTokenService.getUserId(token)).isEqualTo(7L);
        // 租户标识两端一致：签发时写入 3，解析出来仍是 3
        assertThat(jwtTokenService.getTenantId(token)).isEqualTo(3L);
        assertThat(jwtTokenService.getTokenId(token)).isNotBlank();
        assertThat(jwtTokenService.getRemainingExpirationMillis(token)).isGreaterThan(0L);
    }

    @Test
    void refreshTokenIsTypedAsRefresh() {
        String token = jwtTokenService.generateRefreshToken(user);

        assertThat(jwtTokenService.validateRefreshToken(token)).isTrue();
        assertThat(jwtTokenService.validateAccessToken(token)).isFalse();
    }

    @Test
    void twoTokensHaveDifferentJti() {
        String first = jwtTokenService.generateAccessToken(user);
        String second = jwtTokenService.generateAccessToken(user);

        assertThat(jwtTokenService.getTokenId(first)).isNotEqualTo(jwtTokenService.getTokenId(second));
    }

    @Test
    void tamperedOrMalformedTokenIsRejected() {
        String token = jwtTokenService.generateAccessToken(user);

        assertThat(jwtTokenService.validateAccessToken(token + "x")).isFalse();
        assertThat(jwtTokenService.getTokenId("not-a-token")).isNull();
        assertThat(jwtTokenService.getUserId("not-a-token")).isNull();
        assertThat(jwtTokenService.getRemainingExpirationMillis("not-a-token")).isZero();
    }

    @Test
    void tooShortSecretIsRejected() {
        // 防呆：密钥不足 32 字节时 HS256 不可用，必须快速失败而不是悄悄用弱密钥签名
        JwtProperties weak = new JwtProperties();
        weak.setSecret("too-short");
        JwtTokenService weakService = new JwtTokenService(weak);

        assertThatThrownBy(() -> weakService.generateAccessToken(user))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void resolveTokenFromAuthorizationHeader() {
        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.addHeader("Authorization", "Bearer abc.def.ghi");
        assertThat(jwtTokenService.resolveToken(ok)).isEqualTo("abc.def.ghi");

        MockHttpServletRequest wrongPrefix = new MockHttpServletRequest();
        wrongPrefix.addHeader("Authorization", "Basic xxx");
        assertThat(jwtTokenService.resolveToken(wrongPrefix)).isNull();

        assertThat(jwtTokenService.resolveToken(new MockHttpServletRequest())).isNull();
    }
}
