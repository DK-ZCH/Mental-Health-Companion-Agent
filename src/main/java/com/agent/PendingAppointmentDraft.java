package com.agent;

/**
 * 一次请求内的「待确认预约草稿」传递器（Phase 4 / Round 4 · R4-Q5 裁定 C）。
 *
 * <p><b>它是什么</b>：短生命周期的<b>业务编排状态</b>，用于把「本轮预览产生的 token」
 * 交给 Controller 并写入既有的 {@code HttpSession}；以及在确认尝试后通知 Controller 清理。
 *
 * <p><b>它不是什么</b>：
 * <ul>
 *   <li>❌ <b>不是 Memory / 多轮对话</b> —— Agent 仍然是无状态单轮，本对象只在一次请求内存在；</li>
 *   <li>❌ <b>不是授权依据</b> —— 真正的凭证仍是签名 token，确认时全部重新验签、重新校验；</li>
 *   <li>❌ <b>不进入模型</b> —— 它只放在 {@code ToolContext} 里（框架保证不发送给模型）。</li>
 * </ul>
 *
 * <p><b>为什么需要它</b>：Tool 层拿不到 {@code HttpServletRequest}（AgentService 刻意不碰 Web），
 * 因此由 Controller 创建本对象放入上下文，Tool 只写「本轮签发了什么 / 是否已发起确认」，
 * 由 Controller 负责读写 Session。
 */
public class PendingAppointmentDraft {

    /** 本轮预览新签发的 token（没有预览则为 {@code null}）*/
    private String issuedToken;

    /** 本轮是否发起过确认尝试（成功或失败都算；用于清理草稿）*/
    private boolean consumed;

    /** 预览成功后由 Tool 调用 */
    public void issued(String token) {
        this.issuedToken = token;
    }

    /** 发起确认时由 Tool 调用（无论结果如何，草稿都不应再复用）*/
    public void markConsumed() {
        this.consumed = true;
    }

    public String issuedToken() {
        return issuedToken;
    }

    public boolean consumed() {
        return consumed;
    }
}
