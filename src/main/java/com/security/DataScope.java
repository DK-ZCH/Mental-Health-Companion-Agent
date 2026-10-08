package com.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 读范围收敛（Phase 2 / Step 5 第二阶段 <b>批 4 / ②</b>）。
 *
 * <p><b>解决的问题</b>：{@code /page} 列表接口原先在<b>每个 Controller 里各写一段</b>角色分支：
 *
 * <pre>
 * if (false)
 *     return R.error(511, "永不会进入");
 * else if ("学生".equals(role))
 *     params.put("studentId", request.getSession().getAttribute("userId"));
 * else if ("心理老师".equals(role))
 *     params.put("counselorId", request.getSession().getAttribute("userId"));
 * </pre>
 *
 * <p>这段写法有两个问题：
 * <ol>
 *   <li><b>fail-open</b>：<b>未登记角色</b>（{@code role} 为 {@code null} 或任何其他取值）会跳过全部分支 →
 *       <b>不加过滤 → 返回全量</b>。第一阶段审计已将其定性为 fail-open 缺口（回归矩阵第 12 项）。</li>
 *   <li><b>规则散落 12 处</b>：读范围口径分散在 12 个 Controller 中，易随改动漂移。</li>
 * </ol>
 *
 * <p><b>本原语语义</b>（与批 1 的 {@link OwnershipGuard} 同源，一律 fail-closed）：
 *
 * <table border="1">
 *   <caption>角色 × 读范围</caption>
 *   <tr><th>角色</th><th>收敛方式</th></tr>
 *   <tr><td>{@code 管理员}</td><td><b>不加过滤</b>（B 类：管理端本就查看全量）</td></tr>
 *   <tr><td>{@code 学生}</td><td>{@code studentId = 当前用户}（<b>覆盖</b>客户端提交的同名参数）</td></tr>
 *   <tr><td>{@code 心理老师}</td><td>{@code counselorId = 当前用户}</td></tr>
 *   <tr><td><b>其他 / null</b></td><td><b>拒绝</b>（HTTP 403）—— <b>不得默认全量</b></td></tr>
 * </table>
 *
 * <p><b>本批刻意未扩张（守批次边界）</b>：
 * <ul>
 *   <li>教师侧的可见范围业务规则（「老师能看到所服务学生的哪些数据」）在代码与数据中<b>都没有依据</b>
 *       （见批 1 报告登记项），故本原语<b>原样保留</b>既有口径，不新增教师侧收敛。</li>
 *   <li>本批只收敛「**已有**角色分支的 12 个 {@code /page}」。另外 3 个 {@code /page} 端点
 *       本就<b>没有</b>任何角色分支（对所有人都是全量），为它们新增收敛属<b>行为扩张</b>，
 *       需另行取证后处理 —— 本批<b>不动</b>。</li>
 *   <li>免鉴权 {@code /list} 的匿名枚举问题属 Step 3 待决策 D2，不在本批。</li>
 * </ul>
 */
public final class DataScope {

    private DataScope() {
    }

    /**
     * 按当前角色收敛查询范围（原地修改 {@code params}）。
     *
     * @param params         查询参数
     * @param studentField   学生侧范围字段名（各模块沿用既有字段名，如 {@code studentId}）
     * @param counselorField 教师侧范围字段名（如 {@code counselorId}）
     */
    public static void apply(HttpServletRequest request, Map<String, Object> params,
                             String studentField, String counselorField) {
        String role = OwnershipGuard.currentRole(request);

        if (OwnershipGuard.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (OwnershipGuard.ROLE_STUDENT.equals(role)) {
            Integer userId = OwnershipGuard.currentUserId(request);
            if (userId == null || studentField == null) {
                OwnershipGuard.deny();
            }
            params.put(studentField, userId);
            return;
        }
        if (OwnershipGuard.ROLE_COUNSELOR.equals(role)) {
            Integer userId = OwnershipGuard.currentUserId(request);
            if (userId == null || counselorField == null) {
                OwnershipGuard.deny();
            }
            params.put(counselorField, userId);
            return;
        }
        // 未知 / null 角色：fail-closed（原实现会落到「不加过滤 → 返回全量」）
        OwnershipGuard.deny();
    }
}
