package com.agent;

import java.util.List;

/**
 * 测评记录查询结果（Phase 4 / Step 11）。
 *
 * <p><b>核心设计：绝不静默截断</b>。
 * 若用户有 12 条记录而单次只返回 10 条，模型必须能说出
 * 「共 12 条，这里先展示最近 10 条」，而不是错误地回答「你有 10 条」。
 * 因此返回体必须携带 {@code total} 与 {@code truncated}。
 *
 * <p><b>注意区分两个概念</b>：
 * <ul>
 *   <li>{@code MAX_RECORDS}（见 {@link AssessmentTools}）= <b>单次返回上限</b>（工程约束，防 token 膨胀）；</li>
 *   <li>{@code total} = <b>该用户真实记录总数</b>（业务事实）。</li>
 * </ul>
 * 二者不可混为一谈。
 *
 * @param records   本次返回的记录（最多 {@code MAX_RECORDS} 条）
 * @param total     该用户真实记录总数
 * @param truncated 是否因上限而未返回全部（{@code total > records.size()}）
 * @param error     失败原因；成功时为 {@code null}
 */
public record AssessmentRecordToolResult(
        List<AssessmentRecordView> records,
        long total,
        boolean truncated,
        String error
) {

    /** 成功：按实际返回条数自动推导 {@code truncated} */
    public static AssessmentRecordToolResult ok(List<AssessmentRecordView> records, long total) {
        return new AssessmentRecordToolResult(records, total, total > records.size(), null);
    }

    /**
     * 失败：<b>不查库</b>、不返回任何记录。
     *
     * <p>用于「无法确认当前用户身份」等 fail-closed 场景。
     * 刻意<b>不抛异常</b> —— 避免异常信息被回喂给模型。
     */
    public static AssessmentRecordToolResult denied(String reason) {
        return new AssessmentRecordToolResult(List.of(), 0L, false, reason);
    }
}
