package com.agent;

import com.entity.AssessmentPaperEntity;
import com.entity.AssessmentRecordEntity;
import com.service.AssessmentPaperService;
import com.service.AssessmentRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * T5：测评得分趋势（Phase 4 / Round 2）。
 *
 * <p><b>为什么这一批必须把规则写死在代码里</b>：
 * 「上升 / 下降 / 持平」是<b>计算出来的结论</b>，不是数据库里现成的字段。
 * 若把原始分数交给模型「让它自己看趋势」，会不稳定、不可复核，
 * 而且会把<b>不同满分的试卷</b>直接比大小。
 *
 * <p><b>三条核心规则</b>（与实施清单一一对应）：
 * <ol>
 *   <li><b>归一化</b>：{@code rate = score / paper.totalScore}。
 *       实测存在 100 分卷与 10 分卷 —— 60/100 与 8/10 若按原始分会判成「下降」（8−60=−52），
 *       实际是 60% → 80% <b>上升</b>。</li>
 *   <li><b>n 是有效样本数</b>：{@code n = windowSize − excludedCount}。
 *       10 条里 9 条试卷缺失 ⇒ n=1 ⇒ INSUFFICIENT。
 *       「库里有 10 条」不等于「有足够样本算趋势」。</li>
 *   <li><b>排序稳定</b>：先 {@code submitted_at DESC, id DESC} 取最近 10 条，
 *       再按 {@code ASC, id ASC} 重排计算。{@code submitted_at} 是秒级 timestamp，
 *       同秒记录若无 tie-breaker，首尾可能互换 ⇒ 趋势翻转。</li>
 * </ol>
 *
 * <p><b>阈值</b>：{@code deltaPp = (lastRate − firstRate) × 100}（百分点）
 * ⇒ {@code >= +5 UP} / {@code <= -5 DOWN} / 其余 FLAT。
 * 用 {@code >=} / {@code <=}，不用浮点相等。
 *
 * <p>⚠️ <b>已知数据模型局限（本批不改）</b>：{@code assessment_record.submitted_at}
 * 带 {@code ON UPDATE CURRENT_TIMESTAMP}，且存在管理端 {@code /update} ——
 * 编辑记录会改变时间顺序。因此本 Tool 排序依据是「数据库当前记录的 submitted_at」，
 * <b>不得</b>对外声称是「用户真实完成测评的绝对时间」。
 */
public class TrendTools {

    private static final Logger logger = LoggerFactory.getLogger(TrendTools.class);

    /** 原始窗口：最近多少条记录参与计算 */
    static final int MAX_RECORDS = 10;

    /** 趋势阈值（百分点）：|deltaPp| 小于它视为 FLAT */
    static final double THRESHOLD_PP = 5.0;

    private static final String UNKNOWN_PAPER = "未知试卷";

    private final AssessmentRecordService recordService;
    private final AssessmentPaperService paperService;

    public TrendTools(AssessmentRecordService recordService, AssessmentPaperService paperService) {
        this.recordService = recordService;
        this.paperService = paperService;
    }

