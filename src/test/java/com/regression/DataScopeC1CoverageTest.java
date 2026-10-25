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
 * `DataScope` <b>C1 覆盖补齐</b>：`counseling_appointment` 与 `counselor_favorite`
 * 的行级范围行为测试。
 *
 * <p><b>为什么需要本类</b>：`DATASCOPE-SEMANTIC-MATRIX.md` 的覆盖核查发现——
 * 这两个模块虽属 C1（双维度都真实生效），但**没有行级范围断言**。
 * `Phase2CoreRegressionTest` 虽请求过它们的 `/page`，但那是
 * 「响应不含 `password` / 身份证」的**字段检查**，不能证明「返回行被收敛到当前用户」。
 * ⇒ 本类只补测试，<b>不改任何生产代码</b>。
 *
 * <p><b>防「空数据假通过」</b>：每个用例都先断言**有权一侧 `total > 0`**，
 * 再断言**无权一侧被排除**。若种子数据缺失导致两边都为 0，
 * 该断言会失败而不是「通过」。
 *
 * <p><b>种子数据（只读）</b>：
 * <pre>
 * counseling_appointment : student1 → 3 条（counselor1/2/3 各 1）；student2/3 → 0
 *                          counselor1/2/3 → 各 1 条
 * counselor_favorite     : student1/2/3 → 各 2 条
 *                          counselor1 → 2 条 · counselor2 → 3 条 · counselor3 → 1 条
 * </pre>
 * ⇒ 两个模块在**学生维度与咨询师维度**上都能区分「条件生效 / 条件被移除」。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("DataScope C1 覆盖补齐")
class DataScopeC1CoverageTest {

