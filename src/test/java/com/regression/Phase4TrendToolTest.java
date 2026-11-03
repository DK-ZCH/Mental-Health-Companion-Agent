package com.regression;

import com.agent.AgentIdentity;
import com.agent.TrendToolResult;
import com.agent.TrendTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 / Round 2：T5 {@code getMyAssessmentTrend} 的<b>统计规则</b>。
 *
 * <p><b>三个最重要的用例</b>（用户 2026-11-03 指定不可删）：
 * <ul>
 *   <li>{@link #normalizationAcrossDifferentMaxScores()}（N）：100 分卷 60 分 → 10 分卷 8 分
 *       必须判 <b>UP</b>（60% → 80%）；若实现直接用原始分（8−60=−52）会判 DOWN ⇒ 必红。</li>
 *   <li>{@link #nineExcludedOutOfTenIsInsufficient()}（D3）：10 条里 9 条无效 ⇒ n=1 ⇒ INSUFFICIENT。
 *       证明 {@code n} 是「有效样本数」而不是「窗口条数」。</li>
 *   <li>{@link #sameSecondOrderingIsStable()}（O）：同一秒的两条记录必须用 id 做 tie-breaker，
 *       否则首尾可能互换、趋势翻转。</li>
 * </ul>
 *
 * <p><b>数据基线</b>：student1 有 1 条记录；<b>student2 / student3 均为 0 条</b>
 * ⇒ 用 student2 造数、student3 验空结果，互不干扰。所有造数用例 {@code @Transactional} 回滚。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@DisplayName("Phase4 T5 测评趋势")
class Phase4TrendToolTest {

    private static final int STUDENT_2 = 2;
    private static final int STUDENT_3 = 3;

    /** 试卷 1 满分 100；试卷 2 满分 10（实测）*/
    private static final int PAPER_100 = 1;
    private static final int PAPER_10 = 2;
    /** 不存在的试卷，用于制造「无法归一化」样本 */
    private static final int PAPER_MISSING = 999999;

    @Autowired
    private TrendTools trendTools;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    // ================== A. 域拒绝 ==================

    @Test
    @DisplayName("A1. 🔴 counselor 域调用 → 抛异常")
    void counselorDenied() {
        assertThatThrownBy(() -> trendTools.getMyAssessmentTrend(ctx(1, "counselor")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A2. 🔴 admin 域调用 → 抛异常")
    void adminDenied() {
        assertThatThrownBy(() -> trendTools.getMyAssessmentTrend(ctx(1, "admin_user")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A3. 🔴 非法上下文 → 抛异常")
    void invalidContextDenied() {
        assertThatThrownBy(() -> trendTools.getMyAssessmentTrend(new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================== D. 样本不足 ==================

    @Test
    @DisplayName("D1. 无记录 → total=0、INSUFFICIENT、items 为空")
    void noRecordsIsInsufficient() {
        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_3));

        assertThat(result.total()).isZero();
        assertThat(result.items()).isEmpty();
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_INSUFFICIENT);
        assertThat(result.summary().deltaPp()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("D2. 仅 1 条有效 → validCount=1、INSUFFICIENT、deltaPp 为 null")
    void singleSampleIsInsufficient() {
        insertRecord(9401, STUDENT_2, PAPER_100, 60, "2022-01-01 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().validCount()).isEqualTo(1);
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_INSUFFICIENT);
        assertThat(result.summary().deltaPp())
                .as("样本不足时不得给差值（否则模型会把 0 当成「持平」）")
                .isNull();
    }

    @Test
    @Transactional
    @DisplayName("D3. 🔴 10 条中 9 条试卷缺失 → n=1 → INSUFFICIENT（证明 n ≠ windowSize）")
    void nineExcludedOutOfTenIsInsufficient() {
        insertRecord(9410, STUDENT_2, PAPER_100, 60, "2022-01-01 10:00:00");
        for (int i = 0; i < 9; i++) {
            insertRecord(9411 + i, STUDENT_2, PAPER_MISSING, 50, "2022-01-0" + (i + 2) + " 10:00:00");
        }

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().windowSize()).as("原始窗口仍是 10 条").isEqualTo(10);
        assertThat(result.summary().excludedCount()).isEqualTo(9);
        assertThat(result.summary().validCount()).as("n 必须是有效样本数，不是窗口条数").isEqualTo(1);
        assertThat(result.summary().trend())
                .as("数据库里有 10 条记录，但只有 1 条可用 ⇒ 不得判 FLAT/UP/DOWN")
                .isEqualTo(TrendToolResult.TREND_INSUFFICIENT);
    }

    // ================== N. 归一化（核心）==================

    @Test
    @Transactional
    @DisplayName("N1. 🔴 100 分卷 60 → 10 分卷 8，必须判 UP（60%→80%）；按原始分会判 DOWN")
    void normalizationAcrossDifferentMaxScores() {
        insertRecord(9420, STUDENT_2, PAPER_100, 60, "2022-01-01 10:00:00");
        insertRecord(9421, STUDENT_2, PAPER_10, 8, "2022-01-02 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().firstRate()).isEqualTo(0.60);
        assertThat(result.summary().lastRate()).isEqualTo(0.80);
        assertThat(result.summary().deltaPp()).as("60% → 80% = +20 个百分点").isEqualTo(20.0);
        assertThat(result.summary().trend())
                .as("若实现用原始分（8 − 60 = −52）会判 DOWN —— 这条断言就是用来打这个错的")
                .isEqualTo(TrendToolResult.TREND_UP);
    }

    // ================== T. 趋势与阈值边界 ==================

    @Test
    @Transactional
    @DisplayName("T1. 50% → 60% → 70% ⇒ UP（+20pp）")
    void risingTrend() {
        insertRecord(9430, STUDENT_2, PAPER_100, 50, "2022-01-01 10:00:00");
        insertRecord(9431, STUDENT_2, PAPER_100, 60, "2022-01-02 10:00:00");
        insertRecord(9432, STUDENT_2, PAPER_100, 70, "2022-01-03 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().deltaPp()).isEqualTo(20.0);
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_UP);
    }

    @Test
    @Transactional
    @DisplayName("T2. 70% → 60% → 50% ⇒ DOWN（−20pp）")
    void fallingTrend() {
        insertRecord(9433, STUDENT_2, PAPER_100, 70, "2022-01-01 10:00:00");
        insertRecord(9434, STUDENT_2, PAPER_100, 60, "2022-01-02 10:00:00");
        insertRecord(9435, STUDENT_2, PAPER_100, 50, "2022-01-03 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().deltaPp()).isEqualTo(-20.0);
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_DOWN);
    }

    @Test
    @Transactional
    @DisplayName("T3. 60% → 64% ⇒ +4pp ⇒ FLAT（阈值内）")
    void withinThresholdIsFlat() {
        insertRecord(9436, STUDENT_2, PAPER_100, 60, "2022-01-01 10:00:00");
        insertRecord(9437, STUDENT_2, PAPER_100, 64, "2022-01-02 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().deltaPp()).isEqualTo(4.0);
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_FLAT);
    }

    @Test
    @Transactional
    @DisplayName("T4. 🔴 60% → 65% ⇒ +5pp ⇒ UP（阈值含等号）")
    void positiveBoundaryIsUp() {
        insertRecord(9438, STUDENT_2, PAPER_100, 60, "2022-01-01 10:00:00");
        insertRecord(9439, STUDENT_2, PAPER_100, 65, "2022-01-02 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().deltaPp()).isEqualTo(5.0);
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_UP);
    }

    @Test
    @Transactional
    @DisplayName("T5. 🔴 65% → 60% ⇒ −5pp ⇒ DOWN（阈值含等号）")
    void negativeBoundaryIsDown() {
        insertRecord(9440, STUDENT_2, PAPER_100, 65, "2022-01-01 10:00:00");
        insertRecord(9441, STUDENT_2, PAPER_100, 60, "2022-01-02 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().deltaPp()).isEqualTo(-5.0);
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_DOWN);
    }

    // ================== O. 排序稳定性 ==================

    @Test
    @Transactional
    @DisplayName("O1. 🔴 同一秒两条记录：必须按 id 升序作为 tie-breaker（否则趋势翻转）")
    void sameSecondOrderingIsStable() {
        // 同一秒：id 9450 = 50 分（较早的有效首样本），id 9451 = 90 分（末样本）
        insertRecord(9450, STUDENT_2, PAPER_100, 50, "2022-02-01 10:00:00");
        insertRecord(9451, STUDENT_2, PAPER_100, 90, "2022-02-01 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().firstRate()).as("同秒时必须按 id 升序，id 9450 在前").isEqualTo(0.50);
        assertThat(result.summary().lastRate()).isEqualTo(0.90);
        assertThat(result.summary().trend()).isEqualTo(TrendToolResult.TREND_UP);
    }

    // ================== X / E / C ==================

    @Test
    @Transactional
    @DisplayName("X1. 试卷不存在 → 该条 excluded=true 且原因正确，其余照常统计")
    void missingPaperIsExcludedWithReason() {
        insertRecord(9460, STUDENT_2, PAPER_100, 60, "2022-01-01 10:00:00");
        insertRecord(9461, STUDENT_2, PAPER_MISSING, 50, "2022-01-02 10:00:00");
        insertRecord(9462, STUDENT_2, PAPER_100, 80, "2022-01-03 10:00:00");

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.summary().excludedCount()).isEqualTo(1);
        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(1).excluded()).isTrue();
        assertThat(result.items().get(1).excludedReason())
                .isEqualTo(TrendToolResult.REASON_PAPER_MISSING);
        assertThat(result.summary().validCount()).isEqualTo(2);
        assertThat(result.summary().deltaPp()).as("有效首尾：60% → 80%").isEqualTo(20.0);
    }

    @Test
    @Transactional
    @DisplayName("E1. 记录超过 10 条 → items=10、total 真实、truncated=true")
    void trendWindowIsTruncated() {
        for (int i = 0; i < 12; i++) {
            insertRecord(9470 + i, STUDENT_2, PAPER_100, 50 + i, "2022-03-" + String.format("%02d", i + 1) + " 10:00:00");
        }

        TrendToolResult result = trendTools.getMyAssessmentTrend(student(STUDENT_2));

        assertThat(result.total()).isEqualTo(12);
        assertThat(result.items()).hasSize(10);
        assertThat(result.truncated()).isTrue();
        assertThat(result.summary().windowSize()).isEqualTo(10);
    }

    @Test
    @Transactional
    @DisplayName("C1. 🔴 返回契约不含 studentId / answer / analysis / 试题内容")
    void resultHasNoSensitiveKeys() throws Exception {
        insertRecord(9480, STUDENT_2, PAPER_100, 60, "2022-01-01 10:00:00");
        insertRecord(9481, STUDENT_2, PAPER_100, 70, "2022-01-02 10:00:00");

        Set<String> forbidden = Set.of("studentId", "answer", "analysis", "options", "content");
        Set<String> hit = new HashSet<>(jsonKeys(trendTools.getMyAssessmentTrend(student(STUDENT_2))));
        hit.retainAll(forbidden);

        assertThat(hit).isEmpty();
    }

    // ================== helpers ==================

    private ToolContext student(int userId) {
        return ctx(userId, AgentIdentity.TABLE_STUDENT);
    }

    private ToolContext ctx(int userId, String table) {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, userId,
                AgentIdentity.CTX_USER_TABLE, table));
    }

    private void insertRecord(int id, int studentId, int paperId, Integer score, String submittedAt) {
        jdbcTemplate.update(
                "INSERT INTO assessment_record (id, record_no, student_id, paper_id, total_score, submitted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "FIXTURE-" + id, studentId, paperId, score, submittedAt);
    }

    private Set<String> jsonKeys(Object value) throws Exception {
        JsonNode node = objectMapper.valueToTree(value);
        Set<String> keys = new HashSet<>();
        collect(node, keys);
        return keys;
    }

    private void collect(JsonNode node, Set<String> keys) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(keys::add);
            node.elements().forEachRemaining(child -> collect(child, keys));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collect(child, keys));
        }
    }
}
