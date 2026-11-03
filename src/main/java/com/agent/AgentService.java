package com.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Agent 编排（Phase 4 / Step 11）。
 *
 * <p><b>职责（仅三件）</b>：
 * <ol>
 *   <li>把已认证的<b>二元身份</b>（{@link AgentIdentity}：userId + userTable）
 *       装入 {@link org.springframework.ai.chat.model.ToolContext}；</li>
 *   <li>调用 {@link AgentModel}；</li>
 *   <li>按 {@link SafetyRules} 判定是否命中，命中则<b>追加</b> {@link SafetyResources} 指引，
 *       并返回 {@link AgentReply}（回答 + 结构化安全状态）。</li>
 * </ol>
 *
 * <p><b>刻意不做</b>：
 * <ul>
 *   <li><b>不接触 {@code HttpServletRequest}</b> —— 身份在 Controller 层已转成纯 {@code Integer}，
 *       本层因此可在无 Web 上下文的场景（如测试、后台任务）复用；</li>
 *   <li>不组装 Prompt 文本（固定在 {@link AgentPrompts}）；</li>
 *   <li>不查业务数据（由 Tool 负责）；</li>
 *   <li>不做异常转换（由 Controller 统一处理）。</li>
 * </ul>
 */
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final AgentModel agentModel;

    /** 确定性输入规则（Round 3 · Q3 常量类）*/
    private final SafetyRules safetyRules;

    /** 危机转介资源（Round 3 · Q2）：未确认时只给无号码指引 */
    private final SafetyResources safetyResources;

    /**
     * 本轮可用的全部 Tool 对象（Phase 4 / Round 1 起为多个）。
     *
     * <p>刻意用 {@code Object[]} 而非具体类型：Tool 数量会随批次增长，
     * 每次新增都改构造器签名会牵连所有调用点；保持 {@code Object...} 可扩展。
     * <b>不</b>在此处做任何 Tool 过滤/权限判断 —— 域校验收在 {@code AgentToolGuard}。
     */
    private final Object[] tools;

    public AgentService(AgentModel agentModel, SafetyRules safetyRules, SafetyResources safetyResources,
                        Object... tools) {
        if (safetyRules == null || safetyResources == null) {
            // fail-closed：缺安全组件时【不允许】退化成「没有兜底」的 Agent ——
            // 那会让「忘记装配」变成静默的安全降级。
            throw new IllegalArgumentException("safetyRules / safetyResources 不得为空");
        }
        this.agentModel = agentModel;
        this.safetyRules = safetyRules;
        this.safetyResources = safetyResources;
        this.tools = tools == null ? new Object[0] : tools.clone();
    }

    /**
     * 单轮对话（无历史，Step 11 不做 ChatMemory）。
     *
     * <p><b>Step 12 起身份为二元组</b>：{@link AgentIdentity}（{@code userId} + {@code userTable}）
     * 一并写入 {@code ToolContext}。只传 id 会让 Tool 无法区分
     * 「学生 1」与「咨询师 1」—— 这正是 Step 11 串读缺陷的根源。
     *
     * @param message  用户输入（已由 Controller 校验长度与空值）
     * @param identity 已认证身份（来自 {@code CurrentUserProvider}）
     * @return 模型生成的回答
     */
    public AgentReply chat(String message, AgentIdentity identity) {
        if (identity == null) {
            // 编程契约：调用方必须先完成身份解析；此处仅防御性检查，不兜底
            throw new IllegalArgumentException("identity 不得为空");
        }
        String modelAnswer = agentModel.answer(
                AgentPrompts.SYSTEM,
                message,
                tools,
                Map.of(
                        AgentIdentity.CTX_USER_ID, identity.userId(),
                        AgentIdentity.CTX_USER_TABLE, identity.userTable()
                )
        );

        SafetyRules.SafetyDecision decision = safetyRules.evaluate(message);
        SafetyNotice safety = new SafetyNotice(decision.triggered(), safetyResources.status());

        if (!decision.triggered()) {
            return new AgentReply(modelAnswer == null ? "" : modelAnswer, safety);
        }

        // 只记录规则 id：不记录用户原文（隐私），也不把规则 id 返回给客户端（防绕过）
        logger.info("[AGENT][SAFETY] 命中安全兜底 rule={} resourceStatus={}",
                decision.ruleId(), safety.resourceStatus());

        return new AgentReply(appendResources(modelAnswer), safety);
    }

    /**
     * 在模型回答<b>之后</b>追加资源指引。
     *
     * <p><b>为什么在服务端拼接、而不是写进 Prompt</b>：
     * 写进 Prompt 时，模型拒答、被注入或「忘记」都会让资源消失；
     * 服务端追加则与模型输出无关 —— 这是本轮要证明的核心。
     *
     * <p><b>为什么是追加而非替换</b>：用户的正常回答不应被安全文案覆盖，
     * 否则会给人「刚说的话被系统顶掉了」的体验。
     */
    private String appendResources(String modelAnswer) {
        String base = modelAnswer == null ? "" : modelAnswer.stripTrailing();
        String card = safetyResources.guidanceText();
        return base.isEmpty() ? card : base + "\n\n" + card;
    }
}
