package com.agent;

/**
 * Agent 一次对话的完整结果（Phase 4 / Round 3 起）。
 *
 * <p><b>为什么不再返回裸 {@code String}</b>：安全兜底需要同时表达
 * 「最终回答（可能已追加资源）」与「是否触发」。
 * 若只返回字符串，Controller 无法在响应体里给出结构化标记，
 * 测试也只能靠字符串匹配，无法区分「模型自己写了资源」与「服务端追加了资源」。
 *
 * @param answer 最终回答（模型文本 + 服务端追加的资源）
 * @param safety 触发状态与资源状态
 */
public record AgentReply(String answer, SafetyNotice safety) {
}
