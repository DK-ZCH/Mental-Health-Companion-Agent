package com.agent;

/**
 * 预约「确认」结果（Phase 4 / Round 4）。
 *
 * <p><b>失败不抛异常</b>：业务性失败（凭证失效、时段已被预约）必须能被模型如实转述，
 * 若抛异常会被 Controller 统一转换成「服务暂时不可用」，用户就看不到真实原因。
 *
 * @param success       是否成功创建
 * @param message       给模型/用户看的文本
 * @param appointmentId 新记录 id（失败时为 {@code null}）
 */
public record AppointmentConfirmResult(boolean success, String message, Integer appointmentId) {

    public static AppointmentConfirmResult ok(String message, Integer appointmentId) {
        return new AppointmentConfirmResult(true, message, appointmentId);
    }

    public static AppointmentConfirmResult fail(String message) {
        return new AppointmentConfirmResult(false, message, null);
    }
}
