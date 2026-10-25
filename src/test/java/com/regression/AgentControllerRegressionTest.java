package com.regression;

import com.agent.AgentIdentity;
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

import java.nio.charset.StandardCharsets;
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

        /**
         * 🔴 模型层调用计数（Step 12 / A5 的<b>直接证据</b>）。
         *
         * <p>计数位置是 {@link AgentModel#answer} 的<b>方法体内第一行</b> ——
         * 也就是「是否进入模型层」这条边界本身。
         * 不是 Controller 层、不是 AgentService 层的近似指标，也不是靠耗时推断。
         */
        int invocationCount;

        String lastSystemPrompt;
        String lastUserMessage;
        Object lastTools;
        Map<String, Object> lastToolContext;
        RuntimeException failWith;
        String reply = "这是测试回答";

        @Override
        public String answer(String systemPrompt, String userMessage, Object tools,
                             Map<String, Object> toolContext) {
            invocationCount++;          // ← 方法入口：先计数，再做任何事
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
            invocationCount = 0;
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
        studentToken = login("/student/login", "a1", "123456");
    }

    // ================== 认证 ==================

    @Test
    @DisplayName("1. 未登录 → 既有契约 HTTP 200 + body.code=401")
    void unauthenticated() throws Exception {
        MvcResult result = chat("{\"message\":\"你好\"}", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("code").asInt()).isEqualTo(401);
        assertThat(fakeModel.invocationCount)
                .as("未认证：AgentModel.answer() 调用次数必须为 0")
                .isZero();
    }

    // ================== 入参校验 ==================

    @Test
    @DisplayName("2. message 为空 / 空白 → 400")
    void blankMessage() throws Exception {
        assertThat(chat("{\"message\":\"\"}", studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(chat("{\"message\":\"   \"}", studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(chat("{}", studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(fakeModel.invocationCount)
                .as("入参校验失败：AgentModel.answer() 调用次数必须为 0")
                .isZero();
    }

    @Test
    @DisplayName("3. message 超过 1000 字符 → 400（边界 1000 应通过）")
    void messageTooLong() throws Exception {
        String tooLong = "a".repeat(MAX_MESSAGE_LENGTH + 1);
        assertThat(chat(json(tooLong), studentToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(fakeModel.invocationCount)
                .as("超长消息：AgentModel.answer() 调用次数必须为 0")
                .isZero();

        String boundary = "a".repeat(MAX_MESSAGE_LENGTH);
        MvcResult ok = chat(json(boundary), studentToken);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(ok).path("code").asInt()).isZero();
        assertThat(fakeModel.invocationCount)
                .as("边界值 1000 字符应正常进入模型层，且仅 1 次")
                .isEqualTo(1);
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
        assertThat(fakeModel.invocationCount)
                .as("正常路径：AgentModel.answer() 恰好调用 1 次")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("5. 【安全】模型层收到【二元身份】：userId = 登录用户 id，userTable = student")
    void toolContextCarriesAuthenticatedIdentity() throws Exception {
        chat("{\"message\":\"查一下我的记录\"}", studentToken);

        assertThat(fakeModel.lastToolContext)
                .as("必须携带 ToolContext")
                .isNotNull();
        assertThat(fakeModel.lastToolContext.get(AgentIdentity.CTX_USER_ID))
                .as("userId 必须是已认证用户的 id，且只能是 Integer")
                .isEqualTo(Integer.valueOf(STUDENT_A1_ID));
        assertThat(fakeModel.lastToolContext.get(AgentIdentity.CTX_USER_TABLE))
                .as("Step 12：身份域必须一并下传，否则 Tool 无法区分学生/咨询师")
                .isEqualTo(AgentIdentity.TABLE_STUDENT);
    }

    // ================== Step 12：身份域准入（L1） ==================

    @Test
    @DisplayName("8. 🔴【Step 12 核心】counselor 登录 → 403，且【AgentModel 调用次数 = 0】")
    void counselorForbidden() throws Exception {
        String token = login("/counselor/login", "a1", "123456");

        MvcResult result = chat("{\"message\":\"我最近的心理测评记录\"}", token);

        assertThat(result.getResponse().getStatus())
                .as("已认证但无 Agent 权限 → 403（不是 401，也不是 200）")
                .isEqualTo(403);
        assertThat(body(result).path("code").asInt()).isEqualTo(403);

        // 🔴 关键证据：不是「耗时短」，而是【模型层根本没被调用】—— 显式计数
        assertThat(fakeModel.invocationCount)
                .as("AgentModel.answer() 调用次数必须为 0（计数点在 answer 方法入口）")
                .isZero();
        assertThat(fakeModel.lastToolContext)
                .as("ToolContext 也不得被构造")
                .isNull();
    }

    @Test
    @DisplayName("9. 🔴【Step 12 核心】admin 登录 → 403，且【AgentModel 调用次数 = 0】")
    void adminForbidden() throws Exception {
        String token = login("/adminUser/login", "admin", "admin");

        MvcResult result = chat("{\"message\":\"我最近的心理测评记录\"}", token);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).path("code").asInt()).isEqualTo(403);
        assertThat(fakeModel.invocationCount)
                .as("管理员默认拒绝：AgentModel.answer() 调用次数必须为 0")
                .isZero();
    }

    @Test
    @DisplayName("10. 【Step 12】403 响应体不得含任何学生数据 / 不得伪装成正常回答")
    void forbiddenResponseLeaksNothing() throws Exception {
        String token = login("/counselor/login", "a1", "123456");

        String raw = chat("{\"message\":\"我最近的心理测评记录\"}", token)
                .getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(raw)
                .as("不得泄露种子数据（学生 a1 的试卷名与分数）")
                .doesNotContain("考试试卷1")
                .doesNotContain("15");
        assertThat(raw)
                .as("授权拒绝不得带 answer 字段（否则等于把策略拒绝包装成 Agent 回答）")
                .doesNotContain("answer");
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
        // 🔴 自检：证明计数器【不是恒为 0】—— 否则「counselor 调用次数 = 0」这条证据就是假的
        assertThat(fakeModel.invocationCount)
                .as("异常路径确实进入过模型层，调用 1 次")
                .isEqualTo(1);
    }

    // ================== helpers ==================

    /** 登录并返回 token；失败直接断言，避免后续用例因 token 为空而误判 */
    private String login(String path, String username, String password) throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(
                        post(path).param("username", username).param("password", password))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(node.path("code").asInt()).as("%s 登录 %s 应成功", path, username).isZero();
        String token = node.path("token").asText();
        assertThat(token).as("%s 登录必须返回 token", path).isNotBlank();
        return token;
    }

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
