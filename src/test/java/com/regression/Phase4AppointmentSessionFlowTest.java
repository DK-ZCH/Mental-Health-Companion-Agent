package com.regression;

import com.agent.AgentModel;
import com.agent.AppointmentBookingTools;
import com.agent.AppointmentConfirmResult;
import com.agent.AppointmentPreviewResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 4 / Round 4 · <b>R4-Q5（C）跨请求编排</b>：Session 待确认草稿。
 *
 * <p><b>要证明什么</b>：无状态 Agent 也能完成「预览 → 用户确认 → 写入」，
 * 且 <b>Session 不是授权依据</b>：
 * 值只是一个签名 token，确认时仍重新验签 + 重新校验业务规则。
 *
 * <p><b>如何做到"不用真实 LLM"</b>：{@link DrivingAgentModel} 直接调用真实 Tool，
 * 模拟模型在两次请求中分别选择 preview / confirm；
 * Controller、Session、Tool、Service、DB 全部是真实链路。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("Phase4 Round4 跨请求确认（Session 草稿）")
class Phase4AppointmentSessionFlowTest {

    /** 与 AgentController#SESSION_PENDING_APPOINTMENT 一致（包内可见，测试用字面量）*/
    private static final String SESSION_KEY = "PENDING_APPOINTMENT_CONFIRMATION";

    private static final int COUNSELOR_A = 2;
    private static final int COUNSELOR_B = 3;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DrivingAgentModel model;

    private String token;

    @TestConfiguration
    static class DrivingModelConfig {
        @Bean
        @Primary
        DrivingAgentModel drivingAgentModel() {
            return new DrivingAgentModel();
        }
    }

    /** 替身模型：按当前 mode 直接调用真实 Tool，模拟模型的两轮工具选择 */
    static class DrivingAgentModel implements AgentModel {

        enum Mode { PREVIEW, CONFIRM }

        Mode mode = Mode.PREVIEW;
        Integer counselorId;
        String date;
        Integer slot;
        String lastAnswer;
        String lastSystemPrompt;

        @Override
        public String answer(String systemPrompt, String userMessage, Object[] tools,
                             Map<String, Object> toolContext) {
            this.lastSystemPrompt = systemPrompt;
            AppointmentBookingTools booking = null;
            for (Object tool : tools) {
                if (tool instanceof AppointmentBookingTools candidate) {
                    booking = candidate;
                }
            }
            if (booking == null) {
                throw new IllegalStateException("AppointmentBookingTools 未装配");
            }
            ToolContext ctx = new ToolContext(toolContext);
            if (mode == Mode.PREVIEW) {
                AppointmentPreviewResult result = booking.previewAppointment(counselorId, date, slot, ctx);
                lastAnswer = result.success() ? "已生成预览：" + result.message() : "预览失败：" + result.message();
            } else {
                AppointmentConfirmResult result = booking.confirmAppointment(ctx);
                lastAnswer = result.success() ? "已提交：" + result.message() : "提交失败：" + result.message();
            }
            return lastAnswer;
        }

        void reset() {
            mode = Mode.PREVIEW;
            counselorId = null;
            date = null;
            slot = null;
            lastAnswer = null;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        model.reset();
        token = login();
    }

    // ================== C1：完整两轮 ==================

    @Test
    @Transactional
    @DisplayName("C1. 🔴 第 1 轮预览写草稿、第 2 轮确认写入并清理草稿")
    void previewThenConfirmAcrossRequests() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        MockHttpSession session = new MockHttpSession();

        // 第 1 轮：预览（不写库）
        model.mode = DrivingAgentModel.Mode.PREVIEW;
        model.counselorId = COUNSELOR_A;
        model.date = date.toString();
        model.slot = 4;

        JsonNode first = chat(session, "帮我预约");
        assertThat(first.path("answer").asText()).contains("预览");
        assertThat(session.getAttribute(SESSION_KEY)).as("预览后应写入待确认草稿").isNotNull();
        assertThat(countFor(COUNSELOR_A, date, 4)).as("预览不得写库").isZero();

        // 第 2 轮：用户只说「确认」
        model.mode = DrivingAgentModel.Mode.CONFIRM;

        JsonNode second = chat(session, "确认");
        assertThat(second.path("answer").asText()).contains("已提交");
        assertThat(model.lastSystemPrompt)
                .as("无状态 Agent 不知道草稿存在 ⇒ 第二次请求必须由服务端注入状态提示")
                .contains("待确认的预约草稿");
        assertThat(countFor(COUNSELOR_A, date, 4)).as("确认后应恰好 1 条").isEqualTo(1);
        assertThat(session.getAttribute(SESSION_KEY)).as("确认成功后必须清理草稿").isNull();
    }

