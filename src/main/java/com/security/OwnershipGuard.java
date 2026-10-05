package com.security;

import com.interceptor.AuthorizationInterceptor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 对象归属授权守卫（Phase 2 / Step 5 第二阶段 <b>批 1</b>）。
 *
 * <p><b>解决的问题</b>：当前用户身份本身可信（token → {@code auth_token} → Session），
 * 但 {@code /info/{id}} 这类「按 id 取对象」的接口**拿到对象后没有校验它是否属于当前用户** →
 * 水平越权（IDOR，第一阶段已实测证实）。
 *
 * <p><b>规则（证据驱动 + fail-closed）</b>：
 *
 * <table border="1">
 *   <caption>角色 × 归属</caption>
 *   <tr><th>角色</th><th>判定</th></tr>
 *   <tr><td>{@code 管理员}</td><td><b>显式放行</b>（B 类：管理员本就可查他人）</td></tr>
 *   <tr><td>{@code 学生}</td><td>目标对象的 {@code studentId} 必须等于当前用户，否则拒绝</td></tr>
 *   <tr><td>{@code 心理老师}</td><td>实体<b>有</b> {@code counselorId} 字段时：必须等于当前用户，否则拒绝；<br>
 *       实体<b>无</b>该字段（如测评记录）时：无 schema 依据可判定归属 → <b>维持现状</b>（见下方 ⚠️）</td></tr>
 *   <tr><td><b>其他 / null / 空</b></td><td><b>默认拒绝</b>（fail-closed —— 不沿用「else 即全量」的旧写法）</td></tr>
 * </table>
 *
 * <p>⚠️ <b>已知未收敛项（登记于批 1 报告，不凭空发明规则）</b>：
 * {@code assessment_record} / {@code assessment_redetail} / {@code assessment_wrong_question} 三张表
 * <b>只有 {@code student_id}、没有 {@code counselor_id}</b>，
 * 因此「心理老师能看到哪些学生的测评」这一业务规则**在代码与数据中都没有依据**。
 * 本批对这 3 个端点（及 {@code /yonghu/info/{id}}）**保持现状、不做教师侧收敛**，
 * 待有明确业务证据后再决定（避免「为修安全问题而凭空创造业务规则」，也不会误伤老师端页面）。
 *
 * <p>本类的「学生侧」规则是全量生效的 —— 第一阶段实测证实的那条越权链
 * （学生 a1 读取 student 2/3 的留言与收藏）在本批被彻底堵住。
 */
public final class OwnershipGuard {

    /** 角色字面量：与 {@code auth_token.role} / 数据库中的取值保持一致 */
    public static final String ROLE_ADMIN = "管理员";
    public static final String ROLE_STUDENT = "学生";
    public static final String ROLE_COUNSELOR = "心理老师";

    private OwnershipGuard() {
    }

    /**
     * 便捷重载：目标对象<b>不存在</b>时一律拒绝。
     *
     * <p>目的：避免用「200 无数据」与「403」的差异探测某个 id 是否存在（存在性预言机）。
     *
     * @param target 已按 id 查出的实体；为 {@code null} 表示记录不存在
     */
    public static void assertOwnership(HttpServletRequest request,
                                       Object target,
                                       Integer ownerStudentId,
                                       Integer ownerCounselorId) {
        if (target == null) {
            deny();
        }
        assertOwnership(request, ownerStudentId, ownerCounselorId);
    }

    /**
     * 校验「当前登录用户是否有权访问该业务对象」。
     *
     * @param request          当前请求（身份取自 Session，由 {@link AuthorizationInterceptor} 写入）
     * @param ownerStudentId   目标对象的所属学生 id；<b>传 {@code null} 表示该实体没有此字段</b>
     * @param ownerCounselorId 目标对象的所属心理老师 id；<b>传 {@code null} 表示该实体没有此字段</b>
     * @throws ForbiddenException 无权访问（→ HTTP 403）
     */
    public static void assertOwnership(HttpServletRequest request,
                                       Integer ownerStudentId,
                                       Integer ownerCounselorId) {
        String role = currentRole(request);
        Integer currentUserId = currentUserId(request);

        // ① 管理员：显式放行（不是 else 兜底）
        if (ROLE_ADMIN.equals(role)) {
            return;
        }

        // ② 学生：必须归属自己
        if (ROLE_STUDENT.equals(role)) {
            if (currentUserId != null && currentUserId.equals(ownerStudentId)) {
                return;
            }
            deny();
        }

        // ③ 心理老师：仅当实体存在 counselorId 归属字段时才做归属判定
        if (ROLE_COUNSELOR.equals(role)) {
            if (ownerCounselorId == null) {
                // 实体无 counselor_id 字段 → 无依据判定 → 维持现状（见类注释 ⚠️）
                return;
            }
            if (currentUserId != null && currentUserId.equals(ownerCounselorId)) {
                return;
            }
            deny();
        }

        // ④ 其他 / null / 空角色：默认拒绝（fail-closed）
        deny();
    }

    /**
     * 仅允许管理员访问（用于管理端账号等无「学生/老师归属」概念的对象）。
     *
     * <p>依据：Step 5 设计文档 §3.2 —— {@code /users/info/{id}} 属管理端账号，
     * 学生与心理老师一律拒绝（D2 已确认纳入）。
     */
    public static void assertAdminOnly(HttpServletRequest request) {
        if (ROLE_ADMIN.equals(currentRole(request))) {
            return;
        }
        deny();
    }

    /** 当前登录用户 id（来自 Session；未登录时由拦截器保证不会走到这里） */
    public static Integer currentUserId(HttpServletRequest request) {
        Object v = request.getSession().getAttribute("userId");
        return (v instanceof Integer) ? (Integer) v : null;
    }

    /** 当前登录角色（来自 Session） */
    public static String currentRole(HttpServletRequest request) {
        Object v = request.getSession().getAttribute("role");
        return v == null ? null : String.valueOf(v);
    }

    private static void deny() {
        throw new ForbiddenException();
    }
}
