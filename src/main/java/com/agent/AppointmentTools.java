package com.agent;

import com.entity.CounselingAppointmentEntity;
import com.entity.CounselorEntity;
import com.service.CounselingAppointmentService;
import com.service.CounselorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * T1：查询<b>当前登录学生本人</b>的咨询预约（Phase 4 / Round 1）。
 *
 * <p><b>数据形态</b>：用户私有 + 状态机（1 待审核 / 2 同意 / 3 拒绝）。
 *
 * <p><b>职责边界</b>：
 * <ol>
 *   <li>身份与域校验交给 {@link AgentToolGuard}（一处定义，四 Tool 共用）；</li>
 *   <li>只查本人：{@code eq(student_id, userId)}，<b>方法签名中没有任何身份/id 参数</b>；</li>
 *   <li>只 select 必要列，<b>不查</b> {@code review_comment} / {@code appointment_no} / {@code student_id}；</li>
 *   <li>咨询师姓名通过<b>只取 id/name</b> 的查询获得（不把咨询师 PII 载入内存）；</li>
 *   <li>状态与时段用 {@link AgentDictLabels} 在<b>代码内</b>翻译，不让模型猜数字。</li>
 * </ol>
 */
public class AppointmentTools {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentTools.class);

    /** 单次返回上限（工程约束，非业务事实；超出由 {@code total/truncated} 表达）*/
    static final int MAX_RECORDS = 10;

    private static final String UNKNOWN_COUNSELOR = "未知咨询师";

    private final CounselingAppointmentService appointmentService;
    private final CounselorService counselorService;
    private final AgentDictLabels labels;

    public AppointmentTools(CounselingAppointmentService appointmentService,
                            CounselorService counselorService,
                            AgentDictLabels labels) {
        this.appointmentService = appointmentService;
        this.counselorService = counselorService;
        this.labels = labels;
    }

    /**
     * 查询当前学生自己的预约记录。
     *
     * <p>⚠️ 方法签名<b>不含</b> {@code userId} / {@code userTable} —— 模型收到的
     * JSON Schema 里没有可填写的身份字段，从结构上消除「由 LLM 指定用户」的可能。
     */
    @Tool(description = "查询当前登录学生自己的咨询预约记录。"
            + "返回最近若干条预约的日期、时间段、状态（待审核/同意/拒绝）与咨询师姓名，"
            + "以及预约总数与是否被截断。无需任何参数。")
    public AppointmentToolResult getMyAppointments(ToolContext toolContext) {
        AgentIdentity identity = AgentToolGuard.requireStudent(toolContext);
        int userId = identity.userId();

        long total = appointmentService.lambdaQuery()
                .eq(CounselingAppointmentEntity::getStudentId, userId)
                .count();
        if (total == 0L) {
            logger.debug("[AGENT][TOOL] getMyAppointments userId={} total=0", userId);
            return AppointmentToolResult.ok(List.of(), 0L);
        }

        List<CounselingAppointmentEntity> rows = appointmentService.lambdaQuery()
                .select(CounselingAppointmentEntity::getAppointmentDate,
                        CounselingAppointmentEntity::getTimeSlot,
                        CounselingAppointmentEntity::getStatus,
                        CounselingAppointmentEntity::getCounselorId)
                .eq(CounselingAppointmentEntity::getStudentId, userId)
                .orderByDesc(CounselingAppointmentEntity::getAppliedAt)
                .last("LIMIT " + MAX_RECORDS)   // 常量拼接，无外部输入
                .list();

        List<Integer> counselorIds = new ArrayList<>(rows.size());
        for (CounselingAppointmentEntity row : rows) {
            counselorIds.add(row.getCounselorId());
        }
        Map<Integer, String> names = counselorNames(counselorIds);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        List<AppointmentToolResult.Item> items = new ArrayList<>(rows.size());
        for (CounselingAppointmentEntity row : rows) {
            items.add(new AppointmentToolResult.Item(
                    row.getAppointmentDate() == null ? null : dateFormat.format(row.getAppointmentDate()),
                    labels.label("time_slot", row.getTimeSlot()),
                    labels.label("appointment_status", row.getStatus()),
                    names.getOrDefault(row.getCounselorId(), UNKNOWN_COUNSELOR)
            ));
        }

        logger.debug("[AGENT][TOOL] getMyAppointments userId={} total={} returned={}",
                userId, total, items.size());
        return AppointmentToolResult.ok(items, total);
    }

    /** 只取 id + name，避免把咨询师 phone/email/password 载入内存 */
    private Map<Integer, String> counselorNames(List<Integer> counselorIds) {
        Set<Integer> distinct = new HashSet<>();
        for (Integer id : counselorIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> names = new HashMap<>(distinct.size());
        for (CounselorEntity counselor : counselorService.lambdaQuery()
                .select(CounselorEntity::getId, CounselorEntity::getName)
                .in(CounselorEntity::getId, distinct)
                .list()) {
            names.put(counselor.getId(), counselor.getName());
        }
        return names;
    }
}
