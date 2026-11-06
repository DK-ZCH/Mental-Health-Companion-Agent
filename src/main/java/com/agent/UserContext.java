package com.agent;

import java.util.List;

/**
 * Step 23：当前登录用户的业务上下文（不可变聚合结果）。
 *
 * <p><b>范围（用户 2026-11-05 裁定 · B 方案）</b>：只承载「客观业务上下文」——
 * 最近测评、历史趋势（Java 已算结果）、近期预约（现有三种状态）、咨询留言（双方）、
 * 错题薄弱点摘要。刻意<b>不设</b>以下分段，从类型层面杜绝范围回潮：
 * <ul>
 *   <li>{@code basicInfo}：不纳入姓名/用户名/年龄/性别/手机/身份证/邮箱/密码；</li>
 *   <li>{@code favorites}：文章收藏暂不实现，不用咨询师收藏代替；</li>
 *   <li>{@code feedback}：咨询师咨询后总结暂不实现；{@code review_comment} 是预约审批意见；</li>
 *   <li>{@code agentMessage}：会话历史，不是主动业务记录；</li>
 *   <li>日记 / 心情 / 状态打卡 / 用户画像：不新增。</li>
 * </ul>
 *
 * <p><b>不可变性</b>：本类及其全部分段均为 record，集合由 {@link List#copyOf} 复制，
 * 构建完成后不可修改。{@link ContextMeta} 只用于服务端诊断，不注入模型文本。
 *
 * <p><b>边界</b>：Step 23 只解决「系统掌握哪些可靠的用户业务上下文」；
 * 个性化分析、心理状态推断、变化趋势重算均属 Step 24，不在本类实现。
 */
public record UserContext(
        AssessmentSection assessments,
        TrendSection trend,
        AppointmentSection appointments,
        MessageSection messages,
        WrongPointSection wrongPoints,
        ContextMeta meta
) {

    /** 最近测评：直接复用 {@code com.agent.AssessmentRecordView}（3 字段，无日期）。 */
    public record AssessmentSection(List<AssessmentRecordView> records, long total, boolean truncated) {
        public AssessmentSection {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    /** 历史趋势：只承载 Java 已计算的 {@link TrendToolResult.TrendSummary}，不重算、不带明细。 */
    public record TrendSection(TrendToolResult.TrendSummary summary, long total, boolean truncated) {
    }

    /** 近期预约：状态为已翻译的中文标签（待审核 / 同意 / 拒绝）。 */
    public record AppointmentSection(List<AppointmentToolResult.Item> items, long total, boolean truncated) {
        public AppointmentSection {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** 咨询留言：用户留言与咨询师回复均为不可信业务文本（渲染时逐处转义）。 */
    public record MessageSection(List<MessageToolResult.Item> items, long total, boolean truncated) {
        public MessageSection {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** 错题薄弱点：沿用 {@code WrongQuestionTools} 的分组、计数与排序结果。 */
    public record WrongPointSection(List<WrongQuestionToolResult.Item> items,
                                    long totalPapers,
                                    long totalWrongQuestions,
                                    boolean truncated) {
        public WrongPointSection {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** 构建元信息：只记数据源 id 与是否被裁剪，不记用户原文、不记身份标识（P1）。 */
    public record ContextMeta(List<String> degradedSources, boolean clipped) {
        public ContextMeta {
            degradedSources = degradedSources == null ? List.of() : List.copyOf(degradedSources);
        }
    }

    /** 五个分段全无数据 ⇒ 不注入 USER_CONTEXT 段（与 ⑦a S9 同口径）。 */
    public boolean isEmpty() {
        return (assessments == null || assessments.records().isEmpty())
                && (trend == null || trend.summary() == null)
                && (appointments == null || appointments.items().isEmpty())
                && (messages == null || messages.items().isEmpty())
                && (wrongPoints == null || wrongPoints.items().isEmpty());
    }
}
