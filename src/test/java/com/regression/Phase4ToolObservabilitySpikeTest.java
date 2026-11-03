package com.regression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Observability 小批的 <b>10 分钟 spike</b>（保留为契约测试）。
 *
 * <p><b>为什么必须先做</b>：包装 {@link ToolCallback} 的方案依赖三个未经验证的 API 行为：
 * <ol>
 *   <li>包装后 {@link ToolDefinition#name()} 是否仍是原方法名；</li>
 *   <li>{@link ToolContext} 是否仍透传到工具方法；</li>
 *   <li>异常是否原样抛出（观测层不得改变失败语义）；</li>
 *   <li>{@link ChatClient#prompt()}.tools(Object...) 是否接受包装后的 callback。</li>
 * </ol>
 *
 * <p>若本类任一断言失败 ⇒ **停止实现并上报**，不得为了维持方案强行绕过。
 */
@DisplayName("Spike: ToolCallback 包装可行性")
class Phase4ToolObservabilitySpikeTest {

    /** 被测工具：读取 ToolContext、可按入参抛异常（异常 message 含哨兵串）*/
    static class DemoTool {

        @Tool(description = "spike demo tool")
        public String demo(String input, ToolContext toolContext) {
            if ("boom".equals(input)) {
                throw new IllegalStateException("secret-exception-message");
            }
            Object userId = toolContext == null ? null : toolContext.getContext().get("userId");
            return "OK:" + userId;
        }
    }

    /** 最小包装器：只做计时/计数/失败标记，语义完全委托 */
    static class RecordingCallback implements ToolCallback {

        private final ToolCallback delegate;
        final List<String> names = new ArrayList<>();
        boolean lastOk;

        RecordingCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            boolean ok = true;
            try {
                return delegate.call(toolInput, toolContext);
            } catch (RuntimeException e) {
                ok = false;
                throw e;                      // 🔴 原样抛出
            } finally {
                names.add(delegate.getToolDefinition().name());
                lastOk = ok;
            }
        }
    }

    @Test
    @DisplayName("S1. 包装后 name 不变、ToolContext 透传、异常原样抛出")
    void wrapperPreservesNameContextAndException() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new DemoTool())
                .build()
                .getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("demo");

        RecordingCallback recording = new RecordingCallback(callbacks[0]);
        ToolContext ctx = new ToolContext(Map.of("userId", 42));

        // ⚠️ spike 发现（1）：带参数的 @Tool 入参是 JSON 字符串（Spring AI 按参数名解析）
        // ⚠️ spike 发现（2）：String 返回值会被 JSON 序列化 ⇒ 裸值外面带引号
        assertThat(recording.call("{\"input\":\"hi\"}", ctx)).isEqualTo("\"OK:42\"");
        assertThat(recording.lastOk).isTrue();
        assertThat(recording.names).containsExactly("demo");

        // ⚠️ spike 发现（4）：工具异常会被 Spring AI 包装成 ToolExecutionException（message 保留）。
        //    观测层的要求是「不替换、不吞掉」—— 即捕获后原样 rethrow，而不是改类型或改写消息。
        assertThatThrownBy(() -> recording.call("{\"input\":\"boom\"}", ctx))
                .isInstanceOf(org.springframework.ai.tool.execution.ToolExecutionException.class)
                .hasMessageContaining("secret-exception-message");
        assertThat(recording.lastOk).isFalse();
        assertThat(recording.names).containsExactly("demo", "demo");
    }

    @Test
    @DisplayName("S2. ChatClient.tools(...) 接受包装后的 ToolCallback，并保持 name 与 ToolContext")
    void chatClientAcceptsWrappedCallback() {
        StubChatModel model = new StubChatModel();
        ChatClient client = ChatClient.builder(model).build();

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new DemoTool())
                .build()
                .getToolCallbacks();
        RecordingCallback recording = new RecordingCallback(callbacks[0]);

        // ⚠️ spike 发现（3）：.tools(Object...) 不接受 ToolCallback（会尝试扫描 @Tool），
        //    必须用 .toolCallbacks(ToolCallback...) —— 这是本方案唯一的 API 修正点。
        String content = client.prompt()
                .user("hi")
                .toolCallbacks(recording)
                .toolContext(Map.of("userId", 7))
                .call()
                .content();

        assertThat(content).isEqualTo("stub-answer");
        assertThat(model.lastPrompt).isNotNull();
        assertThat(model.lastPrompt.getOptions()).isInstanceOf(ToolCallingChatOptions.class);

        ToolCallingChatOptions options = (ToolCallingChatOptions) model.lastPrompt.getOptions();
        assertThat(options.getToolCallbacks()).hasSize(1);
        assertThat(options.getToolCallbacks().get(0).getToolDefinition().name()).isEqualTo("demo");
        assertThat(options.getToolContext()).containsEntry("userId", 7);
    }

    /** 只记录 Prompt，不调用任何模型 */
    static class StubChatModel implements ChatModel {

        Prompt lastPrompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage("stub-answer"))));
        }
    }
}
