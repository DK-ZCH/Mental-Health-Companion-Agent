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
 * <p>⚠️ <b>「实体无 {@code counselor_id} 字段」组合的处置（③ 起演进）</b>：
 * {@code assessment_record} / {@code assessment_answer} / {@code assessment_wrong_question}
 * 等表<b>只有 {@code student_id}、没有 {@code counselor_id}</b>，
 * 无法用「字段相等」判定心理老师是否有权访问。
 *
 * <p>本类<b>不</b>内置任何具体业务规则（那会污染通用原语），而是提供
 * {@link RelationChecker} <b>关系回调</b>：
 * <ul>
 *   <li><b>未传回调</b> → 维持原语义（放行）—— 19 处既有调用点<b>行为零变化</b>；</li>
 *   <li><b>传了回调</b> → 由调用方判定关系（如「与该学生有已同意预约」），
 *       关系成立放行、否则拒绝。</li>
 * </ul>
 *
 * <p>当前已接入回调的端点：{@code /assessmentRecord/info/{id}}
 * （规则见 {@link CounselorAssessmentScope}）。
 * 其余同类端点仍维持原语义，待各自业务规则明确后逐个接入
 * （<b>禁止</b>把某类数据的规则自动套用到其他敏感数据）。
 *
 * <p>本类的「学生侧」规则是全量生效的 —— 第一阶段实测证实的那条越权链
 * （学生 a1 读取 student 2/3 的留言与收藏）在本批被彻底堵住。
 */
public final class OwnershipGuard {

    /** 角色字面量：与 {@code auth_token.role} / 数据库中的取值保持一致 */
    public static final String ROLE_ADMIN = "管理员";
    public static final String ROLE_STUDENT = "学生";
    public static final String ROLE_COUNSELOR = "心理老师";

    /** 身份表名字面量：与 {@code auth_token.user_type} / Session 中的取值保持一致（不改造现有取值）*/
    public static final String TABLE_STUDENT = "student";
    public static final String TABLE_COUNSELOR = "counselor";
    public static final String TABLE_ADMIN_USER = "admin_user";

    private static final Logger logger = LoggerFactory.getLogger(OwnershipGuard.class);

    private OwnershipGuard() {
    }

    /**
     * 通用「关系授权」回调（③ 引入）。
     *
     * <p><b>解决的问题</b>：某些实体本身<b>没有</b> {@code counselor_id} 字段
     * （如 {@code assessment_record}），无法用「字段相等」判定心理老师是否有权访问；
     * 但业务上确实存在另一条关系链（如「与该学生有已同意预约」）。
     * 本回调让<b>调用方</b>提供这条关系的判定，
     * {@link OwnershipGuard} 只负责「<b>何时</b>该问」与「问到之后<b>如何处置</b>」。
     *
     * <p><b>刻意不做（防止通用原语被业务污染）</b>：本接口<b>不</b>感知任何具体业务 ——
     * 不出现 assessment / appointment / status / 测评 / 预约 等概念。
     * 具体关系如何判定，完全由调用方（如 {@code CounselorAssessmentScope}）实现。
     *
     * <p><b>触发条件（仅在必要时调用）</b>：
     * <ul>
     *   <li>管理员 → <b>不调用</b>（本就放行）</li>
     *   <li>学生 → <b>不调用</b>（按 {@code studentId} 字段判定）</li>
     *   <li>心理老师 + 实体<b>有</b> {@code counselor_id} → <b>不调用</b>（按字段相等判定）</li>
     *   <li>心理老师 + 实体<b>无</b> {@code counselor_id} → <b>调用</b></li>
     * </ul>
     */
    @FunctionalInterface
    public interface RelationChecker {

        /**
         * @param request 当前请求（身份来源于 Session，由拦截器写入）
         * @return {@code true} = 关系成立（放行）；{@code false} = 不成立（拒绝）
         */
        boolean hasRelation(HttpServletRequest request);
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
        assertOwnership(request, target, ownerStudentId, ownerCounselorId, null);
    }

    /**
     * 带<b>关系回调</b>的重载（③ 引入）。
     *
     * <p>与上一重载唯一区别：当「心理老师 + 实体无 {@code counselor_id} 字段」时，
     * 不再<b>默认放行</b>，而是交由 {@code relationChecker} 判定。
     *
     * <p>传 {@code null} 等价于旧行为 ⇒ <b>19 处既有调用点无需任何改动</b>。
     *
     * @param relationChecker 关系判定回调；{@code null} = 不启用（维持既有语义）
     */
    public static void assertOwnership(HttpServletRequest request,
                                       Object target,
                                       Integer ownerStudentId,
                                       Integer ownerCounselorId,
                                       RelationChecker relationChecker) {
        if (target == null) {
            deny();
        }
        assertOwnership(request, ownerStudentId, ownerCounselorId, relationChecker);
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
        // 显式转型：否则 null 同时匹配 (Object,Integer,Integer) 与 (Integer,Integer,RelationChecker)
        assertOwnership(request, ownerStudentId, ownerCounselorId, (RelationChecker) null);
    }

