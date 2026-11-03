package com.agent;

/**
 * 预约「预览」结果（Phase 4 / Round 4）。
 *
 * <p><b>预览阶段不写库</b>：本结果只携带摘要与确认凭证；
 * 真正的副作用发生在 {@link AppointmentBookingTools#confirmAppointment}。
 *
 * @param success         是否成功生成预览
 * @param message         给模型/用户看的文本（失败时说明原因）
 * @param token           confirmation token（失败时为 {@code null}）
 * @param expiresInSeconds 凭证有效期
 */
public record AppointmentPreviewResult(boolean success, String message, String token, int expiresInSeconds) {

    public static AppointmentPreviewResult ok(String message, String token, int ttlSeconds) {
        return new AppointmentPreviewResult(true, message, token, ttlSeconds);
    }

    public static AppointmentPreviewResult fail(String message) {
        return new AppointmentPreviewResult(false, message, null, 0);
    }
}
