package com.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 咨询师读取<b>学生资料</b>的访问规则（`AUTHZ-COUNSELOR-STUDENT-SCOPE`）。
 *
 * <p><b>规则</b>（用户 2026-10-25 裁定）：心理老师仅可访问
 * <b>与其存在至少一条 {@code counseling_appointment.status = 2}（已批准）预约</b>的学生。
 *
 * <table border="1">
 *   <caption>预约状态 × 是否授权</caption>
 *   <tr><th>状态</th><th>语义</th><th>可访问</th></tr>
 *   <tr><td>1</td><td>待审核</td><td><b>否</b></td></tr>
 *   <tr><td><b>2</b></td><td><b>同意</b></td><td><b>是</b></td></tr>
 *   <tr><td>3</td><td>拒绝</td><td><b>否</b></td></tr>
 *   <tr><td>—</td><td>不存在预约</td><td><b>否</b></td></tr>
 * </table>
 *
 * <p>⚠️ <b>待审核不得访问</b>的依据（已核查，非推测）：
 * 预约审核页 `xinlilaoshiOrder/list.vue` 的表格已包含 {@code studentName}，
 * 审核对话框只需 {@code status} + {@code reviewComment}，<b>不调用任何学生详情接口</b>
 * ⇒ 审核流程不依赖「待审核关系」读取学生资料。
 *
 * <p><b>与 {@link CounselorAssessmentScope} 的关系（刻意如此）</b>：
 * <ul>
 *   <li><b>复用其「关系判定」实现</b> —— 直接委派 {@code hasApprovedAppointment}，
 *       <b>不</b>在本类另写一套查询，避免「两套略有差异的关系 SQL」随时间漂移；</li>
 *   <li><b>但不复用其「规则」</b> —— 那个类明确声明只服务 {@code assessment_record}，
 *       且规定「每类敏感数据必须单独定义规则」。本类是<b>学生资料</b>这一类数据的规则载体，
 *       未来若学生资料的可见范围变化（例如纳入「待审核」），只改本类，不影响测评记录。</li>
 * </ul>
 *
 * <p><b>列表侧的双表达</b>：{@code StudentDao.xml} 的 {@code EXISTS} 子查询实现同一规则
 * （逐行过滤无法用 Java 完成）。状态常量只在 {@link CounselorAssessmentScope} 定义一处，
 * SQL 通过 {@code params.approvedStatus} 接收，<b>XML 中不出现数字 2</b>。
 * 两处语义一致性由测试矩阵分别覆盖（列表与详情各自断言）。
 */
@Component
public class CounselorStudentScope {

    /**
     * 传给 Mapper 的参数名（XML 取 {@code params.approvedStatus}）。
     *
     * <p>与 {@link CounselorAssessmentScope#PARAM_APPROVED_STATUS} 同源，
     * 保证「状态口径」全系统只有一处定义。
     */
    public static final String PARAM_APPROVED_STATUS = CounselorAssessmentScope.PARAM_APPROVED_STATUS;

    /** 唯一认可的授权状态（= 已批准）。 */
    public static final int STATUS_APPROVED = CounselorAssessmentScope.STATUS_APPROVED;

    private final CounselorAssessmentScope relationPredicate;

    public CounselorStudentScope(CounselorAssessmentScope relationPredicate) {
        this.relationPredicate = relationPredicate;
    }

    /**
     * 关系判定（供 {@link OwnershipGuard.RelationChecker} 使用）。
     *
     * <p><b>只回答「关系是否存在」，不判断角色</b> ——
     * 「是否该问这条关系」由 {@link OwnershipGuard} 决定
     * （仅「心理老师 + 实体无 {@code counselor_id}」组合才会调用）。
     *
     * <p>⚠️ <b>fail-closed</b>：身份缺失 / {@code studentId} 缺失 / 查询异常
     * 均<b>不得</b>返回 {@code true}。
     *
     * @param studentId 目标学生 id
     * @return 存在「已批准预约」→ {@code true}
     */
    public boolean hasApprovedRelation(HttpServletRequest request, Integer studentId) {
        if (studentId == null) {
            return false;
        }
        // 委派到已批准的关系判定（同一实现，避免规则漂移）
        return relationPredicate.hasApprovedAppointment(request, studentId);
    }
}
