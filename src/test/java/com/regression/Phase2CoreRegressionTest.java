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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Phase 2 / T-2：最小回归测试基线
 *
 * <p><b>目的</b>：把 Step 2 / SH-A / Step 3 / T-1 中<strong>已用脚本化 curl 人工验证过</strong>的行为，
 * 固化为可自动执行的回归测试，为后续 Step 5b/5c 的批量重命名与 Phase 3 前端重构
 * 提供「旧业务是否被破坏」的判定依据（硬验收 1）。
 *
 * <p><b>原则</b>：测试的是<strong>当前已确认正确的行为</strong>，不是期望的未来形态。
 *
 * <p><b>形态</b>：{@code @SpringBootTest} + {@code @AutoConfigureMockMvc}（方案 A）。
 * 不启动真实端口，但走完整 MVC + 拦截器链，<strong>不对任何环节做 mock</strong>。
 *
 * <p><b>前置条件</b>：需要可用 MySQL —— {@code DictionaryServletContextListener}（{@code @WebListener}）
 * 在 context 初始化时会查询 {@code sys_dict_item} 表，DB 不可用则上下文启动失败。
 *
 * <p><b>基线数据来源</b>：{@code mental_health_companion_agent} 库的种子数据
 * （见 {@code src/main/resources/mental_health_companion_agent.sql}）。
 * 下列常量即<strong>当前的基线数值</strong>；若日后数据变更导致失败，失败本身就是「数据变了」的记录。
 */
@SpringBootTest
@AutoConfigureMockMvc
class Phase2CoreRegressionTest {

    // ================== 基线常量（当前种子数据的期望值） ==================

    /** 心理健康知识文章数（knowledge_article） */
    private static final int BASELINE_KNOWLEDGE_ARTICLES = 5;
    /** 测评试卷数（assessment_paper） */
    private static final int BASELINE_ASSESSMENT_PAPERS = 2;
    /** 学生数（student） */
    private static final int BASELINE_STUDENTS = 3;
    /** 心理老师数（counselor） */
    private static final int BASELINE_COUNSELORS = 3;

    /** 分页基线：每页 2 条，共 3 条学生 → 2 页 */
    private static final int PAGE_LIMIT = 2;
    private static final int BASELINE_STUDENT_TOTAL_PAGES = 2;

    /**
     * SH-A 已下线的 15 个动态 SQL 端点。
     * 断言它们<strong>持续 404</strong> —— 防止后续因测试或开发需要而把它们「恢复」出来。
     */
    private static final String[] REMOVED_DYNAMIC_SQL_ENDPOINTS = {
            "/option/sys_dict_item/dict_code",
            "/follow/student/id",
            "/sh/assessment_paper",
            "/cal/student/id",
            "/value/student/id/name",
            "/queryScore",
            "/group/student",
            "/group/student/id",
            "/remind/student/id/1",
            "/newSelectGroupSum",
            "/newSelectGroupCount",
            "/newSelectDateGroupSum",
            "/newSelectDateGroupCount",
            "/barSum",
            "/barCount"
    };

    /**
     * 需要检查「不得出现敏感字段」的分页接口清单。
     * 其中后 6 个是**关联字段泄露**的来源（{@code studentIdCardNo} 等），Step 3 已修复。
     */
    private static Map<String, String> sensitiveCheckTargets() {
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put("学生列表", "/yonghu/list");
        targets.put("学生分页", "/yonghu/page?page=1&limit=" + PAGE_LIMIT);
        targets.put("预约列表", "/xinlilaoshiOrder/list");
        targets.put("预约分页", "/xinlilaoshiOrder/page?page=1&limit=" + PAGE_LIMIT);
        targets.put("留言列表", "/xinlilaoshiLiuyan/list");
        targets.put("留言分页", "/xinlilaoshiLiuyan/page?page=1&limit=" + PAGE_LIMIT);
        targets.put("收藏列表", "/xinlilaoshiCollection/list");
        targets.put("收藏分页", "/xinlilaoshiCollection/page?page=1&limit=" + PAGE_LIMIT);
        targets.put("测评记录列表", "/examrecord/list");
        targets.put("测评记录分页", "/examrecord/page?page=1&limit=" + PAGE_LIMIT);
        targets.put("答题明细列表", "/examredetails/list");
        targets.put("答题明细分页", "/examredetails/page?page=1&limit=" + PAGE_LIMIT);
        targets.put("错题列表", "/examrewrongquestion/list");
        targets.put("错题分页", "/examrewrongquestion/page?page=1&limit=" + PAGE_LIMIT);
        return targets;
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String studentToken;

    @BeforeEach
    void loginAsStudent() throws Exception {
        JsonNode body = getJson("/yonghu/login?username=a1&password=123456", null);
        assertThat(body.path("code").asInt()).as("学生登录应成功").isZero();
        studentToken = body.path("token").asText();
        assertThat(studentToken).as("登录应返回 token").isNotBlank();
    }

    // ================== 1. 登录 ==================

    @Test
    @DisplayName("1. 学生登录：返回 token 与角色信息")
    void studentLogin_returnsTokenAndRole() throws Exception {
        JsonNode body = getJson("/yonghu/login?username=a1&password=123456", null);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("token").asText()).isNotBlank();
        assertThat(body.path("role").asText()).isEqualTo("学生");
        assertThat(body.path("tableName").asText()).isEqualTo("student");
    }

