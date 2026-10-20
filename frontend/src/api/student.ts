import { http, unwrap } from './http'

/** 当前登录学生（/student/session 返回 data 对象） */
export interface CurrentStudent {
  id?: number
  username?: string
  name?: string
  phone?: string
  email?: string
  avatarUrl?: string
  gender?: string
  createdAt?: string
  [key: string]: unknown
}

/**
 * 当前登录者信息：GET /student/session
 * 需登录（未登录 → 后端 HTTP 200 + code=401，由 http.ts 统一清态并回登录页）
 */
export async function getCurrentStudent(): Promise<CurrentStudent> {
  return unwrap<CurrentStudent>(http.get('/student/session'))
}

/**
 * 学生资料更新：POST /student/update
 *
 * 契约（实测，见 docs/PHASE3-STEP7-R10-WRITE-CONTRACT-AUDIT.md）：
 *   · 需登录 ✓；`OwnershipGuard.assertSelfOrAdmin` ⇒ **id 必须是本人**（他人 → 403）
 *   · 学生侧服务端会恢复 `username` / `gender` / `idCardNo` ⇒ **前端绝不提交这些字段** ✗
 *   · `password` 标注 `@JsonIgnore` ⇒ 不可绑定 ✗
 *   · 空更新 → HTTP 400（`UpdateGuard`）
 *   · 唯一性冲突（username/phone/idCardNo）→ `code=511`（业务错误，非 4xx）
 *   · `createdAt` 可绑定但未被服务端恢复 ⇒ **前端绝不提交** ✗（P2 已登记）
 *
 * ⚠️ 本批**不调用任何上传/下载 file 端点**（匿名可上传 = 既有 P1；下载路径穿越 = 既有 P0，
 *    两者均不纳入新功能调用链）。
 */
export interface StudentUpdatePayload {
  /** 必须来自当前会话的 id（不得来自 URL / 用户输入） */
  id: number
  name?: string
  phone?: string
  email?: string
  /** 仅接受 http(s):// 或站内 /upload/...，由页面校验；本接口不做任何 URL 抓取 */
  avatarUrl?: string
}

export async function updateStudent(payload: StudentUpdatePayload): Promise<void> {
  // 白名单构造：只发送允许的 4 个可改字段 + id，杜绝身份字段外溢
  const body: StudentUpdatePayload = { id: payload.id }
  if (payload.name !== undefined) body.name = payload.name
  if (payload.phone !== undefined) body.phone = payload.phone
  if (payload.email !== undefined) body.email = payload.email
  if (payload.avatarUrl !== undefined) body.avatarUrl = payload.avatarUrl

  await unwrap<unknown>(http.post('/student/update', body))
}
