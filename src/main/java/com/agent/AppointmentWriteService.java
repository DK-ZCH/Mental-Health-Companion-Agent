package com.agent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.entity.CounselingAppointmentEntity;
import com.security.AppointmentStatusPolicy;
import com.service.CounselingAppointmentService;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Agent 创建预约的<b>写入边界</b>（Phase 4 / Round 4）。
 *
 * <p><b>为什么不直接调 Mapper</b>：Tool 只做参数与身份适配；
 * 校验、实体组装、状态固定都收敛在这里，便于测试与审查。
 *
 * <p><b>为什么 status 与 studentId 在方法签名里</b>：
 * 它们<b>不是</b> Tool 入参 —— 由调用方（Tool）从 {@code ToolContext} 与常量传入，
 * 模型无法提供。签名本身即证明「无该参数」。
 */
public class AppointmentWriteService {

    private final CounselingAppointmentService appointmentService;

    public AppointmentWriteService(CounselingAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * 是否已存在占槽的有效预约（{@code status ∈ {1,2}}）。
     *
     * <p>应用层查重负责<b>业务语义</b>（给用户友好提示）；
     * 并发条件下的最终一致性由 DB 唯一键 {@code uk_counselor_slot} 兜底。
     */
    public boolean existsActiveSlot(int counselorId, LocalDate date, int timeSlot) {
        QueryWrapper<CounselingAppointmentEntity> query = new QueryWrapper<>();
        query.eq("counselor_id", counselorId)
                .eq("appointment_date", Date.valueOf(date))
                .eq("time_slot", timeSlot)
                .in("status", AppointmentStatusPolicy.STATUS_PENDING, AppointmentStatusPolicy.STATUS_APPROVED);
        return appointmentService.count(query) > 0;
    }

    /**
     * 写入一条待审核预约。
     *
     * @param studentId   <b>必须</b>来自已认证身份，绝不能来自模型
     * @param counselorId 已校验存在的咨询师
     * @return 新记录 id
     * @throws org.springframework.dao.DuplicateKeyException 并发下同槽重复（由 DB 唯一键拒绝）
     */
    public int create(int studentId, int counselorId, LocalDate date, int timeSlot) {
        CounselingAppointmentEntity entity = new CounselingAppointmentEntity();
        entity.setStudentId(studentId);
        entity.setCounselorId(counselorId);
        entity.setAppointmentDate(Date.valueOf(date));
        entity.setTimeSlot(timeSlot);
        // 状态由服务端固定，不接受任何外部值
        entity.setStatus(AppointmentStatusPolicy.STATUS_PENDING);
        entity.setAppointmentNo(String.valueOf(System.currentTimeMillis()));
        entity.setAppliedAt(new java.util.Date());
        entity.setCreatedAt(new java.util.Date());
        appointmentService.save(entity);
        return entity.getId();
    }
}
