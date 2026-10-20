import { http, unwrap, pickList } from './http'

/**
 * 预约状态常量 —— **前端业务契约，不是后端约束** ✗
 *
 * 字典 `appointment_status` = `1 待审核` / `2 同意` / `3 拒绝`（均为**老师侧审核**语义 ✓）。
 * ⚠️ 后端 `/add` 与 `/update` **都不限制 status**（`SEC-APPOINTMENT-STATUS-SPOOF` 🔴 P1，Identified / Unfixed）
 * ⇒ 前端固定 `1`，只是保证**本方 UI 不主动制造 `status=2`** ✓，**不代表后端已修复** ✗
 */
const STATUS_PENDING = 1

/** 预约项（字段按后端实际返回；响应中的学生/老师联系方式属 PII，前端**不渲染**且不纳入类型 ✗） */
export interface AppointmentItem {
  id?: number
  appointmentNo?: string
  counselorId?: number
  studentId?: number
  appointmentDate?: string
  timeSlot?: number
  timeSlotLabel?: string
  status?: number
  statusLabel?: string
  reviewComment?: string
  appliedAt?: string
  createdAt?: string
  counselorName?: string
  counselorAvatarUrl?: string
  counselorExpertise?: string
}

/** 我的预约：GET /counselingAppointment/list（DataScope ⇒ 学生仅本人 ✓） */
export async function listMyAppointments(): Promise<AppointmentItem[]> {
  const data = await unwrap<unknown>(http.get('/counselingAppointment/list'))
  return pickList<AppointmentItem>(data).list
}

/**
 * 新增预约：POST /counselingAppointment/add
 * · `studentId` 由服务端强制为当前用户 ✓（前端不提交）
 * · `status` 固定 `1`（见上）
 * · `appointmentDate` 传 `yyyy-MM-dd`（与旧前端一致 ✓）
 */
export async function addAppointment(payload: {
  counselorId: number | string
  appointmentDate: string
  timeSlot: number | string
}): Promise<void> {
  await unwrap<unknown>(
    http.post('/counselingAppointment/add', {
      counselorId: Number(payload.counselorId),
      appointmentDate: payload.appointmentDate,
      timeSlot: Number(payload.timeSlot),
      status: STATUS_PENDING
    })
  )
}

/**
 * 取消预约：POST /counselingAppointment/delete （body 为 id 数组）
 *
 * ⚠️ 本系统的取消 = **物理删除** ✓（状态字典**没有**"已取消" ✗；`status=3` 是老师审核拒绝 ✗）
 * ⚠️ 该端点**无归属校验**（`SEC-APPOINTMENT-DELETE-IDOR` 🔴 P1，Identified / Unfixed）
 *    ⇒ **只允许传入 `/list` 返回的自有预约 id** ✓ —— 绝不由 counselorId/日期推导 ✗
 */
export async function removeAppointment(id: number): Promise<void> {
  await unwrap<unknown>(http.post('/counselingAppointment/delete', [id]))
}
