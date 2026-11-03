package com.regression;

import com.agent.AgentIdentity;
import com.agent.FavoriteToolResult;
import com.agent.FavoriteTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 / Round 2：T6 {@code getMyFavorites} 的<b>聚合与边界</b>。
 *
 * <p><b>种子数据</b>：student1 收藏老师 {1,2}；student2 收藏 {2,3}；student3 收藏 {1,2}。
 *
 * <p><b>为什么 {@code favoriteTypes} 是列表</b>：数据库实测
 * {@code counselor_favorite} 只有 PRIMARY(id)，<b>没有 (student_id, counselor_id) 唯一约束</b>
 * ⇒ 代码不能假设「一位老师只有一种类型」。出现两种就如实返回两种。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@DisplayName("Phase4 T6 我的收藏")
class Phase4FavoriteToolTest {

    private static final int STUDENT_1 = 1;
    private static final int STUDENT_2 = 2;
    private static final int STUDENT_3 = 3;

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "phone", "email", "username", "password", "avatarUrl", "studentId"
    );

    @Autowired
    private FavoriteTools favoriteTools;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    // ================== A. 域拒绝 ==================

    @Test
    @DisplayName("A1. 🔴 counselor / admin / 非法上下文 全部抛异常")
    void domainDenied() {
        assertThatThrownBy(() -> favoriteTools.getMyFavorites(ctx(1, "counselor")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> favoriteTools.getMyFavorites(ctx(1, "admin_user")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> favoriteTools.getMyFavorites(new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================== B. 行范围 ==================

    @Test
    @DisplayName("B1. 🔴 两个学生的收藏集合不串读（各自只含本人收藏的老师）")
    void rowScopeIsPerStudent() {
        FavoriteToolResult s1 = favoriteTools.getMyFavorites(student(STUDENT_1));
        FavoriteToolResult s2 = favoriteTools.getMyFavorites(student(STUDENT_2));

        List<Integer> ids1 = s1.items().stream().map(FavoriteToolResult.Item::counselorId).collect(Collectors.toList());
        List<Integer> ids2 = s2.items().stream().map(FavoriteToolResult.Item::counselorId).collect(Collectors.toList());

        assertThat(ids1).containsExactlyInAnyOrder(1, 2);
        assertThat(ids2).containsExactlyInAnyOrder(2, 3);
        assertThat(ids1).as("student1 没有收藏老师 3").doesNotContain(3);
        assertThat(ids2).as("student2 没有收藏老师 1").doesNotContain(1);
    }

    @Test
    @DisplayName("B2. total 与数据库中该学生的去重老师数一致")
    void totalMatchesDistinctCounselorCount() {
        int expected1 = distinctCounselors(STUDENT_1);
        int expected2 = distinctCounselors(STUDENT_2);

        assertThat(favoriteTools.getMyFavorites(student(STUDENT_1)).total()).isEqualTo(expected1);
        assertThat(favoriteTools.getMyFavorites(student(STUDENT_2)).total()).isEqualTo(expected2);
    }

    // ================== U. 聚合 ==================

    @Test
    @Transactional
    @DisplayName("U1. 🔴 同一老师收藏 3 次 → 聚合成 1 个 Item，favoriteCount=3")
    void sameCounselorIsAggregated() {
        insertFavorite(9500, STUDENT_2, 1, 1, "2026-01-01 10:00:00");
        insertFavorite(9501, STUDENT_2, 1, 1, "2026-02-01 10:00:00");
        insertFavorite(9502, STUDENT_2, 1, 1, "2026-03-01 10:00:00");

        FavoriteToolResult result = favoriteTools.getMyFavorites(student(STUDENT_2));

        FavoriteToolResult.Item teacher1 = result.items().stream()
                .filter(item -> item.counselorId() == 1)
                .findFirst()
                .orElseThrow();

        assertThat(teacher1.favoriteCount()).isEqualTo(3);
        assertThat(teacher1.favoriteTypes()).as("同类型重复收藏不得产生重复标签").containsExactly("收藏");
        assertThat(teacher1.lastFavoritedAt()).as("取最近一次").startsWith("2026-03-01");
        assertThat(result.total()).as("去重后老师数 = {1,2,3}").isEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("U2. 🔴 同一老师出现两种收藏类型 → 如实返回两个标签（不得只选一个）")
    void multipleFavoriteTypesArePreserved() {
        insertFavorite(9510, STUDENT_2, 1, 1, "2026-01-01 10:00:00");
        insertFavorite(9511, STUDENT_2, 1, 2, "2026-02-01 10:00:00");

        FavoriteToolResult.Item teacher1 = favoriteTools.getMyFavorites(student(STUDENT_2)).items().stream()
                .filter(item -> item.counselorId() == 1)
                .findFirst()
                .orElseThrow();

        assertThat(teacher1.favoriteTypes()).hasSize(2);
        assertThat(teacher1.favoriteTypes()).contains("收藏");
        assertThat(teacher1.favoriteCount()).isEqualTo(2);
    }

    // ================== C / D ==================

    @Test
    @DisplayName("C1. 🔴 返回契约不含 consultor PII 与 studentId")
    void resultHasNoPii() throws Exception {
        Set<String> hit = new HashSet<>(jsonKeys(favoriteTools.getMyFavorites(student(STUDENT_1))));
        hit.retainAll(FORBIDDEN_KEYS);

        assertThat(hit).as("T6 必须白名单返回，不得带出联系方式/凭据/归属 id").isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("D1. 无收藏 → total=0、items 为空、不抛异常")
    void emptyResultIsNormal() {
        jdbcTemplate.update("DELETE FROM counselor_favorite WHERE student_id = ?", STUDENT_3);

        FavoriteToolResult result = favoriteTools.getMyFavorites(student(STUDENT_3));

        assertThat(result.total()).isZero();
        assertThat(result.items()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    // ================== helpers ==================

    private ToolContext student(int userId) {
        return ctx(userId, AgentIdentity.TABLE_STUDENT);
    }

    private ToolContext ctx(int userId, String table) {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, userId,
                AgentIdentity.CTX_USER_TABLE, table));
    }

    private void insertFavorite(int id, int studentId, int counselorId, int type, String favoritedAt) {
        jdbcTemplate.update(
                "INSERT INTO counselor_favorite (id, counselor_id, student_id, favorite_type, favorited_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                id, counselorId, studentId, type, favoritedAt);
    }

    private int distinctCounselors(int studentId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT counselor_id) FROM counselor_favorite WHERE student_id = ?",
                Integer.class, studentId);
        return value == null ? 0 : value;
    }

    private Set<String> jsonKeys(Object value) throws Exception {
        JsonNode node = objectMapper.valueToTree(value);
        Set<String> keys = new HashSet<>();
        collect(node, keys);
        return keys;
    }

    private void collect(JsonNode node, Set<String> keys) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(keys::add);
            node.elements().forEachRemaining(child -> collect(child, keys));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collect(child, keys));
        }
    }
}
