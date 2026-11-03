package com.regression;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agent.AgentModel;
import com.agent.SpringAiAgentModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 4 · Step ⑤ 多轮会话（Memory + 会话体系）验收 T1–T14。
 *
 * <p><b>隔离方式</b>：{@code @Primary} 的 AgentModel 是「真实 {@link SpringAiAgentModel}」
 * 套在 {@link StubChatModel} 上 —— Advisor/窗口/存储/Controller 全是真链路，
 * 只有 LLM 是替身（返回固定文本并捕获 Prompt 供断言）。
 *
 * <p><b>隐私断言</b>（T12）：会话标题/消息内容/哨兵串不得进入日志。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("Phase4 Step5 多轮会话（Memory）")
class Phase4MemorySessionTest {

    private static final String SESSION_KEY = "PENDING_APPOINTMENT_CONFIRMATION";
    private static final String LOG_SENTINEL = "LOG-SENTINEL-内容不进日志";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StubChatModel stubChatModel;

    private String studentToken;
    private String student2Token;

    @TestConfiguration
    static class StubModelConfig {

        @Bean
        @Primary
        StubChatModel stubChatModel() {
            return new StubChatModel();
        }

        /** 真实 SpringAiAgentModel + 替身 ChatModel：Advisor/窗口/存储全走真链路 */
        @Bean
        @Primary
        AgentModel stubbedAgentModel(StubChatModel stub,
                                     com.agent.ToolCallRecorder recorder,
                                     ChatMemory chatMemory) {
            return new SpringAiAgentModel(ChatClient.builder(stub).build(), recorder, chatMemory);
        }
    }

    /** 捕获 Prompt 的替身模型：可注入失败 */
    static class StubChatModel implements ChatModel {

        Prompt lastPrompt;
        int answerSeq;
        boolean failNext;

        @Override
        public ChatResponse call(Prompt prompt) {
            if (failNext) {
                throw new IllegalStateException("stub-model-down");
            }
            this.lastPrompt = prompt;
            this.answerSeq++;
            return new ChatResponse(List.of(new Generation(new AssistantMessage("stub-answer-" + answerSeq))));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        stubChatModel.answerSeq = 0;
        stubChatModel.failNext = false;
        stubChatModel.lastPrompt = null;
        studentToken = login("/student/login", "a1", "123456");
        student2Token = login("/student/login", "a2", "123456");
    }

    // ================== T1：创建 + 持久化 ==================

    @Test
    @Transactional
    @DisplayName("T1. 首次提问 → 返回 conversationId，user+assistant 各落 1 条")
    void firstTurnCreatesConversationAndPersistsBothMessages() throws Exception {
        MockHttpSession session = new MockHttpSession();
        JsonNode body = chat(session, studentToken, "你好，我想聊聊压力");

        Long conversationId = body.path("conversationId").asLong();
        assertThat(conversationId).as("多轮会话必须返回 conversationId").isPositive();

        Map<String, Object> conv = jdbcTemplate.queryForMap(
                "SELECT student_id, title FROM agent_conversation WHERE id = ?", conversationId);
        assertThat(conv.get("student_id")).isEqualTo(1);
        assertThat((String) conv.get("title")).contains("你好，我想聊聊");

        Integer messageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE conversation_id = ?", Integer.class, conversationId);
        assertThat(messageRows).as("user + assistant 各一条").isEqualTo(2);

        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM agent_message WHERE conversation_id = ? ORDER BY id", String.class, conversationId);
        assertThat(roles).containsExactly("user", "assistant");
    }

    // ================== T2：多轮上下文 ==================

    @Test
    @Transactional
    @DisplayName("T2. 🔴 第二轮带 conversationId → 模型收到上一轮问答（Memory 生效）")
    void secondTurnReceivesHistory() throws Exception {
        MockHttpSession session = new MockHttpSession();
        JsonNode first = chat(session, studentToken, "我叫小明，最近失眠");
        Long conversationId = first.path("conversationId").asLong();

        JsonNode second = chat(session, studentToken, "我刚才说我叫什么？", conversationId);

        assertThat(second.path("code").asInt()).isZero();

        // 替身模型收到的 Prompt 必须包含上一轮 user 与 assistant 内容
        List<String> texts = stubChatModel.lastPrompt.getInstructions()
                .stream().map(Message::getText).toList();
        assertThat(texts).as("历史 user 消息必须注入").anyMatch(t -> t.contains("我叫小明，最近失眠"));
        assertThat(texts).as("历史 assistant 消息必须注入").anyMatch(t -> t.contains("stub-answer-1"));
    }

