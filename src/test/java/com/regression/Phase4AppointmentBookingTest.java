package com.regression;

import com.agent.AgentIdentity;
import com.agent.AppointmentBookingTools;
import com.agent.AppointmentConfirmResult;
import com.agent.AppointmentConfirmToken;
import com.agent.AppointmentPreviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 / Round 4：<b>受控创建预约</b>（预览 → 确认 → 写入）。
 *
 * <p><b>三个不可删的核心用例</b>：
 * <ul>
 *   <li>{@link #previewDoesNotWrite()}：预览阶段<b>不写库</b>；</li>
 *   <li>{@link #duplicateConfirmIsRejected()}：同一凭证重复确认 ⇒ 业务查重拒绝（S4）；</li>
 *   <li>{@link #dbUniqueKeyRejectsDuplicateActiveSlot()}：DB 唯一键兜底（S18），
 *       且 {@code status=3} 不占槽 —— 这正是「应用层管语义、数据库管并发」的分工。</li>
 * </ul>
 *
 * <p><b>身份</b>：{@code studentId} 来自 {@link ToolContext}，方法签名中不存在该参数（S13 用反射证明）。
 *
 * <p><b>数据</b>：所有造数 {@code @Transactional} 回滚；日期取「今天 + N」避免与种子数据（2022 年）冲突。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@DisplayName("Phase4 Round4 受控创建预约")
class Phase4AppointmentBookingTest {

    private static final int STUDENT_1 = 1;
    private static final int STUDENT_2 = 2;
    private static final int COUNSELOR = 2;
    private static final int UNKNOWN_COUNSELOR = 999999;

    private static final int FIXTURE_BASE = 9100;

    @Autowired
    private AppointmentBookingTools tools;
    @Autowired
    private AppointmentConfirmToken tokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    // ================== S1 / S2 预览 ==================

    @Test
    @Transactional
    @DisplayName("S1. 预览成功且【不写库】")
    void previewDoesNotWrite() {
        long before = count();

        AppointmentPreviewResult result = tools.previewAppointment(COUNSELOR, tomorrow(), 4, student());

        assertThat(result.success()).isTrue();
        assertThat(result.token()).isNotBlank();
        assertThat(result.message()).contains("预约预览");
        assertThat(count()).as("预览阶段不得产生任何数据库副作用").isEqualTo(before);
    }

    @Test
    @Transactional
    @DisplayName("S2. 槽位已被占用 → 预览失败且【不签发凭证】")
    void occupiedSlotIsRejectedOnPreview() {
        insertAppointment(FIXTURE_BASE, COUNSELOR, STUDENT_2, LocalDate.now().plusDays(1), 4, 1);

        AppointmentPreviewResult result = tools.previewAppointment(COUNSELOR, tomorrow(), 4, student());

        assertThat(result.success()).isFalse();
        assertThat(result.token()).isNull();
        assertThat(result.message()).contains("已被预约");
    }

    // ================== S3 / S4 确认 ==================

    @Test
    @Transactional
    @DisplayName("S3. 确认成功 → 写入 1 条，status=1、student_id=本人")
    void confirmCreatesPendingAppointment() {
        LocalDate date = LocalDate.now().plusDays(2);
        AppointmentPreviewResult preview = tools.previewAppointment(COUNSELOR, date.toString(), 5, student());
        assertThat(preview.success()).isTrue();

        AppointmentConfirmResult confirm = tools.confirmAppointment(studentWithPendingToken(preview.token()));

        assertThat(confirm.success()).isTrue();
        assertThat(confirm.appointmentId()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT student_id, counselor_id, status, appointment_no FROM counseling_appointment WHERE id = ?",
                confirm.appointmentId());
        assertThat(row.get("student_id")).isEqualTo(STUDENT_1);
        assertThat(row.get("counselor_id")).isEqualTo(COUNSELOR);
        assertThat(row.get("status")).as("服务端强制待审核").isEqualTo(1);
        assertThat((String) row.get("appointment_no")).isNotBlank();
    }

    @Test
    @Transactional
    @DisplayName("S4. 🔴 同一凭证重复确认 → 业务查重拒绝，仍只有 1 条")
    void duplicateConfirmIsRejected() {
        LocalDate date = LocalDate.now().plusDays(3);
        AppointmentPreviewResult preview = tools.previewAppointment(COUNSELOR, date.toString(), 6, student());

        assertThat(tools.confirmAppointment(studentWithPendingToken(preview.token())).success()).isTrue();
        AppointmentConfirmResult second = tools.confirmAppointment(studentWithPendingToken(preview.token()));

        assertThat(second.success()).as("签名凭证不是一次性，重复性必须由业务唯一性保证").isFalse();
        assertThat(second.message()).contains("已被预约");
        assertThat(countFor(COUNSELOR, date, 6)).isEqualTo(1);
    }

    // ================== S5 / S7 凭证 ==================

    @Test
    @Transactional
    @DisplayName("S5. 🔴 篡改 payload（改时段）但保留原签名 → 签名失败")
    void tamperedTokenIsRejected() throws Exception {
        String token = tools.previewAppointment(COUNSELOR, tomorrow(), 3, student()).token();
        String[] parts = token.split("\\.");

        // 构造「JSON 合法、签名不匹配」的伪造凭证：
        // 只翻转一个字符可能让 JSON 解析失败，无法精确证明是签名拦住了它
        String json = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.node.ObjectNode node =
                (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(json);
        node.put("s", 5);   // 把时段从 3 改成 5
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(node));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        AppointmentConfirmResult result = tools.confirmAppointment(studentWithPendingToken(forged));

        assertThat(result.success()).as("签名覆盖 payload，改字段必须被拒绝").isFalse();
        assertThat(countFor(COUNSELOR, LocalDate.now().plusDays(1), 5)).isZero();
        assertThat(countFor(COUNSELOR, LocalDate.now().plusDays(1), 3)).isZero();
    }

    @Test
    @Transactional
    @DisplayName("S7. 🔴 别人拿到的凭证不能给我用（uidHash 绑定）")
    void tokenIsBoundToUser() {
        String token = tools.previewAppointment(COUNSELOR, tomorrow(), 2, student(STUDENT_2)).token();

        AppointmentConfirmResult result = tools.confirmAppointment(studentWithPendingToken(STUDENT_1, token));

        assertThat(result.success()).isFalse();
        assertThat(countFor(COUNSELOR, LocalDate.now().plusDays(1), 2)).isZero();
    }

    @Test
    @Transactional
    @DisplayName("S6. 空/畸形凭证 → 拒绝且不写库")
    void malformedTokenIsRejected() {
        long before = count();

        assertThat(tools.confirmAppointment(studentWithPendingToken("")).success()).isFalse();
        assertThat(tools.confirmAppointment(studentWithPendingToken("not-a-token")).success()).isFalse();
        assertThat(count()).as("畸形凭证不得产生写入").isEqualTo(before);
    }

    // ================== S9 / S10 / S11 / S12 参数 ==================

    @Test
    @Transactional
    @DisplayName("S9. 过去日期 → 拒绝")
    void pastDateIsRejected() {
        LocalDate past = LocalDate.now().minusDays(1);
        AppointmentPreviewResult result = tools.previewAppointment(COUNSELOR, past.toString(), 1, student());

        assertThat(result.success()).isFalse();
        assertThat(result.token()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("S10a. 超过 +30 天 → 拒绝")
    void tooFarFutureIsRejected() {
        LocalDate far = LocalDate.now().plusDays(31);
        assertThat(tools.previewAppointment(COUNSELOR, far.toString(), 1, student()).success()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("S10b. 边界 +30 天 → 允许（窗口含边界）")
    void boundaryDayIsAllowed() {
        LocalDate edge = LocalDate.now().plusDays(30);
        AppointmentPreviewResult result = tools.previewAppointment(COUNSELOR, edge.toString(), 1, student());

        assertThat(result.success()).as("今天 ~ +30 天含边界").isTrue();
    }

    @Test
    @Transactional
    @DisplayName("S11. 非法时段 0 / 9 / null → 拒绝")
    void invalidSlotIsRejected() {
        assertThat(tools.previewAppointment(COUNSELOR, tomorrow(), 0, student()).success()).isFalse();
        assertThat(tools.previewAppointment(COUNSELOR, tomorrow(), 9, student()).success()).isFalse();
        assertThat(tools.previewAppointment(COUNSELOR, tomorrow(), null, student()).success()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("S12. 咨询师不存在 → 拒绝")
    void unknownCounselorIsRejected() {
        assertThat(tools.previewAppointment(UNKNOWN_COUNSELOR, tomorrow(), 1, student()).success()).isFalse();
    }

    // ================== S13 / S14 结构边界 ==================

    @Test
    @DisplayName("S13. 🔴 方法签名中【不存在】studentId / status 参数（结构上无法越权）")
    void toolSignatureHasNoIdentityOrStatusParameters() {
        Method preview = method("previewAppointment");
        assertThat(preview.getParameterTypes())
                .as("preview = (counselorId, date, slot, ToolContext)")
                .containsExactly(Integer.class, String.class, Integer.class, ToolContext.class);

        Method confirm = method("confirmAppointment");
        assertThat(confirm.getParameterTypes())
                .as("confirm = (ToolContext) —— 【没有任何模型可控参数】；凭证来自 Session 草稿")
                .containsExactly(ToolContext.class);
    }

    @Test
    @DisplayName("S14. 🔴 counselor 域调用 → 抛异常（fail-closed，不返回失败结果）")
    void counselorDomainIsDenied() {
        ToolContext counselorCtx = new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, 1,
                AgentIdentity.CTX_USER_TABLE, "counselor"));

        assertThatThrownBy(() -> tools.previewAppointment(COUNSELOR, tomorrow(), 1, counselorCtx))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================== S18 DB 唯一键兜底 ==================

    @Test
    @Transactional
    @DisplayName("S18. 🔴 DB 唯一键拒绝同槽第二条有效预约；status=3 不占槽")
    void dbUniqueKeyRejectsDuplicateActiveSlot() {
        LocalDate date = LocalDate.now().plusDays(4);
        insertAppointment(FIXTURE_BASE + 1, COUNSELOR, STUDENT_1, date, 7, 1);

        assertThatThrownBy(() -> insertAppointment(FIXTURE_BASE + 2, COUNSELOR, STUDENT_2, date, 7, 2))
                .as("应用层查重之外，数据库必须兜住并发写入")
                .isInstanceOf(DataIntegrityViolationException.class);

        // status=3（拒绝）不占槽 ⇒ 同槽可以再出现多条被拒绝记录
        insertAppointment(FIXTURE_BASE + 3, COUNSELOR, STUDENT_2, date, 8, 3);
        insertAppointment(FIXTURE_BASE + 4, COUNSELOR, STUDENT_2, date, 8, 3);

        assertThat(countFor(COUNSELOR, date, 8)).isEqualTo(2);
    }

    // ================== helpers ==================

    private ToolContext student() {
        return student(STUDENT_1);
    }

    private ToolContext student(int userId) {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, userId,
                AgentIdentity.CTX_USER_TABLE, AgentIdentity.TABLE_STUDENT));
    }

    /**
     * 模拟 Controller 注入的「Session 待确认凭证」上下文。
     *
     * <p>该键由服务端从 HttpSession 读取后放入，<b>模型无法提供</b> —— 这正是 R4-Q5（C）的关键。
     */
    private ToolContext studentWithPendingToken(String token) {
        return studentWithPendingToken(STUDENT_1, token);
    }

    private ToolContext studentWithPendingToken(int userId, String token) {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, userId,
                AgentIdentity.CTX_USER_TABLE, AgentIdentity.TABLE_STUDENT,
                AgentIdentity.CTX_PENDING_TOKEN, token));
    }

    private String tomorrow() {
        return LocalDate.now().plusDays(1).toString();
    }

    private Method method(String name) {
        return java.util.Arrays.stream(AppointmentBookingTools.class.getMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private long count() {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM counseling_appointment", Long.class);
        return value == null ? 0 : value;
    }

    private int countFor(int counselorId, LocalDate date, int slot) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM counseling_appointment WHERE counselor_id = ? AND appointment_date = ? "
                        + "AND time_slot = ?",
                Integer.class, counselorId, Date.valueOf(date), slot);
        return value == null ? 0 : value;
    }

    private void insertAppointment(int id, int counselorId, int studentId, LocalDate date, int slot, int status) {
        jdbcTemplate.update(
                "INSERT INTO counseling_appointment "
                        + "(id, appointment_no, counselor_id, student_id, appointment_date, time_slot, status, applied_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, "fixture-" + id, counselorId, studentId, Date.valueOf(date), slot, status);
    }
}