    // ================== 2-3. 列表基线数量 ==================

    @Test
    @DisplayName("2. 心理健康知识列表：返回基线数量")
    void knowledgeList_returnsBaselineCount() throws Exception {
        JsonNode body = getJson("/jiankangzhishi/list", null);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt())
                .as("knowledge_article 基线数量")
                .isEqualTo(BASELINE_KNOWLEDGE_ARTICLES);
    }

    @Test
    @DisplayName("3. 测评试卷列表：返回基线数量")
    void assessmentPaperList_returnsBaselineCount() throws Exception {
        JsonNode body = getJson("/exampaper/list", null);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt())
                .as("assessment_paper 基线数量")
                .isEqualTo(BASELINE_ASSESSMENT_PAPERS);
    }

    @Test
    @DisplayName("5. 心理老师列表：返回基线数量")
    void counselorList_returnsBaselineCount() throws Exception {
        JsonNode body = getJson("/xinlilaoshi/list", null);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt())
                .as("counselor 基线数量")
                .isEqualTo(BASELINE_COUNSELORS);
    }

    // ================== 4. MyBatis-Plus 3.x 分页 ==================

    @Test
    @DisplayName("4. MP3 分页：total / pageSize / totalPage / 记录数 正确")
    void studentPage_paginationMetadataIsCorrect() throws Exception {
        JsonNode body = getJson("/yonghu/page?page=1&limit=" + PAGE_LIMIT, studentToken);
        assertThat(body.path("code").asInt()).isZero();

        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt()).as("总记录数").isEqualTo(BASELINE_STUDENTS);
        assertThat(data.path("pageSize").asInt()).as("每页条数").isEqualTo(PAGE_LIMIT);
        assertThat(data.path("totalPage").asInt()).as("总页数").isEqualTo(BASELINE_STUDENT_TOTAL_PAGES);
        assertThat(data.path("list").size()).as("当前页记录数").isEqualTo(PAGE_LIMIT);
    }

    // ================== 6. 敏感字段（Step 3 / S1 的自动化断言） ==================

    @Test
    @DisplayName("6a. 分页/列表接口：响应不含 password 与身份证字段（含关联字段）")
    void sensitiveFields_areNeverExposedInListsAndPages() throws Exception {
        for (Map.Entry<String, String> entry : sensitiveCheckTargets().entrySet()) {
            String label = entry.getKey();
            String uri = entry.getValue();

            MvcResult result = perform(uri, studentToken);
            assertThat(result.getResponse().getStatus()).as("%s 应返回 200", label).isEqualTo(200);

            String body = result.getResponse().getContentAsString();
            assertThat(body).as("%s 响应不应包含 password", label).doesNotContainIgnoringCase("password");
            assertThat(body).as("%s 响应不应包含身份证字段", label).doesNotContainIgnoringCase("idcardno");

            // 防止「空响应让断言空跑」
            JsonNode json = objectMapper.readTree(body);
            assertThat(json.path("code").asInt()).as("%s 应正常返回", label).isZero();
            assertThat(json.path("data").path("total").asInt())
                    .as("%s 应有数据（否则敏感字段断言是空跑）", label)
                    .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("6b. 学生详情接口：响应不含 password 与身份证字段")
    void sensitiveFields_areNeverExposedInDetail() throws Exception {
        MvcResult result = perform("/yonghu/info/1", studentToken);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        String body = result.getResponse().getContentAsString();
        assertThat(body).as("学生详情不应包含 password").doesNotContainIgnoringCase("password");
        assertThat(body).as("学生详情不应包含身份证字段").doesNotContainIgnoringCase("idcardno");
        assertThat(body.length()).as("学生详情应返回内容（否则断言是空跑）").isGreaterThan(50);
    }

    // ================== 7. SH-A 安全回归 ==================

    @Test
    @DisplayName("7. 安全回归：SH-A 删除的 15 个动态 SQL 端点保持 404")
    void removedDynamicSqlEndpoints_areStillGone() throws Exception {
        for (String uri : REMOVED_DYNAMIC_SQL_ENDPOINTS) {
            MvcResult result = perform(uri, studentToken);
            assertThat(result.getResponse().getStatus())
                    .as("端点 %s 应保持 404（不得因测试/开发需要而恢复）", uri)
                    .isEqualTo(404);
        }
    }

    // ================== 工具方法 ==================

    private MvcResult perform(String uri, String token) throws Exception {
        var builder = get(uri);
        if (token != null) {
            builder = builder.header("Token", token);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode getJson(String uri, String token) throws Exception {
        return objectMapper.readTree(perform(uri, token).getResponse().getContentAsString());
    }
}
