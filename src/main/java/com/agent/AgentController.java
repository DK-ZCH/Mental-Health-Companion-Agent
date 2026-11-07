package com.agent;

import com.security.BadRequestException;
import com.security.CurrentUserProvider;
import com.security.ForbiddenException;
import com.service.AgentConversationService;
import com.service.AgentMessageService;
import com.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 对话接口（Phase 4 / Step 11）。
 *
 * <p><b>本层是唯一接触 {@code HttpServletRequest} 的地方</b>：
 * 取到 {@code userId} 后立即下沉为纯 {@code Integer}，下游不再持有 Web 上下文。
 *
 * <p><b>认证</b>：{@code AuthorizationInterceptor} 对 {@code /**} 全量生效，
 * 因此 {@code /agent/**} <b>天然要求登录</b>，无需额外配置；未登录走既有契约
 * （{@code HTTP 200 + body.code=401}）。
 *
 * <p><b>失败语义</b>：
 * <ul>
 *   <li>message 为空 / 超长 → {@link BadRequestException} → HTTP 400（既有处理器）</li>
 *   <li>已登录但 Session 无 userId → {@code ForbiddenException} → HTTP 403（既有契约）</li>
 *   <li>模型调用失败 → {@code code=500} + <b>统一文案</b>（不泄露上游细节）</li>
 * </ul>
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    /**
     * 单次消息长度上限（Java {@code String} 字符数）。
     *
     * <p>工程约束（防超长输入导致的 token 成本与延迟），<b>不是</b>业务规则。
     * 依据：项目当前无统一请求长度规范，且心理支持场景需要容纳较长的自然叙述。
     */
    static final int MAX_MESSAGE_LENGTH = 1000;

    private static final String MSG_UPSTREAM_FAILED = "服务暂时不可用，请稍后再试";

    /**
     * Session 中保存「待确认预约草稿」的键（Round 4 · R4-Q5 裁定 C）。
     *
     * <p><b>只保留一个</b>：新的预览覆盖旧的，避免「先预览 A 再预览 B 然后说确认」的歧义。
     * <p><b>它不是授权依据</b>：值只是一个签名 token，确认时仍要完整验签与重新校验。
     */
    static final String SESSION_PENDING_APPOINTMENT = "PENDING_APPOINTMENT_CONFIRMATION";

    /**
     * Step 25：{@code HttpSession} 中的单个计划草案键（P-1 冻结：会话内、不落库）。
     * 与预约草稿键分开，避免两条状态链互相覆盖。
     */
    /** public：供集成回归注入「过期草稿」夹具；不改变运行时行为。 */
    public static final String SESSION_PENDING_PLAN = "PENDING_PLAN_CONFIRMATION";

    /**
     * 当前会话内「已确认」标记（P-1 冻结：确认后 Session 标记为已确认）。
     * 只存状态字面量，不落库、不存任何用户内容；会话结束即消失。
     *
     * <p>【2026-11-07 审查修正】曾一度以「无读取方」为由删除，
     * 但 P-1 明确要求服务端 Session 标记，而响应状态与客户端 planState
     * 都不能替代服务端会话状态。现按 P-1 恢复，并由集成回归断言写入。
     */
    /** public：供集成回归断言 P-1 的「确认后 Session 标记」；不改变运行时行为。 */
    public static final String SESSION_CONFIRMED_PLAN = "CONFIRMED_PLAN_STATE";

    /** 计划草案有效期（与预约草稿 TTL 一致：5 分钟）；过期后不承诺保留。 */
    /** public：供集成回归计算过期夹具时间戳；不改变运行时行为。 */
    public static final long PLAN_DRAFT_TTL_MILLIS = 300_000L;

    /** 服务端固定文言（P-6：不让模型自称生效）。 */
    private static final String MSG_PLAN_CONFIRMED = "已确认。该计划仅在当前会话内保留，不会自动创建任务或日程。";
    private static final String MSG_PLAN_DISCARDED = "已放弃该建议草案。";
    private static final String MSG_PLAN_NO_DRAFT = "当前没有可确认的建议草案，请重新生成。";
    private static final String MSG_PLAN_EXPIRED = "建议草案已过期，请重新生成。";
    private static final String MSG_PLAN_FAILED = "服务暂时不可用，请稍后再试";

    private final AgentService agentService;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final SafetyResources safetyResources;

    public AgentController(AgentService agentService,
                           AgentConversationService agentConversationService,
                           AgentMessageService agentMessageService,
                           SafetyResources safetyResources) {
        this.agentService = agentService;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.safetyResources = safetyResources;
    }

    /**
     * 单轮对话。
     *
     * @param request     请求体（可为空 → 视为 message 缺失）
     * @param httpRequest 用于读取已认证身份
     */
    @PostMapping("/chat")
    public R chat(@RequestBody(required = false) AgentChatRequest request,
                  HttpServletRequest httpRequest) {
        String message = (request == null) ? null : request.getMessage();

        if (message == null || message.isBlank()) {
            throw new BadRequestException("message 不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("message 长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }

        // 身份解析：缺失时抛 ForbiddenException → HTTP 403（不在 try 内，保持既有语义）
        Integer userId = CurrentUserProvider.requireCurrentUserId(httpRequest);

        // 身份域（Step 12 · L1 准入）—— 白名单制。
        // 来源唯一：CurrentUserProvider（本层【不】直接读 Session，否则会出现第二个身份来源）。
        // 当前仅 student 可用 Agent；counselor / admin_user / null / 未来新增值一律拒绝。
        // 拒绝发生在调用模型【之前】⇒ 不消耗 token，也不进入 LLM。
        String userTable = CurrentUserProvider.currentTableName(httpRequest);
        if (!AgentIdentity.TABLE_STUDENT.equals(userTable)) {
            // ⚠️ 只记录身份域，不记录 userId
            logger.warn("[AGENT][SEC] 身份域不被允许（userTable={}），拒绝进入 Agent", userTable);
            throw new ForbiddenException();
        }

        AgentIdentity identity = new AgentIdentity(userId, userTable);

        // R4-Q5（C）：Session 只负责「跨请求保存编排状态」——
        //   · 预览产生 token ⇒ 写入草稿；
        //   · 任何一次确认尝试 ⇒ 清理草稿（成功/过期/冲突都清）。
        // 它不是授权依据：confirm 仍以签名 token 为准，全部重新验签 + 重新校验。
        HttpSession session = httpRequest.getSession(true);
        String pendingToken = (String) session.getAttribute(SESSION_PENDING_APPOINTMENT);
        PendingAppointmentDraft draft = new PendingAppointmentDraft();

        // Step ⑤：会话生命周期 —— 传了 conversationId 则校验归属，否则创建新会话。
        // studentId 永远来自登录态；conversationId 是资源 id，不是身份。
        Long conversationId;
        if (request.getConversationId() != null) {
            conversationId = agentConversationService.requireOwned(request.getConversationId(), userId).getId();
        } else {
            conversationId = agentConversationService.create(userId, message);
        }

        java.util.Map<String, Object> extraContext = new java.util.HashMap<>();
        extraContext.put(AgentIdentity.CTX_PENDING_DRAFT, draft);
        extraContext.put(AgentIdentity.CTX_CONVERSATION_ID, conversationId);
        if (pendingToken != null) {
            extraContext.put(AgentIdentity.CTX_PENDING_TOKEN, pendingToken);
            // 无状态 Agent 不知道「上一轮留下了草稿」；由服务端在 system prompt 末尾告知（不改用户输入）
            extraContext.put(AgentIdentity.CTX_SYSTEM_HINT,
                    "当前会话存在一条【待确认的预约草稿】（5 分钟内有效）。"
                            + "如果用户表示确认（如「确认」「好的」「可以」「就这样」），"
                            + "请直接调用 confirmAppointment（无需任何参数），不要重复追问老师/日期/时段。");
        }

        long startMs = System.currentTimeMillis();
        try {
            AgentReply reply = agentService.chat(message, identity, extraContext);
            if (draft.consumed()) {
                session.removeAttribute(SESSION_PENDING_APPOINTMENT);
                logger.info("[AGENT][CONFIRM] 已清理待确认草稿 userId={}", userId);
            } else if (draft.issuedToken() != null) {
                session.setAttribute(SESSION_PENDING_APPOINTMENT, draft.issuedToken());
                logger.info("[AGENT][CONFIRM] 已写入待确认草稿 userId={}", userId);
            }
            // 观测性：只记录长度、耗时与安全触发标记，不记录消息与回答内容（隐私）
            String answer = reply.answer();
            logger.info("[AGENT] 对话成功 userId={} msgLen={} answerLen={} safetyTriggered={} elapsed={}ms",
                    userId, message.length(),
                    answer == null ? 0 : answer.length(),
                    reply.safety() != null && reply.safety().triggered(),
                    System.currentTimeMillis() - startMs);
            // safety 为结构化标记（前端可忽略）：不影响既有 answer 契约
            // conversationId：多轮会话必需，前端下次请求带回
            return R.ok()
                    .put("answer", answer)
                    .put("safety", reply.safety())
                    .put("conversationId", conversationId);
        } catch (Exception e) {
            // 只记录长度与异常类型，不记录消息内容与堆栈中的敏感数据
            logger.error("[AGENT] 对话失败 userId={} msgLen={} elapsed={}ms ex={}",
                    userId, message.length(), System.currentTimeMillis() - startMs,
                    e.getClass().getName(), e);
            return R.error(MSG_UPSTREAM_FAILED);
        }
    }

    // ================== Step ⑤：会话生命周期（全部要求归属） ==================

    /** 我的会话列表（按最近消息倒序）；不返回任何其他学生的会话 */
    @GetMapping("/conversations")
    public R conversations(HttpServletRequest httpRequest) {
        Integer userId = CurrentUserProvider.requireCurrentUserId(httpRequest);
        return R.ok().put("data", agentConversationService.listMine(userId));
    }

    /** 某会话的历史消息（时间正序）；归属不符 ⇒ 403 */
    @GetMapping("/conversations/{id}/messages")
    public R conversationMessages(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        Integer userId = CurrentUserProvider.requireCurrentUserId(httpRequest);
        agentConversationService.requireOwned(id, userId);
        return R.ok()
                .put("conversationId", id)
                .put("data", agentMessageService.listByConversation(id));
    }

    /** 删除会话（物理删除，含全部消息）；归属不符 ⇒ 403 */
    @DeleteMapping("/conversations/{id}")
    public R deleteConversation(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        Integer userId = CurrentUserProvider.requireCurrentUserId(httpRequest);
        agentConversationService.deleteOwned(id, userId);
        logger.info("[AGENT][CONFIRM] 会话已删除 userId={}（不记录会话 id）", userId);
        return R.ok();
    }

    // ================== Step 25：辅助计划（P-1～P-6 冻结） ==================

    /**
     * {@code POST /agent/plan}：生成建议草案 / 确认 / 放弃。
     *
     * <p><b>不改 /agent/chat</b>：本端点独立，原有 message 必填校验与契约逐字保留。
     *
     * <p><b>P-2 核心</b>：{@code action} 只是意图声明，不是状态凭证。
     * {@code CONFIRM} 必须经 C-2 五步校验（Session 草稿 / 身份二元归属 /
     * 会话一致性与 requireOwned / 有效期 / 消费状态）才产生 {@code CONFIRMED}。
     * 确认由 Java 确定性处理，模型无决定权；{@code CONFIRM}/{@code DISCARD}
     * 完全不调用 {@link AgentModel}。
     */
    @PostMapping("/plan")
    public R plan(@RequestBody(required = false) AgentPlanRequest request,
                  HttpServletRequest httpRequest) {
        PlanningAction action = request == null ? null : PlanningAction.parse(request.getAction());
        if (action == null) {
            throw new BadRequestException("action 必须是 PREVIEW / CONFIRM / DISCARD");
        }

        // 身份唯一来源：登录态（不在 try 内，保持既有 403 语义）
        Integer userId = CurrentUserProvider.requireCurrentUserId(httpRequest);
        String userTable = CurrentUserProvider.currentTableName(httpRequest);
        if (!AgentIdentity.TABLE_STUDENT.equals(userTable)) {
            logger.warn("[AGENT][SEC] 身份域不被允许（userTable={}），拒绝进入计划（不记录 userId）", userTable);
            throw new ForbiddenException();
        }
        AgentIdentity identity = new AgentIdentity(userId, userTable);
        HttpSession session = httpRequest.getSession(true);

        return switch (action) {
            case PREVIEW -> previewPlan(request, identity, session);
            case CONFIRM -> resolvePlan(request, identity, session, PlanState.CONFIRMED);
            case DISCARD -> resolvePlan(request, identity, session, PlanState.DISCARDED);
        };
    }

    /** PREVIEW：生成建议草案；单草稿，新草案覆盖旧草案。 */
    private R previewPlan(AgentPlanRequest request, AgentIdentity identity, HttpSession session) {
        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            throw new BadRequestException("message 不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("message 长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }

        Long conversationId;
        if (request.getConversationId() != null) {
            conversationId = agentConversationService
                    .requireOwned(request.getConversationId(), identity.userId()).getId();
        } else {
            conversationId = agentConversationService.create(identity.userId(), message);
        }

        Map<String, Object> extraContext = new java.util.HashMap<>();
        extraContext.put(AgentIdentity.CTX_CONVERSATION_ID, conversationId);
        // C-1：仅 PREVIEW 注入服务端当前日期；AgentService 拼接前会移除该键
        extraContext.put(AgentIdentity.CTX_PLANNING_DATE, PlanningTimeContext.current());

        long startMs = System.currentTimeMillis();
        try {
            AgentReply reply = agentService.chat(message, identity, extraContext);
            String draftText = reply.answer() == null ? "" : reply.answer();

            // P-1：仅存 HttpSession，不落库；新草案覆盖旧草案。
            // 刻意不把模型生成的草案正文存入 Session（隐私最小化）；
            // 草案正文已在本次响应的 answer 中交给前端，状态以 plan.state 为准。
            session.setAttribute(SESSION_PENDING_PLAN,
                    new PlanningDraft(identity.userId(), identity.userTable(),
                            conversationId, System.currentTimeMillis()));

            logger.info("[AGENT][PLAN] 草案已生成 state=DRAFT elapsed={}ms",
                    System.currentTimeMillis() - startMs);
            return R.ok()
                    .put("answer", draftText)
                    .put("safety", reply.safety())
                    .put("conversationId", conversationId)
                    .put("plan", Map.of("state", PlanState.DRAFT.name(),
                            "expiresInSeconds", PLAN_DRAFT_TTL_MILLIS / 1000L));
        } catch (Exception e) {
            logger.error("[AGENT][PLAN] 草案生成失败 elapsed={}ms ex={}",
                    System.currentTimeMillis() - startMs, e.getClass().getName(), e);
            return R.error(MSG_PLAN_FAILED);
        }
    }

    /**
     * CONFIRM / DISCARD：纯 Java 状态机，不调用模型、不注入段、不新建会话。
     *
     * <p><b>异常路径语义（2026-11-06 审查修正）</b>：{@code requireOwned} 的真实契约是
     * 抛 {@code ForbiddenException} → HTTP 403（既有 IDOR 契约，与 /agent/chat 的会话接口一致），
     * 不是 {@code NO_DRAFT}。因此归属校验放在<b>触碰 Session 草稿之前</b>：
     * 授权拒绝时草稿保持原状，不被误删、不被消费。
     * 规格中「统一归入 NO_DRAFT」约束的是<b>草稿归属比对失败</b>（matches 失败、
     * 账号切换、会话切换），与「conversationId 本身不属于当前用户」的请求级 403 是两种语义。
     *
     * <p><b>并发语义</b>：「取草稿 + 移除 Session 属性」只是编排清理，
     * <b>不作为并发安全依据</b>。真正的原子保障是
     * {@link PlanningDraft#tryConsume()} —— 同一草稿引用的两个并发请求
     * 至多一个能取得消费权。
     */
    private R resolvePlan(AgentPlanRequest request, AgentIdentity identity, HttpSession session,
                          PlanState successState) {
        Long conversationId = request.getConversationId();
        if (conversationId == null) {
            return planResult(PlanState.NO_DRAFT, MSG_PLAN_NO_DRAFT);
        }

        // ③ 防存在性预言机：归属校验【先于】任何 Session 变更。
        // 失败 ⇒ ForbiddenException → HTTP 403，草稿完全不动（不删除、不消费）。
        agentConversationService.requireOwned(conversationId, identity.userId());

        // ① 取草稿并从 Session 编排清理（不是并发安全依据，见方法注释）
        Object raw = session.getAttribute(SESSION_PENDING_PLAN);
        session.removeAttribute(SESSION_PENDING_PLAN);
        PlanningDraft draft = raw instanceof PlanningDraft value ? value : null;

        // ⑤ 原子消费：检查与状态变更一次完成，消除 isConsumed/consume 的竞态窗口
        if (draft == null || !draft.tryConsume()) {
            return planResult(PlanState.NO_DRAFT, MSG_PLAN_NO_DRAFT);
        }

        // ② 身份二元组 + 会话逐值比对（HttpSession 认证成功 ≠ 归属成功）
        if (!draft.matches(identity.userId(), identity.userTable(), conversationId)) {
            logger.warn("[AGENT][PLAN] 草稿归属校验失败，已消费（不记录身份标识）");
            return planResult(PlanState.NO_DRAFT, MSG_PLAN_NO_DRAFT);
        }
        // ④ 有效期
        if (draft.isExpired(System.currentTimeMillis(), PLAN_DRAFT_TTL_MILLIS)) {
            return planResult(PlanState.EXPIRED, MSG_PLAN_EXPIRED);
        }

        // P-1：确认后在当前 Session 标记为已确认（只存状态字面量，不存用户内容）；
        // 放弃则清除。响应 plan.state 与前端 planState 是另外两处表达，不互相替代。
        if (successState == PlanState.CONFIRMED) {
            session.setAttribute(SESSION_CONFIRMED_PLAN, PlanState.CONFIRMED.name());
        } else {
            session.removeAttribute(SESSION_CONFIRMED_PLAN);
        }
        String answer = successState == PlanState.CONFIRMED
                ? MSG_PLAN_CONFIRMED : MSG_PLAN_DISCARDED;
        logger.info("[AGENT][PLAN] state={}", successState);
        return planResult(successState, answer);
    }

    /** 统一响应：plan.state 由服务端确定性给出，不暴露草稿凭证。 */
    private R planResult(PlanState state, String answer) {
        return R.ok()
                .put("answer", answer)
                .put("safety", new SafetyNotice(false, safetyResources.status()))
                .put("plan", Map.of("state", state.name(), "expiresInSeconds", 0L));
    }
}
