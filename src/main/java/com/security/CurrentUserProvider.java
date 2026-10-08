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
 * <p><b>依赖关系</b>：本类只依赖 {@link OwnershipGuard}（同包），
 * <b>不依赖任何 Controller</b>（无反向依赖）。当前 {@code currentRole} 委托给守卫的既有实现以避免
 * 身份读取出现第二份实现；本步边界禁止改动守卫，故未反向调整依赖方向（留待后续步骤）。
 */
public final class CurrentUserProvider {

    private CurrentUserProvider() {
    }

    /**
     * 当前登录角色；Session 中不存在时返回 {@code null}。
     *
     * @see OwnershipGuard#currentRole(HttpServletRequest)
     */
    public static String currentRole(HttpServletRequest request) {
        return OwnershipGuard.currentRole(request);
    }
}
