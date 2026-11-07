package com.agent;

/**
 * Step 25：响应中的计划状态（第二步统一确定的互斥枚举）。
 *
 * <p><b>唯一生效路径</b>：只有 {@link #CONFIRMED} 表示已确认，且只能由服务端
 * {@code CONFIRM} 分支在 C-2 五步校验全部通过后产生。
 * 归属不一致 / 会话切换 / 账号切换统一归入 {@link #NO_DRAFT}，
 * 不单独暴露状态，避免向客户端泄露他人草稿的存在性。
 */
public enum PlanState {
    /** 草案已生成，未生效（仅 PREVIEW 成功） */
    DRAFT,
    /** 已确认（仅 CONFIRM 且五步全通过） */
    CONFIRMED,
    /** 用户主动放弃 */
    DISCARDED,
    /** 草稿存在但已过有效期 */
    EXPIRED,
    /** 无可用草稿：不存在 / 归属不一致 / 会话不一致 / 重复确认 */
    NO_DRAFT
}
