package com.aaf.link.core.config;

import com.aaf.link.common.constant.CommonConstants;
import com.aaf.link.common.context.TenantContext;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

/**
 * core 的多租户 SQL 处理器。
 *
 * <p>语义：
 * <ul>
 *   <li><b>有租户上下文（非平台）</b>：{@code ignoreTable} 对业务表返回 false，拦截器用
 *       {@code getTenantId()} 给管理 SQL 自动拼 {@code tenant_id = ?}，实现隔离。</li>
 *   <li><b>无上下文 / 平台级（tenant_id=0）</b>：{@code ignoreTable} 对所有表返回 true ——
 *       拦截器<b>不拼接</b>过滤条件（平台管理员看全量；访客跳转/定时任务无上下文也不过滤）。</li>
 * </ul>
 *
 * <p><b>关键坑</b>：不能靠"让 {@code getTenantId()} 返回 null"来表达"不过滤"——MP 会把 null
 * 生成成 {@code tenant_id = NULL}（SQL 恒假，结果为空）。必须在 {@code ignoreTable} 返回 true
 * 才会真正跳过过滤。
 *
 * <p><b>注意</b>：跳转查询（按 code 查短码）在"无上下文"时天然不过滤，但为防御
 * "登录用户误带租户上下文点击他人短链导致跨租户 404"，跳转查询方法仍显式标注
 * {@code @InterceptorIgnore(tenantLine = "true")} 强制豁免（见 LinkMapper）。
 */
public class CoreTenantLineHandler implements TenantLineHandler {

    /** 没有 tenant_id 字段的表：拦截器不得触碰 */
    private static final Set<String> IGNORE_TABLES = Set.of("t_sequence", "t_segment");

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId == CommonConstants.PLATFORM_TENANT_ID) {
            return null;
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        Long tenantId = TenantContext.getTenantId();
        // 无租户上下文 / 平台级：忽略所有表的租户过滤（平台管理员看全量）
        if (tenantId == null || tenantId == CommonConstants.PLATFORM_TENANT_ID) {
            return true;
        }
        return IGNORE_TABLES.contains(tableName.toLowerCase());
    }
}
