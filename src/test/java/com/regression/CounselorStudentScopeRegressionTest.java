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
 * 咨询师读取学生资料的<b>行级范围</b>回归（`AUTHZ-COUNSELOR-STUDENT-SCOPE`）。
 *
 * <p><b>业务规则</b>（用户 2026-10-25 裁定）：咨询师仅可访问
 * <b>与其存在至少一条 {@code counseling_appointment.status = 2}（已批准）预约</b>的学生。
 * 待审核（1）、拒绝（3）、无预约 —— <b>一律不可访问</b>。
 *
 * <p><b>覆盖四个入口</b>：{@code /page}、{@code /list}、{@code /info/{id}}、{@code /detail/{id}}。
 * 前两者共用同一条 {@code selectListView}（SQL 侧约束），后两者共用同一个关系回调（Java 侧约束）。
 *
 * <p><b>种子数据（只读）</b>：
 * <pre>
 * counseling_appointment : student1 ↔ counselor1  status=2（已同意）
 *                          student1 ↔ counselor2  status=1（待审核）
 *                          student1 ↔ counselor3  status=1（待审核）
 * </pre>
 * ⇒ counselor1 应只见 student1（1 条）；counselor2/3 应见 <b>0 条</b>。
 *
 * <p><b>防「全局空」假通过</b>：T1 / T4 / T11 断言有权侧确实有数据；
 * 若整条链路故障，这些用例会先失败，而不是让「counselor 得 0」侥幸通过。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("咨询师 学生资料 行级范围")
class CounselorStudentScopeRegressionTest {

    private static final int TOTAL_STUDENTS = 3;
    private static final int STUDENT1_ID = 1;

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

    // ================== T1–T3：/page ==================

    @Test
    @DisplayName("T1. 🔴 counselor1（status=2）→ page 仅 1 条且为 student1")
    void counselor1SeesOnlyApprovedStudent() throws Exception {
        JsonNode data = listData("/student", counselor1);

        assertThat(data.path("total").asInt())
                .as("前置：counselor1 有已批准关系，必须能看到 1 条（防空数据假通过）")
                .isEqualTo(1);
        assertThat(data.path("list")).hasSize(1);
        assertThat(data.path("list").get(0).path("id").asInt())
                .as("只应看到存在 status=2 预约的学生")
                .isEqualTo(STUDENT1_ID);
    }

    @Test
    @DisplayName("T2. 🔴 counselor2（仅 status=1 待审核）→ page 0 条")
    void counselor2SeesNothing() throws Exception {
        assertThat(listData("/student", counselor2).path("total").asInt())
                .as("仅有待审核预约 ⇒ 不得看到任何学生")
                .isZero();
    }

    @Test
    @DisplayName("T3. 🔴 counselor3（仅 status=1 待审核）→ page 0 条（证明不含待审核）")
    void counselor3SeesNothing() throws Exception {
        assertThat(listData("/student", counselor3).path("total").asInt())
                .as("status=1 不得建立访问权")
                .isZero();
    }

    // ================== T15：/list 与 /page 必须一致（不得旁路） ==================

    @Test
    @DisplayName("T15. 🔴 counselor1/counselor2 的 /list 与 /page 结果一致（无旁路）")
    void listMatchesPage() throws Exception {
        assertThat(rawListTotal("/student/list", counselor1))
                .as("/list 与 /page 必须同源同条件")
                .isEqualTo(listData("/student", counselor1).path("total").asInt())
                .isEqualTo(1);
        assertThat(rawListTotal("/student/list", counselor2)).isZero();
    }

    // ================== T4–T7：/info 与 /detail ==================

