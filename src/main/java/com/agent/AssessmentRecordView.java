package com.agent;

/**
 * 测评记录的 <b>LLM-safe 视图</b>（Phase 4 / Step 11）。
 *
 * <p><b>最小必要原则</b>：只保留模型生成自然语言所必需的字段。
 *
 * <table border="1">
 *   <caption>字段取舍</caption>
 *   <tr><th>字段</th><th>保留</th><th>理由</th></tr>
 *   <tr><td>{@code paperName}</td><td>✅</td><td>用户可识别是哪份测评</td></tr>
 *   <tr><td>{@code score}</td><td>✅</td><td>核心信息</td></tr>
 *   <tr><td>{@code paperTotalScore}</td><td>✅</td><td>没有分母则分数无法解释</td></tr>
 *   <tr><td>{@code id} / {@code studentId}</td><td>❌</td><td>内部主键与归属，模型无需知道</td></tr>
 *   <tr><td>{@code createdAt} / {@code submittedAt}</td><td>❌</td><td>Step 11 无需求，后续按需再加</td></tr>
 *   <tr><td>{@code recordNo}</td><td>❌</td><td>对用户无意义的内部编号</td></tr>
 * </table>
 */
public record AssessmentRecordView(
        String paperName,
        Integer score,
        Integer paperTotalScore
) {
}
