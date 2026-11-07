package com.agent;

/**
 * Step 25：{@code POST /agent/plan} 的请求动作（P-4 冻结）。
 *
 * <p><b>定位</b>：这是【意图声明】，不是状态凭证。客户端传入 {@code CONFIRM}
 * 并不会产生已确认态；服务端仍须完整校验 Session 草稿、身份归属、
 * 会话一致性、有效期与消费状态（见 {@link PlanningDraft}）。
 *
 * <p><b>刻意不做</b>：不由模型从自然语言判断「好的」「可以」——
 * 确认是状态变更，必须由 Java 确定性处理（P-2 冻结）。
 */
public enum PlanningAction {
    /** 生成建议草案（需要非空 message） */
    PREVIEW,
    /** 用户明确确认（无业务参数） */
    CONFIRM,
    /** 用户主动放弃 */
    DISCARD;

    /** 白名单解析；未知或空白一律返回 {@code null}，由 Controller 显式拒绝。 */
    public static PlanningAction parse(String raw) {
        if (raw == null) {
            return null;
        }
        for (PlanningAction action : values()) {
            if (action.name().equals(raw)) {
                return action;
            }
        }
        return null;
    }
}