    private static final int APPOINTMENT_TOTAL = 3;
    private static final int APPOINTMENT_PER_COUNSELOR = 1;
    private static final int FAVORITE_TOTAL = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String student1;
    private String student2;
    private String student3;
    private String counselor1;
    private String counselor2;
    private String counselor3;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        student1 = login("/student/login", "a1", "123456");
        student2 = login("/student/login", "a2", "123456");
        student3 = login("/student/login", "a3", "123456");
        counselor1 = login("/counselor/login", "a1", "123456");
        counselor2 = login("/counselor/login", "a2", "123456");
        counselor3 = login("/counselor/login", "a3", "123456");
        admin = login("/adminUser/login", "admin", "admin");
    }

    // ================== A. counseling_appointment ==================

    @Test
    @DisplayName("A1. 🔴 appointment · 学生维度：student1 得本人 3 条，student2 得 0 条")
    void appointment_studentScope() throws Exception {
        JsonNode mine = page("/counselingAppointment", student1);

        assertThat(mine.path("total").asInt())
                .as("前置：student1 应有本人预约（防空数据假通过）")
                .isEqualTo(APPOINTMENT_TOTAL);
        for (JsonNode row : mine.path("list")) {
            assertThat(row.path("studentId").asInt())
                    .as("学生只能看到本人预约")
                    .isEqualTo(1);
        }

        JsonNode other = page("/counselingAppointment", student2);
        assertThat(other.path("total").asInt())
                .as("student2 无任何预约 ⇒ 必须 0 条（不得看到 student1 的 3 条）")
                .isZero();
        assertThat(other.path("list").size()).isZero();
    }

    @Test
    @DisplayName("A2. 🔴 appointment · 咨询师维度：各自只得本人接的 1 条（对照 admin 的 3 条）")
    void appointment_counselorScope() throws Exception {
        int[] expectedIds = {1, 2, 3};
        String[] tokens = {counselor1, counselor2, counselor3};

        for (int i = 0; i < tokens.length; i++) {
            JsonNode data = page("/counselingAppointment", tokens[i]);

            assertThat(data.path("total").asInt())
                    .as("前置：counselor%d 应至少有 1 条（防空数据假通过）", expectedIds[i])
                    .isEqualTo(APPOINTMENT_PER_COUNSELOR);

            for (JsonNode row : data.path("list")) {
                assertThat(row.path("counselorId").asInt())
                        .as("老师只能看到 counselorId=%d 的预约", expectedIds[i])
                        .isEqualTo(expectedIds[i]);
            }
        }

        // 反向对照：管理员看到全量 3 条 ⇒ 证明上面的 1 条确实是「被收窄」的结果，而非全表本就 1 条
        assertThat(page("/counselingAppointment", admin).path("total").asInt())
                .as("管理员保持全量（B 类）")
                .isEqualTo(APPOINTMENT_TOTAL);
    }

    // ================== B. counselor_favorite ==================

    @Test
    @DisplayName("B1. 🔴 favorite · 学生维度：三位学生各得本人 2 条（对照 admin 的 6 条）")
    void favorite_studentScope() throws Exception {
        String[] tokens = {student1, student2, student3};

        for (int i = 0; i < tokens.length; i++) {
            JsonNode data = page("/counselorFavorite", tokens[i]);

            assertThat(data.path("total").asInt())
                    .as("前置：student%d 应有本人收藏（防空数据假通过）", i + 1)
                    .isEqualTo(2);

            for (JsonNode row : data.path("list")) {
                assertThat(row.path("studentId").asInt())
                        .as("学生只能看到本人收藏")
                        .isEqualTo(i + 1);
            }
        }

        assertThat(page("/counselorFavorite", admin).path("total").asInt())
                .as("管理员全量 6 条 ⇒ 证明学生的 2 条是被收窄的")
                .isEqualTo(FAVORITE_TOTAL);
    }

    @Test
    @DisplayName("B2. 🔴 favorite · 咨询师维度：counselor1 得 2 条、counselor2 得 3 条，且全部归属本人")
    void favorite_counselorScope() throws Exception {
        JsonNode c1 = page("/counselorFavorite", counselor1);
        assertThat(c1.path("total").asInt()).as("counselor1 应收敛到 2 条").isEqualTo(2);
        for (JsonNode row : c1.path("list")) {
            assertThat(row.path("counselorId").asInt()).isEqualTo(1);
        }

        JsonNode c2 = page("/counselorFavorite", counselor2);
        assertThat(c2.path("total").asInt()).as("counselor2 应收敛到 3 条").isEqualTo(3);
        for (JsonNode row : c2.path("list")) {
            assertThat(row.path("counselorId").asInt()).isEqualTo(2);
        }

        // 两人条数不同（2 vs 3），且都小于全量 6 ⇒ 双向证明收敛真实发生
        assertThat(page("/counselorFavorite", admin).path("total").asInt()).isEqualTo(FAVORITE_TOTAL);
    }

    // ================== C. 客户端参数不得放大范围 ==================

    @Test
    @DisplayName("C1. 🔴 学生提交他人 studentId → 结果与不提交完全一致（服务端覆盖）")
    void studentCannotWidenByParam() throws Exception {
        int ownAppointment = page("/counselingAppointment", student2).path("total").asInt();
        int forgedAppointment = page("/counselingAppointment", student2, "studentId", "1")
                .path("total").asInt();
        assertThat(forgedAppointment)
                .as("student2 提交 studentId=1 不得看到 student1 的预约")
                .isEqualTo(ownAppointment)
                .isZero();

        int ownFavorite = page("/counselorFavorite", student1).path("total").asInt();
        int forgedFavorite = page("/counselorFavorite", student1, "studentId", "2")
                .path("total").asInt();
        assertThat(forgedFavorite)
                .as("student1 提交 studentId=2 不得放大范围")
                .isEqualTo(ownFavorite)
                .isEqualTo(2);
    }

    @Test
    @DisplayName("C2. 🔴 老师提交他人 counselorId → 结果与不提交完全一致")
    void counselorCannotWidenByParam() throws Exception {
        int own = page("/counselorFavorite", counselor1).path("total").asInt();
        int forged = page("/counselorFavorite", counselor1, "counselorId", "2").path("total").asInt();

        assertThat(forged)
                .as("counselor1 提交 counselorId=2 不得看到 counselor2 的收藏")
                .isEqualTo(own)
                .isEqualTo(2);
    }

    // ================== D. 未认证契约不变 ==================

    @Test
    @DisplayName("D1. 未认证访问两个模块 → 200 + code=401（既有契约）")
    void anonymousStillGets401() throws Exception {
        for (String module : new String[]{"/counselingAppointment", "/counselorFavorite"}) {
            MvcResult result = call(get(module + "/page").param("page", "1").param("limit", "10"));
            JsonNode root = raw(result);

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(root.path("code").asInt())
                    .as("%s 未认证应保持 200 + code=401", module)
                    .isEqualTo(401);
        }
    }

    // ================== helpers ==================

    private JsonNode page(String module, String token) throws Exception {
        return body(call(get(module + "/page")
                .param("page", "1")
                .param("limit", "20")
                .header("Token", token)));
    }

    private JsonNode page(String module, String token, String key, String value) throws Exception {
        return body(call(get(module + "/page")
                .param("page", "1")
                .param("limit", "20")
                .param(key, value)
                .header("Token", token)));
    }

    private MvcResult call(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn();
    }

    /** 解析响应并断言 {@code code == 0}，返回 {@code data} 节点（本类范围断言都基于 data.total / data.list） */
    private JsonNode body(MvcResult result) throws Exception {
        JsonNode root = raw(result);
        assertThat(root.path("code").asInt())
                .as("列表查询应成功（%s）", result.getRequest().getRequestURI())
                .isZero();
        return root.path("data");
    }

    /** 只解析、不断言 code —— 供「未认证仍为 401」这类本身 code != 0 的用例使用 */
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
