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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * `/student/detail/{id}` 学生跨用户 IDOR 回归（本轮唯一修复目标）。
 *
 * <p><b>已确认缺陷（修复前）</b>：
 * <pre>
 * student a1(id=1) → /student/detail/2 → HTTP 200 + 返回 a2 的资料（含手机号）
 * student a1(id=1) → /student/detail/3 → HTTP 200 + 返回 a3 的资料
 * 对照 /student/info/2 → HTTP 403（该端点有 assertOwnership）
 * </pre>
 *
 * <p><b>本批边界（用户 2026-10-25 裁定）</b>：
 * <ul>
 *   <li>✅ 只修「学生读其他学生」的横向越权；</li>
 *   <li>❌ 不重新定义咨询师的数据范围 —— 本批咨询师**与 `/info` 保持一致**，
 *       但<b>这不代表咨询师全量读取权限已被认定合理</b>（遗留风险，见报告）；</li>
 *   <li>❌ 不修改 {@code OwnershipGuard} 全局逻辑、不修改六个 JOIN View、不改 DB。</li>
 * </ul>
 *
 * <p><b>契约</b>（与 `/info` 完全一致）：
 * <pre>
 * 401 → 未认证（HTTP 200 + body.code=401）
 * 403 → 已认证但无权（HTTP 403 + body.code=403）
 * 403 → 记录不存在（target == null ⇒ deny，与 /info 一致；修复前为 200 + code=511）
 * </pre>
 *
 * <p>种子数据：student a1/a2/a3 的 id = 1/2/3。
 *
 * <p><b>⚠️ 测试数据依赖（限制声明）</b>：本类依赖种子数据的固定 id（1/2/3），
 * 与既有测试口径一致（如 {@code AgentControllerRegressionTest} 的 {@code STUDENT_A1_ID = 1}）。
 * 若种子数据变更需同步更新常量。本类<b>不</b>依赖测试执行顺序
 * —— 每个用例在 {@code @BeforeEach} 中独立登录，无共享可变状态，无 {@code @Order}。
 *
 * <p><b>不使用 mock</b>：全程经 {@code MockMvc} 走
 * 真实拦截器 → 真实 {@link com.security.OwnershipGuard} → 真实 Controller → 真实 DB；
 * <b>不</b>用占位替身模拟授权结果，因此「有 assertOwnership 调用」不会让测试自动变绿。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("/student/detail IDOR 回归")
class StudentDetailIdorRegressionTest {

