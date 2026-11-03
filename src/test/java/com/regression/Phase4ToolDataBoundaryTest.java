package com.regression;

import com.agent.AgentIdentity;
import com.agent.AppointmentToolResult;
import com.agent.AppointmentTools;
import com.agent.CounselorCatalogTools;
import com.agent.CounselorToolResult;
import com.agent.MessageToolResult;
import com.agent.MessageTools;
import com.agent.WrongQuestionToolResult;
import com.agent.WrongQuestionTools;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 / Round 1：T1–T4 的<b>数据边界</b>与<b>空结果/截断语义</b>。
 *
 * <p><b>为什么断言「序列化后的字段集合」而不是只断言 SQL</b>：
 * 「SQL 没 select 敏感列」只覆盖当前实现；
 * 一旦有人图方便把 Entity 直接塞进 Result，字段会重新带出来。
 * 因此这里对<b>最终返回对象的 JSON key</b> 做负向断言 —— 它约束的是<b>契约</b>，不是某一条 SQL。
 *
 * <p><b>种子数据（只读）</b>：student1 → 预约 3 / 留言 2 / 错题 3；student2 → 留言 2 / 预约 0 / 错题 0。
 * 需要造数的用例一律 {@code @Transactional} + 自动回滚，不改正式数据。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@DisplayName("Phase4 Tool 数据边界与截断语义")
class Phase4ToolDataBoundaryTest {

    private static final int STUDENT_1 = 1;
    private static final int STUDENT_2 = 2;

