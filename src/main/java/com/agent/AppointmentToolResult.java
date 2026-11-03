package com.agent;

import java.util.List;

/**
 * T1 {@code getMyAppointments} 的返回契约（Phase 4 / Round 1）。
 *
 * <p><b>字段最小化</b>：只返回「预约发生了什么」——日期、时段、状态、咨询师姓名。
 * 刻意<b>不</b>返回：
 * <ul>
 *   <li>{@code reviewComment}（咨询师内部审核备注，可能含对用户的主观评价）；</li>
 *   <li>{@code appointmentNo}（模型用不到的业务流水号），</li>
 *   <li>{@code studentId}（本人 id 无需暴露给模型），</li>
 *   <li>咨询师 {@code phone / email}（PII）。</li>
 * </ul>
 *
 * <p><b>状态与时段一律是已翻译的中文标签</b>（由 {@link AgentDictLabels} 在代码内完成），
 * 不返回裸数字，避免模型猜测「2 是同意还是拒绝」。
 *
 * @param items     本次返回的预约（最多 {@code MAX_RECORDS} 条）
 * @param total     该学生真实预约总数
 * @param truncated 是否因上限而未返回全部
 */
public record AppointmentToolResult(List<Item> items, long total, boolean truncated) {

    /**
     * @param appointmentDate 预约日期（{@code yyyy-MM-dd}），可能为 {@code null}
     * @param timeSlot        时间段标签（如 {@code 08:00-09:00}）
     * @param status          状态标签（如 {@code 同意}）
     * @param counselorName   咨询师姓名；查不到时为「未知咨询师」
     */
    public record Item(String appointmentDate, String timeSlot, String status, String counselorName) {
    }

    /** 成功 / 空结果统一入口；{@code truncated} 由条数自动推导，禁止静默截断 */
    public static AppointmentToolResult ok(List<Item> items, long total) {
        return new AppointmentToolResult(items, total, total > items.size());
    }
}
