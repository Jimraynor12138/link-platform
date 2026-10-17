package com.aaf.link.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 客户端配置的绑定回归测试（P5-1）。
 *
 * <p>背景：P2 把超时配置写成了 {@code feign.openfeign.client.config.*}——这不是合法的
 * Spring Cloud OpenFeign 路径，配置一直没生效（实际用默认 10s/60s），而且<b>启动不报错</b>，
 * 属于典型的"看起来配了其实没生效"。
 *
 * <p>这个测试直接解析真实的 {@code application.yml} 并绑定到 {@link FeignClientProperties}，
 * 路径一旦写错，{@code config} 里就不会有 {@code default} 键，测试立即失败。
 */
class FeignClientPropertiesBindingTest {

    @Test
    void bindsTimeoutAndLoggerLevelFromRealApplicationYml() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);

        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        FeignClientProperties properties = binder
                .bind("spring.cloud.openfeign.client", Bindable.of(FeignClientProperties.class))
                .orElseGet(FeignClientProperties::new);

        assertThat(properties.getConfig())
                .as("配置必须挂在 spring.cloud.openfeign 下；写在 feign.openfeign 下不会被绑定")
                .containsKey("default");

        FeignClientProperties.FeignClientConfiguration config = properties.getConfig().get("default");
        assertThat(config.getConnectTimeout()).isEqualTo(2000);
        assertThat(config.getReadTimeout()).isEqualTo(5000);
        // 用字符串断言，避免依赖具体枚举类型（实际类型是 feign.Logger.Level）
        assertThat(String.valueOf(config.getLoggerLevel())).isEqualTo("BASIC");
    }

    @Test
    void wrongPathFeignOpenfeignIsNotBound() {
        // 回归：feign.openfeign.* 是非法路径（P5-1 的根因），不应被绑定
        Map<String, Object> wrongPathSource = Map.of(
                "feign.openfeign.client.config.default.read-timeout", 5000);

        Binder binder = new Binder(new MapConfigurationPropertySource(wrongPathSource));
        FeignClientProperties properties = binder
                .bind("spring.cloud.openfeign.client", Bindable.of(FeignClientProperties.class))
                .orElseGet(FeignClientProperties::new);

        assertThat(properties.getConfig()).doesNotContainKey("default");
    }
}
