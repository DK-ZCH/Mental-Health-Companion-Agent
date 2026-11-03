package com.agent;

/**
 * Agent 对话请求体（Phase 4 / Step 11）。
 *
 * <p>刻意保持极简：Step 11 为单轮无状态调用，
 * <b>不</b>含 sessionId / conversationId / history（多轮属后续能力）。
 *
 * <p><b>不做参数校验注解</b>：项目当前未启用 Bean Validation（Phase 2 的 D4 决策），
 * 长度与空值校验由 {@link AgentController} 显式完成，避免引入新的校验契约。
 */
public class AgentChatRequest {

    /** 用户输入的自然语言消息 */
    private String message;

    /**
     * 会话 id（可选，Step ⑤ 多轮会话）。
     *
     * <p>缺省 ⇒ 服务端创建新会话并在响应中返回；
     * 提供时必须属于当前登录学生，否则 403（归属校验在 Controller）。
     * <b>它是资源 id，不是身份</b> —— 身份仍只来自服务端登录态。
     */
    private Long conversationId;

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
