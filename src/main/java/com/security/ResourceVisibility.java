package com.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 资源<b>发布状态可见性</b>（用户 2026-10-27 裁定 A）。
 *
 * <p><b>解决的问题（与归属授权是两件事，不要混用）</b>：
 * <table border="1">
 *   <caption>两种维度</caption>
 *   <tr><th>维度</th><th>问题</th><th>原语</th></tr>
 *   <tr><td>归属</td><td>这条记录<b>是不是你的</b>？</td><td>{@link OwnershipGuard}</td></tr>
 *   <tr><td>可见性</td><td>这类资源<b>是否已对外发布</b>？</td><td><b>本类</b></td></tr>
 * </table>
 *
 * <p><b>本类闭合的真实缺陷（修复前实测）</b>：
 * 种子数据两张试卷均为 {@code status=1（启用）} + {@code is_deleted=1（已删除）}，
 * 但匿名与已登录用户仍可读取 ——
 * <ul>
 *   <li>{@code /assessmentPaper/list}、{@code /assessmentQuestion/list} 是 {@code @IgnoreAuth}（匿名可读）；</li>
 *   <li>两个 {@code /info/{id}} <b>无任何守卫</b>；</li>
 *   <li>试题 XML <b>已 JOIN 试卷并投影 {@code paperStatus/paperIsDeleted}，却无 WHERE 使用</b>
 *       ⇒ 只修试卷会留下试题旁路。</li>
 * </ul>
 *
 * <p><b>视角划分（刻意不写成 {@code if (role == 学生) ... else ...}）</b>：
 * <pre>
 * admin            → 管理视角：全量（含未发布 / 已删除），不注入任何条件
 * 其他（含【匿名】）→ 公共视角：status = 启用 且 is_deleted = 未删除
 * </pre>
 * 之所以按「是否管理员」而不是「角色枚举」，是因为
 * {@code /list} 是 {@code @IgnoreAuth}，<b>匿名没有 role</b>；
 * 用 {@code if/else-if} 枚举角色必然漏掉匿名分支（会退化成「无角色 → 不过滤 → 全量」）。
 *
 * <p><b>为什么条件值走参数而不是写死在 SQL</b>：XML 无法引用 Java 常量，
 * 若在 XML 里写 {@code status = 1}，字典一旦调整就会静默失效。
 * 因此由控制器把常量<b>放进 params</b>，XML 只判断「是否注入」并使用 {@code #{}} 取值。
 *
 * <p><b>客户端参数不得绕过</b>：public 条件以 {@code AND} 追加，只能<b>收窄</b>结果；
 * 客户端传 {@code isDeleted=1} / {@code status=2} 只会让交集为空（返回 0 条），
 * 无法扩大可见范围。
 *
 * <p>⚠️ 本类<b>不</b>修补 {@code DataScope} 的「参数被静默丢弃」契约缺口（独立批次），
 * 也<b>不</b>处理 {@code notification}（已裁定为全站公告，现状正确）。
 */
public final class ResourceVisibility {

    /** 字典 {@code assessment_paper_status}：1=启用 */
    public static final int STATUS_ENABLED = 1;
    /** 字典 {@code assessment_paper_status}：2=禁用 */
    public static final int STATUS_DISABLED = 2;
    /** {@code is_deleted}：0=未删除 */
    public static final int NOT_DELETED = 0;
    /** {@code is_deleted}：1=已删除 */
    public static final int DELETED = 1;

    /** 参数名：是否注入公共视角过滤（存在即注入，与值无关） */
    public static final String PARAM_SCOPE_PUBLIC = "scopePublic";
    /** 参数名：公共视角要求的 {@code status} 取值 */
    public static final String PARAM_PUBLIC_STATUS = "publicStatus";
    /** 参数名：公共视角要求的 {@code is_deleted} 取值 */
    public static final String PARAM_PUBLIC_NOT_DELETED = "publicNotDeleted";

    private ResourceVisibility() {
    }

    /**
     * 当前请求是否处于<b>管理视角</b>。
     *
     * <p>匿名（{@code @IgnoreAuth} 端点）返回 {@code false} ⇒ 走公共视角，这是刻意为之。
     */
    public static boolean isAdmin(HttpServletRequest request) {
        return OwnershipGuard.ROLE_ADMIN.equals(OwnershipGuard.currentRole(request));
    }

    /**
     * 对<b>列表类</b>入口注入公共视角过滤条件（管理视角不注入）。
     *
     * <p>适用于 {@code /page}、{@code /list} 这类走 {@code selectListView} 的入口 ——
     * <b>两个入口都必须调用</b>，否则只修一个会留下等效旁路。
     *
     * @param params 即将传给 DAO 的参数（原地修改）
     */
    public static void applyPublicScope(HttpServletRequest request, Map<String, Object> params) {
        if (isAdmin(request)) {
            return;
        }
        params.put(PARAM_SCOPE_PUBLIC, Boolean.TRUE.toString());
        params.put(PARAM_PUBLIC_STATUS, STATUS_ENABLED);
        params.put(PARAM_PUBLIC_NOT_DELETED, NOT_DELETED);
    }

    /**
     * 对<b>详情类</b>入口（{@code /info/{id}}）做可见性判定。
     *
     * <p><b>失败模式统一为 403，且「不存在」与「存在但不可见」不可区分</b>：
     * 若「不存在」返回 511、「已删除」返回 403，就会形成<b>存在性预言机</b>
     * （可用状态码差异枚举 id）。因此 {@code null} 一律按拒绝处理。
     *
     * @param status    目标资源（或试题的<b>父卷</b>）的状态；{@code null} 表示不存在 / 父卷缺失
     * @param isDeleted 目标资源的 {@code isDeleted}；{@code null} 同上
     */
    public static void assertDetailAccessible(HttpServletRequest request, Integer status, Integer isDeleted) {
        if (isAdmin(request)) {
            return;
        }
        if (status == null || isDeleted == null) {
            OwnershipGuard.deny();
        }
        if (status != STATUS_ENABLED || isDeleted != NOT_DELETED) {
            OwnershipGuard.deny();
        }
    }
}
