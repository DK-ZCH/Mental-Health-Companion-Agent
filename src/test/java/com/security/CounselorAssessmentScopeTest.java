package com.security;

import com.entity.CounselingAppointmentEntity;
import com.service.CounselingAppointmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ③ {@link CounselorAssessmentScope} 单元测试。
 *
 * <p><b>为什么不污染生产数据</b>：需要覆盖 {@code status=3}（拒绝）与「多预约含一条同意」
 * 这两类当前库中不存在的组合 —— 用 {@code @Transactional + @Rollback} 插入临时行，
 * 测试结束自动回滚，<b>生产库最终状态不变</b>。
 *
 * <p><b>为什么不用 mock</b>：{@code lambdaQuery()} 返回链式 wrapper，mock 后
 * 「是否真的按 status=2 过滤」将不可验证 —— 那正是本规则的核心。
 *
 * <p><b>现有种子数据</b>：
 * <pre>
 * counselor 1 ↔ student 1 → status = 2（同意）
 * counselor 2 ↔ student 1 → status = 1（待审核）
 * counselor 3 ↔ student 1 → status = 1（待审核）
 * student 2 无任何预约
 * </pre>
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@Transactional
@Rollback
@DisplayName("③ CounselorAssessmentScope")
class CounselorAssessmentScopeTest {

    /** 种子：counselor 1 与 student 1 存在 status=2 */
    private static final int COUNSELOR_APPROVED = 1;
    /** 种子：counselor 2 与 student 1 仅有 status=1 */
    private static final int COUNSELOR_PENDING = 2;

    private static final int STUDENT_WITH_APPROVAL = 1;
    /** 种子：student 2 无任何预约记录 */
    private static final int STUDENT_WITHOUT_ANY = 2;

    @Autowired
    private CounselorAssessmentScope scope;

    @Autowired
    private CounselingAppointmentService appointmentService;

    // ================== 授权判定（EXISTS 语义） ==================

    @Test
    @DisplayName("1. status=2（同意）→ 有权限")
    void approved_returnsTrue() {
        assertThat(scope.hasApprovedAppointment(COUNSELOR_APPROVED, STUDENT_WITH_APPROVAL))
                .as("存在已同意预约 ⇒ 授权成立")
                .isTrue();
    }

    @Test
    @DisplayName("2. status=1（待审核）→ 无权限")
    void pending_returnsFalse() {
        assertThat(scope.hasApprovedAppointment(COUNSELOR_PENDING, STUDENT_WITH_APPROVAL))
                .as("仅待审核 ⇒ 尚未建立咨询关系，不得授权")
                .isFalse();
    }

    @Test
    @DisplayName("3. 无任何预约 → 无权限")
    void noAppointment_returnsFalse() {
        assertThat(scope.hasApprovedAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY))
                .as("无预约关系 ⇒ 不得授权")
                .isFalse();
    }

    @Test
    @DisplayName("4. status=3（拒绝）→ 无权限（临时数据，测试后回滚）")
    void rejected_returnsFalse() {
        insertAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY, 3);

        assertThat(scope.hasApprovedAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY))
                .as("预约被拒绝 ⇒ 不得授权")
                .isFalse();
    }

    @Test
    @DisplayName("5. 🔴 多预约，仅其中一条 status=2 → 仍有权限（EXISTS 语义，非「全部必须为 2」）")
    void multipleAppointments_oneApproved_returnsTrue() {
        insertAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY, 1);   // 历史：待审核
        insertAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY, 3);   // 历史：拒绝
        insertAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY, 2);   // 当前：同意

        assertThat(scope.hasApprovedAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY))
                .as("只要存在【至少一条】status=2 即授权成立 —— 否则重新预约会误拒")
                .isTrue();
    }

    @Test
    @DisplayName("6. 🔴 多预约，无任何 status=2 → 无权限")
    void multipleAppointments_noneApproved_returnsFalse() {
        insertAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY, 1);
        insertAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY, 3);

        assertThat(scope.hasApprovedAppointment(COUNSELOR_APPROVED, STUDENT_WITHOUT_ANY))
                .as("只有待审核与拒绝 ⇒ 不得授权")
                .isFalse();
    }

    // ================== 关系判定方法（供 OwnershipGuard 回调使用） ==================

    @Test
    @DisplayName("7. 有已同意预约 → true（request 重载）")
    void requestOverload_approved_true() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_APPROVED);

        assertThat(scope.hasApprovedAppointment(req, STUDENT_WITH_APPROVAL))
                .as("身份与归属均合法且有 status=2 ⇒ 关系成立")
                .isTrue();
    }

    @Test
    @DisplayName("8. 归属为 null → false（fail-closed，不得默认放行）")
    void requestOverload_nullStudent_false() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_APPROVED);

        assertThat(scope.hasApprovedAppointment(req, null))
                .as("归属缺失 ⇒ 视为无关系")
                .isFalse();
    }

    @Test
    @DisplayName("9. 身份缺失 → false（fail-closed）")
    void requestOverload_nullIdentity_false() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, null);

        assertThat(scope.hasApprovedAppointment(req, STUDENT_WITH_APPROVAL))
                .as("身份缺失 ⇒ 视为无关系")
                .isFalse();
    }

    // ================== helpers ==================

    /** 构造带 Session 身份的请求（身份来源与拦截器写入的键一致） */
    private MockHttpServletRequest request(String role, Integer userId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (role != null) {
            req.getSession().setAttribute("role", role);
        }
        if (userId != null) {
            req.getSession().setAttribute("userId", userId);
        }
        return req;
    }

    /** 插入临时预约（随事务回滚，生产库不变） */
    private void insertAppointment(int counselorId, int studentId, int status) {
        CounselingAppointmentEntity entity = new CounselingAppointmentEntity();
        entity.setCounselorId(counselorId);
        entity.setStudentId(studentId);
        entity.setStatus(status);
        entity.setTimeSlot(1);
        entity.setAppointmentDate(new Date());
        entity.setAppliedAt(new Date());
        entity.setAppointmentNo("TEST-" + System.nanoTime());
        entity.setCreatedAt(new Date());
        appointmentService.save(entity);
    }
}
