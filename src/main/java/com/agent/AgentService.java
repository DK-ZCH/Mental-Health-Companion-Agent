package com.agent;

import java.util.Map;

/**
 * Agent 编排（Phase 4 / Step 11）。
 *
 * <p><b>职责（仅三件）</b>：
 * <ol>
 *   <li>把已认证的 {@code userId} 装入 {@link org.springframework.ai.chat.model.ToolContext}；</li>
 *   <li>调用 {@link AgentModel}；</li>
 *   <li>返回模型文本。</li>
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

    private final AgentModel agentModel;
    private final AssessmentTools assessmentTools;

    public AgentService(AgentModel agentModel, AssessmentTools assessmentTools) {
        this.agentModel = agentModel;
        this.assessmentTools = assessmentTools;
    }

    /**
     * 单轮对话（无历史，Step 11 不做 ChatMemory）。
     *
     * @param message 用户输入（已由 Controller 校验长度与空值）
     * @param userId  已认证用户 id（来自 {@code CurrentUserProvider}）
     * @return 模型生成的回答
     */
    public String chat(String message, Integer userId) {
        if (userId == null) {
            // 编程契约：调用方必须先完成身份解析；此处仅防御性检查，不兜底
            throw new IllegalArgumentException("userId 不得为空");
        }
        return agentModel.answer(
                AgentPrompts.SYSTEM,
                message,
                assessmentTools,
                Map.of(AssessmentTools.CTX_USER_ID, userId)
        );
    }
}
