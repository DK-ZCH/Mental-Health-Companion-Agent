package com.agent;

import java.util.List;

/**
 * T6 {@code getMyFavorites} 的返回契约（Phase 4 / Round 2）。
 *
 * <p><b>与 T4 的区别（不要混用）</b>：
 * <pre>
 * T4 listAvailableCounselors → 「平台有哪些老师」  （公共资源，按域限制）
 * T6 getMyFavorites          → 「我关注了哪些老师」  （当前用户私有关系）
 * </pre>
 *
 * <p><b>聚合语义（按 counselorId 去重）</b>：最终结果是「一位老师一个 Item」，
 * 而不是把收藏流水逐条返回。因此 {@code favoriteCount} 有意义。
 *
 * <p>🔴 <b>为什么是 {@code favoriteTypes} 列表而不是单一 {@code favoriteTypeLabel}</b>：
 * 数据库实测 {@code counselor_favorite} <b>只有 PRIMARY(id) 索引</b>，
 * <b>没有 (student_id, counselor_id) 唯一约束</b> ⇒ 代码**不能假设**「一位老师只有一种类型」。
 * 一旦将来真的出现两种类型，应<b>如实返回两个</b>，而不是擅自选一个。
 *
 * <p><b>字段白名单</b>：不返回咨询师 {@code phone / email / username / password / avatarUrl}，
 * 也不返回 {@code studentId} 或未聚合的实体对象。
 *
 * @param items     聚合后的老师（按最近收藏时间降序，最多 {@code MAX_RECORDS} 位）
 * @param total     去重后的老师数
 * @param truncated 是否因上限而未返回全部
 */
public record FavoriteToolResult(List<Item> items, long total, boolean truncated) {

    /**
     * @param counselorId     老师 id（用于稳定指代，不含 PII）
     * @param counselorName   姓名；老师不存在时「未知咨询师」
     * @param expertise       擅长领域
     * @param favoriteTypes   去重后的收藏类型标签（顺序稳定：最近收藏的类型在前）
     * @param favoriteCount   当前用户与该老师的收藏记录条数
     * @param lastFavoritedAt 最近一次收藏时间（{@code yyyy-MM-dd HH:mm}）
     */
    public record Item(Integer counselorId,
                       String counselorName,
                       String expertise,
                       List<String> favoriteTypes,
                       int favoriteCount,
                       String lastFavoritedAt) {
    }

    /** 成功 / 空结果统一入口 */
    public static FavoriteToolResult ok(List<Item> items, long total) {
        return new FavoriteToolResult(items, total, total > items.size());
    }
}
