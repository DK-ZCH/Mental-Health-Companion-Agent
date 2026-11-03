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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 4 / Round 4：<b>预约状态来源与状态转换</b>（Q3-c 的最小修复）。
 *
 * <p><b>为什么需要这一批</b>：`/add` `/save` `/update` 从不 `setStatus`（唯一一处是注释）
 * ⇒ 学生可 `POST status=2` 让预约「自我审批」。
 * 而 Round 4 要启用「一师一槽一有效预约」的唯一约束，
 * 若不先修 status，这条缺陷会升级为「**长期占槽**」手段。
 *
 * <p><b>本类只验 status</b>，不验 Agent 写入能力（见 {@code Phase4AppointmentBookingTest}）。
 *
 * <p><b>数据</b>：种子预约 id=1（student 1 / counselor 2 / status 1）；
 * 需要 counselor 1 的记录时用事务内 fixture（id 9001+），方法结束回滚。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@DisplayName("Phase4 Round4 预约状态来源与转换")
class Phase4AppointmentStatusTest {

    private static final int FIXTURE_ID_BASE = 9001;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String studentToken;
    private String counselorToken;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = login("/student/login", "a1", "123456");
        counselorToken = login("/counselor/login", "a1", "123456");
    }

    // ================== 创建路径：status 必须由服务端固定 ==================

    @Test
    @Transactional
    @DisplayName("S15a. 🔴 学生 /add 提交 status=2 → 落库仍为 1（待审核）")
    void studentAddCannotForceStatus() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counselorId", 2);
        body.put("appointmentDate", date.toString());
        body.put("timeSlot", 4);
        body.put("status", 2);          // 伪造「已同意」

        mockMvc.perform(post("/counselingAppointment/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Token", studentToken))
                .andReturn();

        Integer status = latestStatus(2, date, 4);
        assertThat(status).as("创建路径必须忽略客户端 status").isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("S15b. 🔴 学生 /save 提交 status=2 → 落库仍为 1（/save 是旁路，不能漏）")
    void studentSaveCannotForceStatus() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counselorId", 2);
        body.put("appointmentDate", date.toString());
        body.put("timeSlot", 5);
        body.put("status", 2);

        mockMvc.perform(post("/counselingAppointment/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Token", studentToken))
                .andReturn();

        assertThat(latestStatus(2, date, 5)).isEqualTo(1);
    }

    // ================== 审核路径：合法转换必须保留 ==================

    @Test
    @Transactional
    @DisplayName("S16a. ✅ 心理老师 1→2（同意）必须仍可用")
    void counselorCanApprove() throws Exception {
        int id = insertFixture(FIXTURE_ID_BASE, 1, 1, LocalDate.now().plusDays(2), 6, 1);

        MvcResult result = update(id, 2, counselorToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("S16b. ✅ 心理老师 1→3（拒绝）必须仍可用")
    void counselorCanReject() throws Exception {
        int id = insertFixture(FIXTURE_ID_BASE + 1, 1, 1, LocalDate.now().plusDays(2), 7, 1);

        assertThat(update(id, 3, counselorToken).getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("S16c. 🔴 非法转换 2→3 / 3→2 / 2→1 一律 403")
    void illegalTransitionsAreDenied() throws Exception {
        int approved = insertFixture(FIXTURE_ID_BASE + 2, 1, 1, LocalDate.now().plusDays(3), 1, 2);
        int rejected = insertFixture(FIXTURE_ID_BASE + 3, 1, 1, LocalDate.now().plusDays(3), 2, 3);

        assertThat(update(approved, 3, counselorToken).getResponse().getStatus())
                .as("2→3 无业务依据").isEqualTo(403);
        assertThat(update(approved, 1, counselorToken).getResponse().getStatus())
                .as("2→1 无业务依据").isEqualTo(403);
        assertThat(update(rejected, 2, counselorToken).getResponse().getStatus())
                .as("3→2 无业务依据").isEqualTo(403);

        assertThat(statusOf(approved)).isEqualTo(2);
        assertThat(statusOf(rejected)).isEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("S17. 🔴 学生 /update 提交 status=2 → 被忽略，保持 1（学生不能自我审批）")
    void studentCannotChangeStatus() throws Exception {
        // 种子 id=1 属于 student 1（本人），因此能通过归属校验，正好用来验证「status 被忽略」
        MvcResult result = update(1, 2, studentToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOf(1)).isEqualTo(1);
    }

    // ================== helpers ==================

    private MvcResult update(int id, int status, String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("status", status);
        return mockMvc.perform(post("/counselingAppointment/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Token", token))
                .andReturn();
    }

    private int insertFixture(int id, int counselorId, int studentId, LocalDate date, int slot, int status) {
        jdbcTemplate.update(
                "INSERT INTO counseling_appointment "
                        + "(id, appointment_no, counselor_id, student_id, appointment_date, time_slot, status, applied_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, "fixture-" + id, counselorId, studentId, java.sql.Date.valueOf(date), slot, status);
        return id;
    }

    private Integer statusOf(int id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM counseling_appointment WHERE id = ?", Integer.class, id);
    }

    private Integer latestStatus(int counselorId, LocalDate date, int slot) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM counseling_appointment WHERE counselor_id = ? AND appointment_date = ? "
                        + "AND time_slot = ? ORDER BY id DESC LIMIT 1",
                Integer.class, counselorId, java.sql.Date.valueOf(date), slot);
    }

    private String login(String path, String username, String password) throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(
                        post(path).param("username", username).param("password", password))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(node.path("code").asInt()).as("%s 登录应成功", path).isZero();
        String token = node.path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
