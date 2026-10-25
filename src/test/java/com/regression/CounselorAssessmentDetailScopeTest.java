package com.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 逐题作答（{@code assessment_answer}）与错题（{@code assessment_wrong_question}）
 * 的<b>咨询师读取范围</b>回归。
 *
 * <p><b>裁定来源</b>：用户 2026-10-25 明确选择「**咨询师默认不可见**」——
 * 不得由「有权查看测评成绩」自动推导出「有权查看每一道题的作答与错题记录」。
 * 因此本类断言的预期是 <b>咨询师一律 403</b>，
 * <b>不是</b>「已同意预约即可见」（那会错误沿用 {@code assessment_record} 的 C1 规则）。
 *
 * <p><b>为什么必须覆盖三条路径</b>：修复前实测三条路径**各自独立泄露** —
 * <ul>
 *   <li>{@code /page}、{@code /list}：共用 {@code selectListView}，XML 无 {@code params.counselorId} 分支
 *       ⇒ {@code DataScope} 放入的参数被<b>静默丢弃</b> ⇒ 返回全量；</li>
 *   <li>{@code /info/{id}}：{@code OwnershipGuard.assertOwnership(..., null)} 在
 *       「心理老师 + 实体无 counselor_id + 未传回调」组合下<b>放行</b> ⇒ 可按 id 逐条读取。</li>
 * </ul>
 * 只修分页 SQL 会留下 {@code /info} 的洞。
 *
 * <p><b>反向自检（防止「全局返回空」式假通过）</b>：管理员与学生本人必须仍能读到数据。
 * 若这三条断言不同时成立，「咨询师 403」可能只是**整条链路坏了**。
 *
 * <p>种子数据（只读，不修改）：{@code assessment_answer} 5 条、{@code assessment_wrong_question} 3 条，
 * 全部归属 {@code student_id = 1}；预约状态 {@code counselor1 = 2（已同意）}、
 * {@code counselor2/3 = 1（待审核）}。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("逐题作答/错题 咨询师读取范围")
class CounselorAssessmentDetailScopeTest {

