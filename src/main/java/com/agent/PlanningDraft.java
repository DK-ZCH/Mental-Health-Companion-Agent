package com.agent;

import java.io.Serializable;

/**
 * Step 25： {@code HttpSession} 中的单个计划草案值对象（P-1 冻结：会话内确认、不落库）。
 *
 * <p><b>归属四元组</b>：{@code userId + userTable + conversationId + issuedAt}。
 * 认证成功不等于归属校验成功——{@link #matches} 必须逐值比对身份与会话，
 * 这是 C-2 五步校验中的第 ②③ 步。
 *
 * <p><b>不生成凭证</b>：{@code CONFIRM} 无任何业务参数，不存在「确认 A、生效 B」
 * 的参数漂移面；Session 值由服务端持有，客户端无法改写。因此本类不签发 token，
 * 也不向响应体暴露任何内部字段。
 *
 * <p><b>不持久化</b>：无表、无 DAO、无 Service；会话结束或 TTL 到期后不承诺保留。
 */
public final class PlanningDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int userId;
    private final String userTable;
    private final long conversationId;
    private final long issuedAtMillis;
    private boolean consumed;

    public PlanningDraft(int userId, String userTable, long conversationId,
                         long issuedAtMillis) {
        this.userId = userId;
        this.userTable = userTable;
        this.conversationId = conversationId;
        this.issuedAtMillis = issuedAtMillis;
    }

    /** 身份与会话逐值比对；任一不符即拒绝（含账号切换、会话切换）。 */
    public boolean matches(int userId, String userTable, long conversationId) {
        return this.userId == userId
                && this.userTable.equals(userTable)
                && this.conversationId == conversationId;
    }

    public boolean isExpired(long nowMillis, long ttlMillis) {
        return nowMillis - issuedAtMillis > ttlMillis;
    }

    /**
     * 🔴 原子消费：把「检查是否已消费」与「置为已消费」合并为一次同步操作。
     *
     * <p>并发确认时，两个请求可能取得同一个 {@code PlanningDraft} 引用；
     * 若拆成 {@code isConsumed()} + {@code consume()} 两步，二者都可能读到
     * {@code false} 并各自完成消费，出现重复确认窗口。本方法保证至多一个调用方
     * 返回 {@code true}。
     *
     * @return {@code true} = 本次调用成功取得消费权；{@code false} = 已被消费
     */
    public synchronized boolean tryConsume() {
        if (consumed) {
            return false;
        }
        consumed = true;
        return true;
    }
}
