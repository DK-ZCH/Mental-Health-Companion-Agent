package com.regression;

import com.agent.AgentModel;
import com.agent.AgentPrompts;
import com.agent.AssessmentTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 4 / Step 11：{@code /agent/chat} 回归（**全程不调用真实 DeepSeek**）。
 *
 * <p><b>隔离方式</b>：用 {@link CapturingFakeAgentModel}（{@code @Primary}）替换 {@link AgentModel} 实现，
 * 因此本类只验证「Controller → 身份 → ToolContext → AgentModel」这段边界，
 * 不触碰框架内部链式 API，也不会产生任何上游请求。
 *
 * <p><b>test-only api-key</b>：仅用于让 Spring 上下文可创建（`DeepSeekChatModel` 自动装配要求），
 * 由于 {@code AgentModel} 已被替身覆盖，<b>不存在</b>真实调用路径。
 *
 * <p>种子数据：student a1 的 id = 1，密码 123456。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("Step 11 AgentController")
class AgentControllerRegressionTest {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final String STUDENT_A1_ID = "1";
    private static final String MSG_UPSTREAM_FAILED = "服务暂时不可用，请稍后再试";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CapturingFakeAgentModel fakeModel;

    private String studentToken;

    /** 替身：替换真实 AgentModel，记录入参供断言 */
    @TestConfiguration
    static class FakeAgentModelConfig {
        @Bean
        @Primary
        CapturingFakeAgentModel capturingFakeAgentModel() {
            return new CapturingFakeAgentModel();
        }
    }

    static class CapturingFakeAgentModel implements AgentModel {

        String lastSystemPrompt;
        String lastUserMessage;
        Object lastTools;
        Map<String, Object> lastToolContext;
        RuntimeException failWith;
        String reply = "这是测试回答";

        @Override
        public String answer(String systemPrompt, String userMessage, Object tools,
                             Map<String, Object> toolContext) {
            this.lastSystemPrompt = systemPrompt;
            this.lastUserMessage = userMessage;
            this.lastTools = tools;
            this.lastToolContext = toolContext;
            if (failWith != null) {
                throw failWith;
            }
            return reply;
        }

        void reset() {
            lastSystemPrompt = null;
            lastUserMessage = null;
            lastTools = null;
            lastToolContext = null;
            failWith = null;
            reply = "这是测试回答";
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        fakeModel.reset();
        JsonNode login = objectMapper.readTree(mockMvc.perform(
                        post("/student/login").param("username", "a1").param("password", "123456"))
                .andReturn().getResponse().getContentAsString());
        assertThat(login.path("code").asInt()).as("学生 a1 登录").isZero();
        studentToken = login.path("token").asText();
    }

    // ================== 认证 ==================

    @Test
    @DisplayName("1. 未登录 → 既有契约 HTTP 200 + body.code=401")
    void unauthenticated() throws Exception {
        MvcResult result = chat("{\"message\":\"你好\"}", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("code").asInt()).isEqualTo(401);
        assertThat(fakeModel.lastUserMessage).as("未认证不应触达模型层").isNull();
    }

    // ================== 入参校验 ==================

    @Test
    @DisplayName("2. message 为空 / 空白 → 400")
    void blankMessage() throws Exception {
        assertThat(chat("{\"message\":\"\"}", studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(chat("{\"message\":\"   \"}", studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(chat("{}", studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(fakeModel.lastUserMessage).isNull();
    }

    @Test
    @DisplayName("3. message 超过 1000 字符 → 400（边界 1000 应通过）")
    void messageTooLong() throws Exception {
        String tooLong = "a".repeat(MAX_MESSAGE_LENGTH + 1);
        assertThat(chat(json(tooLong), studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(fakeModel.lastUserMessage).as("超长消息不应触达模型层").isNull();

        String boundary = "a".repeat(MAX_MESSAGE_LENGTH);
        MvcResult ok = chat(json(boundary), studentToken);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(ok).path("code").asInt()).isZero();
        assertThat(fakeModel.lastUserMessage).hasSize(MAX_MESSAGE_LENGTH);
    }

    // ================== 正常路径 ==================

    @Test
    @DisplayName("4. 已登录 + 合法消息 → code=0 + answer 透传")
    void happyPath() throws Exception {
        fakeModel.reply = "你有 1 条测评记录。";

        MvcResult result = chat("{\"message\":\"我有几条测评记录\"}", studentToken);
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("answer").asText()).isEqualTo("你有 1 条测评记录。");
    }

    @Test
    @DisplayName("5. 【安全】模型层收到的 toolContext.userId == 登录用户 id（由服务器注入）")
    void toolContextCarriesAuthenticatedUserId() throws Exception {
        chat("{\"message\":\"查一下我的记录\"}", studentToken);

        assertThat(fakeModel.lastToolContext)
                .as("必须携带 ToolContext")
                .isNotNull();
        assertThat(fakeModel.lastToolContext.get(AssessmentTools.CTX_USER_ID))
                .as("userId 必须是已认证用户的 id，且只能是 Integer")
                .isEqualTo(Integer.valueOf(STUDENT_A1_ID));
    }

    @Test
    @DisplayName("6. 【安全】模型层只拿到 AssessmentTools，且 system prompt 为服务端常量")
    void toolsAndPromptAreServerControlled() throws Exception {
        chat("{\"message\":\"你好\"}", studentToken);

        assertThat(fakeModel.lastTools)
                .as("Step 11 只挂载一个只读工具")
                .isInstanceOf(AssessmentTools.class);
        assertThat(fakeModel.lastSystemPrompt)
                .as("system prompt 必须来自服务端常量，不得拼接用户输入")
                .isEqualTo(AgentPrompts.SYSTEM);
        assertThat(fakeModel.lastSystemPrompt)
                .as("用户输入不得混入 system prompt")
                .doesNotContain("你好");
    }

    // ================== 上游失败 ==================

    @Test
    @DisplayName("7. 模型调用异常 → code=500 + 统一文案（不泄露上游细节）")
    void upstreamFailure() throws Exception {
        fakeModel.failWith = new IllegalStateException("upstream secret detail");

        MvcResult result = chat("{\"message\":\"你好\"}", studentToken);
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body.path("code").asInt()).isEqualTo(500);
        assertThat(body.path("msg").asText()).isEqualTo(MSG_UPSTREAM_FAILED);
        assertThat(body.path("msg").asText())
                .as("异常细节不得外泄")
                .doesNotContain("upstream secret detail");
        assertThat(body.has("answer")).isFalse();
    }

    // ================== helpers ==================

    private MvcResult chat(String json, String token) throws Exception {
        var builder = post("/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (token != null) {
            builder = builder.header("Token", token);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(String message) throws Exception {
        return objectMapper.writeValueAsString(Map.of("message", message));
    }
}
