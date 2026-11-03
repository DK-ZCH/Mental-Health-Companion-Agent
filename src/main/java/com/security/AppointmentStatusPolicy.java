package com.security;

import com.entity.CounselingAppointmentEntity;

/**
 * 预约状态来源与状态转换（Phase 4 / Round 4 · 用户 Q3-c 裁定）。
 *
 * <p><b>为什么需要它</b>：{@code /add} {@code /save} {@code /update} 此前<b>从不</b>设置 status
 * （唯一一处 {@code setStatus} 是注释），因此学生可以 {@code POST status=2} 让预约「自我审批」。
 * 而本轮要启用「一师一槽一有效预约」的唯一约束 ——
 * 若不先修状态来源，这条缺陷会升级成「<b>长期占槽</b>」手段。
 *
 * <p><b>本类不是「禁止 update 改 status」</b>（那是错误理解）：
 * <ul>
 *   <li>合法审核流必须保留：心理老师 / 管理员 {@code 1 → 2}（同意）、{@code 1 → 3}（拒绝）；</li>
 *   <li>只禁止<b>没有业务依据</b>的跳转：{@code 2→1}、{@code 2→3}、{@code 3→1}、{@code 3→2}；</li>
 *   <li>学生提交的 status 一律<b>被忽略</b>（保留库里原值），不是报错 —— 避免影响其它字段的正常更新。</li>
 * </ul>
 *
 * <p><b>为什么学生是「忽略」而不是「拒绝」</b>：{@code /update} 也承载学生修改其它字段的场景；
 * 若因为夹带 status 就整体 403，会把合法更新一起打断。这里采用「静默丢弃 + 保留原值」。
 */
public final class AppointmentStatusPolicy {

    /** 待审核（创建时的唯一合法初始状态）*/
    public static final int STATUS_PENDING = 1;

    /** 老师同意 */
    public static final int STATUS_APPROVED = 2;

    /** 老师拒绝（不占用槽位；与「取消 = 物理删除」不同）*/
    public static final int STATUS_REJECTED = 3;

    private static final String ROLE_STUDENT = "学生";
    private static final String ROLE_COUNSELOR = "心理老师";
    private static final String ROLE_ADMIN = "管理员";

    private AppointmentStatusPolicy() {
    }

    /**
     * 创建路径：无条件置为待审核，<b>忽略</b>客户端/模型传入的任何 status。
     *
     * <p>适用：{@code /add}、{@code /save}（无 id 时）。
     * 管理员创建同样强制 {@code 1} —— 不设「管理员可直接建成已同意」的越权通道，
     * 否则审计上无法区分「管理员代表用户创建」与「绕过审核」。
     */
    public static void forceCreateStatus(CounselingAppointmentEntity entity) {
        if (entity == null) {
            return;
        }
        entity.setStatus(STATUS_PENDING);
    }

    /**
     * 更新路径：按角色与合法转换决定最终 status。
     *
     * @param incoming      客户端提交的实体（本方法会就地修正其 status）
     * @param currentStatus 数据库中该记录的当前 status（可能为 {@code null}，历史脏数据）
     * @param role          当前登录角色（{@code 学生} / {@code 心理老师} / {@code 管理员}）
     * @throws ForbiddenException 角色无权变更，或转换没有业务依据
     */
    public static void resolveUpdateStatus(CounselingAppointmentEntity incoming,
                                           Integer currentStatus,
                                           String role) {
        if (incoming == null) {
            return;
        }
        Integer requested = incoming.getStatus();

        // 学生：不参与审核，status 一律保留原值（其它字段仍可正常更新）
        if (ROLE_STUDENT.equals(role)) {
            incoming.setStatus(currentStatus);
            return;
        }

        // 未提交 status ⇒ 不改变
        if (requested == null) {
            incoming.setStatus(currentStatus);
            return;
        }

        // 同值视为「无变化」，允许（用于更新其它字段）
        if (requested.equals(currentStatus)) {
            return;
        }

        boolean reviewer = ROLE_COUNSELOR.equals(role) || ROLE_ADMIN.equals(role);
        boolean allowedTransition = currentStatus != null
                && currentStatus == STATUS_PENDING
                && (requested == STATUS_APPROVED || requested == STATUS_REJECTED);

        if (!reviewer || !allowedTransition) {
            // 只记录角色与状态对（不含用户标识），便于审计但不泄露隐私
            throw new ForbiddenException();
        }
    }
}