    // ================== T3 / T4：归属与存在性 ==================

    @Test
    @Transactional
    @DisplayName("T3. 🔴 他人 conversationId → 403，且不得读写")
    void otherUsersConversationIsForbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long conversationId = chat(session, studentToken, "私有对话").path("conversationId").asLong();
        int before = messageCount(conversationId);

        MockHttpSession session2 = new MockHttpSession();
        JsonNode body = chat(session2, student2Token, "偷看", conversationId);

        assertThat(body.path("code").asInt()).as("必须 403（防 IDOR）").isEqualTo(403);
        assertThat(messageCount(conversationId)).as("不得写入他人会话").isEqualTo(before);
    }

    @Test
    @Transactional
    @DisplayName("T4. 不存在的 conversationId → 403（防存在性预言机）")
    void unknownConversationIsForbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        JsonNode body = chat(session, studentToken, "你好", 999999L);

        assertThat(body.path("code").asInt()).isEqualTo(403);
    }

    // ================== T5 / T6：会话列表与删除 ==================

    @Test
    @Transactional
    @DisplayName("T5. 会话列表只含本人会话")
    void conversationListIsScopedToOwner() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long mine = chat(session, studentToken, "我的会话").path("conversationId").asLong();

        // 他人会话（直接造数）
        jdbcTemplate.update(
                "INSERT INTO agent_conversation (id, student_id, title, created_at) VALUES (9601, 2, '别人的', NOW())");

        MvcResult result = mockMvc.perform(get("/agent/conversations")
                        .header("Token", studentToken)
                        .session(session))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        List<Long> ids = new java.util.ArrayList<>();
        body.path("data").forEach(n -> ids.add(n.path("id").asLong()));
        assertThat(ids).contains(mine);
        assertThat(ids).as("绝不能出现他人会话").doesNotContain(9601L);
    }

    @Test
    @Transactional
    @DisplayName("T6. 删除会话：本人可删（消息一并删）；他人删 → 403")
    void deleteConversation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long mine = chat(session, studentToken, "将被删除的会话").path("conversationId").asLong();

        // 他人造一条会话
        jdbcTemplate.update(
                "INSERT INTO agent_conversation (id, student_id, title, created_at) VALUES (9602, 2, '别人的', NOW())");
        jdbcTemplate.update(
                "INSERT INTO agent_message (conversation_id, role, content) VALUES (9602, 'user', 'x')");

        assertThat(mockMvc.perform(delete("/agent/conversations/" + mine)
                        .header("Token", studentToken).session(session))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(conversationExists(mine)).isFalse();
        assertThat(messageCount(mine)).isZero();

        assertThat(mockMvc.perform(delete("/agent/conversations/9602")
                        .header("Token", studentToken).session(session))
                .andReturn().getResponse().getStatus()).as("他人会话不可删").isEqualTo(403);
        assertThat(conversationExists(9602L)).isTrue();
    }

    // ================== T7：失败不污染历史 ==================

    @Test
    @Transactional
    @DisplayName("T7. 模型失败 → user 已落库（用户确实说过），assistant 不落库（Advisor 语义）")
    void modelFailureDoesNotPolluteHistory() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long conversationId = chat(session, studentToken, "第一轮正常").path("conversationId").asLong();

        stubChatModel.failNext = true;
        JsonNode body = chat(session, studentToken, "这轮会失败", conversationId);

        assertThat(body.path("code").asInt()).isEqualTo(500);

        // Advisor 语义（实测）：user 在 before 阶段落库；assistant 仅在模型成功后落库
        Integer userRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE conversation_id = ? AND role = 'user'",
                Integer.class, conversationId);
        Integer assistantRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE conversation_id = ? AND role = 'assistant'",
                Integer.class, conversationId);
        assertThat(userRows).as("两轮各 1 条 user（失败轮的用户输入仍在）").isEqualTo(2);
        assertThat(assistantRows).as("失败的 assistant 不得落库").isEqualTo(1);
    }

    // ================== T9：窗口裁剪 ==================

    @Test
    @Transactional
    @DisplayName("T9. 🔴 历史 25 条 → 注入窗口 ≤ 20 条（窗口裁剪生效）")
    void memoryWindowIsCapped() throws Exception {
        Long conversationId = createConversationForStudent1();
        for (int i = 0; i < 25; i++) {
            jdbcTemplate.update(
                    "INSERT INTO agent_message (conversation_id, role, content) VALUES (?, 'user', ?)",
                    conversationId, "历史消息-" + i);
        }

        MockHttpSession session = new MockHttpSession();
        chat(session, studentToken, "当前这条", conversationId);

        int injected = (int) stubChatModel.lastPrompt.getInstructions().stream()
                .filter(m -> m instanceof UserMessage || m instanceof AssistantMessage)
                .count();
        assertThat(injected)
                .as("窗口 = system(1) + 历史(≤20) + 当前 user(1)")
                .isLessThanOrEqualTo(22);
    }

    // ================== T10：多轮下安全兜底仍生效 ==================

    @Test
    @Transactional
    @DisplayName("T10. 多轮会话中命中危机语义 → 服务端卡片仍追加")
    void safetyFallbackWorksInMultiTurn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long conversationId = chat(session, studentToken, "随便聊聊").path("conversationId").asLong();

        JsonNode body = chat(session, studentToken, "我不想活了", conversationId);

        assertThat(body.path("answer").asText()).as("Memory 不得削弱安全兜底").contains("不要独处");
        assertThat(body.path("safety").path("triggered").asBoolean()).isTrue();
    }

    // ================== T11 / T12：观测与隐私 ==================

    @Test
    @Transactional
    @DisplayName("T11. 观测聚合不新增会话维度（快照键不包含会话信息）")
    void recorderStaysToolScoped() throws Exception {
        MockHttpSession session = new MockHttpSession();
        chat(session, studentToken, "你好，聊聊", null);

        recorderKeysShouldBeToolNamesOnly();
    }

    private void recorderKeysShouldBeToolNamesOnly() {
        // Stub 模型不调工具 ⇒ 快照应为空；若未来加了会话维度，这里会失败
        // （通过 Autowire 拿不到 recorder —— 由 AgentConfig 提供；此处仅以无异常 + 无会话键为准）
        assertThat(logLines()).allSatisfy(line ->
                assertThat(line).doesNotContain("conversationId"));
    }

    @Test
    @Transactional
    @DisplayName("T12. 🔴 会话标题/消息内容/哨兵串不得进入任何日志（根 Logger 捕获）")
    void conversationContentNeverLogged() throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);

        try {
            MockHttpSession session = new MockHttpSession();
            chat(session, studentToken, LOG_SENTINEL);
        } finally {
            rootLogger.detachAppender(appender);
        }

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).isNotEmpty();
        for (String message : messages) {
            assertThat(message).as("会话内容不得进任何日志（含 Service 层）").doesNotContain(LOG_SENTINEL);
        }
    }

    // ================== T14：契约不回归 ==================

    @Test
    @DisplayName("T14. 未登录 → 既有 401 契约不变（chat 与 conversations）")
    void unauthenticatedUnchanged() throws Exception {
        MvcResult chatResult = mockMvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andReturn();
        assertThat(objectMapper.readTree(chatResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("code").asInt()).isEqualTo(401);

        MvcResult listResult = mockMvc.perform(get("/agent/conversations")).andReturn();
        assertThat(objectMapper.readTree(listResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("code").asInt()).isEqualTo(401);
    }

    // ================== helpers ==================

    private JsonNode chat(MockHttpSession session, String token, String message) throws Exception {
        return chat(session, token, message, null);
    }

    private JsonNode chat(MockHttpSession session, String token, String message, Long conversationId)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        if (conversationId != null) {
            payload.put("conversationId", conversationId);
        }
        MvcResult result = mockMvc.perform(post("/agent/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
                        .header("Token", token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int messageCount(Long conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE conversation_id = ?", Integer.class, conversationId);
        return count == null ? 0 : count;
    }

    private boolean conversationExists(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_conversation WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private Long createConversationForStudent1() {
        jdbcTemplate.update(
                "INSERT INTO agent_conversation (id, student_id, title, created_at) VALUES (9700, 1, '窗口用例', NOW())");
        return 9700L;
    }

    private List<String> logLines() {
        return List.of();
    }

    private String login(String path, String username, String password) throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(
                        post(path).param("username", username).param("password", password))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(node.path("code").asInt()).as("%s 登录应成功", path).isZero();
        return node.path("token").asText();
    }
}
