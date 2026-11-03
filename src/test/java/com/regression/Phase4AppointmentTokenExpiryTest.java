package com.regression;

import com.agent.AgentIdentity;
import com.agent.AppointmentBookingTools;
import com.agent.AppointmentConfirmResult;
import com.agent.AppointmentPreviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 / Round 4：<b>确认凭证过期</b>（S6）。
 *
 * <p><b>为什么单独一个类</b>：过期必须靠「时间」验证，
 * 而 TTL 是配置项 —— 用 {@code agent.confirm.ttl-seconds=0}
 * 让凭证在签发瞬间即到期，避免在测试里 sleep 或修改系统时钟。
 *
 * <p><b>必须加 {@code @Transactional}</b>：正常实现下确认必然失败、不会写库；
 * 但如果过期校验被变异移除，确认会<b>真的写入</b> —— 届时若无回滚，
 * 会污染正式种子数据（本轮 M3 变异就发生过一次，已清理）。
 */
@SpringBootTest(properties = {
        "spring.ai.deepseek.api-key=test-only",
        "agent.confirm.ttl-seconds=0"
})
@org.springframework.transaction.annotation.Transactional
@DisplayName("Phase4 Round4 凭证过期")
class Phase4AppointmentTokenExpiryTest {

    private static final int COUNSELOR = 2;
    private static final int STUDENT = 1;

    @Autowired
    private AppointmentBookingTools tools;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("S6. 🔴 TTL=0 → 签发成功但确认时已过期，且不写库")
    void expiredTokenIsRejected() {
        LocalDate date = LocalDate.now().plusDays(5);
        long before = count();

        AppointmentPreviewResult preview = tools.previewAppointment(COUNSELOR, date.toString(), 1, student());
        assertThat(preview.success()).as("签发本身应成功").isTrue();
        assertThat(preview.expiresInSeconds()).isZero();

        AppointmentConfirmResult confirm = tools.confirmAppointment(studentWithPendingToken(preview.token()));

        assertThat(confirm.success()).isFalse();
        assertThat(confirm.message()).contains("失效");
        assertThat(count()).as("过期凭证不得产生写入").isEqualTo(before);
    }

    private ToolContext student() {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, STUDENT,
                AgentIdentity.CTX_USER_TABLE, AgentIdentity.TABLE_STUDENT));
    }

    /** 模拟 Controller 注入的 Session 待确认凭证 */
    private ToolContext studentWithPendingToken(String token) {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, STUDENT,
                AgentIdentity.CTX_USER_TABLE, AgentIdentity.TABLE_STUDENT,
                AgentIdentity.CTX_PENDING_TOKEN, token));
    }

    private long count() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM counseling_appointment WHERE counselor_id = ? AND appointment_date = ?",
                Long.class, COUNSELOR, java.sql.Date.valueOf(LocalDate.now().plusDays(5)));
        return value == null ? 0 : value;
    }
}
