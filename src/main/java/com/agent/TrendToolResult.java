package com.agent;

import java.util.List;

/**
 * T5 {@code getMyAssessmentTrend} 的返回契约（Phase 4 / Round 2）。
 *
 * <p><b>为什么必须归一化</b>：不同试卷满分不同（实测存在 100 分卷与 10 分卷）。
 * 原始分<b>不可跨卷比较</b>：60/100 与 8/10 若按原始分会判成「下降」，实际是 60% → 80%。
 * 因此所有比较与趋势判定一律基于 {@link TrendPoint#scoreRate()}。
 *
 * <p><b>窗口与有效性（两条不同的量）</b>：
 * <pre>
 * windowSize    = 从数据库取到的原始条数（≤ MAX_RECORDS）
 * excludedCount = 无法归一化的条数（试卷缺失 / 满分非法 / 分数为空）
 * validCount    = windowSize − excludedCount      ← 只有它决定是否 INSUFFICIENT
 * </pre>
 * ⚠️ 「库里有 10 条」**不等于**「有足够样本算趋势」：
 * 10 条中 9 条试卷缺失 ⇒ validCount = 1 ⇒ {@link #TREND_INSUFFICIENT}。
 *
 * <p><b>趋势阈值</b>：{@code deltaPp = (lastRate − firstRate) × 100}（百分点）
 * <pre>
 * deltaPp ≥ +5  → UP
 * deltaPp ≤ −5  → DOWN
 * 其余          → FLAT
 * </pre>
 *
 * <p><b>刻意不返回</b>：原始计算过程、试题内容、学生 id。
 * <b>刻意不做</b>：预测下次分数、判断严重程度、给诊断结论。
 *
 * @param items     按时间升序的样本点（含被排除项，不静默丢弃）
 * @param summary   统计结论
 * @param total     本人记录总数（业务事实）
 * @param truncated 是否因窗口上限而未纳入全部记录
 */
public record TrendToolResult(List<TrendPoint> items,
                              TrendSummary summary,
                              long total,
                              boolean truncated) {

    public static final String TREND_UP = "UP";
    public static final String TREND_DOWN = "DOWN";
    public static final String TREND_FLAT = "FLAT";
    public static final String TREND_INSUFFICIENT = "INSUFFICIENT";

    /** 排除原因：试卷不存在 */
    public static final String REASON_PAPER_MISSING = "paper_missing";
    /** 排除原因：试卷满分为空或 ≤ 0，无法归一化 */
    public static final String REASON_PAPER_SCORE_INVALID = "paper_score_invalid";
    /** 排除原因：本次记录分数为空 */
    public static final String REASON_SCORE_MISSING = "score_missing";

    /**
     * @param date           提交日期（{@code yyyy-MM-dd}，取数据库中的 submitted_at）
     * @param paperName      试卷名；试卷不存在时「未知试卷」
     * @param score          原始分（仅供展示，**不参与比较**）
     * @param maxScore       试卷满分（可能为 null）
     * @param scoreRate      得分率 0~1；无法计算时 null
     * @param excluded       是否被排除出统计
     * @param excludedReason 排除原因；未排除时 null
     */
    public record TrendPoint(String date,
                             String paperName,
                             Integer score,
                             Integer maxScore,
                             Double scoreRate,
                             boolean excluded,
                             String excludedReason) {
    }

    /**
     * @param windowSize    原始窗口条数
     * @param validCount    有效样本数（= n，决定 INSUFFICIENT）
     * @param excludedCount 被排除条数
     * @param firstRate     第一个有效样本得分率
     * @param lastRate      最后一个有效样本得分率
     * @param deltaPp       百分点差值 {@code (lastRate−firstRate)×100}；样本不足时为 null
     * @param averageRate   有效样本平均得分率
     * @param trend         UP / DOWN / FLAT / INSUFFICIENT
     */
    public record TrendSummary(int windowSize,
                               int validCount,
                               int excludedCount,
                               Double firstRate,
                               Double lastRate,
                               Double deltaPp,
                               Double averageRate,
                               String trend) {
    }

    /** 成功 / 空结果统一入口；{@code truncated} 由总数与返回条数推导 */
    public static TrendToolResult ok(List<TrendPoint> items, TrendSummary summary, long total) {
        return new TrendToolResult(items, summary, total, total > items.size());
    }
}