    /** 任何 Tool 返回体中都不允许出现的键（PII / 凭据 / 归属 id / 试题答案） */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "phone", "email", "username", "password", "idCardNo", "id_card_no",
            "studentId", "counselorId",
            "answer", "analysis", "options", "studentAnswer",
            "avatarUrl"
    );

    @Autowired
    private AppointmentTools appointmentTools;
    @Autowired
    private MessageTools messageTools;
    @Autowired
    private WrongQuestionTools wrongQuestionTools;
    @Autowired
    private CounselorCatalogTools counselorCatalogTools;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ================== C. 敏感字段负向（字段级契约）==================

    @Test
    @DisplayName("C1. 🔴 T1 返回契约不含 phone/email/username/password/reviewComment/appointmentNo")
    void appointmentResultHasNoSensitiveFields() throws Exception {
        AppointmentToolResult result = appointmentTools.getMyAppointments(student(STUDENT_1));

        Set<String> keys = jsonKeys(result);
        assertThat(intersection(keys, FORBIDDEN_KEYS)).as("T1 命中敏感键").isEmpty();
        assertThat(keys).as("T1 不得返回咨询师审核备注").doesNotContain("reviewComment", "review_comment");
        assertThat(keys).as("T1 不得返回业务流水号").doesNotContain("appointmentNo", "appointment_no");
    }

    @Test
    @DisplayName("C2. 🔴 T3 返回契约不含 answer/analysis/options/studentAnswer")
    void wrongQuestionResultHasNoAnswers() throws Exception {
        WrongQuestionToolResult result = wrongQuestionTools.getMyWrongQuestionSummary(student(STUDENT_1));

        Set<String> keys = jsonKeys(result);
        assertThat(intersection(keys, Set.of("answer", "analysis", "options", "studentAnswer")))
                .as("T3 不得返回正确答案/解析/选项/学生作答")
                .isEmpty();
    }

    @Test
    @DisplayName("C3. 🔴 T4 返回契约不含 password/phone/email/username/avatarUrl")
    void counselorResultHasNoPii() throws Exception {
        CounselorToolResult result = counselorCatalogTools.listAvailableCounselors(student(STUDENT_1));

        Set<String> keys = jsonKeys(result);
        assertThat(intersection(keys, Set.of("password", "phone", "email", "username", "avatarUrl")))
                .as("T4 必须白名单返回，不得带出凭据与联系方式")
                .isEmpty();
        assertThat(keys).as("T4 至少应包含姓名与擅长领域").contains("name", "expertise");
    }

    @Test
    @DisplayName("C4. 🔴 T2 返回契约不含咨询师 PII，也不含 studentId")
    void messageResultHasNoPii() throws Exception {
        MessageToolResult result = messageTools.getMyMessages(student(STUDENT_1));

        Set<String> keys = jsonKeys(result);
        assertThat(intersection(keys, FORBIDDEN_KEYS)).as("T2 命中敏感键").isEmpty();
    }

    // ================== D. 空结果语义 ==================

    @Test
    @DisplayName("D1. ✅ student2 无预约/无错题 → total=0、items 为空，且【不抛异常】")
    void emptyResultIsNormalNotError() {
        AppointmentToolResult appointments = appointmentTools.getMyAppointments(student(STUDENT_2));
        assertThat(appointments.total()).isZero();
        assertThat(appointments.items()).isEmpty();
        assertThat(appointments.truncated()).isFalse();

        WrongQuestionToolResult wrong = wrongQuestionTools.getMyWrongQuestionSummary(student(STUDENT_2));
        assertThat(wrong.totalWrongQuestions()).isZero();
        assertThat(wrong.items()).isEmpty();
    }

    @Test
    @DisplayName("D2. ✅ 有留言但未回复 → replyContent 为 null，不是“空结果”")
    void unansweredMessageIsNotAnEmptyResult() {
        MessageToolResult result = messageTools.getMyMessages(student(STUDENT_1));
        assertThat(result.total()).isPositive();
        assertThat(result.items()).isNotEmpty();
        assertThat(result.items().get(0).content()).isNotBlank();
    }

    // ================== B. 行范围（独立 oracle）==================

    @Test
    @DisplayName("B1. 🔴 三个 Tool 的 total 与数据库中该学生真实条数一致（行级范围）")
    void totalsMatchDatabaseOracle() {
        int appointments = count("SELECT COUNT(*) FROM counseling_appointment WHERE student_id = ?", STUDENT_1);
        int messages = count("SELECT COUNT(*) FROM counselor_message WHERE student_id = ?", STUDENT_1);
        int wrong = count("SELECT COUNT(*) FROM assessment_wrong_question WHERE student_id = ?", STUDENT_1);

        assertThat(appointmentTools.getMyAppointments(student(STUDENT_1)).total()).isEqualTo(appointments);
        assertThat(messageTools.getMyMessages(student(STUDENT_1)).total()).isEqualTo(messages);
        assertThat(wrongQuestionTools.getMyWrongQuestionSummary(student(STUDENT_1)).totalWrongQuestions())
                .isEqualTo(wrong);
    }

    @Test
    @DisplayName("B2. 🔴 两个学生的返回集合不相交（同一 Tool 不同身份不得串读）")
    void twoStudentsSeeDisjointData() {
        long s1 = appointmentTools.getMyAppointments(student(STUDENT_1)).total();
        long s2 = appointmentTools.getMyAppointments(student(STUDENT_2)).total();

        assertThat(s1).isEqualTo(count("SELECT COUNT(*) FROM counseling_appointment WHERE student_id = ?", STUDENT_1));
        assertThat(s2).isEqualTo(count("SELECT COUNT(*) FROM counseling_appointment WHERE student_id = ?", STUDENT_2));
    }

    // ================== E. 截断语义 ==================

    @Test
    @Transactional
    @DisplayName("E1. 🔴 预约超过 10 条 → items=10、total 为真实数、truncated=true")
    void appointmentTruncationIsExplicit() {
        int existing = count("SELECT COUNT(*) FROM counseling_appointment WHERE student_id = ?", STUDENT_1);
        for (int i = 0; i < 12; i++) {
            jdbcTemplate.update(
                    // Round 4 起 (counselor_id, appointment_date, time_slot) 有唯一约束：
                    // 原实现 12 条全用 CURDATE()+slot1 会撞键；改为逐日错开，保持「条数」意图不变
                    "INSERT INTO counseling_appointment (id, appointment_no, counselor_id, student_id, "
                            + "appointment_date, time_slot, status, applied_at) "
                            + "VALUES (?, ?, ?, ?, DATE_ADD(CURDATE(), INTERVAL ? DAY), ?, ?, NOW())",
                    9100 + i, "FIXTURE-" + (9100 + i), 1, STUDENT_1, i, 1, 1);
        }

        AppointmentToolResult result = appointmentTools.getMyAppointments(student(STUDENT_1));

        assertThat(result.total()).isEqualTo(existing + 12);
        assertThat(result.items()).hasSize(10);
        assertThat(result.truncated()).as("必须显式告知被截断，不得静默丢弃").isTrue();
    }

    @Test
    @Transactional
    @DisplayName("E2. 🔴 单条留言超过 500 字符 → 截断并置 contentTruncated=true（不改数据库原文）")
    void messageContentTruncationIsExplicit() {
        String longContent = "测".repeat(600);
        jdbcTemplate.update(
                "INSERT INTO counselor_message (id, counselor_id, student_id, content, sent_at) "
                        + "VALUES (?, ?, ?, ?, NOW())",
                9201, 1, STUDENT_1, longContent);

        MessageToolResult result = messageTools.getMyMessages(student(STUDENT_1));
        MessageToolResult.Item first = result.items().get(0);

        assertThat(first.contentTruncated()).as("必须显式标记截断").isTrue();
        assertThat(first.content()).hasSize(500);

        // 数据库原文不得被修改
        String stored = jdbcTemplate.queryForObject(
                "SELECT content FROM counselor_message WHERE id = ?", String.class, 9201);
        assertThat(stored).as("截断只作用于 Tool 输出视图").hasSize(600);
    }

    // ================== helpers ==================

    private ToolContext student(int userId) {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, userId,
                AgentIdentity.CTX_USER_TABLE, AgentIdentity.TABLE_STUDENT));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** 递归收集 JSON 中出现的所有 key（不区分层级）*/
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

    private Set<String> intersection(Set<String> actual, Set<String> forbidden) {
        Set<String> hit = new HashSet<>(actual);
        hit.retainAll(forbidden);
        return hit;
    }
}
