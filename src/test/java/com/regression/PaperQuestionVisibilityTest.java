package com.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 试卷（{@code assessment_paper}）/ 试题（{@code assessment_question}）的
 * <b>资源可见性</b>回归。
 *
 * <p><b>裁定来源</b>：用户 2026-10-27 —— 可见性 = <b>A（按角色视角过滤）</b>，
 * 独立批次，<b>不并入</b> {@code DataScope} Contract Audit。
 *
 * <p><b>预期语义（两个不同性质的结果，不得混写）</b>：
 * <pre>
 * 列表（/list、/page）：HTTP 200，total = 0
 * 详情（/info/{id}）  ：HTTP 403
 * </pre>
 *
 * <p><b>修复前实测（红基线依据）</b>：种子数据两张试卷均为
 * {@code status=1（启用）} + {@code is_deleted=1（已删除）}，
 * 而四条读取路径对匿名/学生<b>均返回数据</b>：
 * {@code /list} 与 {@code /question/list} 是 {@code @IgnoreAuth}（匿名可读），
 * 两个 {@code /info/{id}} <b>无任何守卫</b>；试题还把父卷状态
 * <b>SELECT 出来却不参与 WHERE</b>（{@code AssessmentQuestionDao.xml:26-27} 有投影、无条件）。
 *
 * <p><b>反向自检（防止「全局返回空」式假通过）</b>：
 * 管理员必须仍看到 papers = 2、questions = 6，且试题含 {@code answer/analysis}。
 * 若这三条不成立，「学生 0 条」可能只是<b>整条链路坏了</b>。
 *
 * <p><b>独立验收</b>：{@code SEC-QUESTION-ANSWER-PUBLIC} 只做<b>回归固化</b>
 * （实测已由 {@code QuestionPublicDTO} 修复），不与可见性断言混为一谈。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("试卷/试题 资源可见性")
class PaperQuestionVisibilityTest {

    /** 种子数据基准（只读断言） */
    private static final int PAPER_ROWS = 2;
    private static final int QUESTION_ROWS = 6;

