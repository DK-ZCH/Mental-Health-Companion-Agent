package com.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ③ {@link OwnershipGuard} 的<b>通用关系回调机制</b>单元测试（纯内存，不启动容器、不连 DB）。
 *
 * <p><b>本类要证明两件事（用户 2026-10-25 指定的验收点）</b>：
 * <ol>
 *   <li><b>19 处既有调用点行为完全不变</b> —— 不传回调时，
 *       「心理老师 + 实体无 {@code counselor_id}」仍<b>放行</b>（用例 1）；</li>
 *   <li><b>通用原语未被业务污染</b> —— 本类<b>不</b>引用任何 assessment / appointment 概念，
 *       只用「任意关系回调」验证机制本身；且回调<b>只在必要组合</b>下被调用（用例 6–9）。</li>
 * </ol>
 *
 * <p><b>回调触发矩阵</b>：
 * <table border="1">
 *   <caption>何时调用 relationChecker</caption>
 *   <tr><th>角色</th><th>ownerCounselorId</th><th>是否调用</th></tr>
 *   <tr><td>管理员</td><td>任意</td><td>否</td></tr>
 *   <tr><td>学生</td><td>任意</td><td>否</td></tr>
 *   <tr><td>心理老师</td><td><b>非 null</b>（实体有该字段）</td><td>否（按字段相等）</td></tr>
 *   <tr><td><b>心理老师</b></td><td><b>null</b>（实体无该字段）</td><td><b>是</b></td></tr>
 *   <tr><td>未知 / null</td><td>任意</td><td>否（直接拒绝）</td></tr>
 * </table>
 */
@DisplayName("③ OwnershipGuard 关系回调")
class OwnershipGuardRelationTest {

    private static final Integer STUDENT_OWNER_ID = 1;
    private static final Integer COUNSELOR_OWNER_ID = 1;

    private final AtomicInteger invoked = new AtomicInteger();

    @BeforeEach
    void reset() {
        invoked.set(0);
    }

    // ================== 核心：不传回调时行为完全不变 ==================

    @Test
    @DisplayName("1. 🔴 无回调 + 心理老师 + 实体无 counselor 字段 → 仍【放行】（19 处既有调用点行为零变化）")
    void withoutChecker_counselorNullField_allowed() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_OWNER_ID);

        assertThatCode(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, null))
                .as("这是 Step 5 批 1 起的既有语义，③ 不得改变它")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("2. 无回调 + 心理老师 + 实体有 counselor 字段且匹配 → 放行")
    void withoutChecker_counselorOwned_allowed() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_OWNER_ID);

        assertThatCode(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, COUNSELOR_OWNER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("3. 无回调 + 心理老师 + 实体有 counselor 字段但不匹配 → 403")
    void withoutChecker_counselorNotOwned_denied() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, 99);

        assertThatThrownBy(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, COUNSELOR_OWNER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ================== 回调生效（仅在「老师 + 无字段」组合） ==================

    @Test
    @DisplayName("4. 🔴 有回调 + 回调返回 false → 403")
    void withChecker_false_denied() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_OWNER_ID);

        assertThatThrownBy(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, null,
                checker(false)))
                .as("关系不成立 ⇒ 拒绝")
                .isInstanceOf(ForbiddenException.class);
        assertThat(invoked.get()).as("回调应被调用一次").isEqualTo(1);
    }

    @Test
    @DisplayName("5. 🔴 有回调 + 回调返回 true → 放行")
    void withChecker_true_allowed() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_OWNER_ID);

        assertThatCode(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, null,
                checker(true)))
                .as("关系成立 ⇒ 放行")
                .doesNotThrowAnyException();
        assertThat(invoked.get()).isEqualTo(1);
    }

    // ================== 回调不应被调用（避免无意义查询 / 越权扩张） ==================

    @Test
    @DisplayName("6. 有回调 + 学生 + 本人记录 → 放行，且【不调用回调】")
    void withChecker_student_notInvoked() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_STUDENT, STUDENT_OWNER_ID);

        assertThatCode(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, null,
                checker(false)))
                .as("学生侧按 studentId 判定，回调不参与（传 false 也不影响）")
                .doesNotThrowAnyException();
        assertThat(invoked.get()).as("回调不应被调用").isZero();
    }

    @Test
    @DisplayName("7. 有回调 + 管理员 → 放行，且【不调用回调】")
    void withChecker_admin_notInvoked() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_ADMIN, 6);

        assertThatCode(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, null,
                checker(false)))
                .doesNotThrowAnyException();
        assertThat(invoked.get()).isZero();
    }

    @Test
    @DisplayName("8. 有回调 + 心理老师但实体【有】counselor 字段 → 按字段判定，【不调用回调】")
    void withChecker_counselorOwnedField_notInvoked() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_OWNER_ID);

        assertThatCode(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, COUNSELOR_OWNER_ID,
                checker(false)))
                .as("有字段时走既有相等判定，回调传 false 也不应导致拒绝")
                .doesNotThrowAnyException();
        assertThat(invoked.get()).isZero();
    }

    @Test
    @DisplayName("9. 🔴 有回调 + 未知角色 → 403，且【不调用回调】（回调不得成为绕过通道）")
    void withChecker_unknownRole_deniedWithoutInvoking() {
        MockHttpServletRequest req = request("some_future_role", 1);

        assertThatThrownBy(() -> OwnershipGuard.assertOwnership(req, STUDENT_OWNER_ID, null,
                checker(true)))
                .as("即使回调返回 true，未知角色也必须拒绝 —— 回调不能扩大授权面")
                .isInstanceOf(ForbiddenException.class);
        assertThat(invoked.get()).isZero();
    }

    @Test
    @DisplayName("10. 目标对象为 null → 403（存在性预言机防护，不调用回调）")
    void nullTarget_deniedWithoutInvoking() {
        MockHttpServletRequest req = request(OwnershipGuard.ROLE_COUNSELOR, COUNSELOR_OWNER_ID);

        assertThatThrownBy(() -> OwnershipGuard.assertOwnership(req, null, STUDENT_OWNER_ID, null,
                checker(true)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(invoked.get()).isZero();
    }

    // ================== helpers ==================

    private OwnershipGuard.RelationChecker checker(boolean result) {
        return req -> {
            invoked.incrementAndGet();
            return result;
        };
    }

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
}
