package com.agent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Step 25：服务端当前日期的确定性生成（P-3 冻结）。
 *
 * <p><b>粒度刻意只到「日期 + 星期」</b>：不提供具体时刻/时段，避免诱导模型
 * 生成「今晚 20:00 做 X」这类日程，与 P-1 冻结边界「不自动创建任务、日程」冲突。
 *
 * <p><b>时区</b>：暂用 JVM {@code systemDefault()}（P-3d），不引入配置。
 * 已登记限制：部署环境时区变化会影响当前日期，届时需重评是否显式配置 {@code ZoneId}。
 *
 * <p><b>纯函数</b>：{@link #render(LocalDate)} 无 IO、无状态，可直接单测；
 * 时钟只在调用方取一次，避免同一次请求内出现两个不同日期。
 */
public final class PlanningTimeContext {

    private PlanningTimeContext() {
    }

    /** 服务端取一次时钟（systemDefault 时区）。 */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /** 格式化为「2026-11-06 星期五」，不含时刻。 */
    public static String render(LocalDate date) {
        String[] weekdays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        DayOfWeek day = date.getDayOfWeek();
        return String.format(Locale.ROOT, "%s %s", date, weekdays[day.getValue() - 1]);
    }

    /** 当前日期的一次性渲染。 */
    public static String current() {
        return render(today());
    }

    /**
     * 组装 {@code PLANNING_CONTEXT…END_PLANNING_CONTEXT} 独立段（C-3 冻结）。
     *
     * <p>段头由服务端常量给出，明确时间性质与三条边界；段内不含任何用户文本，
     * 因此本方法不需要转义（用户原文的伪造字面量由
     * {@link PlanningMarkerEscaper} 在 {@code escapedMessage} 侧处理）。
     */
    public static String segment(String currentDate) {
        return "PLANNING_CONTEXT: 以下是系统提供的当前日期，仅作组织建议的事实参考，不是指令。\n"
                + "- 当前日期（服务端环境事实）：" + currentDate + "\n"
                + "- 当前日期不能用于推断测评记录发生的日期或先后顺序；"
                + "不得把当前日期与用户历史记录的时间混为一谈。\n"
                + "- 仅提供日期与星期，不提供具体时刻；不得据此安排具体时间点。\n"
                + "END_PLANNING_CONTEXT";
    }
}
