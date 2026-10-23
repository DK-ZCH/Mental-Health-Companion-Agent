package com.agent;

import java.util.Map;

/**
 * Agent 模型调用边界（Phase 4 / Step 11）。
 *
 * <p><b>为什么需要这一层</b>：把「框架如何调用 LLM」与「Agent 编排逻辑」隔开，
 * 使回归测试可以注入替身，<b>绝不发起真实 DeepSeek 请求</b>
 * （避免花钱 / 限流 / 网络波动污染 63+ 项回归）。
 *
 * <p><b>命名约束</b>：实现类不得命名为 {@code DeepSeekXxxAgentModel} 之类把厂商写死；
 * 厂商细节只存在于本接口的实现中（当前为 {@link SpringAiAgentModel}）。
 *
 * <p><b>职责边界（严格）</b>：本层只负责「把 4 个入参交给模型并取回文本」，
 * <b>不</b>做身份判断、<b>不</b>查业务数据、<b>不</b>组装 Prompt 内容。
 */
public interface AgentModel {

    /**
     * 发起一次单轮对话（无多轮历史，Step 11 明确不做 ChatMemory）。
     *
     * @param systemPrompt 系统提示词（服务端固定常量，不含用户数据）
     * @param userMessage  用户输入（已做长度校验）
     * @param tools        挂载的工具对象（当前为 {@code AssessmentTools}）
     * @param toolContext  工具上下文（含 {@code userId}，<b>不会发送给模型</b>）
     * @return 模型生成的最终自然语言回答
     */
    String answer(String systemPrompt, String userMessage, Object tools, Map<String, Object> toolContext);
}
