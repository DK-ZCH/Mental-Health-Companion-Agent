package com.agent;

import com.security.BadRequestException;
import com.security.CurrentUserProvider;
import com.security.ForbiddenException;
import com.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
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

        long startMs = System.currentTimeMillis();
        try {
            String answer = agentService.chat(message, identity);
            // 观测性：只记录长度与耗时，不记录消息与回答内容（隐私）
            logger.info("[AGENT] 对话成功 userId={} msgLen={} answerLen={} elapsed={}ms",
                    userId, message.length(),
                    answer == null ? 0 : answer.length(),
                    System.currentTimeMillis() - startMs);
            return R.ok().put("answer", answer);
        } catch (Exception e) {
            // 只记录长度与异常类型，不记录消息内容与堆栈中的敏感数据
            logger.error("[AGENT] 对话失败 userId={} msgLen={} elapsed={}ms ex={}",
                    userId, message.length(), System.currentTimeMillis() - startMs,
                    e.getClass().getName(), e);
            return R.error(MSG_UPSTREAM_FAILED);
        }
    }
}