    /**
     * 完整实现（③ 起）：带<b>可选关系回调</b>。
     *
     * <p>回调<b>只在一种组合</b>下被调用：<b>心理老师</b> + 实体<b>无</b> {@code counselor_id} 字段。
     * 其余角色/组合一律走既有判定，<b>回调不参与</b>（避免无意义的额外查询）。
     *
     * @param relationChecker 关系判定回调；{@code null} = 维持既有语义（默认放行该组合）
     */
    public static void assertOwnership(HttpServletRequest request,
                                       Integer ownerStudentId,
                                       Integer ownerCounselorId,
                                       RelationChecker relationChecker) {
        String role = currentRole(request);
        Integer currentUserId = currentUserId(request);

        // ① 管理员：显式放行（不是 else 兜底）—— 不触发回调
        if (ROLE_ADMIN.equals(role)) {
            return;
        }

        // ② 学生：必须归属自己 —— 不触发回调
        if (ROLE_STUDENT.equals(role)) {
            if (currentUserId != null && currentUserId.equals(ownerStudentId)) {
                return;
            }
            deny();
        }

        // ③ 心理老师
        if (ROLE_COUNSELOR.equals(role)) {
            if (ownerCounselorId != null) {
                // 实体有 counselor_id → 走既有「字段相等」判定，不触发回调
                if (currentUserId != null && currentUserId.equals(ownerCounselorId)) {
                    return;
                }
                deny();
            }
            // 实体无 counselor_id 字段（如 assessment_record）：
            //   · 未提供回调（19 处既有调用点）→ 维持现状放行，行为零变化
            //   · 提供了回调 → 由调用方判定（关系成立放行，否则拒绝）
            if (relationChecker == null) {
                return;
            }
            if (relationChecker.hasRelation(request)) {
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

    /**
     * {@code /session} 专用（S2-E-1）：当前已认证身份必须<b>同时</b>匹配 {@code role} 与 {@code tableName}。
     *
     * <p><b>解决的问题</b>：{@code /student/session}、{@code /counselor/session}、{@code /adminUser/session}
     * 原先只取 Session 的 {@code userId} 直接 {@code getById}，不校验身份类型。而三张身份表的 id
     * 是各自独立的 id 空间（实测 student/counselor 的 id 1,2,3 完全重合）⇒
     * <b>student token 可读到 counselor 行，反之亦然</b>（双向 PII 越权）。
     *
     * <p><b>职责边界（刻意收窄）</b>：只做身份一致性校验 ——
     * <b>不</b>查库、<b>不</b>判归属、<b>不</b>改 Session、<b>不</b>自动推断角色、<b>不</b>处理业务权限。
     *
     * <p><b>role 与 tableName 不是两个独立认证因素</b>：二者同源于服务器已校验的同一条
     * {@code auth_token}（拦截器同时写入 Session）。此处是<b>一致性检查</b>，
     * 用于防止「Session 内角色与表类型不一致」，不构成两层认证。
     *
     * <p><b>失败模式</b>：任一字段缺失或不匹配 → {@link #deny()}（HTTP 403 + code=403）。
     * 匿名请求由拦截器先行处理（HTTP 200 + code=401），不会到达本方法。
     *
     * <p><b>短路顺序</b>：role 不匹配时<b>直接拒绝</b>，不再读取 tableName（保持最简逻辑）。
     *
     * @param expectedRole      该端点代表的角色（传 {@link #ROLE_STUDENT} / {@link #ROLE_COUNSELOR} / {@link #ROLE_ADMIN}）
     * @param expectedTableName 该端点代表的身份表名（传 {@link #TABLE_STUDENT} / {@link #TABLE_COUNSELOR} / {@link #TABLE_ADMIN_USER}）
     */
    public static void assertCurrentIdentity(HttpServletRequest request,
                                             String expectedRole,
                                             String expectedTableName) {
        // ① 角色一致性（常量在前，避免 currentRole() 为 null 时 NPE）
        if (!expectedRole.equals(currentRole(request))) {
            deny();
        }
        // ② 表类型一致性（同上：常量在前）
        if (!expectedTableName.equals(CurrentUserProvider.currentTableName(request))) {
            deny();
        }
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
     * 写路径·删除授权（S2-C1，用于「学生个人数据」类资源的 {@code /delete}）。
     *
     * <p>语义：管理员放行；学生仅限<b>本人</b>（{@code ownerStudentId == 当前用户}）；
     * 其余角色（含心理老师、未知 / 缺失）<b>一律拒绝</b> —— 与读路径的
     * {@link #assertOwnership} 刻意不同：教师不是这些资源的所有者，不应具备删除权限。
     *
     * @param target         已按 id 查出的现有记录；{@code null}（记录不存在）→ 一律拒绝（防存在性预言机）
     * @param ownerStudentId 目标记录所属学生 id
     */
    public static void assertOwnerOrAdmin(HttpServletRequest request, Object target,
                                          Integer ownerStudentId) {
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

    /**
     * 当前登录用户 id（来自 Session；未登录时由拦截器保证不会走到这里）。
     *
     * <p><b>依赖反转后委托给 {@link CurrentUserProvider}</b> —— 本方法保留公开签名（
     * {@code DataScope} 等仍在用），但身份读取的实现只有 `CurrentUserProvider` 一处。
     */
    public static Integer currentUserId(HttpServletRequest request) {
        return CurrentUserProvider.currentUserIdOrNull(request);
    }

    /**
     * 当前登录角色（来自 Session）。
     *
     * <p><b>依赖反转后委托给 {@link CurrentUserProvider}</b>（同上：签名保留，实现唯一）。
     */
    public static String currentRole(HttpServletRequest request) {
        return CurrentUserProvider.currentRole(request);
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
