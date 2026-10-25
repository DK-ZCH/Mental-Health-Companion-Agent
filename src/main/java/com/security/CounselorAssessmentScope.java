package com.security;

import com.entity.CounselingAppointmentEntity;
import com.service.CounselingAppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * ③ <b>Counselor Assessment Access Rule v1</b> —— 测评记录的访问规则。
 *
 * <p><b>规则</b>：心理老师仅可访问与其存在<b>已同意预约</b>
 * （{@code counseling_appointment.status = 2}）的学生的心理测评记录。
 *
 * <table border="1">
 *   <caption>预约状态 × 是否授权</caption>
 *   <tr><th>状态</th><th>语义</th><th>可访问</th></tr>
 *   <tr><td>1</td><td>待审核</td><td>否</td></tr>
 *   <tr><td><b>2</b></td><td><b>同意</b></td><td><b>是</b></td></tr>
 *   <tr><td>3</td><td>拒绝</td><td>否</td></tr>
 *   <tr><td>—</td><td>不存在预约</td><td>否</td></tr>
 * </table>
 *
 * <p><b>职责边界（刻意收窄，三条）</b>：
 * <ol>
 *   <li><b>只补 counselor 的缺口</b> —— 学生 / 管理员 / 其他角色的授权仍由既有
 *       {@link OwnershipGuard} 与 {@link DataScope} 负责，本类<b>不重写、不替代、不重新定义</b>；</li>
 *   <li><b>只服务 {@code assessment_record}</b> —— <b>不</b>适用于 message / appointment /
 *       心理档案 / 危机记录 / AI 对话历史等其他数据；
 *       每类敏感数据都必须单独定义规则，<b>不得继承本规则</b>；</li>
 *   <li>不修改 {@link OwnershipGuard}（19 处共用原语），
 *       不修改 {@link DataScope}（保持「只 put params」的通用语义）。</li>
 * </ol>
 *
 * <p><b>与 SQL 的关系（规则的双表达）</b>：列表侧由 {@code AssessmentRecordDao.xml} 的
 * {@code EXISTS} 子查询实现同一规则 —— 逐行过滤无法用 Java 完成。两处必须语义一致，保障手段：
 * <ol>
 *   <li>状态常量只在此处定义，XML 通过 {@code params.approvedStatus} 传入，<b>SQL 中不出现数字 2</b>；</li>
 *   <li>同一测试矩阵分别覆盖列表与详情 —— 若两处漂移，必有一半失败。</li>
 * </ol>
 */
@Component
public class CounselorAssessmentScope {

    /**
     * 预约状态：<b>同意</b>（字典 {@code appointment_status}：1 待审核 / 2 同意 / 3 拒绝）。
     *
     * <p>本规则<b>唯一</b>认可的授权状态 —— 待审核与拒绝均<b>不</b>建立访问权。
     */
    public static final int STATUS_APPROVED = 2;

    /**
     * 传给 Mapper 的参数名（XML 取 {@code params.approvedStatus}）。
     *
     * <p>存在意义：让 SQL 不必写死数字，规则口径只有本类一处定义。
     */
    public static final String PARAM_APPROVED_STATUS = "approvedStatus";

    private final CounselingAppointmentService appointmentService;

    public CounselorAssessmentScope(CounselingAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * 关系判定（<b>供 {@link OwnershipGuard.RelationChecker} 使用</b>）。
     *
     * <p><b>只回答「关系是否存在」，不判断角色</b> ——
     * 「当前角色是否该问这条关系」由 {@link OwnershipGuard} 决定；
     * 本方法被调用时，调用方已确认是「心理老师 + 实体无 {@code counselor_id} 字段」。
     *
     * <p>因此本类<b>不再</b>自行串联第二道授权 —— 授权入口统一为 {@code OwnershipGuard}。
     *
     * @param studentId 目标测评记录所属学生 id
     * @return 存在「已同意预约」→ {@code true}；身份/归属缺失或关系不成立 → {@code false}
     */
    public boolean hasApprovedAppointment(HttpServletRequest request, Integer studentId) {
        Integer counselorId = OwnershipGuard.currentUserId(request);
        if (counselorId == null || studentId == null) {
            // fail-closed：关系不可判定 ⇒ 视为无关系（不得默认放行）
            return false;
        }
        return hasApprovedAppointment(counselorId, studentId);
    }

    /**
     * 是否存在<b>至少一条</b>「已同意」预约（{@code EXISTS} 语义）。
     *
     * <p>多条预约（含历史的 status=1 / status=3）<b>不影响</b>结果 ——
     * 只要存在一条 status=2 即成立。这与 XML 中 {@code EXISTS} 子查询的语义完全一致。
     *
     * @return 存在已同意预约为 {@code true}
     */
    boolean hasApprovedAppointment(Integer counselorId, Integer studentId) {
        return appointmentService.lambdaQuery()
                .eq(CounselingAppointmentEntity::getCounselorId, counselorId)
                .eq(CounselingAppointmentEntity::getStudentId, studentId)
                .eq(CounselingAppointmentEntity::getStatus, STATUS_APPROVED)
                .count() > 0;
    }
}