    /**
     * 一张<b>可见</b>的试卷 id：种子数据全部 {@code is_deleted=1}，
     * 故此处为「存在但不可见」的样本（用于验证 403，而非 511）。
     */
    private static final long HIDDEN_PAPER_ID = 1L;
    private static final long HIDDEN_QUESTION_ID = 1L;
    /** 不存在的 id：非管理视角必须与「存在但不可见」同码，避免存在性预言机 */
    private static final long MISSING_ID = 999999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 仅用于<b>事务内 fixture</b>（方法级 {@code @Transactional} ⇒ 测试结束自动回滚，不污染正式数据）*/
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String student1;
    private String counselor1;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        student1 = login("/student/login", "a1", "123456");
        counselor1 = login("/counselor/login", "a1", "123456");
        admin = login("/adminUser/login", "admin", "admin");
    }

    // ================== A. 试卷：列表（200 + total=0） ==================

    @Test
    @DisplayName("1. 🔴 匿名访问 paper/list → 200 且 total=0（/list 是 @IgnoreAuth，最易漏）")
    void anonymousSeesNoPaperOnList() throws Exception {
        MvcResult result = call(list("/assessmentPaper", null));
        assertThat(result.getResponse().getStatus()).as("列表语义是 200，不是 403").isEqualTo(200);
        assertThat(total(result)).as("已删除/禁用试卷不得对匿名可见").isZero();
    }

    @Test
    @DisplayName("2. 🔴 学生访问 paper/list → 200 且 total=0")
    void studentSeesNoPaperOnList() throws Exception {
        MvcResult result = call(list("/assessmentPaper", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    @Test
    @DisplayName("3. 🔴 学生访问 paper/page → 200 且 total=0")
    void studentSeesNoPaperOnPage() throws Exception {
        MvcResult result = call(page("/assessmentPaper", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    @Test
    @DisplayName("4. 🔴 咨询师与归属无关：paper/page 一律按公共视角 → total=0")
    void counselorSeesNoPaperOnPage() throws Exception {
        MvcResult result = call(page("/assessmentPaper", counselor1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    // ================== B. 试卷：详情（403） ==================

    @Test
    @DisplayName("5. 🔴 学生访问 paper/info/{已删除} → 403（修复前此路径无任何守卫）")
    void studentDeniedOnHiddenPaperInfo() throws Exception {
        MvcResult result = call(get("/assessmentPaper/info/" + HIDDEN_PAPER_ID).header("Token", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).path("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("6. 🔴 咨询师访问 paper/info/{已删除} → 403")
    void counselorDeniedOnHiddenPaperInfo() throws Exception {
        MvcResult result = call(get("/assessmentPaper/info/" + HIDDEN_PAPER_ID).header("Token", counselor1));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("7. 🔴 不存在 id 与「存在但不可见」同码 403 —— 不得形成存在性预言机")
    void missingPaperInfoIsIndistinguishable() throws Exception {
        MvcResult hidden = call(get("/assessmentPaper/info/" + HIDDEN_PAPER_ID).header("Token", student1));
        MvcResult missing = call(get("/assessmentPaper/info/" + MISSING_ID).header("Token", student1));

        assertThat(missing.getResponse().getStatus())
                .as("若「不存在」返回 511、「不可见」返回 403，则可用差异探测 id 是否存在")
                .isEqualTo(hidden.getResponse().getStatus());
        assertThat(body(missing).path("code").asInt()).isEqualTo(body(hidden).path("code").asInt());
    }

    // ================== C. 试题：必须继承父卷可见性 ==================

    @Test
    @DisplayName("8. 🔴 匿名访问 question/list → 200 且 total=0（父卷已删除 ⇒ 题目不可见）")
    void anonymousSeesNoQuestionOnList() throws Exception {
        MvcResult result = call(list("/assessmentQuestion", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).as("修复前：6 条 —— 父卷状态被 SELECT 但未参与 WHERE").isZero();
    }

    @Test
    @DisplayName("9. 🔴 学生访问 question/list → 200 且 total=0")
    void studentSeesNoQuestionOnList() throws Exception {
        MvcResult result = call(list("/assessmentQuestion", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    @Test
    @DisplayName("10. 🔴 学生访问 question/page → 200 且 total=0")
    void studentSeesNoQuestionOnPage() throws Exception {
        MvcResult result = call(page("/assessmentQuestion", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    @Test
    @DisplayName("11. 🔴 学生访问 question/info/{id} → 403（父卷已删除）")
    void studentDeniedOnHiddenQuestionInfo() throws Exception {
        MvcResult result = call(get("/assessmentQuestion/info/" + HIDDEN_QUESTION_ID).header("Token", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("12. 🔴 咨询师访问 question/page → total=0（与归属无关，按公共视角）")
    void counselorSeesNoQuestionOnPage() throws Exception {
        MvcResult result = call(page("/assessmentQuestion", counselor1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    // ================== D. 客户端参数不得绕过 ==================

    @Test
    @DisplayName("13. 🔴 学生传 isDeleted=1 不得绕过（服务端条件只能收窄，不能被扩大）")
    void clientCannotWidenByIsDeleted() throws Exception {
        MvcResult result = call(get("/assessmentPaper/page")
                .param("page", "1").param("limit", "10").param("isDeleted", "1")
                .header("Token", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    @Test
    @DisplayName("14. 🔴 学生传 status=2 不得绕过（禁用态同样不可见）")
    void clientCannotWidenByStatus() throws Exception {
        MvcResult result = call(get("/assessmentPaper/page")
                .param("page", "1").param("limit", "10").param("status", "2")
                .header("Token", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    @Test
    @DisplayName("15. 🔴 学生传 paperIsDeleted=1 不得绕过试题可见性")
    void clientCannotWidenQuestionByPaperIsDeleted() throws Exception {
        MvcResult result = call(get("/assessmentQuestion/page")
                .param("page", "1").param("limit", "10").param("paperIsDeleted", "1")
                .header("Token", student1));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isZero();
    }

    // ================== E. 反向自检（管理员观不变） ==================

    @Test
    @DisplayName("16. ✅ 反向自检：admin 的 paper/page 必须 " + PAPER_ROWS + " 条（含已删除）")
    void adminStillSeesAllPapers() throws Exception {
        MvcResult result = call(page("/assessmentPaper", admin));
        assertThat(result.getResponse().getStatus()).as("管理视角不得被本批收紧").isEqualTo(200);
        assertThat(total(result)).as("否则「学生 0 条」可能只是查询坏了").isEqualTo(PAPER_ROWS);
    }

    @Test
    @DisplayName("17. ✅ 反向自检：admin 的 question/page 必须 " + QUESTION_ROWS + " 条")
    void adminStillSeesAllQuestions() throws Exception {
        MvcResult result = call(page("/assessmentQuestion", admin));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(total(result)).isEqualTo(QUESTION_ROWS);
    }

    @Test
    @DisplayName("18. ✅ 反向自检：admin 的 paper/info/{id} 仍 200（管理端详情不受影响）")
    void adminStillReadsPaperInfo() throws Exception {
        MvcResult result = call(get("/assessmentPaper/info/" + HIDDEN_PAPER_ID).header("Token", admin));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("19. ✅ 反向自检：admin 的 question/info/{id} 仍 200 且含 answer/analysis")
    void adminStillReadsQuestionInfoWithAnswer() throws Exception {
        MvcResult result = call(get("/assessmentQuestion/info/" + HIDDEN_QUESTION_ID).header("Token", admin));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode data = body(result).path("data");
        assertThat(data.path("answer").isMissingNode())
                .as("管理员需要答案与解析做管理").isFalse();
        assertThat(data.path("analysis").isMissingNode()).isFalse();
    }

    // ================== F. 独立验收：SEC-QUESTION-ANSWER-PUBLIC（仅回归固化） ==================

    @Test
    @DisplayName("20. ⚠️【独立项·弱断言】匿名 question/list 返回空集且不含 answer/analysis（不足以单独证明剥离）")
    void anonymousListStripsAnswer() throws Exception {
        MvcResult result = call(list("/assessmentQuestion", null));
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // ⚠️ 诚实边界：本批可见性修复后，匿名列表为【空集】⇒「不含 answer」在空集上恒真，
        //    因此本用例【不能】单独作为 SEC-QUESTION-ANSWER-PUBLIC 已修复的证据。
        //    真正的剥离证据是【修复前】的运行时实测（当时返回 6 条）：
        //      匿名/学生 → answer=false · analysis=false；管理员 → answer=true · analysis=true
        //    见 docs/BIZ-PAPER-QUESTION-VISIBILITY-BATCH.md §一。
        assertThat(total(result)).as("前置条件：空集，故下方字段断言是弱断言").isZero();
        assertThat(raw).doesNotContain("\"answer\"");
        assertThat(raw).doesNotContain("\"analysis\"");
    }

    @Test
    @DisplayName("21. ⚠️【独立项·弱断言】学生 question/info 已 403，响应体不含 answer/analysis")
    void studentInfoStripsAnswer() throws Exception {
        MvcResult result = call(get("/assessmentQuestion/info/" + HIDDEN_QUESTION_ID).header("Token", student1));
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // ⚠️ 同上：修复后该路径是 403，响应体本就无数据 ⇒ 无法据此证明 DTO 剥离有效。
        //    剥离生效的【正向对照】在用例 19（管理员必须看到 answer/analysis）。
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(raw).doesNotContain("\"answer\"");
        assertThat(raw).doesNotContain("\"analysis\"");
    }

    // ================== G. 契约不变 ==================

    @Test
    @DisplayName("22. ✅ 未认证访问 page/info 仍 200 + code=401（拦截器契约不变）")
    void unauthenticatedStillRejectedByInterceptor() throws Exception {
        MvcResult page = call(get("/assessmentPaper/page").param("page", "1").param("limit", "10"));
        assertThat(page.getResponse().getStatus()).as("未认证由拦截器处理，保持 200").isEqualTo(200);
        assertThat(body(page).path("code").asInt()).isEqualTo(401);

        MvcResult info = call(get("/assessmentQuestion/info/" + HIDDEN_QUESTION_ID));
        assertThat(info.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(info).path("code").asInt()).isEqualTo(401);
    }

    // ================== H. 🔴 正向可访问（事务 fixture） ==================

    /*
     * 为什么必须有这一组（用户 2026-10-27 指出的验收缺口）：
     * 正式库两张试卷【全部 is_deleted=1】⇒ 仅凭 403 只能证明「已删除不可读」，
     * 无法排除「非管理员一律 403」这种【过度限制】的错误实现。
     *
     * 因此这里在【测试事务内】插入 status=1 + is_deleted=0 的资源，
     * 断言学生/匿名【可以】正常读取；事务结束自动回滚 ⇒ 不修改正式 DB / 种子数据。
     * 同时插入 status=2 + is_deleted=0 覆盖【禁用分支】（此前是纯证据缺口）。
     */

    private static final int VISIBLE_PAPER_ID = 9001;
    private static final int DELETED_PAPER_ID = 9002;
    private static final int DISABLED_PAPER_ID = 9003;
    private static final int VISIBLE_QUESTION_ID = 9101;
    private static final int DISABLED_QUESTION_ID = 9103;

    @Test
    @Transactional
    @DisplayName("23. ✅【正向】学生可读「启用+未删除」试卷详情 → 200（证明不是「非管理员一律拒绝」）")
    void studentCanReadVisiblePaperInfo() throws Exception {
        insertPaper(VISIBLE_PAPER_ID, 1, 0);

        MvcResult result = call(get("/assessmentPaper/info/" + VISIBLE_PAPER_ID).header("Token", student1));
        assertThat(result.getResponse().getStatus())
                .as("若这里 403，说明实现是「非 admin 一律拒绝」，而不是「按资源状态授权」")
                .isEqualTo(200);
        assertThat(body(result).path("code").asInt()).isZero();
        assertThat(body(result).path("data").path("id").asInt()).isEqualTo(VISIBLE_PAPER_ID);
    }

    @Test
    @Transactional
    @DisplayName("24. ✅【正向】咨询师可读「启用+未删除」试卷详情 → 200（按状态，与归属无关）")
    void counselorCanReadVisiblePaperInfo() throws Exception {
        insertPaper(VISIBLE_PAPER_ID, 1, 0);

        assertThat(call(get("/assessmentPaper/info/" + VISIBLE_PAPER_ID).header("Token", counselor1))
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @Transactional
    @DisplayName("25. ✅【正向】学生可读「父卷可见」的题目详情 → 200 且不含 answer/analysis（有效剥离验证）")
    void studentCanReadVisibleQuestionInfoWithoutAnswer() throws Exception {
        insertPaper(VISIBLE_PAPER_ID, 1, 0);
        insertQuestion(VISIBLE_QUESTION_ID, VISIBLE_PAPER_ID);

        MvcResult result = call(get("/assessmentQuestion/info/" + VISIBLE_QUESTION_ID).header("Token", student1));
        assertThat(result.getResponse().getStatus())
                .as("父卷可见 ⇒ 题目应可读；若 403 则说明实现过度限制")
                .isEqualTo(200);

        JsonNode data = body(result).path("data");
        assertThat(data.path("id").asInt()).isEqualTo(VISIBLE_QUESTION_ID);
        // ✅ 与用例 20/21 的差别：这里有【真实数据】，「不含 answer」不再是空集恒真
        assertThat(data.has("answer")).as("非管理员必须走 QuestionPublicDTO").isFalse();
        assertThat(data.has("analysis")).isFalse();
        assertThat(data.path("content").asText()).isEqualTo("fixture题目-" + VISIBLE_QUESTION_ID);
    }

    @Test
    @Transactional
    @DisplayName("26. ✅【正向】匿名/学生的列表包含「启用+未删除」资源（列表不是永远为 0）")
    void visibleResourcesAppearInLists() throws Exception {
        insertPaper(VISIBLE_PAPER_ID, 1, 0);
        insertQuestion(VISIBLE_QUESTION_ID, VISIBLE_PAPER_ID);

        assertThat(ids(call(list("/assessmentPaper", null))))
                .as("匿名必须能看到已发布试卷 —— 否则「total=0」可能只是过滤过度")
                .contains(VISIBLE_PAPER_ID);
        assertThat(ids(call(list("/assessmentPaper", student1)))).contains(VISIBLE_PAPER_ID);
        assertThat(ids(call(list("/assessmentQuestion", student1))))
                .as("父卷可见 ⇒ 题目也应出现在列表")
                .contains(VISIBLE_QUESTION_ID);
    }

    @Test
    @Transactional
    @DisplayName("27. 🔴【禁用分支】status=2 + is_deleted=0 → 学生/匿名不可见，admin 仍可见")
    void disabledPaperIsHiddenButAdminStillReads() throws Exception {
        insertPaper(DISABLED_PAPER_ID, 2, 0);
        insertQuestion(DISABLED_QUESTION_ID, DISABLED_PAPER_ID);

        assertThat(call(get("/assessmentPaper/info/" + DISABLED_PAPER_ID).header("Token", student1))
                .getResponse().getStatus()).as("禁用试卷对学生不可见").isEqualTo(403);
        assertThat(ids(call(list("/assessmentPaper", null)))).doesNotContain(DISABLED_PAPER_ID);
        assertThat(ids(call(list("/assessmentQuestion", student1)))).doesNotContain(DISABLED_QUESTION_ID);

        // 反向自检：管理员不受发布状态限制（否则全局收紧无法与「被正确拒绝」区分）
        assertThat(call(get("/assessmentPaper/info/" + DISABLED_PAPER_ID).header("Token", admin))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(ids(call(page("/assessmentQuestion", admin))))
                .as("管理端题目列表必须仍能看到禁用试卷下的题目")
                .contains(DISABLED_QUESTION_ID);
    }

    @Test
    @Transactional
    @DisplayName("28. ✅【成对边界】同一角色：可见资源 200 / 已删除资源 403（判定看状态而非身份）")
    void visibleAllowedWhileDeletedDeniedForSameRole() throws Exception {
        insertPaper(VISIBLE_PAPER_ID, 1, 0);
        insertPaper(DELETED_PAPER_ID, 1, 1);

        assertThat(call(get("/assessmentPaper/info/" + VISIBLE_PAPER_ID).header("Token", student1))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(call(get("/assessmentPaper/info/" + DELETED_PAPER_ID).header("Token", student1))
                .getResponse().getStatus())
                .as("同一学生：可见 200、已删除 403 ⇒ 判定依据是资源状态")
                .isEqualTo(403);
    }

    // ================== helpers ==================

    /**
     * 事务内插入试卷 fixture。<b>必须在带 {@code @Transactional} 的用例中调用</b> ⇒ 结束自动回滚。
     *
     * @param status    1=启用 / 2=禁用（字典 {@code assessment_paper_status}）
     * @param isDeleted 0=未删除 / 1=已删除
     */
    private void insertPaper(int id, int status, int isDeleted) {
        jdbcTemplate.update(
                "INSERT INTO assessment_paper (id, name, duration_minutes, total_score, status, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "可见性fixture-" + id, 30, 100, status, isDeleted);
    }

    /** 事务内插入题目 fixture（父卷由 {@link #insertPaper} 提供）*/
    private void insertQuestion(int id, int paperId) {
        jdbcTemplate.update(
                "INSERT INTO assessment_question (id, paper_id, content, options, score, answer, analysis, question_type, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, paperId, "fixture题目-" + id, "[{\"code\":\"A\",\"text\":\"A\"}]", 5, "A", "fixture解析", 1, 1);
    }

    /** 取 {@code data.list[*].id}（列表断言用；避免只看 total 而漏掉「返回了别的记录」）*/
    private List<Integer> ids(MvcResult result) throws Exception {
        List<Integer> out = new ArrayList<>();
        for (JsonNode node : body(result).path("data").path("list")) {
            out.add(node.path("id").asInt());
        }
        return out;
    }

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

    private int total(MvcResult result) throws Exception {
        return body(result).path("data").path("total").asInt(-1);
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
