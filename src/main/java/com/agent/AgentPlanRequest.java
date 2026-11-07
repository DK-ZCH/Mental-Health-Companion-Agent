package com.agent;

/**
 * Step 25：{@code POST /agent/plan} 请求体（P-4 冻结：独立 DTO，不改 {@link AgentChatRequest}）。
 *
 * <p><b>刻意不启用 Bean Validation</b>：沿用 Phase 2 的 D4 决策，
 * 校验在 {@link AgentController} 显式完成，避免引入新的校验契约。
 *
 * <p><b>字段都不是身份</b>：身份只来自服务端登录态；
 * {@code action} 只是意图声明，{@code conversationId} 是资源 id 且须校验归属。
 */
public class AgentPlanRequest {

    /** PREVIEW / CONFIRM / DISCARD（白名单由 Controller 显式校验） */
    private String action;

    /** 用户目标；仅 PREVIEW 必填、非空、≤1000 字 */
    private String message;

    /** 会话 id；CONFIRM / DISCARD 必填，用于草稿绑定校验 */
    private Long conversationId;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }
}
