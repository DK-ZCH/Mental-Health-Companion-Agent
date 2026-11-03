package com.agent;

import java.util.List;

/**
 * T3 {@code getMyWrongQuestionSummary} 的返回契约（Phase 4 / Round 1）。
 *
 * <p><b>职责</b>：回答「我哪块比较薄弱」——<b>按试卷聚合</b>的错误结构，
 * 而不是逐题作答分析。聚合（分组、计数、排序）全部在<b>代码</b>中完成，
 * 不让模型自己算数。
 *
 * <p><b>字段最小化（强约束）</b>：刻意<b>不</b>返回
 * <ul>
 *   <li>{@code answer} / {@code analysis}（正确答案与解析）—— 这属于「逐题分析」能力，</li>
 *   <li>{@code options}（选项原文），</li>
 *   <li>{@code studentAnswer}（学生原始作答）—— 未来若要做「为什么这题总错」，
 *       应设计<b>新的更窄能力</b>，不得偷偷扩大本类字段。</li>
 * </ul>
 *
 * <p><b>两个 total 的区别</b>：
 * <ul>
 *   <li>{@code totalPapers} = 有错题的<b>试卷分组数</b>（与本类的 {@code items} 同量纲），</li>
 *   <li>{@code totalWrongQuestions} = 错题<b>总条数</b>（业务事实，供模型如实描述规模）。</li>
 * </ul>
 * 二者不可混用；{@code truncated} 只针对<b>分组数</b>。
 *
 * @param items               本次返回的试卷分组（最多 {@code MAX_PAPERS} 组）
 * @param totalPapers         有错题的试卷分组总数
 * @param totalWrongQuestions 错题总条数
 * @param truncated           是否因分组上限而未返回全部试卷
 */
public record WrongQuestionToolResult(List<Item> items,
                                      long totalPapers,
                                      long totalWrongQuestions,
                                      boolean truncated) {

    /**
     * @param paperName       试卷名称；试卷已不存在时为「未知试卷」
     * @param wrongCount      该卷错题数
     * @param sampleQuestions 题干样例（最多 {@code MAX_SAMPLE_QUESTIONS} 条，仅题干文本）
     */
    public record Item(String paperName, int wrongCount, List<String> sampleQuestions) {
    }

    /** 成功 / 空结果统一入口；{@code truncated} 由<b>分组数</b>推导 */
    public static WrongQuestionToolResult ok(List<Item> items,
                                             long totalPapers,
                                             long totalWrongQuestions) {
        return new WrongQuestionToolResult(items, totalPapers, totalWrongQuestions,
                totalPapers > items.size());
    }
}
