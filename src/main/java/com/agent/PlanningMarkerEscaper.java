package com.agent;

/**
 * Step 25：用户原文中 {@code PLANNING_CONTEXT} 段标记的转义（P-3 冻结：独立纯函数）。
 *
 * <p><b>定位声明</b>：只消除两个段标记字面量被伪造的问题，
 * <b>不宣称解决所有 Prompt Injection</b>。真段由服务端生成、不经过本函数。
 *
 * <p><b>与既有两个 Escaper 的关系</b>：{@link KnowledgeMarkerEscaper} 与
 * {@link UserContextMarkerEscaper} 的常量、文案与用例<b>零改动</b>；
 * 三个类各自独立，避免段标记语义串味。
 *
 * <p><b>长标记优先</b>：{@code END_PLANNING_CONTEXT} 含
 * {@code PLANNING_CONTEXT} 子串，必须先替换长标记，否则段尾会被拆坏。
 */
public final class PlanningMarkerEscaper {

    private PlanningMarkerEscaper() {
    }

    public static final String ESCAPED_TEXT = "[计划上下文标记字样，已转义]";

    /** 按长度降序排列。 */
    private static final String[] MARKERS = {
            "END_PLANNING_CONTEXT", "PLANNING_CONTEXT"
    };

    /** null/空串原样透传；不含标记的文本恒等返回。 */
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
