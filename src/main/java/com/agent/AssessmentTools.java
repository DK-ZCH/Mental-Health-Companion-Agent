package com.agent;

import com.entity.AssessmentPaperEntity;
import com.entity.AssessmentRecordEntity;
import com.service.AssessmentPaperService;
import com.service.AssessmentRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 测评记录查询工具（Phase 4 / Step 11，Step 12 加入身份域校验）。
 *
 * <p><b>职责边界（严格）</b>：
 * <ol>
 *   <li>从 {@link ToolContext} 解析<b>二元身份</b>（{@link AgentIdentity}：userId + userTable）；</li>
 *   <li>校验身份域∈{@link #ALLOWED_TABLE}（不符 → 抛异常终止，fail-closed）；</li>
 *   <li>按该 id 查询本人记录；</li>
 *   <li>把业务实体转成 LLM-safe DTO（{@link AssessmentRecordView}）。</li>
 * </ol>
 * <b>不做</b>：业务权限规则分支（如 {@code if (userId == 1)}）、写操作、SQL 拼接、身份兜底。
 *
 * <p><b>为什么必须有第 2 步</b>：{@code student.id} 与 {@code counselor.id} 取值重叠
 * （均是 1/2/3），只按 id 查询会使咨询师读到学生的心理测评 ——
 * 这是 Step 11 引入、Step 12 修复的跨角色串读。
 *
 * <p><b>为什么不用 {@code queryPage}（既有分页入口）</b>：
 * 其 {@code selectListView} 会 JOIN {@code student} / {@code assessment_paper} 表，
 * 带出 {@code studentName / studentAvatarUrl} 等整套展示字段与试卷侧冗余列。
 * Tool 走那条路径会把<b>远超所需的数据载入内存</b>；且该 View 的列集会随管理端
 * 展示需求增长 —— 若将来重新加入联系方式等敏感列，Tool 会<b>被动继承</b>该暴露面。
 * 因此这里用 {@code lambdaQuery().select(...)} <b>只查两列</b>，从源头收敛数据面。
 *
 * <p><b>数据范围说明</b>：{@code eq("student_id", userId)} 与既有 {@code DataScope}
 * 的「学生侧只看自己」语义等价；区别在于此处 userId <b>只能</b>来自 ToolContext，
 * 不存在任何外部可控的 studentId 入口。
 *
 * <p><b>截断语义</b>：{@code MAX_RECORDS} 是<b>单次返回上限</b>（防 token 膨胀的工程约束），
 * 不是业务事实。结果必须携带 {@code total / truncated}，保证模型能说
 * 「共 N 条，这里先展示最近 M 条」，<b>绝不静默截断</b>。
 */
public class AssessmentTools {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentTools.class);

    /**
     * ToolContext 中承载当前登录用户 id 的键 —— 服务端注入，不进入模型视野。
     *
     * <p>Step 12 起统一由 {@link AgentIdentity} 定义（身份是二元组），此处仅保留别名。
     */
    public static final String CTX_USER_ID = AgentIdentity.CTX_USER_ID;

    /**
     * 本工具<b>允许</b>的身份域（Step 12）。
     *
     * <p>当前<b>仅学生</b>：{@code assessment_record.student_id} 只能由学生本人查询。
     * 咨询师 / 管理员在本批<b>一律拒绝</b> —— 白名单制而非黑名单：
     * 未来新增的 {@code user_type} 也会自动被拒，不会默认放行。
     *
     * <p>咨询师能看哪些学生属<b>业务规则待定项</b>（见
     * {@code PHASE4-ROADMAP-REVIEW.md} §三），不由本工具猜测。
     */
    static final String ALLOWED_TABLE = AgentIdentity.TABLE_STUDENT;

    /**
     * 单次返回记录上限（工程约束）。
     *
     * <p>⚠️ 不是「用户最多只有 10 条记录」这一业务语义；
     * 超出部分由 {@code total / truncated} 表达，不允许静默丢弃。
     */
    static final int MAX_RECORDS = 10;

    /** 身份上下文非法的异常消息（固定英文，避免把内部语义与中文文案耦合） */
    private static final String ERR_IDENTITY_CONTEXT_INVALID = "agent identity context invalid";

    /** 身份域不被本工具允许的异常消息 */
    private static final String ERR_IDENTITY_DOMAIN_DENIED = "agent identity domain not allowed";

    private static final String UNKNOWN_PAPER = "未知试卷";

    private final AssessmentRecordService recordService;
    private final AssessmentPaperService paperService;

    public AssessmentTools(AssessmentRecordService recordService, AssessmentPaperService paperService) {
        this.recordService = recordService;
        this.paperService = paperService;
    }

    /**
     * 查询<b>当前登录学生本人</b>的心理测评记录。
     *
     * <p><b>方法签名中不存在 userId / userTable 参数</b> —— 因此模型收到的 JSON Schema 里
     * 没有可填写的身份字段，从结构上消除「由 LLM 指定用户或角色」的可能。
     *
     * <p><b>Step 12 起为二元身份校验</b>（{@code userId} + {@code userTable}）：
     * 只校验 id 不足以区分「学生 1」与「咨询师 1」（二者是不同的人），
     * 因此域不符同样按安全失败终止。
     *
     * @param toolContext 框架注入的工具上下文（{@code userId} / {@code userTable}），不发送给模型
     */
    @Tool(description = "查询当前登录用户自己的心理测评记录。"
            + "返回最近若干条记录的试卷名称、得分与满分，以及该用户的记录总数与是否被截断。"
            + "无需任何参数。")
    public AssessmentRecordToolResult getMyAssessmentRecords(ToolContext toolContext) {
        // ① 解析【二元身份】—— 缺任一项即视为非法上下文
        AgentIdentity identity = AgentIdentity.from(toolContext);
        if (identity == null) {
            // fail-closed（安全失败语义）：身份不可信属于 Agent 执行安全错误，
            // 必须以异常【终止本次 Agent 调用】，不得作为「正常业务结果」回喂模型 ——
            // 否则模型可能把安全失败包装成「抱歉暂时无法查询」，最终对外表现为 HTTP 200 + code=0，
            // 使 HTTP 层与监控层都看不到安全事件。
            // 生效前提：spring.ai.tools.throw-exception-on-error=true（异常穿透至 AgentController）。
            // ⚠️ 日志只记录上下文形态，不记录其内容（可能含身份字段）。
            logger.error("[AGENT][SEC] 身份上下文非法（{}），终止本次 Agent 调用",
                    toolContext == null ? "toolContext=null" : "missing-or-invalid-identity");
            throw new IllegalStateException(ERR_IDENTITY_CONTEXT_INVALID);
        }

        // ② 身份域校验（Step 12 核心）：userId 合法【不代表】可查。
        //    例如 counselor id=1 与 student id=1 是不同的人，若只校验 id，
        //    咨询师会读到学生的心理测评（Step 11 已实测的跨角色串读）。
        //    因此域不符同样按安全失败终止，绝不降级为「按 userId 直接查」。
        // ⚠️ 只记录身份域，不记录 userId。
        if (!ALLOWED_TABLE.equals(identity.userTable())) {
            logger.error("[AGENT][SEC] 身份域不符（该工具仅允许 {}，实际 {}），终止本次 Agent 调用",
                    ALLOWED_TABLE, identity.userTable());
            throw new IllegalStateException(ERR_IDENTITY_DOMAIN_DENIED);
        }

        int userId = identity.userId();

        long total = recordService.lambdaQuery()
                .eq(AssessmentRecordEntity::getStudentId, userId)
                .count();

        if (total == 0L) {
            logger.debug("[AGENT][TOOL] getMyAssessmentRecords userId={} total=0", userId);
            return AssessmentRecordToolResult.ok(List.of(), 0L);
        }

        // 只取两列：从源头收敛数据面（不 select *，不 JOIN 任何敏感表）
        List<AssessmentRecordEntity> records = recordService.lambdaQuery()
                .select(AssessmentRecordEntity::getPaperId, AssessmentRecordEntity::getTotalScore)
                .eq(AssessmentRecordEntity::getStudentId, userId)
                .orderByDesc(AssessmentRecordEntity::getSubmittedAt)
                .last("LIMIT " + MAX_RECORDS)   // 常量拼接，无外部输入
                .list();

        Map<Integer, AssessmentPaperEntity> paperMap = loadPapers(records);
        List<AssessmentRecordView> views = new ArrayList<>(records.size());
        for (AssessmentRecordEntity record : records) {
            AssessmentPaperEntity paper = paperMap.get(record.getPaperId());
            views.add(new AssessmentRecordView(
                    paper == null ? UNKNOWN_PAPER : paper.getName(),
                    record.getTotalScore(),
                    paper == null ? null : paper.getTotalScore()
            ));
        }

        logger.debug("[AGENT][TOOL] getMyAssessmentRecords userId={} total={} returned={}",
                userId, total, views.size());

        return AssessmentRecordToolResult.ok(views, total);
    }

    /** 批量取试卷名与满分，避免逐条查询（N+1） */
    private Map<Integer, AssessmentPaperEntity> loadPapers(List<AssessmentRecordEntity> records) {
        Set<Integer> paperIds = new HashSet<>();
        for (AssessmentRecordEntity record : records) {
            if (record.getPaperId() != null) {
                paperIds.add(record.getPaperId());
            }
        }
        if (paperIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, AssessmentPaperEntity> paperMap = new HashMap<>(paperIds.size());
        for (AssessmentPaperEntity paper : paperService.listByIds(paperIds)) {
            paperMap.put(paper.getId(), paper);
        }
        return paperMap;
    }
}
