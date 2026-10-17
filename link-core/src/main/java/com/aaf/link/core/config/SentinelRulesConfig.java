package com.aaf.link.core.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务层 Sentinel 规则（代码加载，Dashboard 可观察/覆盖）。
 *
 * <p>这里只做"服务细粒度"的 QPS 限流（对应"两层限流"的服务层）；网关入口限流见网关的
 * {@code SentinelGatewayConfig}。
 *
 * <p>规则先写进代码，是为了<b>可复现 + 不依赖 Dashboard 也能跑</b>（Dashboard 默认规则存内存、重启丢）；
 * Dashboard 的价值是"可视化 + 动态调参"。
 */
@Configuration
public class SentinelRulesConfig {

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        // 创建短链：QPS=10（P6 从 1 上调——后台连续创建会被限流挡住）。
        // 演示"限流生效"时可临时调回 1。
        FlowRule createLinkRule = new FlowRule("createLink");
        createLinkRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        createLinkRule.setCount(10);
        rules.add(createLinkRule);

        FlowRuleManager.loadRules(rules);
    }
}
