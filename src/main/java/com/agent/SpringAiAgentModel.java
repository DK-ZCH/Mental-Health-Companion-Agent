package com.agent;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * Spring AI 实现（Phase 4 / Step 11）。
 *
 * <p>唯一职责：把 {@link AgentModel} 的 4 个入参转成一次 {@code ChatClient} 调用。
 * 厂商差异（DeepSeek）封装在此处，上层 {@link AgentService} 无感知。
 *
 * <p><b>工具执行位置</b>：Spring AI 1.x 的工具循环在 {@code ChatModel} 内部完成
 * （{@code ChatClient} 只负责组装 {@code ToolCallback} 与 {@code ToolContext}），
 * 因此本类无需任何 advisor 配置。
 *
 * <p><b>实测依据</b>（见 {@code PHASE4-STEP11-PREFLIGHT.md}）：
 * {@code .tools(Object...)} / {@code .toolContext(Map)} 在 1.1.8 上可用；
 * {@code ToolContext} 内容不进入模型视野。
 */
public class SpringAiAgentModel implements AgentModel {

    private final ChatClient chatClient;

    public SpringAiAgentModel(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String answer(String systemPrompt, String userMessage, Object tools, Map<String, Object> toolContext) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(tools)
                .toolContext(toolContext)
                .call()
                .content();
    }
}
