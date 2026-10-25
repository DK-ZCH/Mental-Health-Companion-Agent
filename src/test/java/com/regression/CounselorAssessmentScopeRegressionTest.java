package com.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ③ Counselor Assessment Access Rule v1 —— 端到端回归（真实 DB，不调用 LLM）。
 *
 * <p><b>规则</b>：Counselor 仅可访问与其存在<b>已同意预约</b>
 * （{@code counseling_appointment.status = 2}）的学生的测评记录。
 *
 * <p><b>种子数据（现有真实数据，未造数据）</b>：
 * <pre>
 * student 1 (a1) 有 1 条 assessment_record（id = 1，试卷1，15/100）
 * 预约：counselor 1 → student 1 → status = 2（同意）    ✅ 应有权限
 *       counselor 2 → student 1 → status = 1（待审核）  ❌ 应无权限
 *       counselor 3 → student 1 → status = 1（待审核）  ❌ 应无权限
 * </pre>
 *
 * <p><b>本类必须先红后绿</b>：在修复前，counselor 2/3 能读到全表与详情，
 * T4/T5 会<b>失败</b> —— 这正是「测试确实捕获了修复前的漏洞」的证据。
 *
 * <p><b>覆盖两条读路径</b>：
 * <ul>
 *   <li>{@code /assessmentRecord/page} —— 列表（XML {@code EXISTS} 子查询）</li>
 *   <li>{@code /assessmentRecord/info/{id}} —— 详情（{@code CounselorAssessmentScope}）</li>
 * </ul>
 * 二者必须一致；若规则只在其中一处生效，本类必有一半失败。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("③ Counselor Assessment Scope")
class CounselorAssessmentScopeRegressionTest {

    /** 种子数据：student 1 (a1) 唯一的测评记录 id */
    private static final long RECORD_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String studentA1Token;
    private String studentA2Token;
    private String counselorA1Token;
    private String counselorA2Token;
    private String counselorA3Token;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        studentA1Token = login("/student/login", "a1", "123456");
        studentA2Token = login("/student/login", "a2", "123456");
        counselorA1Token = login("/counselor/login", "a1", "123456");   // status = 2
        counselorA2Token = login("/counselor/login", "a2", "123456");   // status = 1
        counselorA3Token = login("/counselor/login", "a3", "123456");   // status = 1
        adminToken = login("/adminUser/login", "admin", "admin");
    }

    // ================== T1 / T2：学生侧不得回归 ==================

    @Test
    @DisplayName("T1. student 1 → 本人记录：列表 1 条 + 详情 200")
    void studentOwnRecord() throws Exception {
        assertThat(listSize(studentA1Token)).as("学生本人应看到自己的 1 条记录").isEqualTo(1);
        assertThat(infoStatus(studentA1Token, RECORD_ID)).as("学生本人可看详情").isEqualTo(200);
    }

    @Test
    @DisplayName("T2. student 2 → 他人记录：列表 0 条 + 详情 403")
    void studentOtherRecord() throws Exception {
        assertThat(listSize(studentA2Token)).as("学生不得看到他人记录").isZero();
        assertThat(infoStatus(studentA2Token, RECORD_ID)).as("学生不得看他人详情").isEqualTo(403);
    }

    // ================== T3 / T4 / T5：C1 规则核心 ==================

    @Test
    @DisplayName("T3. 🔴 counselor 1（status=2 同意）→ 列表 1 条 + 详情 200")
    void counselorWithApprovedAppointment_canAccess() throws Exception {
        assertThat(listSize(counselorA1Token))
                .as("存在 status=2 的预约关系 ⇒ 应能看到该学生的记录")
                .isEqualTo(1);
        assertThat(infoStatus(counselorA1Token, RECORD_ID))
                .as("存在 status=2 的预约关系 ⇒ 应能打开详情")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("T4. 🔴 counselor 2（status=1 待审核）→ 列表 0 条 + 详情 403")
    void counselorWithPendingAppointment_cannotAccess() throws Exception {
        assertThat(listSize(counselorA2Token))
                .as("仅有 status=1（待审核）⇒ 不得看到任何测评记录")
                .isZero();
        assertThat(infoStatus(counselorA2Token, RECORD_ID))
                .as("仅有 status=1（待审核）⇒ 不得打开详情")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("T5. 🔴 counselor 3（status=1 待审核）→ 列表 0 条 + 详情 403")
    void counselorWithPendingAppointment_cannotAccess2() throws Exception {
        assertThat(listSize(counselorA3Token)).isZero();
        assertThat(infoStatus(counselorA3Token, RECORD_ID)).isEqualTo(403);
    }

    // ================== T6：管理员语义不得改变 ==================

    @Test
    @DisplayName("T6. admin → 列表 1 条 + 详情 200（管理端全量，语义不变）")
    void adminSeesAll() throws Exception {
        assertThat(listSize(adminToken)).as("管理员保持全量").isEqualTo(1);
        assertThat(infoStatus(adminToken, RECORD_ID)).as("管理员保持全量详情").isEqualTo(200);
    }

    // ================== T7：未认证契约不得改变 ==================

    @Test
    @DisplayName("T7. 未登录 → 既有契约 HTTP 200 + body.code=401")
    void unauthenticated() throws Exception {
        MvcResult page = mockMvc.perform(get("/assessmentRecord/page")
                .param("page", "1").param("limit", "10")).andReturn();
        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(page).path("code").asInt()).isEqualTo(401);

        MvcResult info = mockMvc.perform(get("/assessmentRecord/info/1")).andReturn();
        assertThat(info.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(info).path("code").asInt()).isEqualTo(401);
    }

    // ================== helpers ==================

    /** 列表可见条数（/page 与 /list 共用 selectListView，此处用 /page） */
    private int listSize(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/assessmentRecord/page")
                        .param("page", "1").param("limit", "10")
                        .header("Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("列表请求不应以 HTTP 错误返回（范围收敛应是「过滤」而非「拒绝」）")
                .isEqualTo(200);
        JsonNode data = body(result).path("data");
        return data.path("list").size();
    }

    /** 详情 HTTP 状态码 */
    private int infoStatus(String token, long id) throws Exception {
        return mockMvc.perform(get("/assessmentRecord/info/" + id).header("Token", token))
                .andReturn().getResponse().getStatus();
    }

    private String login(String path, String username, String password) throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(
                        post(path).param("username", username).param("password", password))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(node.path("code").asInt()).as("%s 登录 %s 应成功", path, username).isZero();
        String token = node.path("token").asText();
        assertThat(token).as("%s 登录必须返回 token", path).isNotBlank();
        return token;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
