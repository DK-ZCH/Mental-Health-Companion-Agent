package com.agent;

import com.entity.CounselorEntity;
import com.entity.CounselorFavoriteEntity;
import com.service.CounselorFavoriteService;
import com.service.CounselorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * T6：我收藏的咨询师（Phase 4 / Round 2）。
 *
 * <p><b>与 T4 的区别</b>：T4 是「平台有哪些老师」（公共资源）；
 * T6 是「<b>我</b>关注了哪些老师」（当前用户私有关系）。
 *
 * <p><b>聚合语义</b>：按 {@code counselorId} 去重 —— 一位老师一个 Item，
 * 因此 {@code favoriteCount} 才有意义；不会把收藏流水逐条抛给模型。
 *
 * <p>🔴 <b>为什么是 {@code favoriteTypes} 列表</b>：
 * 实测 {@code counselor_favorite} <b>只有 PRIMARY(id) 索引</b>，
 * <b>没有 (student_id, counselor_id) 唯一约束</b> ⇒ 不能假设「一位老师只有一种类型」。
 * 出现两种就如实返回两种，而不是擅自选一个。
 *
 * <p><b>字段白名单</b>：{@code CounselorEntity} 含 {@code password/phone/email/username/avatarUrl}，
 * 因此 SQL 层只 select {@code id/name/expertise} —— 不做「先全量取回再遮挡」。
 */
public class FavoriteTools {

    private static final Logger logger = LoggerFactory.getLogger(FavoriteTools.class);

    /** 最多返回多少位老师（聚合后） */
    static final int MAX_RECORDS = 10;

    private static final String UNKNOWN_COUNSELOR = "未知咨询师";
    private static final String DICT_FAVORITE_TYPE = "counselor_favorite_type";

    private final CounselorFavoriteService favoriteService;
    private final CounselorService counselorService;
    private final AgentDictLabels labels;

    public FavoriteTools(CounselorFavoriteService favoriteService,
                         CounselorService counselorService,
                         AgentDictLabels labels) {
        this.favoriteService = favoriteService;
        this.counselorService = counselorService;
        this.labels = labels;
    }

    /**
     * 查询当前学生自己收藏的咨询师（按老师聚合）。
     *
     * <p>⚠️ 签名无身份参数；不返回任何咨询师 PII。
     */
    @Tool(description = "查询当前登录学生自己收藏的咨询师。"
            + "按咨询师聚合返回姓名、擅长领域、收藏类型与最近收藏时间。"
            + "不返回任何联系方式。无需任何参数。")
    public FavoriteToolResult getMyFavorites(ToolContext toolContext) {
        AgentIdentity identity = AgentToolGuard.requireStudent(toolContext);
        int userId = identity.userId();

        List<CounselorFavoriteEntity> rows = favoriteService.lambdaQuery()
                .select(CounselorFavoriteEntity::getId,
                        CounselorFavoriteEntity::getCounselorId,
                        CounselorFavoriteEntity::getFavoriteType,
                        CounselorFavoriteEntity::getFavoritedAt)
                .eq(CounselorFavoriteEntity::getStudentId, userId)
                .orderByDesc(CounselorFavoriteEntity::getFavoritedAt)
                .orderByDesc(CounselorFavoriteEntity::getId)
                .list();

        if (rows.isEmpty()) {
            logger.debug("[AGENT][TOOL] getMyFavorites userId={} total=0", userId);
            return FavoriteToolResult.ok(List.of(), 0L);
        }

        // 按老师聚合：顺序 = 首次出现顺序（已按收藏时间降序 ⇒ 最近收藏的老师在前）
        Map<Integer, Aggregate> byCounselor = new LinkedHashMap<>();
        for (CounselorFavoriteEntity row : rows) {
            Integer counselorId = row.getCounselorId();
            if (counselorId == null) {
                // 无法指代老师，跳过（不编造名字）；不影响其他记录
                continue;
            }
            Aggregate agg = byCounselor.computeIfAbsent(counselorId, key -> new Aggregate());
            agg.count++;
            String label = labels.label(DICT_FAVORITE_TYPE, row.getFavoriteType());
            if (label != null) {
                agg.types.add(label);   // LinkedHashSet：去重且保持「最近优先」顺序
            }
            if (agg.lastFavoritedAt == null && row.getFavoritedAt() != null) {
                agg.lastFavoritedAt = row.getFavoritedAt();
            }
        }

        if (byCounselor.isEmpty()) {
            return FavoriteToolResult.ok(List.of(), 0L);
        }

        long total = byCounselor.size();
        List<Integer> counselorIds = new ArrayList<>(byCounselor.keySet());
        List<Integer> shown = counselorIds.size() > MAX_RECORDS
                ? counselorIds.subList(0, MAX_RECORDS)
                : counselorIds;

        Map<Integer, CounselorEntity> counselorMap = loadCounselors(shown);
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

        List<FavoriteToolResult.Item> items = new ArrayList<>(shown.size());
        for (Integer counselorId : shown) {
            Aggregate agg = byCounselor.get(counselorId);
            CounselorEntity counselor = counselorMap.get(counselorId);
            items.add(new FavoriteToolResult.Item(
                    counselorId,
                    counselor == null ? UNKNOWN_COUNSELOR : counselor.getName(),
                    counselor == null ? null : counselor.getExpertise(),
                    new ArrayList<>(agg.types),
                    agg.count,
                    agg.lastFavoritedAt == null ? null : timeFormat.format(agg.lastFavoritedAt)
            ));
        }

        logger.debug("[AGENT][TOOL] getMyFavorites userId={} totalDistinct={} returned={}",
                userId, total, items.size());
        return FavoriteToolResult.ok(items, total);
    }

    /** 只取 id + name + expertise：绝不 select password/phone/email/username/avatar_url */
    private Map<Integer, CounselorEntity> loadCounselors(List<Integer> counselorIds) {
        if (counselorIds.isEmpty()) {
            return Map.of();
        }
        Set<Integer> distinct = new HashSet<>(counselorIds);
        Map<Integer, CounselorEntity> map = new HashMap<>(distinct.size());
        for (CounselorEntity counselor : counselorService.lambdaQuery()
                .select(CounselorEntity::getId,
                        CounselorEntity::getName,
                        CounselorEntity::getExpertise)
                .in(CounselorEntity::getId, distinct)
                .list()) {
            map.put(counselor.getId(), counselor);
        }
        return map;
    }

    /** 聚合中间态 */
    private static final class Aggregate {
        private int count;
        private final Set<String> types = new LinkedHashSet<>();
        private Date lastFavoritedAt;
    }
}