    /** 种子数据条数（只读断言基准） */
    private static final int ANSWER_ROWS = 5;
    private static final int WRONG_ROWS = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String student1;
    private String student2;
    private String counselor1;
    private String counselor2;
    private String counselor3;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        student1 = login("/student/login", "a1", "123456");
        student2 = login("/student/login", "a2", "123456");
        counselor1 = login("/counselor/login", "a1", "123456");
        counselor2 = login("/counselor/login", "a2", "123456");
        counselor3 = login("/counselor/login", "a3", "123456");
        admin = login("/adminUser/login", "admin", "admin");
    }

    // ================== A. 咨询师拒绝（裁定 B） ==================

    @Test
    @DisplayName("1. 🔴【裁定B】counselor1/2/3 访问 answer/page 全部 403（含已同意预约者）")
    void allCounselorsDeniedOnAnswerPage() throws Exception {
        for (String token : new String[]{counselor1, counselor2, counselor3}) {
            MvcResult result = call(page("/assessmentAnswer", token));
            assertThat(result.getResponse().getStatus())
                    .as("逐题作答对咨询师整体不可用，与预约状态无关")
                    .isEqualTo(403);
            assertThat(body(result).path("code").asInt()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("2. 🔴【裁定B】counselor 访问 answer/list 403（与 /page 同一条 SQL，不得只修一个入口）")
    void counselorDeniedOnAnswerList() throws Exception {
        MvcResult result = call(list("/assessmentAnswer", counselor2));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("3. 🔴【裁定B】counselor 访问 answer/info/{id} 403（修复前此路径单独泄露）")
    void counselorDeniedOnAnswerInfo() throws Exception {
        MvcResult result = call(get("/assessmentAnswer/info/1").header("Token", counselor2));
        assertThat(result.getResponse().getStatus())
                .as("修复前实测：counselor2 → HTTP 200 + code=0 + studentId=1")
                .isEqualTo(403);
        assertThat(body(result).path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("4. 🔴【裁定B】counselor 访问 wrongQuestion/page 403")
    void counselorDeniedOnWrongQuestionPage() throws Exception {
        MvcResult result = call(page("/assessmentWrongQuestion", counselor1));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("5. 🔴【裁定B】counselor 访问 wrongQuestion/list 403")
    void counselorDeniedOnWrongQuestionList() throws Exception {
        MvcResult result = call(list("/assessmentWrongQuestion", counselor2));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("6. 🔴【裁定B】counselor 访问 wrongQuestion/info/{id} 403")
    void counselorDeniedOnWrongQuestionInfo() throws Exception {
        MvcResult result = call(get("/assessmentWrongQuestion/info/1").header("Token", counselor2));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("7. 【裁定B】403 响应体不得含任何逐题内容或学生标识")
    void deniedResponseLeaksNothing() throws Exception {
        String raw = call(page("/assessmentAnswer", counselor2))
                .getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(raw).as("拒绝响应不得带 data 中的逐题字段").doesNotContain("questionId");
        assertThat(raw).as("拒绝响应不得伪装成正常回答").doesNotContain("answer");
    }

    // ================== B. 学生隔离 ==================

    @Test
    @DisplayName("8. student1 读取 answer/page → 200 且为本人数据")
    void student1SeesOwnAnswers() throws Exception {
        MvcResult result = call(page("/assessmentAnswer", student1));
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("list")).hasSize(ANSWER_ROWS);
        for (JsonNode row : body.path("data").path("list")) {
            assertThat(row.path("studentId").asInt()).as("学生只能看到自己的记录").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("9. student2 读取 answer/page → 200 但 0 条（不是 403）")
    void student2SeesNothing() throws Exception {
        MvcResult result = call(page("/assessmentAnswer", student2));
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus()).as("同一模块对学生开放，只是无数据").isEqualTo(200);
        assertThat(body.path("data").path("total").asInt()).isZero();
    }

    @Test
    @DisplayName("10. 🔴 student2 提交 studentId=1 → 仍 0 条（DataScope 覆盖生效，不可越权）")
    void studentCannotOverrideStudentId() throws Exception {
        MvcResult result = call(page("/assessmentAnswer", student2).param("studentId", "1"));

        assertThat(body(result).path("data").path("total").asInt())
                .as("客户端提交的 studentId 必须被服务端覆盖为当前用户")
                .isZero();
    }

    @Test
    @DisplayName("11. 🔴 student2 访问 answer/info/1（他人记录）→ 403")
    void student2DeniedOnOthersInfo() throws Exception {
        MvcResult result = call(get("/assessmentAnswer/info/1").header("Token", student2));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("12. student1 访问 answer/info/1（本人记录）→ 200")
    void student1ReadsOwnInfo() throws Exception {
        MvcResult result = call(get("/assessmentAnswer/info/1").header("Token", student1));
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body.path("data").path("studentId").asInt()).isEqualTo(1);
    }

    // ================== C. 反向自检（防「全局空」假通过） ==================

    @Test
    @DisplayName("13. 🔴【自检】admin 读取 answer/page 必须 5 条 —— 否则「咨询师 403」可能只是链路坏了")
    void adminStillSeesAllAnswers() throws Exception {
        MvcResult result = call(page("/assessmentAnswer", admin));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("total").asInt())
                .as("管理端权限不得因本批受限")
                .isEqualTo(ANSWER_ROWS);
    }

    @Test
    @DisplayName("14. 🔴【自检】admin 读取 wrongQuestion/page 必须 3 条")
    void adminStillSeesAllWrongQuestions() throws Exception {
        MvcResult result = call(page("/assessmentWrongQuestion", admin));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("total").asInt()).isEqualTo(WRONG_ROWS);
    }

    @Test
    @DisplayName("15. 🔴【自检】admin 访问 answer/info/1 必须 200")
    void adminStillReadsAnswerInfo() throws Exception {
        MvcResult result = call(get("/assessmentAnswer/info/1").header("Token", admin));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("studentId").asInt()).isEqualTo(1);
    }

    // ================== D. 未认证契约不变 ==================

    @Test
    @DisplayName("16. 未认证访问 answer/page → 200 + code=401（既有契约，不得改成 403）")
    void anonymousStillGets401() throws Exception {
        MvcResult result = call(page("/assessmentAnswer", null));
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus())
                .as("未认证由拦截器处理，保持 200 + code=401")
                .isEqualTo(200);
        assertThat(body.path("code").asInt()).isEqualTo(401);
    }

    // ================== E. 不得连带改动 assessment_record 的既有 C1 规则 ==================

    @Test
    @DisplayName("17. 【边界】record/info/1：counselor1（已同意）仍 200 —— 既有 C1 规则不变")
    void recordRuleUnchangedForApprovedCounselor() throws Exception {
        MvcResult result = call(get("/assessmentRecord/info/1").header("Token", counselor1));
        assertThat(result.getResponse().getStatus())
                .as("本批只处理逐题作答与错题，不得改动 assessment_record 的既有规则")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("18. 【边界】record/info/1：counselor2（待审核）仍 403 —— 既有 C1 规则不变")
    void recordRuleUnchangedForPendingCounselor() throws Exception {
        MvcResult result = call(get("/assessmentRecord/info/1").header("Token", counselor2));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    // ================== helpers ==================

    private MockHttpServletRequestBuilder page(String module, String token) {
        MockHttpServletRequestBuilder builder = get(module + "/page")
                .param("page", "1")
                .param("limit", "10");
        return token == null ? builder : builder.header("Token", token);
    }

    private MockHttpServletRequestBuilder list(String module, String token) {
        MockHttpServletRequestBuilder builder = get(module + "/list");
        return token == null ? builder : builder.header("Token", token);
    }

    private MvcResult call(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
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
}
