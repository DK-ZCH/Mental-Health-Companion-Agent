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

    private final AgentService agentService;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;

    public AgentController(AgentService agentService,
                           AgentConversationService agentConversationService,
                           AgentMessageService agentMessageService) {
        this.agentService = agentService;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
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
}
