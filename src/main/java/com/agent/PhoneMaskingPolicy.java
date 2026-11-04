package com.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 债 1：服务端号码遮蔽 —— 最终输出环节的<b>确定性</b>防线（不依赖 Prompt）。
 *
 * <p><b>为什么存在</b>：R3 真实模型测试曾出现模型在普通回答中生成未经验证的号码
 * （{@code 12356}、{@code 400-161-9995} 等），Prompt 约束未能完全阻止。
 * 依据「无号码原则」（Round 3 Q2 延伸到所有输出），必须在服务端最终 answer 上做机械遮蔽。
 *
 * <p><b>设计冻结（docs/BATCH-DEBT1-PHONE-MASK-PLAN.md，用户 2026-11-04 批准）</b>：
 * <ul>
 *   <li>4 组高置信电话模式：P1 400 号段（无分隔也命中，特征强）·
 *       P2 手机 {@code 1[3-9]\d{9}}（无分隔命中）·
 *       P3 座机（<b>强制分隔符</b>——连续无分隔不命中，如学号 {@code 01012345678}，
 *       保守取舍：宁可漏遮、不可误伤）·
 *       P4 全角数字先规范化（影子文本）再套 P1–P3 判定——<b>仅长度不构成遮蔽理由</b>；</li>
 *   <li>替换文案冻结：隐藏 ≠ 已提供可用热线，必须附带指引；</li>
 *   <li>多号码逐处替换，前后文原样保留；</li>
 *   <li>命中区间合并后一次性重建——正例 S1–S5 必须命中，反例 R1–R8 必须零误伤
 *       （日期/时间/分数/范围/时长/编号/小数/全角，见单测）。</li>
 * </ul>
 *
 * <p><b>适用范围</b>：只遮蔽返回给用户的模型自由文本（AgentService 唯一遮蔽点）；
 * 卡片/语料/KNOWLEDGE_CONTEXT 段不经过本路径。
 */
public final class PhoneMaskingPolicy {

    private PhoneMaskingPolicy() {
    }

    /** 替换文案（冻结）：隐藏的同时给出无号码指引。 */
    public static final String MASK_TEXT =
            "[此号码未经平台核实，已隐藏。紧急援助请通过当地官方渠道查找，或前往最近的医院急诊。]";

    /** P1：400 号段（400 特征强，无分隔连续 10 位也命中）。 */
    private static final Pattern P400 =
            Pattern.compile("(?<!\\d)400[-\\s]?\\d{3}[-\\s]?\\d{3,4}(?!\\d)");

    /** P2：手机号 1[3-9] 开头共 11 位（无分隔命中）。 */
    private static final Pattern MOBILE =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /**
     * P3：座机（0 前缀 3–4 位区号 + <b>必须有分隔符</b> + 7–8 位号码）。
     *
     * <p>强制分隔符是冻结决策：连续无分隔的 0 开头长数字串（学号/订单号）与座机无法区分，
     * 误伤代价高于漏遮 ⇒ 无分隔不命中。
     */
    private static final Pattern LANDLINE =
            Pattern.compile("(?<!\\d)0\\d{2,3}[-\\s]\\d{7,8}(?!\\d)");

    private static final Pattern[] PATTERNS = {P400, MOBILE, LANDLINE};

    /**
     * 对模型自由文本做号码遮蔽。
     *
     * <p>{@code null}/空串原样透传（AgentService 的 null 处理逻辑保持不变）。
     * 半角原文与全角规范化影子文本各扫一遍，命中区间并集合并后重建，保证：
     * 未命中的内容（含未命中的全角文字）逐字原样保留。
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String shadow = normalizeFullwidth(text);

        List<int[]> spans = new ArrayList<>();
        for (Pattern p : PATTERNS) {
            collect(p.matcher(text), spans);
            collect(p.matcher(shadow), spans);
        }
        if (spans.isEmpty()) {
            return text;
        }
        spans.sort(Comparator.comparingInt(s -> s[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] s : spans) {
            if (!merged.isEmpty() && s[0] <= merged.get(merged.size() - 1)[1]) {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], s[1]);
            } else {
                merged.add(new int[]{s[0], s[1]});
            }
        }
        StringBuilder sb = new StringBuilder(text.length());
        int idx = 0;
        for (int[] s : merged) {
            sb.append(text, idx, s[0]).append(MASK_TEXT);
            idx = s[1];
        }
        sb.append(text, idx, text.length());
        return sb.toString();
    }

    /**
     * 影子文本：全角数字 ０-９（U+FF10–FF19）→ 半角、全角连字符 － → -、全角空格 　→ 空格；
     * 其余字符原样。逐字符 1:1 转换 ⇒ 长度不变，索引与原文一一对应。
     */
    private static String normalizeFullwidth(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uFF10' && c <= '\uFF19') {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\uFF0D') {
                sb.append('-');
            } else if (c == '\u3000') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void collect(Matcher m, List<int[]> spans) {
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
        }
    }
}
