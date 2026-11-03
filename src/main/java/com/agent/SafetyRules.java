package com.agent;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 安全兜底的<b>确定性输入规则</b>（Phase 4 / Round 3 · 用户 Q3 裁定：常量类）。
 *
 * <p><b>为什么是常量类而不是 DB 字典</b>：这是<b>安全边界规则</b>。
 * 规则变更应当走「代码 + 评审 + 测试」，而不是运行时可改 ——
 * 否则等于给线上开了一个「悄悄放宽安全规则」的管理面。
 *
 * <p><b>用户 Q1 裁定的技术含义</b>：<b>不允许</b>「出现「自杀」二字即触发」。
 * 因此判定顺序是：
 * <ol>
 *   <li>归一化（{@link Normalizer#NFKC} + 去空白）—— 不改写语义，<b>不做</b>拼音/谐音扩展；</li>
 *   <li>第三人称风险内容 + <b>明确求助意图</b> ⇒ 触发（Q1b 裁定 ②）；</li>
 *   <li>否则先**移除**第三人称片段、再移除否定片段；</li>
 *   <li>剩余文本任一条 R 规则命中 ⇒ 触发。</li>
 * </ol>
 *
 * <p><b>为什么「移除片段」而不是「全局否决」</b>：
 * 「我不想自杀，但我想死」中，否定只应覆盖「自杀」那一段；
 * 若做成全局否决，就会漏掉后半句的真实风险。
 *
 * <p><b>刻意不做</b>：不做风险分级、不打分、不判断严重程度 ——
 * 本类只回答「是否命中」，其余判断不属确定性规则的能力范围。
 */
public final class SafetyRules {

    /** 规则 id（<b>只进日志，绝不进响应</b>：暴露规则 id 等于教人绕过）*/
    public static final String R_INTENT = "R_INTENT";

    /** 规则 id：否定生存意愿 */
    public static final String R_NO_WILL = "R_NO_WILL";

    /** 规则 id：计划或进行中的行为 */
    public static final String R_PLAN = "R_PLAN";

    /** 规则 id：自伤行为 */
    public static final String R_SELF_HARM = "R_SELF_HARM";

    /** 规则 id：第三人称风险 + 明确求助意图（Q1b 裁定 ②）*/
    public static final String R_THIRD_PARTY_HELP = "R_THIRD_PARTY_HELP";

    /** R1 意愿：想 / 要 / 打算 / 准备 / 决定 / 考虑 / 计划 + 风险对象 */
    private static final Pattern P_INTENT = Pattern.compile(
            "(想|要|打算|准备|决定|考虑|计划)(自杀|轻生|结束自己的?生命|去死|死)");

    /** R2 否定生存：不想活 / 活不下去 / 活着没意思 / 不如死 */
    private static final Pattern P_NO_WILL = Pattern.compile(
            "(不想|不愿|不要)(再)?活(下去|了|着|着没意思|着没有意思)?"
                    + "|活不下去|活着没(有)?意思|不如死(了|掉)?");

    /** R3 计划或进行：时间/状态词 + 具体行为 */
    private static final Pattern P_PLAN = Pattern.compile(
            "(已经|正在|马上|刚刚|今晚|现在|待会|等会|明天)(要|想|就)?"
                    + "(自杀|割腕|跳楼|上吊|吞药|吃安眠药)");

    /** R4 自伤行为 */
    private static final Pattern P_SELF_HARM = Pattern.compile("(自残|自伤|割腕|划手腕)");

    /**
     * 第三人称风险内容。
     *
     * <p>注意：<b>它本身不触发</b>，只用于「移除片段」或与求助意图组合判定。
     * 这正是用户强调的「第三人称风险内容本身不触发」。
     */
    private static final Pattern P_THIRD_PARTY = Pattern.compile(
            "(朋友|同学|室友|同事|他|她|网上|有人|别人|新闻|报道|文章|电影|视频)"
                    + "[^。！？!?]{0,8}?(想|要|说|讨论|提到|有)[^。！？!?]{0,3}?(自杀|轻生|自伤|自残)");

    /**
     * 否定片段（N1）。
     *
     * <p>只否定<b>紧随其后的风险词</b>，不否定整句 —— 见类注释的 E8 用例。
     */
    private static final Pattern P_NEGATION = Pattern.compile(
            "(不会|没有|没|从未|从来没有|不再|不至于|不可能|不想|不打算|不准备|不计划)"
                    + "[^。！？!?]{0,4}?(自杀|轻生|自伤|自残|割腕|跳楼)");

    /**
     * 明确求助意图（Q1b）。
     *
     * <p><b>刻意不含「想了解」「好奇」</b> —— 否则「网上有人讨论自杀，我想了解一下」
     * 会被误判成求助，重新退化为关键词扫描。
     */
    private static final Pattern P_HELP_SEEKING = Pattern.compile(
            "怎么办|咋办|怎么帮|如何帮|帮帮|救救|我很担心|我担心|该做什么|能做什么|怎么劝|如何劝");

    /**
     * 判定一次用户输入。
     *
     * @param rawMessage 用户原文（可能为 {@code null}）
     * @return 命中结果；{@link SafetyDecision#none()} 表示未命中
     */
    public SafetyDecision evaluate(String rawMessage) {
        String text = normalize(rawMessage);
        if (text.isEmpty()) {
            return SafetyDecision.none();
        }

        boolean thirdPartyRisk = P_THIRD_PARTY.matcher(text).find();
        boolean helpSeeking = P_HELP_SEEKING.matcher(text).find();

        // Q1b 裁定 ②：第三人称风险内容【本身】不触发；
        // 与明确求助意图【同时出现】时才触发（真实需求是资源）。
        if (thirdPartyRisk && helpSeeking) {
            return new SafetyDecision(true, R_THIRD_PARTY_HELP);
        }

        String remaining = text;
        if (thirdPartyRisk) {
            // 纯叙述/讨论他人 ⇒ 移除该片段，避免「朋友想自杀」被当成用户本人风险
            remaining = P_THIRD_PARTY.matcher(remaining).replaceAll("");
        }
        remaining = P_NEGATION.matcher(remaining).replaceAll("");

        if (P_INTENT.matcher(remaining).find()) {
            return new SafetyDecision(true, R_INTENT);
        }
        if (P_NO_WILL.matcher(remaining).find()) {
            return new SafetyDecision(true, R_NO_WILL);
        }
        if (P_PLAN.matcher(remaining).find()) {
            return new SafetyDecision(true, R_PLAN);
        }
        if (P_SELF_HARM.matcher(remaining).find()) {
            return new SafetyDecision(true, R_SELF_HARM);
        }
        return SafetyDecision.none();
    }

    /**
     * 归一化：{@link Normalizer.Form#NFKC} + 去所有空白。
     *
     * <p>去空白是为了覆盖「自 杀」这类拆分写法；
     * <b>刻意不做</b>拼音/谐音/外语扩展 —— 那会带来不可控的误报与维护负担，
     * 属已知限制（见 {@code PHASE4-ROUND3-DESIGN.md}）。
     */
    static String normalize(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        return Normalizer.normalize(rawMessage, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * 单次判定结果。
     *
     * @param triggered 是否命中
     * @param ruleId    命中的规则 id（<b>只用于日志</b>；未命中时为 {@code null}）
     */
    public record SafetyDecision(boolean triggered, String ruleId) {

        static SafetyDecision none() {
            return new SafetyDecision(false, null);
        }
    }
}
