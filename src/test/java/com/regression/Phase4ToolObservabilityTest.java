package com.regression;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agent.TimedToolCallback;
import com.agent.ToolCallRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Observability 小批验收（O1–O7）。
 *
 * <p><b>核心不变量</b>：
 * <ul>
 *   <li>成功：记录后<b>原样返回</b>（不改写返回值）；</li>
 *   <li>失败：记录 {@code ok=false} 后<b>原样抛出</b>（不吞、不改类型/消息）—— 旁路原则；</li>
 *   <li>只记录 <b>name / elapsedMs / ok</b>；入参、返回值、异常消息<b>一律不进日志</b>。</li>
 * </ul>
 *
 * <p>O5 用哨兵串（`SENSITIVE-IN` / `SECRET-OUT` / `SECRET-EXC`）证明隐私边界；
 * 没有这条断言，「不记录参数」只是口头承诺。
 */
@DisplayName("Phase4 Observability（tool 调用观测）")
class Phase4ToolObservabilityTest {

    private static final String SENSITIVE_IN = "SENSITIVE-IN";
    private static final String SECRET_OUT = "SECRET-OUT";
    private static final String SECRET_EXC = "SECRET-EXC";

    private ToolCallRecorder recorder;
    private ListAppender<ILoggingEvent> appender;
    private Logger toolLogger;

    @BeforeEach
    void setUp() {
        recorder = new ToolCallRecorder();
        toolLogger = (Logger) LoggerFactory.getLogger(ToolCallRecorder.class);
        appender = new ListAppender<>();
        appender.start();
        toolLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        toolLogger.detachAppender(appender);
    }

    // ================== O1 / O7 ==================

    @Test
    @DisplayName("O1/O7. 成功调用 → 记录 name/ok/耗时；返回值原样；ToolContext 透传")
    void successfulCallIsRecordedAndPassedThrough() {
        ToolCallback callback = wrap(new DemoTool());

        String result = callback.call(json("hello"), ctx(42));

        assertThat(result)
                .as("返回值必须原样（JSON 序列化后的字符串），观测层不得改写")
                .isEqualTo("\"" + SECRET_OUT + ":42\"");

        ToolCallRecorder.Snapshot snapshot = recorder.snapshot().get("demo");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.calls()).isEqualTo(1);
        assertThat(snapshot.failures()).isZero();
        assertThat(snapshot.totalMs()).isGreaterThanOrEqualTo(0);
    }

    // ================== O2 ==================

    @Test
    @DisplayName("O2. 工具异常 → 记录 ok=false，且异常原样抛出（旁路：不吞异常）")
    void failureIsRecordedAndRethrown() {
        ToolCallback callback = wrap(new DemoTool());

        assertThatThrownBy(() -> callback.call(json("boom"), ctx(1)))
                .isInstanceOf(org.springframework.ai.tool.execution.ToolExecutionException.class)
                .hasMessageContaining(SECRET_EXC);

        ToolCallRecorder.Snapshot snapshot = recorder.snapshot().get("demo");
        assertThat(snapshot.calls()).isEqualTo(1);
        assertThat(snapshot.failures()).as("失败必须计数").isEqualTo(1);
    }

    // ================== O3 ==================

    @Test
    @DisplayName("O3. 未调用任何工具 → 不产生记录、不产生日志")
    void noCallProducesNothing() {
        assertThat(recorder.snapshot()).isEmpty();
        assertThat(appender.list).isEmpty();
    }

    // ================== O4 ==================

    @Test
    @DisplayName("O4. 调用两个不同工具 → 分别计数（不合并）")
    void twoToolsAreCountedSeparately() {
        wrap(new DemoTool()).call(json("a"), ctx(1));
        wrap(new OtherTool()).call("{}", ctx(1));

        assertThat(recorder.snapshot()).containsKeys("demo", "other");
        assertThat(recorder.snapshot().get("demo").calls()).isEqualTo(1);
        assertThat(recorder.snapshot().get("other").calls()).isEqualTo(1);
    }

    // ================== O5 / O6 ==================

    @Test
    @DisplayName("O5. 🔴 入参 / 返回值 / 异常消息均不得进入日志")
    void sensitiveDataNeverAppearsInLogs() {
        ToolCallback callback = wrap(new DemoTool());

        // 用不易与耗时数字重合的 userId，避免「elapsedMs=987654」造成误判
        callback.call(json(SENSITIVE_IN), ctx(987654));
        assertThatThrownBy(() -> callback.call(json("boom"), ctx(987654)))
                .isInstanceOf(RuntimeException.class);

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).isNotEmpty();

        for (String message : messages) {
            assertThat(message).doesNotContain(SENSITIVE_IN);
            assertThat(message).doesNotContain(SECRET_OUT);
            assertThat(message).doesNotContain(SECRET_EXC);
            assertThat(message).doesNotContain("987654");   // userId 也不进日志
        }
    }

    @Test
    @DisplayName("O6. 日志行严格为 name / elapsedMs / ok 三个字段")
    void logFormatHasExactlyThreeFields() {
        wrap(new DemoTool()).call(json("a"), ctx(1));

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).matches(
                Pattern.compile("^\\[AGENT\\]\\[TOOL\\] name=\\S+ elapsedMs=\\d+ ok=(true|false)$"));
    }

    // ================== helpers ==================

    private ToolCallback wrap(Object toolObject) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(toolObject)
                .build()
                .getToolCallbacks();
        assertThat(callbacks).hasSize(1);
        return new TimedToolCallback(callbacks[0], recorder);
    }

    private String json(String input) {
        return "{\"input\":\"" + input + "\"}";
    }

    private ToolContext ctx(int userId) {
        return new ToolContext(Map.of("userId", userId));
    }

    /** 演示工具：成功返回含哨兵串；boom 抛含哨兵串的异常 */
    static class DemoTool {

        @Tool(description = "demo")
        public String demo(String input, ToolContext toolContext) {
            if ("boom".equals(input)) {
                throw new IllegalStateException(SECRET_EXC);
            }
            Object userId = toolContext.getContext().get("userId");
            return SECRET_OUT + ":" + userId;
        }
    }

    /** 无参工具（验证不同工具的计数互不干扰）*/
    static class OtherTool {

        @Tool(description = "other")
        public String other() {
            return "other-ok";
        }
    }
}