    @Test
    @DisplayName("T4. 🔴 counselor1 → info/1 200")
    void counselor1ReadsApprovedStudentInfo() throws Exception {
        assertThat(status(get("/student/info/1").header("Token", counselor1)))
                .as("存在 status=2 关系 ⇒ 可读详情")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("T5. 🔴 counselor2 → info/1 403")
    void counselor2DeniedOnInfo() throws Exception {
        assertThat(status(get("/student/info/1").header("Token", counselor2)))
                .as("尚无已批准关系 ⇒ 403（不得因回调缺失而放行）")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("T6. 🔴 counselor2 → detail/1 403")
    void counselor2DeniedOnDetail() throws Exception {
        assertThat(status(get("/student/detail/1").header("Token", counselor2)))
                .as("detail 与 info 必须同一判定")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("T7. 🔴 counselor1 的 info 与 detail 状态码/业务码一致；counselor2 亦一致")
    void infoAndDetailAreConsistent() throws Exception {
        MvcResult infoC1 = call(get("/student/info/1").header("Token", counselor1));
        MvcResult detailC1 = call(get("/student/detail/1").header("Token", counselor1));
        assertThat(infoC1.getResponse().getStatus())
                .as("有权：info 与 detail 必须同为 200")
                .isEqualTo(detailC1.getResponse().getStatus())
                .isEqualTo(200);
        assertThat(raw(infoC1).path("code").asInt())
                .isEqualTo(raw(detailC1).path("code").asInt())
                .isZero();

        MvcResult infoC2 = call(get("/student/info/1").header("Token", counselor2));
        MvcResult detailC2 = call(get("/student/detail/1").header("Token", counselor2));
        assertThat(infoC2.getResponse().getStatus())
                .as("无权：info 与 detail 必须同为 403")
                .isEqualTo(detailC2.getResponse().getStatus())
                .isEqualTo(403);
        assertThat(raw(infoC2).path("code").asInt())
                .isEqualTo(raw(detailC2).path("code").asInt())
                .isEqualTo(403);
    }

    // ================== T8–T10：学生维度不得回归 ==================

    @Test
    @DisplayName("T8. 🔴 student1 / student2 各自只见本人（学生维度不回归）")
    void studentScopeUnchanged() throws Exception {
        assertThat(listData("/student", student1).path("total").asInt()).isEqualTo(1);
        assertThat(listData("/student", student1).path("list").get(0).path("id").asInt())
                .isEqualTo(1);

        assertThat(listData("/student", student2).path("total").asInt()).isEqualTo(1);
        assertThat(listData("/student", student2).path("list").get(0).path("id").asInt())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("T10. 🔴 student2 → info/1 仍 403（既有 IDOR 修复不回归）")
    void studentCannotReadOthers() throws Exception {
        assertThat(status(get("/student/info/1").header("Token", student2))).isEqualTo(403);
        assertThat(status(get("/student/detail/1").header("Token", student2))).isEqualTo(403);
    }

    // ================== T11–T12：反向自检与契约 ==================

    @Test
    @DisplayName("T11. 🔴 admin 仍可见全部 3 条且 info/1 200（防全局空假通过）")
    void adminStillSeesAll() throws Exception {
        assertThat(listData("/student", admin).path("total").asInt())
                .as("管理端权限不得因本批受限")
                .isEqualTo(TOTAL_STUDENTS);
        assertThat(status(get("/student/info/1").header("Token", admin))).isEqualTo(200);
    }

    @Test
    @DisplayName("T12. 未认证 → 200 + code=401（既有契约不变）")
    void anonymousStillGets401() throws Exception {
        MvcResult result = call(get("/student/page").param("page", "1").param("limit", "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(raw(result).path("code").asInt()).isEqualTo(401);
    }

    // ================== helpers ==================

    private JsonNode listData(String module, String token) throws Exception {
        MvcResult result = call(get(module + "/page")
                .param("page", "1")
                .param("limit", "20")
                .header("Token", token));
        JsonNode root = raw(result);
        assertThat(root.path("code").asInt())
                .as("列表查询应成功（%s）", module)
                .isZero();
        return root.path("data");
    }

    private int rawListTotal(String path, String token) throws Exception {
        JsonNode root = raw(call(get(path).header("Token", token)));
        assertThat(root.path("code").asInt()).as("%s 应成功", path).isZero();
        return root.path("data").path("total").asInt();
    }

    private int status(MockHttpServletRequestBuilder builder) throws Exception {
        return call(builder).getResponse().getStatus();
    }

    private MvcResult call(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode raw(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
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
