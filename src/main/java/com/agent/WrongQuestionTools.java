package com.agent;

import com.entity.AssessmentPaperEntity;
import com.entity.AssessmentQuestionEntity;
import com.entity.AssessmentWrongQuestionEntity;
import com.service.AssessmentPaperService;
import com.service.AssessmentQuestionService;
import com.service.AssessmentWrongQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T3：当前学生的<b>错题薄弱点摘要</b>（Phase 4 / Round 1）。
 *
 * <p><b>数据形态</b>：用户私有 + 跨表聚合（错题 → 题目 → 试卷）。
 *
 * <p><b>为什么必须由代码聚合</b>：分组、计数、排序都是<b>确定性计算</b>，
 * 交给 LLM 会得到不稳定、不可复核的结论。Tool 只提供结构化事实。
 *
 * <p><b>字段最小化（硬约束）</b>：只取
 * {@code wrong_question(paper_id, question_id)} + {@code question(id, content)}
 * + {@code paper(id, name)}。
 * <b>绝不</b>查询 {@code answer} / {@code analysis} / {@code options} / {@code studentAnswer} ——
 * 一旦需要逐题作答分析，应新增<b>更窄的专用能力</b>，而不是扩大本 Tool。
 */
public class WrongQuestionTools {

    private static final Logger logger = LoggerFactory.getLogger(WrongQuestionTools.class);

    /** 单次返回的<b>试卷分组</b>上限 */
    static final int MAX_PAPERS = 10;

    /** 每组返回的题干样例上限 */
    static final int MAX_SAMPLE_QUESTIONS = 3;

    private static final String UNKNOWN_PAPER = "未知试卷";

    private final AssessmentWrongQuestionService wrongQuestionService;
    private final AssessmentQuestionService questionService;
    private final AssessmentPaperService paperService;

    public WrongQuestionTools(AssessmentWrongQuestionService wrongQuestionService,
                              AssessmentQuestionService questionService,
                              AssessmentPaperService paperService) {
        this.wrongQuestionService = wrongQuestionService;
        this.questionService = questionService;
        this.paperService = paperService;
    }

    /**
     * 查询当前学生自己的错题分布（按试卷聚合）。
     *
     * <p>⚠️ 签名无身份参数；返回<b>不含</b>正确答案、解析、选项与学生原始作答。
     */
    @Tool(description = "查询当前登录学生自己的错题分布，按试卷聚合："
            + "每张试卷的错误题数与少量题干样例，用于判断薄弱方向。"
            + "不返回正确答案、解析或学生原始作答。无需任何参数。")
    public WrongQuestionToolResult getMyWrongQuestionSummary(ToolContext toolContext) {
        AgentIdentity identity = AgentToolGuard.requireStudent(toolContext);
        int userId = identity.userId();

        // 只取两列：paperId 用于分组，questionId 用于取题干
        List<AssessmentWrongQuestionEntity> rows = wrongQuestionService.lambdaQuery()
                .select(AssessmentWrongQuestionEntity::getPaperId,
                        AssessmentWrongQuestionEntity::getQuestionId)
                .eq(AssessmentWrongQuestionEntity::getStudentId, userId)
                .list();

        if (rows.isEmpty()) {
            logger.debug("[AGENT][TOOL] getMyWrongQuestionSummary userId={} total=0", userId);
            return WrongQuestionToolResult.ok(List.of(), 0L, 0L);
        }

        // 代码内聚合：paperId → questionIds（保持插入顺序，便于稳定输出）
        Map<Integer, List<Integer>> byPaper = new LinkedHashMap<>();
        for (AssessmentWrongQuestionEntity row : rows) {
            Integer paperId = row.getPaperId();
            Integer questionId = row.getQuestionId();
            List<Integer> list = byPaper.computeIfAbsent(paperId, key -> new ArrayList<>());
            if (questionId != null) {
                list.add(questionId);
            }
        }

        // 错误数降序；并列时按 paperId 升序（确定性，避免同分顺序漂移）
        List<Map.Entry<Integer, List<Integer>>> groups = new ArrayList<>(byPaper.entrySet());
        groups.sort(Comparator
                .<Map.Entry<Integer, List<Integer>>>comparingInt(entry -> entry.getValue().size())
                .reversed()
                .thenComparing(entry -> entry.getKey() == null ? Integer.MAX_VALUE : entry.getKey()));

        int totalPapers = groups.size();
        List<Map.Entry<Integer, List<Integer>>> shown =
                groups.size() > MAX_PAPERS ? groups.subList(0, MAX_PAPERS) : groups;

        Map<Integer, String> paperNames = paperNames(shown);
        Map<Integer, String> questionContents = questionContents(shown);

        List<WrongQuestionToolResult.Item> items = new ArrayList<>(shown.size());
        for (Map.Entry<Integer, List<Integer>> group : shown) {
            List<String> samples = new ArrayList<>(MAX_SAMPLE_QUESTIONS);
            for (Integer questionId : group.getValue()) {
                if (samples.size() >= MAX_SAMPLE_QUESTIONS) {
                    break;
                }
                String content = questionContents.get(questionId);
                if (content != null) {
                    samples.add(content);
                }
            }
            items.add(new WrongQuestionToolResult.Item(
                    paperNames.getOrDefault(group.getKey(), UNKNOWN_PAPER),
                    group.getValue().size(),
                    samples
            ));
        }

        logger.debug("[AGENT][TOOL] getMyWrongQuestionSummary userId={} totalWrong={} papers={} returned={}",
                userId, rows.size(), totalPapers, items.size());
        return WrongQuestionToolResult.ok(items, totalPapers, rows.size());
    }

    /** 只取 id + name */
    private Map<Integer, String> paperNames(List<Map.Entry<Integer, List<Integer>>> groups) {
        Set<Integer> ids = new HashSet<>();
        for (Map.Entry<Integer, List<Integer>> group : groups) {
            if (group.getKey() != null) {
                ids.add(group.getKey());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> names = new HashMap<>(ids.size());
        for (AssessmentPaperEntity paper : paperService.lambdaQuery()
                .select(AssessmentPaperEntity::getId, AssessmentPaperEntity::getName)
                .in(AssessmentPaperEntity::getId, ids)
                .list()) {
            names.put(paper.getId(), paper.getName());
        }
        return names;
    }

    /** 只取 id + content（⚠️ 不取 answer / analysis / options）*/
    private Map<Integer, String> questionContents(List<Map.Entry<Integer, List<Integer>>> groups) {
        Set<Integer> ids = new HashSet<>();
        for (Map.Entry<Integer, List<Integer>> group : groups) {
            for (Integer questionId : group.getValue()) {
                if (questionId != null) {
                    ids.add(questionId);
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> contents = new HashMap<>(ids.size());
        for (AssessmentQuestionEntity question : questionService.lambdaQuery()
                .select(AssessmentQuestionEntity::getId, AssessmentQuestionEntity::getContent)
                .in(AssessmentQuestionEntity::getId, ids)
                .list()) {
            contents.put(question.getId(), question.getContent());
        }
        return contents;
    }
}
