package com.agent;

/**
 * ⑦b-2 方案 d：用户原文标记转义（docs/STEP7B2-MARKER-ESCAPE-PLAN.md，用户 2026-11-04 批准）。
 *
 * <p><b>定位声明（冻结）</b>：本类只消除用户原文伪造
 * {@code KNOWLEDGE_CONTEXT}/{@code END_KNOWLEDGE_CONTEXT} 两个字面量的问题，
 * <b>不宣称解决所有 Prompt Injection</b>；真段信任边界仍由「消息结构 + System 15 条声明 +
 * 真段在前且服务端闭合」表达。架构级硬隔离（独立 UserMessage 对象，方案 b）为条件性候选，
 * 仅当 Context 来源扩展或真实观察到混淆时单独立项。
 *
 * <p><b>转义边界</b>：
 * <ul>
 *   <li>只处理<b>用户来源内容</b>；服务端生成的真段不经过本函数；</li>
 *   <li>替换文案冻结为透明说明（不引入不可见字符）；</li>
 *   <li>{@code END_KNOWLEDGE_CONTEXT} 含 {@code KNOWLEDGE_CONTEXT} 子串 ⇒ 必须先替换长标记，
 *       否则段尾标记会被拆成 {@code END_ + 转义文案}；</li>
 *   <li>Gate/Retriever 消费<b>原始消息</b>（E5），本函数只作用于最终发给模型的拼接文本。</li>
 * </ul>
 */
public final class KnowledgeMarkerEscaper {

    private KnowledgeMarkerEscaper() {
    }

    /** 转义文案（冻结）：语义透明、可断言。 */
    public static final String ESCAPED_TEXT = "[用户文本中出现标记字样，已转义]";

    /** 按<b>长度降序</b>排列：长标记（段尾）先替换，避免被子串标记拆坏。 */
    private static final String[] MARKERS = {"END_KNOWLEDGE_CONTEXT", "KNOWLEDGE_CONTEXT"};

    /**
     * 转义用户原文中的段标记字面量。
     *
     * <p>null/空串原样透传；不含标记的消息恒等返回（一字不改）；
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
