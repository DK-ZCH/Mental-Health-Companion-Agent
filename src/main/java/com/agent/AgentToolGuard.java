package com.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Tool 层统一身份守卫（Phase 4 / Round 1）。
 *
 * <p><b>为什么抽出来</b>：Step 12 建立的「二元身份 + 域白名单 + fail-closed」逻辑
 * 若在 4 个 Tool 里各写一遍，任何一个写错都会成为新的串读入口。
 * 因此安全校验只保留<b>一处</b>定义，Tool 只表达业务查询。
 *
 * <p><b>与 {@link AssessmentTools} 的关系</b>：T0（测评记录）在 Step 12 时已内联实现，
 * 行为与本类<b>完全一致</b>（同样的两个错误码 + 同样抛 {@link IllegalStateException}）。
 * 本批<b>不重构 T0</b>，避免把已 CLOSED 的 Step 12 批次拉回改动面。
 *
 * <p><b>失败语义（不可更改）</b>：身份非法 / 域不符一律<b>抛异常终止本次 Agent 调用</b>，
 * 绝不返回「空结果」或「denied 结果」——
 * 否则安全失败会被包装成 HTTP 200 + code=0，监控层看不到安全事件。
 *
 * <p>Step 23 定点修正：抛出的是 {@link AgentIdentityDeniedException}
 * （{@link IllegalStateException} 的子类），使调用方能明确识别身份拒绝；
 * 父类契约、错误码字符串与校验条件均未改变。
 *
 * <p>⚠️ 日志只记录<b>身份域</b>与<b>上下文形态</b>，<b>不记录 userId</b>。
 */
public final class AgentToolGuard {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolGuard.class);

    /** 本批全部 Tool 允许的身份域（白名单制：未声明域一律拒绝） */
    public static final String ALLOWED_TABLE = AgentIdentity.TABLE_STUDENT;

    /** 身份上下文非法（缺 userId / 类型错误 / 域空白） */
    private static final String ERR_IDENTITY_CONTEXT_INVALID = "agent identity context invalid";

    /** 身份域不被允许（如 counselor / admin_user） */
    private static final String ERR_IDENTITY_DOMAIN_DENIED = "agent identity domain not allowed";

    private AgentToolGuard() {
    }

    /**
     * 要求当前 Tool 调用者处于<b>学生域</b>，否则终止本次 Agent 调用。
     *
     * @param toolContext 框架注入的工具上下文（内容不发送给模型）
     * @return 已校验的身份（调用方可直接使用 {@code userId}）
     * @throws IllegalStateException 身份非法或域不符（fail-closed）
     */
    public static AgentIdentity requireStudent(ToolContext toolContext) {
        AgentIdentity identity = AgentIdentity.from(toolContext);
        if (identity == null) {
            logger.error("[AGENT][SEC] 身份上下文非法（{}），终止本次 Agent 调用",
                    toolContext == null ? "toolContext=null" : "missing-or-invalid-identity");
            throw new AgentIdentityDeniedException(ERR_IDENTITY_CONTEXT_INVALID);
        }
        if (!ALLOWED_TABLE.equals(identity.userTable())) {
            logger.error("[AGENT][SEC] 身份域不符（该工具仅允许 {}，实际 {}），终止本次 Agent 调用",
                    ALLOWED_TABLE, identity.userTable());
            throw new AgentIdentityDeniedException(ERR_IDENTITY_DOMAIN_DENIED);
        }
        return identity;
    }
}
