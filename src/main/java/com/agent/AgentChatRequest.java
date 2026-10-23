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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
