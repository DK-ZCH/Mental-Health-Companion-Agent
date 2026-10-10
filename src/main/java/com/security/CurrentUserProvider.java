package com.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户身份读取（Phase 2 / Step 5 批 5 · <b>A1</b>）。
 *
 * <p><b>解决的问题</b>：身份来源分散在 50 处 Controller 代码里各自读 Session：
 *
 * <pre>
 * String role = String.valueOf(request.getSession().getAttribute("role"));
 * </pre>
 *
 * → 收敛为单一读取入口。身份的**写入源**不变，仍是 {@code AuthorizationInterceptor}
 * （它把 {@code auth_token} 的 4 个属性写入 Session —— 这是身份进入 Session 的唯一来源）。
 *
 * <p><b>本步边界（严格）</b>：只做 {@code role} 的身份读取源收敛。
 * <b>不</b>处理 {@code userId}（29 处，其 {@code NumberFormatException} / {@code ClassCastException}
 * 失败模式需单独设计契约）、<b>不</b>处理 Excel {@code /batchInsert}（13 处，身份语义待取证）、
 * <b>不</b>改动 {@code AuthorizationInterceptor}、{@code DataScope}、{@code OwnershipGuard}，
 * <b>不</b>改动 HTTP 401/403 契约与任何业务权限。
 *
 * <p><b>契约（已在 {@code PHASE2-T5B5-STEP1-CLASSIFICATION.md} §5 逐条确认后裁定）</b>：
 *
 * <pre>
 * Session 中不存在 role  →  currentRole(request) 返回 {@code null}（真正的缺失）
 * </pre>
 *
 * <p><b>为什么可以这样收敛（不是「顺手修掉」）</b>：收工前对全部 21 个调用点做过三项结构化检查 ——
 * ① 无任何 {@code role.equals(...)} 形态（故不会引入 NPE）；
 * ② 无任何代码依赖字符串 {@code "null"}（唯一相关处 {@code StringUtil} 对 {@code null} 与 {@code "null"} 等价处理）；
 * ③ 无任何调用点把 {@code role} 传出该站点（故不涉及 SQL 参数/响应体）。
 * → 对**所有可能形态**而言 {@code null} 与 {@code "null"} 行为等价，故此为**已确认的语义收敛**。
 *
 * <p>⚠️ 未以「38 项回归通过」作为等价性依据 —— 该回归网**不覆盖 role 缺失路径**。
 *
 * <p><b>依赖方向（已反转完成）</b>：{@code Controller → CurrentUserProvider → Session}，
 * 而 {@link OwnershipGuard} / {@code DataScope} 的自身 id/角色读取<b>委托到本类</b>。
 * 因此：① 身份读取只有**一份实现**（本类）；② 本类**不依赖任何 Controller**，也不依赖守卫
 * （无循环依赖）。
 */
public final class CurrentUserProvider {

    private CurrentUserProvider() {
    }

    /**
     * 当前登录角色；Session 中不存在时返回 {@code null}。
     *
     * <p><b>本类是身份读取的唯一实现</b>（依赖反转后）：{@link OwnershipGuard} 与本类的
     * {@link #currentUserIdOrNull} 都委托到此，故「Session 怎么读」只有一处定义。
     */
    public static String currentRole(HttpServletRequest request) {
        Object v = request.getSession().getAttribute("role");
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 当前登录用户 id，<b>允许缺失</b>（缺失时返回 {@code null}）。
     *
     * <p><b>适用</b>：{@code /session} 这类「报告当前登录者」的读端点 —— 保留既有行为（缺失即 {@code null}，
     * 交由下游各自处理），<b>不改变语义</b>。
     */
    public static Integer currentUserIdOrNull(HttpServletRequest request) {
        Object v = request.getSession().getAttribute("userId");
        return (v instanceof Integer) ? (Integer) v : null;
    }

    /**
     * 当前登录用户 id，<b>必须存在</b>（写入路径的自我归属）—— 缺失时<b>拒绝</b>（fail-closed）。
     *
     * <p><b>契约（A2 裁定）</b>：缺失时抛 {@link ForbiddenException} → {@code HTTP 403 + code=403}。
     * 复用既有拒绝形态，<b>不新增契约</b>；「未认证」仍由拦截器的 {@code HTTP 200 + code=401} 表达
     * （D1 已锁定，不改）。
     *
     * <p><b>为什么不能返回 {@code null}</b>：本方法用于写路径的归属赋值，如
     * {@code entity.setStudentId(currentUserId())} —— 返回 {@code null} 时 MyBatis-Plus 会<b>跳过该字段</b>，
     * 于是<b>客户端提交的归属值就会生效</b> = 归属校验失效（<b>fail-open</b>）。
     *
     * <p><b>本次迁移同时修正一处既有 fail-open（需显式声明）</b>：
     * {@code Examrecord:376}、{@code Examredetails:422}、{@code XinlilaoshiOrder:343} 原写作
     * {@code (Integer) request.getSession().getAttribute("userId")} —— 缺失时得到 {@code null} 并<b>静默不覆盖归属</b>；
     * 而其余 10 处 {@code Integer.valueOf(String.valueOf(...))} 形态会抛 {@code NumberFormatException}
     * （<b>意外</b> fail-closed）。两者失败模式不一致 → 收敛后**一律 fail-closed**。
     */
    public static Integer requireCurrentUserId(HttpServletRequest request) {
        Integer userId = currentUserIdOrNull(request);
        if (userId == null) {
            throw new ForbiddenException();
        }
        return userId;
    }
}