    /**
     * 查询当前学生自己的测评得分趋势。
     *
     * <p>⚠️ 签名无身份参数：身份只来自 {@code ToolContext}（服务端注入，不进模型视野）。
     */
    @Tool(description = "查询当前登录学生自己的测评得分趋势。"
            + "返回最近若干次测评的得分率、平均得分率、首尾差值（百分点）与趋势结论"
            + "（UP 上升 / DOWN 下降 / FLAT 持平 / INSUFFICIENT 样本不足）。"
            + "不要自行从原始分数推断趋势，一律以本工具返回的 trend 为准。无需任何参数。")
    public TrendToolResult getMyAssessmentTrend(ToolContext toolContext) {
        AgentIdentity identity = AgentToolGuard.requireStudent(toolContext);
        int userId = identity.userId();

        long total = recordService.lambdaQuery()
                .eq(AssessmentRecordEntity::getStudentId, userId)
                .count();
        if (total == 0L) {
            logger.debug("[AGENT][TOOL] getMyAssessmentTrend userId={} total=0", userId);
            return TrendToolResult.ok(List.of(),
                    new TrendToolResult.TrendSummary(0, 0, 0, null, null, null, null,
                            TrendToolResult.TREND_INSUFFICIENT), 0L);
        }

        // ① 取「最近 10 条」：DESC + id DESC（id 为稳定 tie-breaker）
        List<AssessmentRecordEntity> rows = recordService.lambdaQuery()
                .select(AssessmentRecordEntity::getId,
                        AssessmentRecordEntity::getPaperId,
                        AssessmentRecordEntity::getTotalScore,
                        AssessmentRecordEntity::getSubmittedAt)
                .eq(AssessmentRecordEntity::getStudentId, userId)
                .orderByDesc(AssessmentRecordEntity::getSubmittedAt)
                .orderByDesc(AssessmentRecordEntity::getId)
                .last("LIMIT " + MAX_RECORDS)   // 常量拼接，无外部输入
                .list();

        // ② 计算前重排为「时间上的首 → 尾」，同样带 id tie-breaker
        // submitted_at 与 id 在库中均为 NOT NULL ⇒ 直接比较，无需 null 处理。
        // 用显式 lambda 而非 Comparator.comparing 方法引用：实体是泛型类，方法引用会引发类型推断歧义。
        rows.sort((left, right) -> {
            int byTime = left.getSubmittedAt().compareTo(right.getSubmittedAt());
            return byTime != 0 ? byTime : left.getId().compareTo(right.getId());
        });

        Map<Integer, AssessmentPaperEntity> paperMap = loadPapers(rows);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        List<TrendToolResult.TrendPoint> items = new ArrayList<>(rows.size());
        List<TrendToolResult.TrendPoint> valid = new ArrayList<>(rows.size());

        for (AssessmentRecordEntity row : rows) {
            AssessmentPaperEntity paper = paperMap.get(row.getPaperId());
            Integer maxScore = paper == null ? null : paper.getTotalScore();
            Double rate = scoreRate(row.getTotalScore(), maxScore);

            boolean excluded = rate == null;
            String reason = excluded ? excludeReason(paper, row.getTotalScore()) : null;

            TrendToolResult.TrendPoint point = new TrendToolResult.TrendPoint(
                    row.getSubmittedAt() == null ? null : dateFormat.format(row.getSubmittedAt()),
                    paper == null ? UNKNOWN_PAPER : paper.getName(),
                    row.getTotalScore(),
                    maxScore,
                    rate,
                    excluded,
                    reason);
            items.add(point);
            if (!excluded) {
                valid.add(point);
            }
        }

        int windowSize = items.size();
        int validCount = valid.size();
        int excludedCount = windowSize - validCount;

        Double firstRate = null;
        Double lastRate = null;
        Double deltaPp = null;
        Double averageRate = null;
        String trend;

        if (validCount >= 2) {
            firstRate = valid.get(0).scoreRate();
            lastRate = valid.get(validCount - 1).scoreRate();
            deltaPp = round2((lastRate - firstRate) * 100.0);
            averageRate = round4(averageRate(valid));
            trend = trendOf(deltaPp);
        } else if (validCount == 1) {
            // 单样本：给出该值，但【不给差值】—— 否则模型会把 0 当成「持平」
            firstRate = valid.get(0).scoreRate();
            lastRate = firstRate;
            averageRate = round4(firstRate);
            trend = TrendToolResult.TREND_INSUFFICIENT;
        } else {
            trend = TrendToolResult.TREND_INSUFFICIENT;
        }

        logger.debug("[AGENT][TOOL] getMyAssessmentTrend userId={} total={} window={} valid={} excluded={} trend={}",
                userId, total, windowSize, validCount, excludedCount, trend);

        TrendToolResult.TrendSummary summary = new TrendToolResult.TrendSummary(
                windowSize, validCount, excludedCount, firstRate, lastRate, deltaPp, averageRate, trend);
        return TrendToolResult.ok(items, summary, total);
    }

    /**
     * 归一化得分率。
     *
     * @return 0~1（4 位小数）；分数为空 / 满分缺失或 ≤0 时返回 {@code null}（该条被排除）
     */
    static Double scoreRate(Integer score, Integer maxScore) {
        if (score == null || maxScore == null || maxScore <= 0) {
            return null;
        }
        return round4(score.doubleValue() / maxScore.doubleValue());
    }

    /** 趋势判定：{@code >= +5} UP / {@code <= -5} DOWN / 其余 FLAT */
    static String trendOf(double deltaPp) {
        if (deltaPp >= THRESHOLD_PP) {
            return TrendToolResult.TREND_UP;
        }
        if (deltaPp <= -THRESHOLD_PP) {
            return TrendToolResult.TREND_DOWN;
        }
        return TrendToolResult.TREND_FLAT;
    }

    private static String excludeReason(AssessmentPaperEntity paper, Integer score) {
        if (paper == null) {
            return TrendToolResult.REASON_PAPER_MISSING;
        }
        if (paper.getTotalScore() == null || paper.getTotalScore() <= 0) {
            return TrendToolResult.REASON_PAPER_SCORE_INVALID;
        }
        return TrendToolResult.REASON_SCORE_MISSING;
    }

    private static double averageRate(List<TrendToolResult.TrendPoint> valid) {
        double sum = 0.0;
        for (TrendToolResult.TrendPoint point : valid) {
            sum += point.scoreRate();
        }
        return sum / valid.size();
    }

    private static Double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /** 批量取试卷名与满分，避免 N+1 */
    private Map<Integer, AssessmentPaperEntity> loadPapers(List<AssessmentRecordEntity> rows) {
        Set<Integer> paperIds = new HashSet<>();
        for (AssessmentRecordEntity row : rows) {
            if (row.getPaperId() != null) {
                paperIds.add(row.getPaperId());
            }
        }
        if (paperIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, AssessmentPaperEntity> map = new HashMap<>(paperIds.size());
        for (AssessmentPaperEntity paper : paperService.lambdaQuery()
                .select(AssessmentPaperEntity::getId,
                        AssessmentPaperEntity::getName,
                        AssessmentPaperEntity::getTotalScore)
                .in(AssessmentPaperEntity::getId, paperIds)
                .list()) {
            map.put(paper.getId(), paper);
        }
        return map;
    }
}
