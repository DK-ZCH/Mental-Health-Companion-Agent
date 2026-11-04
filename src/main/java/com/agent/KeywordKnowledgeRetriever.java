package com.agent;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 / Step ⑦（⑦a）：关键词知识检索（零新依赖 · 确定性 · 可审计）。
 *
 * <p><b>算法（全部为可审计常量，无隐藏行为）</b>：
 * <ol>
 *   <li>用 {@link #CATEGORY_KEYWORDS}（6 域词表，对应 Step ⑥ 六个真实分类）找出 Query
 *       命中的分类；无命中 ⇒ 空列表（S6/S8/S9）；</li>
 *   <li>候选 = 命中分类下的全部语料文章（当前 12 篇规模，全量入内存再过滤 —— 可审计优先）；</li>
 *   <li>排序：Query 包含其标题连续子串（≥4 字）者优先（标题精确命中优先，S4/S5 依赖），
 *       其余按 id 升序 —— 同域两篇一起入选（topK=2 恰容纳，设计 §四）；</li>
 *   <li>topK=2；单篇 content 截断 ≤1500 字（⑦a 初始安全上限，非实验最优值）。</li>
 * </ol>
 *
 * <p><b>已知限制（不隐瞒）</b>：词表存在同义覆盖不足（「睡不着」已收录，词表外表述会漏）；
 * 这正是 ⑦b 引入向量检索需先补的失败证据（设计 §五）。
 */
public class KeywordKnowledgeRetriever implements RagRetriever {

    /** topK —— ⑦a 初始安全上限（设计冻结 D3） */
    static final int TOP_K = 2;
    /** 单篇截断上限（字符） */
    static final int PER_ARTICLE_LIMIT = 1500;

    /** 6 域词表（key = knowledge_article.category，与 Step ⑥ 字典一一对应） */
    private static final Map<Integer, String[]> CATEGORY_KEYWORDS = buildKeywords();

    private final JdbcTemplate jdbcTemplate;

    public KeywordKnowledgeRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RagSnippet> retrieve(String query) {
        String q = query == null ? "" : query;
        List<Integer> hitCategories = new ArrayList<>();
        for (Map.Entry<Integer, String[]> e : CATEGORY_KEYWORDS.entrySet()) {
            if (containsAny(q, e.getValue())) {
                hitCategories.add(e.getKey());
            }
        }
        if (hitCategories.isEmpty()) {
            return List.of();
        }

        String inSql = joinPlaceholders(hitCategories.size());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, content FROM knowledge_article WHERE category IN (" + inSql + ") ORDER BY id",
                hitCategories.toArray());

        // 排序：标题被子串命中者优先；其余 id 升序（已由 SQL 保证）
        Map<Long, Map<String, Object>> ordered = new LinkedHashMap<>();
        rows.forEach(r -> ordered.put(((Number) r.get("id")).longValue(), r));
        List<Map<String, Object>> sorted = new ArrayList<>(ordered.values());
        sorted.sort((a, b) -> {
            boolean aTitleHit = titleSubstringHit(q, (String) a.get("title"));
            boolean bTitleHit = titleSubstringHit(q, (String) b.get("title"));
            if (aTitleHit != bTitleHit) {
                return aTitleHit ? -1 : 1;
            }
            return Long.compare(((Number) a.get("id")).longValue(), ((Number) b.get("id")).longValue());
        });

        List<RagSnippet> result = new ArrayList<>();
        for (Map<String, Object> r : sorted) {
            if (result.size() >= TOP_K) {
                break;
            }
            String content = String.valueOf(r.get("content"));
            if (content.length() > PER_ARTICLE_LIMIT) {
                // 截断状态显式标注（用户裁定）：避免模型把不完整内容当作完整文章
                content = content.substring(0, PER_ARTICLE_LIMIT) + "\n…[已截断]";
            }
            result.add(new RagSnippet(((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")), content));
        }
        return result;
    }

    /** Query 是否包含文章标题的连续子串（≥4 字）—— 标题精确命中优先 */
    static boolean titleSubstringHit(String query, String title) {
        if (query == null || title == null) {
            return false;
        }
        int min = Math.min(4, title.length());
        for (int len = title.length(); len >= min; len--) {
            for (int start = 0; start + len <= title.length(); start++) {
                String sub = title.substring(start, start + len);
                if (query.contains(sub)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String[] patterns) {
        for (String p : patterns) {
            if (text.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static String joinPlaceholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    private static Map<Integer, String[]> buildKeywords() {
        Map<Integer, String[]> m = new LinkedHashMap<>();
        // 1 睡眠与作息（01/02）
        m.put(1, new String[]{"失眠", "睡不着", "睡眠", "作息", "熬夜", "早睡", "入睡"});
        // 2 情绪基础（05/06）
        m.put(2, new String[]{"情绪", "低落", "抑郁", "难过", "烦躁", "压力大", "崩溃"});
        // 3 学业与考试压力（03/04）
        m.put(3, new String[]{"考试", "焦虑", "复习", "截止", "拖延", "学业", "论文", " GPA".trim()});
        // 4 人际与沟通（07/08）
        m.put(4, new String[]{"宿舍", "室友", "社交", "人际", "沟通", "矛盾", "发言"});
        // 5 求助与心理咨询（09/10）
        m.put(5, new String[]{"心理咨询", "咨询", "求助", "倾诉", "开口", "咨询师"});
        // 6 危机识别与身边人支持（11/12）
        m.put(6, new String[]{"危机", "身边人", "陪伴", "安慰", "结束生命", "撑不下去"});
        return m;
    }
}
