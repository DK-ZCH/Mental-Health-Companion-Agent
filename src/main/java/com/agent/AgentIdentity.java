package com.agent;

import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

/**
 * 已认证身份：{@code (userId, userTable)} <b>二元组</b>（Phase 4 / Step 12）。
 *
 * <p><b>为什么需要它</b>：{@code userId} 是<b>命名空间未限定</b>的整数 ——
 * {@code student.id = 1} 与 {@code counselor.id = 1} 是<b>完全不同的两个人</b>。
 * 只传 id 时，Tool 无法区分「学生 1」与「咨询师 1」，会跨角色串读
 * （Step 11 实测：{@code counselor a1} 读到了 {@code student a1} 的 15/100）。
 * 因此身份必须由<b>二元组</b>表达，任何单维度校验都是不完整的。
 *
 * <p><b>{@code userTable} 的来源（唯一）</b>：
 * {@code AuthorizationInterceptor} 每次请求从 {@code auth_token.user_type}
 * <b>重新读取并覆盖</b> Session，再由
 * {@link com.security.CurrentUserProvider#currentTableName} 读取 ——
 * <b>服务端来源，客户端不可篡改</b>。
 * 本类只负责在内存中承载该值，<b>不</b>自行读取 Session、<b>不</b>接受外部输入。
 *
 * <p><b>ToolContext 的安全性</b>：Spring AI 保证 {@code ToolContext} 内容
 * <b>不发送给模型</b>（Step 11 已实测 {@code userId leaked=false}），
 * 因此身份放在这里既不会进入模型视野，也不存在「模型填写身份参数」的入口。
 *
 * <p><b>失败语义</b>：{@link #from} 对任何非法形态返回 {@code null}，
 * 由调用方 <b>fail-closed</b>（抛异常终止本次 Agent 调用），
 * <b>绝不</b>使用默认值兜底、<b>绝不</b>在域缺失时假定为某个角色。
 */
public record AgentIdentity(int userId, String userTable) {

    /** 学生域（{@code auth_token.user_type} 取值之一） */
    public static final String TABLE_STUDENT = "student";

    /** {@code ToolContext} 中承载用户 id 的键 */
    public static final String CTX_USER_ID = "userId";

    /** {@code ToolContext} 中承载身份域的键 */
    public static final String CTX_USER_TABLE = "userTable";

    /**
     * 从 {@link ToolContext} 解析身份。
     *
     * <p><b>刻意返回 {@code null} 而不是抛异常</b>：本方法只做「解析」，
     * 「如何处置非法身份」由调用方决定（Tool 层统一抛
     * {@link IllegalStateException} 终止 Agent 调用）。
     *
     * @param toolContext 框架注入的工具上下文
     * @return 合法身份；{@code toolContext} 为 {@code null}、缺键、类型错误、
     *         或域为空白 → {@code null}
     */
    public static AgentIdentity from(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        // 防御性判断：Spring AI 的 ToolContext 构造器已保证内部 Map 非 null
        // （传 null 会在构造时 NPE，该状态不可达）；此处保留以免框架行为变化后 NPE。
        Map<String, Object> context = toolContext.getContext();
        if (context == null) {
            return null;
        }
        // id 必须是 Integer：不做 String/Long 强转兜底（类型污染一律拒绝）
        if (!(context.get(CTX_USER_ID) instanceof Integer userId)) {
            return null;
        }
        // 域必须是 String 且非空白
        if (!(context.get(CTX_USER_TABLE) instanceof String userTable)) {
            return null;
        }
        String normalized = userTable.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return new AgentIdentity(userId, normalized);
    }

    /** 是否学生域 */
    public boolean isStudent() {
        return TABLE_STUDENT.equals(userTable);
    }

    /**
     * 刻意<b>不</b>输出 {@code userId}。
     *
     * <p>本对象可能出现在日志、异常消息或断言的失败信息里；
     * record 的默认 {@code toString} 会把两个字段都打印出来。
     * 这里只保留身份域（非敏感），确保即便被误用也不会暴露用户标识。
     * 需要排查 id 时，由调用方显式选择记录。
     */
    @Override
    public String toString() {
        return "AgentIdentity[userTable=" + userTable + "]";
    }
}
