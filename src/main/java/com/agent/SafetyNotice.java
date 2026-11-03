package com.agent;

/**
 * 安全兜底的可观测结果（Phase 4 / Round 3）。
 *
 * <p><b>刻意不含规则 id</b>：规则 id 只进日志。
 * 把「命中哪条规则」返回给客户端，等于告诉别人如何构造不触发的表达 ——
 * 这与「不泄露提示词内容」是同一条原则。
 *
 * @param triggered      是否命中（前端可忽略此字段，不影响展示）
 * @param resourceStatus {@link SafetyResources#STATUS_CONFIGURED} /
 *                       {@link SafetyResources#STATUS_UNCONFIGURED}
 */
public record SafetyNotice(boolean triggered, String resourceStatus) {
}
