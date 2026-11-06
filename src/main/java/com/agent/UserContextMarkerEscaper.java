package com.agent;

/**
 * Step 23：UserContext 段内自由文本的标记转义（D-5 冻结：新增独立纯函数，处理双方自由文本）。
 *
 * <p><b>定位声明（必须保留）</b>：本类只消除数据库自由文本伪造段标记字面量的问题，
 * <b>不宣称解决所有 Prompt Injection</b>。UserContext 的可信边界由「消息结构 +
 * System 第 16 条声明 + 段在用户原文之前且服务端闭合」共同表达。
 *
 * <p><b>与 {@link KnowledgeMarkerEscaper} 的关系</b>：两个类各自独立、文案不同，
 * 本批<b>不修改</b> ⑦b-2 已 CLOSED 的转义语义、常量与测试。
 *
 * <p><b>双方都不可信</b>：用户留言 {@code MessageToolResult.Item.content} 与
 * 咨询师回复 {@code Item.replyContent} 都是数据库文本，均可含伪造段标记或伪造指令；
 * 调用方必须对两个字段<b>分别</b>调用 {@link #escape(String)}。
 *
 * <p><b>为什么同时转义 RAG 段标记</b>：留言文本进入 USER_CONTEXT 段后，若其中出现
 * {@code KNOWLEDGE_CONTEXT} / {@code END_KNOWLEDGE_CONTEXT}，会伪造第二个 RAG 段头/段尾，
 * 破坏 ⑦b-2 已建立的上下文边界。这是同一「用户来源文本不得伪造服务端标记」原则的延续，
 * 不是业务范围扩大；且由本类独立完成，不改 ⑦b-2 的实现。
 *
 * <p><b>长标记优先</b>：{@code END_USER_CONTEXT} 含 {@code USER_CONTEXT} 子串、
 * {@code END_KNOWLEDGE_CONTEXT} 含 {@code KNOWLEDGE_CONTEXT} 子串 ⇒ 按长度降序替换，
 * 否则段尾会被拆成 {@code END_ + 转义文案}。
 */
public final class UserContextMarkerEscaper {

    private UserContextMarkerEscaper() {
    }

    /** 转义文案：语义透明、可断言；刻意与 ⑦b-2 的文案不同，避免测试串味。 */
    public static final String ESCAPED_TEXT = "[上下文标记字样，已转义]";

    /** 按<b>长度降序</b>排列：长标记先替换，避免被子串标记拆坏。 */
    private static final String[] MARKERS = {
            "END_KNOWLEDGE_CONTEXT", "KNOWLEDGE_CONTEXT",
            "END_USER_CONTEXT", "USER_CONTEXT"
    };

    /**
     * 转义单条自由文本中的段标记字面量。
     *
     * <p>null/空串原样透传；不含标记的文本恒等返回（一字不改）；
     * 命中标记逐处替换为 {@link #ESCAPED_TEXT}，其余文字逐字保留。
     */
    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (String marker : MARKERS) {
            out = out.replace(marker, ESCAPED_TEXT);
        }
        return out;
    }
}