    // ================== C2：新预览覆盖旧草稿 ==================

    @Test
    @Transactional
    @DisplayName("C2. 🔴 单 Session 只保留一个草稿：第二次预览覆盖第一次")
    void newPreviewOverwritesPreviousDraft() throws Exception {
        LocalDate date = LocalDate.now().plusDays(2);
        MockHttpSession session = new MockHttpSession();

        model.mode = DrivingAgentModel.Mode.PREVIEW;
        model.counselorId = COUNSELOR_A;
        model.date = date.toString();
        model.slot = 5;
        chat(session, "先看看 A 老师");

        model.counselorId = COUNSELOR_B;
        chat(session, "再看看 B 老师");

        model.mode = DrivingAgentModel.Mode.CONFIRM;
        chat(session, "确认");

        assertThat(countFor(COUNSELOR_B, date, 5)).as("确认的是最后一次预览（B）").isEqualTo(1);
        assertThat(countFor(COUNSELOR_A, date, 5)).as("A 的草稿已被覆盖，不得被写入").isZero();
    }

    // ================== C3：失败/过期也清理 ==================

    @Test
    @Transactional
    @DisplayName("C3. 🔴 草稿失效（伪造 token）→ 拒绝、清理草稿、不写库")
    void invalidDraftIsRejectedAndCleared() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_KEY, "not-a-valid-token");

        model.mode = DrivingAgentModel.Mode.CONFIRM;

        JsonNode body = chat(session, "确认");
        assertThat(body.path("answer").asText()).contains("失效");
        assertThat(session.getAttribute(SESSION_KEY)).as("失败后也必须清理").isNull();
    }

    @Test
    @Transactional
    @DisplayName("C4. 没有草稿时确认 → 明确提示，不写库")
    void confirmWithoutDraftIsRejected() throws Exception {
        LocalDate date = LocalDate.now().plusDays(3);
        MockHttpSession session = new MockHttpSession();
        long before = countFor(COUNSELOR_A, date, 6);

        model.mode = DrivingAgentModel.Mode.CONFIRM;

        JsonNode body = chat(session, "确认");
        assertThat(body.path("answer").asText()).contains("没有待确认");
        assertThat(countFor(COUNSELOR_A, date, 6)).isEqualTo(before);
    }

    // ================== C5：Session 不是授权依据 ==================

    @Test
    @Transactional
    @DisplayName("C5. 🔴 把别人的凭证塞进我的 Session → 验签/绑定失败，不写库")
    void sessionCannotBypassTokenBinding() throws Exception {
        LocalDate date = LocalDate.now().plusDays(4);

        // 用 student2 的身份签发一个真实 token（直接调用 Tool，模拟另一用户的预览）
        String otherUsersToken = issueTokenForOtherUser(date);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_KEY, otherUsersToken);

        model.mode = DrivingAgentModel.Mode.CONFIRM;
        JsonNode body = chat(session, "确认");

        assertThat(body.path("answer").asText()).contains("失效");
        assertThat(countFor(COUNSELOR_A, date, 7)).isZero();
        assertThat(session.getAttribute(SESSION_KEY)).isNull();
    }

    // ================== helpers ==================

    private String issueTokenForOtherUser(LocalDate date) {
        // preview 需要 student 域上下文；这里用 userId=2 直接签发，拿到真实 token
        ToolContext other = new ToolContext(Map.of(
                com.agent.AgentIdentity.CTX_USER_ID, 2,
                com.agent.AgentIdentity.CTX_USER_TABLE, com.agent.AgentIdentity.TABLE_STUDENT));
        AppointmentPreviewResult result = bookingTools.previewAppointment(COUNSELOR_A, date.toString(), 7, other);
        assertThat(result.success()).isTrue();
        return result.token();
    }

    @Autowired
    private AppointmentBookingTools bookingTools;

    private JsonNode chat(HttpSession session, String message) throws Exception {
        MvcResult result = mockMvc.perform(post("/agent/chat")
                        .session((MockHttpSession) session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", message)))
                        .header("Token", token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int countFor(int counselorId, LocalDate date, int slot) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM counseling_appointment WHERE counselor_id = ? AND appointment_date = ? "
                        + "AND time_slot = ?",
                Integer.class, counselorId, java.sql.Date.valueOf(date), slot);
        return value == null ? 0 : value;
    }

    private String login() throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(
                        post("/student/login").param("username", "a1").param("password", "123456"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(node.path("code").asInt()).isZero();
        return node.path("token").asText();
    }
}
