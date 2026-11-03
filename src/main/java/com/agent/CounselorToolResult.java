package com.agent;

import java.util.List;

/**
 * T4 {@code listAvailableCounselors} 的返回契约（Phase 4 / Round 1）。
 *
 * <p><b>这属于「公开资源」而非「用户私有数据」</b>：
 * 它<b>不</b>采用 {@code WHERE student_id = 当前用户} 的行级过滤，
 * 而是依靠<b>Agent 身份域白名单</b>限制调用者（仅学生域）。
 * 这正是本批要证明的第二种访问模式：公开数据 + 域限制。
 *
 * <p>🔴 <b>字段白名单（必须与 SQL 的 select 一一对应）</b>：
 * 只返回 {@code id / name / genderLabel / expertise / resume / introduction}。
 * {@code CounselorEntity} 本身含 {@code password / phone / email / username / avatarUrl}，
 * 因此 <b>禁止</b> 用 {@code list()} 全量取回后再过滤 —— 那会把 PII 载入内存。
 *
 * <p>{@code avatarUrl} 刻意不返回：模型不需要图片地址，返回只会增加 token 与无关噪声。
 * 前端需要头像时走的是 {@code /counselor/list}，与本 Tool 契约无关。
 *
 * @param items     本次返回的咨询师（最多 {@code MAX_RECORDS} 条）
 * @param total     咨询师总数
 * @param truncated 是否因上限而未返回全部
 */
public record CounselorToolResult(List<Item> items, long total, boolean truncated) {

    /**
     * @param id           咨询师 id（仅用于让模型稳定指代同一位老师，不含任何 PII）
     * @param name         姓名
     * @param genderLabel  性别标签（如 {@code 男}）
     * @param expertise    擅长领域
     * @param resume       简介/履历
     * @param introduction 详细介绍
     */
    public record Item(Integer id,
                       String name,
                       String genderLabel,
                       String expertise,
                       String resume,
                       String introduction) {
    }

    /** 成功 / 空结果统一入口 */
    public static CounselorToolResult ok(List<Item> items, long total) {
        return new CounselorToolResult(items, total, total > items.size());
    }
}
