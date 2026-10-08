package com.security;

import com.interceptor.AuthorizationInterceptor;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(OwnershipGuard.class);

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

    // ================== 写路径（Step 5 批 3B） ==================

    /**
     * 写路径允许管理员**代表目标用户**执行的【具名业务操作】。
     *
     * <p><b>为什么必须这样设计</b>：管理员确实会代表目标学生写数据 ——
     * 证据是管理端表单里的目标学生选择器
     * （{@code <el-select v-model="ruleForm.studentId" placeholder="请选择学生">}，
     * 见 {@code examrecord/add-or-update.vue:65} 等，取证于 {@code PHASE2-T5B3A-WRITE-PATH-AUDIT.md}）。
     *
     * <p>但这**不能**退化成「管理员可以写任何数据」的万能绕过。因此这里要求调用方
     * **显式声明操作名**：未登记的操作拿不到授权，新增管理端写入口必须在此显式登记。
     * 表达的是「管理员被明确授权执行【该具名业务操作】」，
     * 而不是「{@code if (role == 管理员) return true;}」。
     */
    public enum AdminWriteOperation {
        /** 管理端编辑咨询预约（管理员可选择目标学生） */
        UPDATE_APPOINTMENT("编辑咨询预约"),
        /** 管理端编辑咨询留言 */
        UPDATE_MESSAGE("编辑咨询留言"),
        /** 管理端编辑咨询收藏 */
        UPDATE_FAVORITE("编辑咨询收藏"),
        /** 管理端编辑测评记录 */
        UPDATE_ASSESSMENT_RECORD("编辑测评记录"),
        /** 管理端编辑答题明细 */
        UPDATE_ASSESSMENT_DETAIL("编辑答题明细"),
        /** 管理端编辑错题 */
        UPDATE_WRONG_QUESTION("编辑错题"),
        /**
         * 用户端提交咨询留言。
         * 批 3A 取证：**未发现管理端调用**；此处登记仅为「保持现状、不破坏既有能力」，
         * 不等于已确认管理端需要该权限 —— 若后续确认无此需求，应收紧为拒绝。
         */
        ADD_MESSAGE("提交咨询留言"),
        /** 用户端提交咨询收藏（同上，未发现管理端调用） */
        ADD_FAVORITE("提交咨询收藏");

        private final String label;

        AdminWriteOperation(String label) {
            this.label = label;
        }

        /** 用于审计日志与错误提示的可读名 */
        public String label() {
            return label;
        }
    }

    /**
     * 写路径·目标记录**可写性**判定（{@code /update} 使用）。
     *
     * <p>与读路径的 {@link #assertOwnership} 规则<b>刻意不同</b>：教师侧的写授权规则
     * <b>尚无证据</b>（批 3A 结论），且已明确**并入批 4** —— 故此处对心理老师
     * <b>保持现状（放行、不收紧）</b>，避免「为修安全问题凭空创造业务规则」。
     *
     * <table border="1">
     *   <caption>写路径目标可写性</caption>
     *   <tr><th>角色</th><th>判定</th></tr>
     *   <tr><td>{@code 管理员}</td><td>放行（但记录必须存在）</td></tr>
     *   <tr><td>{@code 学生}</td><td>目标记录的 {@code studentId} 必须等于当前用户，否则拒绝</td></tr>
     *   <tr><td>{@code 心理老师}</td><td><b>保持现状放行</b>（写授权规则 → 批 4）</td></tr>
     *   <tr><td>其他 / null</td><td><b>拒绝</b>（fail-closed）</td></tr>
     * </table>
     *
     * @param target 已按 id 查出的**现有记录**；为 {@code null} 表示记录不存在 → 一律拒绝
     */
    public static void assertWritableTarget(HttpServletRequest request, Object target,
                                            Integer ownerStudentId, Integer ownerCounselorId) {
        String role = currentRole(request);
        Integer currentUserId = currentUserId(request);

        if (ROLE_ADMIN.equals(role)) {
            if (target == null) {
                deny();
            }
            return;
        }
        if (ROLE_STUDENT.equals(role)) {
            if (target != null && currentUserId != null && currentUserId.equals(ownerStudentId)) {
                return;
            }
            deny();
        }
        if (ROLE_COUNSELOR.equals(role)) {
            // 批 3B：教师侧写授权待批 4 取证 → 保持现状（不收紧、不拒绝）
            return;
        }
        deny();
    }

    /**
     * 写路径·解析本次写入**应落地的归属用户 id**。
     *
     * <p>语义（服务端说了算，而不是「客户端提交什么就是什么」）：
     * <ul>
     *   <li><b>学生</b> → 一律强制为**当前用户**：客户端伪造的 {@code studentId} 被忽略
     *       （安全前提：目标记录的可写性已由 {@link #assertWritableTarget} 判定）</li>
     *   <li><b>管理员</b> → 必须声明**具名操作**（{@link AdminWriteOperation}）方可代表目标用户；
     *       保留其显式目标（未提交则返回 {@code null}，表示不动该字段）</li>
     *   <li><b>心理老师</b> → 保持现状（写授权规则 → 批 4）</li>
     *   <li><b>其他 / null</b> → 拒绝（fail-closed）</li>
     * </ul>
     */
    public static Integer resolveWriteOwner(HttpServletRequest request,
                                            AdminWriteOperation operation,
                                            Integer submittedOwnerId) {
        String role = currentRole(request);

        if (ROLE_STUDENT.equals(role)) {
            Integer currentUserId = currentUserId(request);
            if (currentUserId == null) {
                deny();
            }
            return currentUserId;
        }
        if (ROLE_ADMIN.equals(role)) {
            if (operation == null) {
                deny();
            }
            logger.info("写路径授权：管理员以具名操作[{}]代表目标用户 {} 执行写入",
                    operation.label(), submittedOwnerId);
            return submittedOwnerId;
        }
        if (ROLE_COUNSELOR.equals(role)) {
            // 批 3B：教师侧保持现状（登记批 4）
            return submittedOwnerId;
        }
        deny();
        return null; // 不可达（deny 抛异常）
    }

    /**
     * 自助资料更新授权（Step 5 批 4-B，业务规则来自需求方答复 Q2）。
     *
     * <table border="1">
     *   <caption>资料更新授权</caption>
     *   <tr><th>角色</th><th>判定</th></tr>
     *   <tr><td>{@code 管理员}</td><td><b>放行</b>（管理端编辑用户资料，B 类；保持现状）</td></tr>
     *   <tr><td>{@code selfRole}（该模块对应的自助角色）</td><td>目标必须<b>是本人</b>（{@code targetId == 当前用户}），否则拒绝</td></tr>
     *   <tr><td>其他（含<b>跨角色</b>：老师改学生资料等）</td><td><b>拒绝</b></td></tr>
     * </table>
     *
     * <p><b>为什么必须角色匹配</b>：{@code yonghu.id} 与 {@code xinlilaoshi.id} 是两张表的独立 id 空间，
     * 若只判「id 相等」，一个 id 恰好相同的老师就能改学生资料（反之亦然）——这是跨角色串位。
     *
     * <p><b>本方法闭合的真实风险</b>：改造前 {@code /yonghu/update}、{@code /xinlilaoshi/update}
     * 无任何授权且客户端实体被整体采信 → <b>任一登录账号（含老师）可提交他人 id 篡改其可绑定资料字段</b>
     * （{@code name} / {@code phone} / {@code email} / {@code avatarUrl} 等）= <b>对象级授权缺失（IDOR）</b>。
     *
     * <p><b>措辞更正（2026-10-08 复核）</b>：{@code password} 因 {@code getPassword()} 标注
     * {@code @JsonIgnore} 而<b>不会从 JSON 绑定</b>（见 ADR-0001），故该路径<b>改不了密码</b>，
     * 也**不构成对批 2「重置密码限管理员」的绕过** —— 原表述已更正。
     *
     * @param selfRole 该模块允许「自助」的角色（如 {@code /yonghu/update} → 学生）
     * @param existing 已按 id 查出的现有记录；为 {@code null}（记录不存在）→ 一律拒绝
     */
    public static void assertSelfOrAdmin(HttpServletRequest request, String selfRole,
                                         Integer targetId, Object existing) {
        if (existing == null) {
            deny();
        }
        String role = currentRole(request);
        if (ROLE_ADMIN.equals(role)) {
            return;
        }
        if (selfRole != null && selfRole.equals(role)) {
            Integer currentUserId = currentUserId(request);
            if (currentUserId != null && currentUserId.equals(targetId)) {
                return;
            }
            deny();
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

    /**
     * 统一拒绝（抛 {@link ForbiddenException} → 由 {@code GlobalExceptionHandler} 转 {@code HTTP 403 + code=403}）。
     *
     * <p>包内可见，供同包安全原语（如 {@code DataScope}）复用，避免每个原语各写一套拒绝形态。
     */
    static void deny() {
        throw new ForbiddenException();
    }
}
