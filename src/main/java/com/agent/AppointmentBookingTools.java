package com.agent;

import com.entity.CounselorEntity;
import com.service.CounselorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * T7：受控创建预约（Phase 4 / Round 4）。
 *
 * <p><b>两阶段</b>：
 * <ol>
 *   <li>{@link #previewAppointment} —— 只读校验 + 摘要 + 签发凭证（<b>不写库</b>）；</li>
 *   <li>{@link #confirmAppointment} —— 凭证是<b>唯一参数</b>，验签后重新校验并写入。</li>
 * </ol>
 *
 * <p><b>为什么确认只有 token 一个参数</b>：若同时接受 counselorId/date/slot，
 * 模型就有机会在用户确认之后替换参数（确认 A 老师，提交 B 老师）。
 * 把字段放进凭证 ⇒ 参数漂移在结构上不可能。
 *
 * <p><b>身份</b>：{@code studentId} 只来自 {@code ToolContext}，方法签名里<b>不存在</b>该参数。
 *
 * <p><b>幂等边界</b>：凭证不是一次性的（签名无状态）；重复提交由
 * <b>业务查重 → DB 唯一键</b> 拒绝，两者分工不同。
 */
public class AppointmentBookingTools {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentBookingTools.class);

    /** 时段字典编码（与 {@code sys_dict_item.dict_code} 一致）*/
    private static final String DICT_TIME_SLOT = "time_slot";

    /** 时段合法范围（字典 1..8，每项固定 1 小时）*/
    static final int MIN_SLOT = 1;
    static final int MAX_SLOT = 8;

    /** Agent 创建预约的窗口上限（不含修改旧接口语义）*/
    static final int MAX_DAYS_AHEAD = 30;

    private final AppointmentWriteService writeService;
    private final AppointmentConfirmToken tokenService;
    private final CounselorService counselorService;
    private final AgentDictLabels labels;

    public AppointmentBookingTools(AppointmentWriteService writeService,
                                   AppointmentConfirmToken tokenService,
                                   CounselorService counselorService,
                                   AgentDictLabels labels) {
        this.writeService = writeService;
        this.tokenService = tokenService;
        this.counselorService = counselorService;
        this.labels = labels;
    }

    /**
     * 生成预约预览（<b>不写库</b>）。
     */
    @Tool(description = "生成预约预览。只做校验与摘要，不写数据库。"
            + "参数：counselorId（必须来自可咨询教师名录工具，不得编造）、"
            + "appointmentDate（yyyy-MM-dd，服务端限定今天起 30 天内）、"
            + "timeSlot（1-8，来自时段字典）。"
            + "返回预览文本与确认凭证；必须把老师、日期、时段完整复述给用户，"
            + "等用户明确确认后才可调用 confirmAppointment。")
    public AppointmentPreviewResult previewAppointment(Integer counselorId,
                                                       String appointmentDate,
                                                       Integer timeSlot,
                                                       ToolContext toolContext) {
        AgentIdentity identity = AgentToolGuard.requireStudent(toolContext);

        String invalid = validate(counselorId, appointmentDate, timeSlot);
        if (invalid != null) {
            return AppointmentPreviewResult.fail(invalid);
        }

        LocalDate date = LocalDate.parse(appointmentDate);
        CounselorEntity counselor = loadCounselor(counselorId);
        if (counselor == null) {
            return AppointmentPreviewResult.fail("未找到该咨询师，请先从可咨询教师名录中选择");
        }

        if (writeService.existsActiveSlot(counselorId, date, timeSlot)) {
            return AppointmentPreviewResult.fail("该时段已被预约，请选择其他时段");
        }

        String token = tokenService.issue(identity.userId(), counselorId, date, timeSlot);

        // R4-Q5（C）：把本轮签发的凭证交给 Controller 写入 HttpSession。
        // 单个 Session 只保留一个待确认草稿（新的预览覆盖旧的）。
        PendingAppointmentDraft draft = pendingDraft(toolContext);
        if (draft != null) {
            draft.issued(token);
        }

        String slotLabel = labels.label(DICT_TIME_SLOT, timeSlot);

        String message = "预约预览（尚未提交）\n"
                + "· 咨询师：" + counselor.getName()
                + (counselor.getExpertise() == null ? "" : "（擅长：" + counselor.getExpertise() + "）") + "\n"
                + "· 日期：" + date + "\n"
                + "· 时段：" + slotLabel + "\n"
                + "· 提交后状态：待老师审核\n"
                + "请确认信息无误后告诉我，我再为你提交。";

        logger.info("[AGENT][CONFIRM] 生成预览 counselorId={} date={} slot={}", counselorId, date, timeSlot);
        return AppointmentPreviewResult.ok(message, token, tokenService.ttlSeconds());
    }

    /**
     * 确认并创建预约。
     *
     * <p><b>只有 token 一个参数</b>：老师/日期/时段全部从凭证里读，模型无法替换。
     */
    @Tool(description = "确认创建预约。无需任何参数：凭证由服务端从当前会话的待确认草稿读取。"
            + "当用户对上一条预约预览表示确认（如「确认」「好的」「可以」「就这样」）时，"
            + "【直接调用本工具】，不要追问老师/日期/时段。"
            + "若当前没有待确认草稿，本工具会返回明确提示，你如实转述即可。"
            + "凭证有效期 5 分钟；重复提交会被业务规则拒绝。"
            + "若返回失败，请如实告知用户原因，不要重复尝试或编造成功。")
    public AppointmentConfirmResult confirmAppointment(ToolContext toolContext) {
        AgentIdentity identity = AgentToolGuard.requireStudent(toolContext);

        // 任何一次确认尝试（无论成功/失败）都视为「消耗掉草稿」⇒ 由 Controller 清理 Session，
        // 避免用户后续说「确认」时反复命中旧草稿。
        PendingAppointmentDraft draft = pendingDraft(toolContext);
        if (draft != null) {
            draft.markConsumed();
        }

        String confirmationToken = pendingToken(toolContext);
        if (confirmationToken == null || confirmationToken.isBlank()) {
            return AppointmentConfirmResult.fail("当前没有待确认的预约，请先生成预约预览");
        }

        AppointmentConfirmToken.Payload payload;
        try {
            payload = tokenService.verify(confirmationToken, identity.userId());
        } catch (AppointmentConfirmToken.TokenException e) {
            // 只记录原因，不记录凭证与用户标识
            logger.warn("[AGENT][CONFIRM] 凭证校验失败 reason={}", e.reason());
            return AppointmentConfirmResult.fail("预约信息已失效，请重新生成预约预览");
        }

        // 凭证可能签发于数分钟前：写入前重新校验时间窗口与咨询师有效性
        String invalid = validatePayload(payload);
        if (invalid != null) {
            return AppointmentConfirmResult.fail(invalid);
        }

        if (writeService.existsActiveSlot(payload.counselorId(), payload.date(), payload.timeSlot())) {
            return AppointmentConfirmResult.fail("该时段已被预约，请选择其他时段");
        }

        try {
            int id = writeService.create(identity.userId(), payload.counselorId(),
                    payload.date(), payload.timeSlot());
            logger.info("[AGENT][CONFIRM] 创建成功 appointmentId={}", id);
            return AppointmentConfirmResult.ok("预约已提交（记录编号 " + id + "），等待老师审核。", id);
        } catch (DataIntegrityViolationException e) {
            // 并发下应用层查重可能同时通过，由 DB 唯一键兜底
            logger.info("[AGENT][CONFIRM] 唯一键拒绝同槽重复 counselorId={} date={} slot={}",
                    payload.counselorId(), payload.date(), payload.timeSlot());
            return AppointmentConfirmResult.fail("该时段已被预约，请选择其他时段");
        }
    }

    /** 预览参数校验；返回 {@code null} 表示通过 */
    private String validate(Integer counselorId, String appointmentDate, Integer timeSlot) {
        if (counselorId == null) {
            return "缺少咨询师信息，请先选择一位咨询师";
        }
        if (appointmentDate == null || appointmentDate.isBlank()) {
            return "缺少预约日期";
        }
        LocalDate date;
        try {
            date = LocalDate.parse(appointmentDate);
        } catch (DateTimeParseException e) {
            return "日期格式不正确，应为 yyyy-MM-dd";
        }
        String windowError = checkWindow(date);
        if (windowError != null) {
            return windowError;
        }
        return checkSlot(timeSlot);
    }

    /** 确认阶段：从凭证读出的字段重新校验 */
    private String validatePayload(AppointmentConfirmToken.Payload payload) {
        String windowError = checkWindow(payload.date());
        if (windowError != null) {
            return windowError;
        }
        String slotError = checkSlot(payload.timeSlot());
        if (slotError != null) {
            return slotError;
        }
        if (loadCounselor(payload.counselorId()) == null) {
            return "未找到该咨询师，请重新生成预约预览";
        }
        return null;
    }

    private String checkWindow(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            return "不能预约过去的日期，请选择今天或之后的日期";
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            return "只能预约今天起 " + MAX_DAYS_AHEAD + " 天内的时段";
        }
        return null;
    }

    private String checkSlot(Integer timeSlot) {
        if (timeSlot == null) {
            return "缺少预约时段";
        }
        if (timeSlot < MIN_SLOT || timeSlot > MAX_SLOT) {
            return "预约时段不合法（应为 1-" + MAX_SLOT + "）";
        }
        return null;
    }

    /** 从 ToolContext 取「本轮草稿传递器」；直接调用（测试）时可能为 null */
    private static PendingAppointmentDraft pendingDraft(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get(AgentIdentity.CTX_PENDING_DRAFT);
        return value instanceof PendingAppointmentDraft draft ? draft : null;
    }

    /**
     * 从 ToolContext 取「当前 Session 的待确认凭证」。
     *
     * <p><b>这不是模型参数</b>：由 Controller 从 HttpSession 读出后注入，模型无法提供/篡改。
     */
    private static String pendingToken(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get(AgentIdentity.CTX_PENDING_TOKEN);
        return value instanceof String token ? token : null;
    }

    /** 只取 id/name/expertise：不加载 password/phone/email 等字段 */
    private CounselorEntity loadCounselor(Integer counselorId) {
        if (counselorId == null) {
            return null;
        }
        return counselorService.lambdaQuery()
                .select(CounselorEntity::getId, CounselorEntity::getName, CounselorEntity::getExpertise)
                .eq(CounselorEntity::getId, counselorId)
                .one();
    }
}