    private static final long SELF_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long ANOTHER_ID = 3L;
    private static final long MISSING_ID = 999999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String studentA1Token;
    private String counselorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        studentA1Token = login("/student/login", "a1", "123456");
        counselorToken = login("/counselor/login", "a1", "123456");
        adminToken = login("/adminUser/login", "admin", "admin");
    }

    // ================== T1：本人（不得回归） ==================

    @Test
    @DisplayName("T1. 学生读【本人】资料 → 200 + code=0 + 返回本人 id")
    void studentSelf_allowed() throws Exception {
        MvcResult result = detail(studentA1Token, SELF_ID);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = body(result);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("id").asInt()).isEqualTo((int) SELF_ID);
    }

    // ================== T2：核心目标 ==================

    @Test
    @DisplayName("T2. 🔴 学生读【其他学生】→ 403（本批唯一修复目标）")
    void studentOther_denied() throws Exception {
        MvcResult other = detail(studentA1Token, OTHER_ID);
        assertThat(other.getResponse().getStatus())
                .as("学生 a1 读 a2 必须被拒绝")
                .isEqualTo(403);

        MvcResult another = detail(studentA1Token, ANOTHER_ID);
        assertThat(another.getResponse().getStatus())
                .as("学生 a1 读 a3 必须被拒绝")
                .isEqualTo(403);

        // 响应体不得泄露目标学生的任何可识别字段（username / name / phone / email）
        String raw = other.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw)
                .as("拒绝时不得返回 username")
                .doesNotContain("a2");
        assertThat(raw)
                .as("拒绝时不得返回姓名")
                .doesNotContain("学生姓名2");
        assertThat(raw)
                .as("拒绝时不得返回手机号")
                .doesNotContain("17703786902");
        assertThat(raw)
                .as("拒绝时不得返回邮箱")
                .doesNotContain("2@qq.com");
    }

    // ================== T3 / T4：既有合法访问不得被打断 ==================

    @Test
    @DisplayName("T3. 咨询师读【无已批准关系】的学生 → 403（AUTHZ-COUNSELOR-STUDENT-SCOPE）")
    void counselor_denied_forUnrelatedStudent() throws Exception {
        MvcResult result = detail(counselorToken, OTHER_ID);

        // 本用例原为「咨询师 → 200」，那是把【越权行为当成基线】固化下来的写法。
        // 用户 2026-10-25 裁定：咨询师仅可访问存在 status=2 已批准预约的学生
        // ⇒ 该断言必须改为 403。counselor a1 与 student 2 之间无任何预约记录。
        assertThat(result.getResponse().getStatus())
                .as("无已批准预约关系 ⇒ 不得读取该学生资料")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("T3b. 🔴 咨询师读【已批准关系】的学生 → 200（防「一律 403」式假通过）")
    void counselor_allowed_forApprovedStudent() throws Exception {
        MvcResult result = detail(counselorToken, SELF_ID);

        // 反向自检：counselor a1 与 student 1 存在 status=2 的预约 ⇒ 必须放行。
        // 若此用例也变 403，说明收窄过头（误伤），而非修复成功。
        assertThat(result.getResponse().getStatus())
                .as("存在 status=2 已批准预约 ⇒ 应可读该学生资料")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("T4. 管理员读学生 → 200（既有合法能力不得回归）")
    void admin_allowed() throws Exception {
        assertThat(detail(adminToken, OTHER_ID).getResponse().getStatus()).isEqualTo(200);
    }

    // ================== T5：认证契约不得改变 ==================

    @Test
    @DisplayName("T5. 未登录 → 既有契约 HTTP 200 + body.code=401")
    void unauthenticated_contractUnchanged() throws Exception {
        MvcResult result = mockMvc.perform(get("/student/detail/" + SELF_ID)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("未认证仍为 HTTP 200（拦截器契约，不得改为 401 状态码）")
                .isEqualTo(200);
        assertThat(body(result).path("code").asInt()).isEqualTo(401);
    }

    // ================== T6：不存在的 id（已确认的行为变更） ==================

    @Test
    @DisplayName("T6. 不存在的 id → 403（修复前为 200 + code=511，与 /info 对齐）")
    void missingId_denied() throws Exception {
        MvcResult result = detail(studentA1Token, MISSING_ID);

        assertThat(result.getResponse().getStatus())
                .as("target == null ⇒ deny() ⇒ 403（与 /info 一致）")
                .isEqualTo(403);
    }

    // ================== T7：两个端点行为一致性 ==================

    @Test
    @DisplayName("T7. /detail 与 /info 对同一身份 + 同一 id 的【HTTP 状态 + 业务 code】必须一致")
    void detailAndInfo_consistent() throws Exception {
        for (long id : new long[]{SELF_ID, OTHER_ID, MISSING_ID}) {
            assertConsistent(studentA1Token, id);
        }

        // 成功路径再验一层：两端点返回的 data.id 必须相同
        MvcResult d = detail(studentA1Token, SELF_ID);
        MvcResult i = info(studentA1Token, SELF_ID);
        assertThat(body(d).path("data").path("id").asInt())
                .as("本人资料：两端点返回同一对象")
                .isEqualTo(body(i).path("data").path("id").asInt());
    }

    /** 逐 id 对比 HTTP 状态码 + 业务 code（两者都必须一致） */
    private void assertConsistent(String token, long id) throws Exception {
        MvcResult d = detail(token, id);
        MvcResult i = info(token, id);

        assertThat(d.getResponse().getStatus())
                .as("id=%d：HTTP 状态码必须一致", id)
                .isEqualTo(i.getResponse().getStatus());
        assertThat(body(d).path("code").asInt())
                .as("id=%d：业务 code 必须一致", id)
                .isEqualTo(body(i).path("code").asInt());
    }

    // ================== helpers ==================

    private MvcResult detail(String token, long id) throws Exception {
        return mockMvc.perform(get("/student/detail/" + id).header("Token", token)).andReturn();
    }

    private MvcResult info(String token, long id) throws Exception {
        return mockMvc.perform(get("/student/info/" + id).header("Token", token)).andReturn();
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
