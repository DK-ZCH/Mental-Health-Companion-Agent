package com.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

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
    private final ToolCallRecorder recorder;
    private final ChatMemory chatMemory;

    /** 回调缓存：AgentService 持有固定的 tools 数组，按其身份缓存，避免每轮反射重建 */
    private volatile Object[] cachedTools;
    private volatile ToolCallback[] cachedCallbacks;

    public SpringAiAgentModel(ChatClient chatClient, ToolCallRecorder recorder, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.recorder = recorder;
        this.chatMemory = chatMemory;
    }

    /**
     * 把工具对象转换为<b>包装后的</b>回调。
     *
     * <p>🔴 Spike 事实：{@code ChatClient.tools(Object...)} <b>不接受</b> {@link ToolCallback}
     * （会尝试扫描 {@code @Tool} 并失败），必须使用 {@code .toolCallbacks(...)}。
     */
    private ToolCallback[] callbacks(Object[] tools) {
        ToolCallback[] local = cachedCallbacks;
        if (local != null && cachedTools == tools) {
            return local;
        }
        if (tools == null || tools.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] built = MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build()
                .getToolCallbacks();

        ToolCallback[] wrapped = new ToolCallback[built.length];
        for (int i = 0; i < built.length; i++) {
            wrapped[i] = new TimedToolCallback(built[i], recorder);
        }
        cachedTools = tools;
        cachedCallbacks = wrapped;
        return wrapped;
    }

    /**
     * {@inheritDoc}
     *
     * <p>🔴 <b>形参必须是 {@code Object[]} 而不是 {@code Object}</b>：
     * {@code ChatClient.tools(Object...)} 是可变参数，
     * 若把数组装进一个 {@code Object} 再传，Spring AI 会把<b>整个数组</b>当成一个工具对象，
     * 抛出 {@code No @Tool annotated methods found in [Ljava.lang.Object;}。
     * 该缺陷在单测中不可见（测试用 {@code FakeAgentModel} 绕过了本类），
     * 由运行时冒烟测试发现（2026-11-03）。
     */
    @Override
    public String answer(String systemPrompt, String userMessage, Object[] tools, Map<String, Object> toolContext) {
        // Step ⑤：有会话 id 时挂 Memory Advisor（历史以独立 messages 注入，不污染 system）
        Object conversationId = toolContext == null ? null : toolContext.get(AgentIdentity.CTX_CONVERSATION_ID);

        var spec = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .toolCallbacks(callbacks(tools))   // ⚠️ 必须用 toolCallbacks，不能用 tools
                .toolContext(toolContext);

        if (conversationId != null) {
            MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
            spec = spec.advisors(advisors -> advisors
                    .param(ChatMemory.CONVERSATION_ID, String.valueOf(conversationId))
                    .advisors(advisor));
        }

        return spec.call().content();
    }
}
