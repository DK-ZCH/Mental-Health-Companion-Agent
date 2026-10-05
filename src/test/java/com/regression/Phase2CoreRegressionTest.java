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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 *
 * <p><b>用例分组</b>：1–8 为 T-2 基线；<b>9 为 Step 4 异常契约</b>
 * （EIException / 400 / 404 / 500 / 405 与 {@code /error} 劫持防回归）。
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
     * 受保护接口清单（无 {@code @IgnoreAuth}，且不在拦截器硬编码白名单中）。
     *
     * <p>用于断言「无 Token → 拒绝访问」。<b>注意当前实现形态</b>：
     * {@code AuthorizationInterceptor} 只把 {@code {"code":401,"msg":"请先登录"}} 写进响应体，
     * <b>并不设置 HTTP 状态码</b>，所以实测是 <b>HTTP 200 + body {@code code=401}</b>。
     *
     * <p>因此这里<b>只断言 body 的 {@code code}</b>、<b>刻意不断言 HTTP 状态</b> ——
     * 将来若把状态码修正为标准的 401，本测试不会被误伤。
     *
     * <p>另：{@code /yonghu/list}、{@code /exampaper/list}、{@code /dictionary/page} 属免鉴权路径，
     * 是否收紧属**待决策 D2**（匿名 /list 是否收敛），故本测试<b>不对它们做任何断言</b>。
     */
    private static final String[] TOKEN_PROTECTED_ENDPOINTS = {
            "/yonghu/page?page=1&limit=" + PAGE_LIMIT,
            "/yonghu/info/1",
            "/users/page?page=1&limit=" + PAGE_LIMIT,
            "/users/list",
            "/xinlilaoshi/page?page=1&limit=" + PAGE_LIMIT,
            "/exampaper/page?page=1&limit=" + PAGE_LIMIT,
            "/tongzhi/page?page=1&limit=" + PAGE_LIMIT,
            "/examrecord/page?page=1&limit=" + PAGE_LIMIT,
            "/examredetails/page?page=1&limit=" + PAGE_LIMIT,
            "/xinlilaoshiOrder/page?page=1&limit=" + PAGE_LIMIT
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

    // ================== 8. 鉴权 ==================

    @Test
    @DisplayName("8. 鉴权：无 Token 访问受保护接口被拒绝（body code=401）")
    void protectedEndpoints_withoutToken_areRejected() throws Exception {
        for (String uri : TOKEN_PROTECTED_ENDPOINTS) {
            MvcResult result = perform(uri, null);
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

            assertThat(body.path("code").asInt())
                    .as("无 Token 访问 %s 应被拒绝（body.code=401）", uri)
                    .isEqualTo(401);
            assertThat(body.path("msg").asText())
                    .as("无 Token 访问 %s 应提示「请先登录」", uri)
                    .contains("请先登录");
        }
        // 配套对照：同一批接口在【带 Token】时可通过 —— 已由用例 2/3/4/5/6a 覆盖。
        // 两者成对，才能证明 401 来自「缺少 Token」而非「接口本身坏掉」。
    }

    // ================== 9. 异常契约（Step 4 第二阶段 A1–A4 的自动化固化） ==================
    //
    // 背景：改造前，未捕获异常 / 参数错误 / 404 的响应体被鉴权拦截器改写成 {"code":401}，
    //       导致「服务器错误」被误报为「未登录」（详见 docs/PHASE2-T4-EXCEPTION-AUDIT.md §4.1）。
    //
    // 断言语义：以下用例同时断言【HTTP 状态】与【body.code】，且两者必须精确匹配 ——
    //       只断言其一，都可能漏掉「HTTP 状态正确但 body 被劫持」这类缺陷（改造前正是这种组合）。
    //       因此「错误响应体不再是 code=401」已被精确状态码断言覆盖，无需额外的 != 401 断言。

    @Test
    @DisplayName("9a. EIException 业务异常：HTTP 200 + 异常自带 code + 业务提示")
    void businessException_returnsOkWithBusinessMessage() throws Exception {
        // 触发源 1：空文件上传（FileController 显式 throw EIException）
        //
        // 注意：/file/upload 在生产环境属「拦截器硬编码白名单」，无 Token 也能访问（已由真实 HTTP 实测确认）。
        // 但该白名单比较的是 request.getServletPath()，而 MockMvc 不会像真实容器那样填充该字段，
        // 故测试环境下白名单不生效。这里显式带上 Token —— 本用例要固化的是「EIException 的响应契约」，
        // 不是「鉴权白名单」，带上 Token 可让用例与运行环境无关。
        MvcResult emptyUpload = mockMvc.perform(multipart("/file/upload")
                .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .header("Token", studentToken)).andReturn();
        assertThat(emptyUpload.getResponse().getStatus())
                .as("业务异常应保持 HTTP 200 —— 两套前端只在 2xx 上显示 msg，返 4xx 会丢失业务提示")
                .isEqualTo(200);
        JsonNode b1 = objectMapper.readTree(emptyUpload.getResponse().getContentAsString());
        assertThat(b1.path("code").asInt()).as("应返回 EIException 自带 code").isEqualTo(500);
        assertThat(b1.path("msg").asText()).as("应返回可读业务提示").isEqualTo("上传文件不能为空");

        // 触发源 2：SQLFilter 命中关键词（Query → SQLFilter.sqlInject 抛 EIException）
        MvcResult keyword = perform("/yonghu/page?page=1&limit=" + PAGE_LIMIT + "&sidx=select", studentToken);
        assertThat(keyword.getResponse().getStatus()).as("业务异常应保持 HTTP 200").isEqualTo(200);
        JsonNode b2 = objectMapper.readTree(keyword.getResponse().getContentAsString());
        assertThat(b2.path("code").asInt()).isEqualTo(500);
        assertThat(b2.path("msg").asText()).isEqualTo("包含非法字符");
    }

    @Test
    @DisplayName("9b. 参数/请求错误 → HTTP 400 + code=400（三个触发源）")
    void badRequest_returns400() throws Exception {
        assertBadRequest(perform("/file/download", null), "缺少必填参数");

        assertBadRequest(perform("/yonghu/resetPassword?id=abc", studentToken), "参数类型不符");

        assertBadRequest(perform(post("/yonghu/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad json")
                .header("Token", studentToken)), "请求体不可读（非法 JSON）");
    }

    @Test
    @DisplayName("9c. 资源不存在 → HTTP 404 + code=404（改造前是被劫持的 code=401）")
    void notFound_returns404() throws Exception {
        MvcResult result = perform("/not-exist-xyz", null);

        assertThat(result.getResponse().getStatus()).as("不存在的路径应返回 HTTP 404").isEqualTo(404);

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt())
                .as("body.code 应为 404 —— 改造前这里是 401（错误体被鉴权拦截器劫持）")
                .isEqualTo(404);
        assertThat(body.path("msg").asText()).isEqualTo("请求的资源不存在");
    }

    @Test
    @DisplayName("9d. 未捕获异常 → HTTP 500 + code=500 且不泄漏内部细节（两个触发源）")
    void unhandledException_returns500() throws Exception {
        assertServerError(
                perform("/yonghu/page?page=1&limit=" + PAGE_LIMIT + "&sidx=not_exist_col&order=asc", studentToken),
                "排序列不存在（SQL 异常）");

        assertServerError(
                perform("/yonghu/page?page=abc&limit=" + PAGE_LIMIT, studentToken),
                "分页参数非数字");
    }

    @Test
    @DisplayName("9e. 标准异常保留自带状态码；/error 不再被鉴权劫持（P0 防回归）")
    void standardExceptionKeepsStatus_andErrorPathIsNotHijacked() throws Exception {
        // 405：/users/login 只接受 POST。若兜底处理器一律返回 500，本条会失败。
        MvcResult methodNotAllowed = perform("/users/login", null);
        assertThat(methodNotAllowed.getResponse().getStatus())
                .as("应为 405，而不是被兜底处理器吞成 500")
                .isEqualTo(405);
        JsonNode body = objectMapper.readTree(methodNotAllowed.getResponse().getContentAsString());
        assertThat(body.path("code").asInt())
                .as("body.code 应跟随 HTTP 状态（405），而不是 401")
                .isEqualTo(405);

        // P0 防回归：/error 是 Spring Boot 内部错误分派路径，必须被拦截器排除。
        // 若排除项被移除，这里会拿到鉴权拦截器写的 {"code":401}。
        JsonNode errorBody = objectMapper.readTree(
                perform("/error", null).getResponse().getContentAsString());
        assertThat(errorBody.has("status"))
                .as("/error 应返回 Spring Boot 默认错误结构（防空跑：若响应体形态变了，本条先失败）")
                .isTrue();
        assertThat(errorBody.path("code").asInt(-1))
                .as("/error 不得返回鉴权拦截器的 code=401（拦截器应已排除该路径）")
                .isNotEqualTo(401);
    }

    private void assertBadRequest(MvcResult result, String source) throws Exception {
        assertThat(result.getResponse().getStatus()).as("%s 应返回 HTTP 400", source).isEqualTo(400);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt()).as("%s 的 body.code 应为 400", source).isEqualTo(400);
    }

    private void assertServerError(MvcResult result, String source) throws Exception {
        assertThat(result.getResponse().getStatus()).as("%s 应返回 HTTP 500", source).isEqualTo(500);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt()).as("%s 的 body.code 应为 500", source).isEqualTo(500);
        assertThat(body.path("msg").asText())
                .as("%s 不应向客户端泄漏内部异常细节", source)
                .isEqualTo("服务器内部错误，请联系管理员");
    }

    // ================== 工具方法 ==================

    private MvcResult perform(String uri, String token) throws Exception {
        var builder = get(uri);
        if (token != null) {
            builder = builder.header("Token", token);
        }
        return perform(builder);
    }

    private MvcResult perform(RequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode getJson(String uri, String token) throws Exception {
        return objectMapper.readTree(perform(uri, token).getResponse().getContentAsString());
    }
}
