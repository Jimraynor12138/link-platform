package com.aaf.link.core.config;

import com.aaf.link.common.context.TenantContext;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多租户 SQL 处理器单测：验证"有租户→过滤、无上下文/平台级→不过滤、无 tenant_id 字段的表豁免"。
 */
class CoreTenantLineHandlerTest {

    private final CoreTenantLineHandler handler = new CoreTenantLineHandler();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void noContextReturnsNull() {
        assertThat(handler.getTenantId()).isNull();
    }

    @Test
    void platformTenantReturnsNull() {
        TenantContext.setTenantId(0L);
        assertThat(handler.getTenantId()).isNull();
    }

    @Test
    void tenantReturnsLongValue() {
        TenantContext.setTenantId(5L);
        LongValue value = (LongValue) handler.getTenantId();
        assertThat(value.getValue()).isEqualTo(5L);
    }

    @Test
    void ignoresTablesWithoutTenantColumn() {
        assertThat(handler.ignoreTable("t_sequence")).isTrue();
        assertThat(handler.ignoreTable("t_segment")).isTrue();
        assertThat(handler.ignoreTable("t_link")).isFalse();
        assertThat(handler.ignoreTable("t_link_stats")).isFalse();
        assertThat(handler.ignoreTable("t_group")).isFalse();
    }

    @Test
    void tenantIdColumnNameIsTenantId() {
        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");
    }
}
