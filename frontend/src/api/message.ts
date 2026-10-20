import { http, unwrap, pickList } from './http'

/**
 * 咨询留言（R13）
 *
 * **数据模型事实**：`counselor_message` = 一条留言 + 一条回复（`content` / `replyContent`）
 *   · 无 `parentId` / 无会话表 / **无已读字段** ⇒ 本模块**不做聊天流、不做未读** ✗
 *   · 回复由**管理员在管理端**录入（Phase 2 批 4-B Q1 取证）⇒ 学生端**只读回复** ✗
 *
 * **安全边界（均 Identified / Unfixed）**：
 *   · `SEC-MESSAGE-DELETE-IDOR` 🔴 P1 —— `/delete` 无归属校验 ⇒ 本模块**不提供删除** ✗
 *   · `SEC-MESSAGE-REPLY-SPOOF` —— `/add` `replyContent` 可被客户端绑定
 *     （**已实测**：学生提交 `replyContent` → 落库成功 ✗）⇒ 前端**只提交 `counselorId` + `content`** ✓
 *   · `SEC-DETAIL-IDOR` 🔴 P1 —— `/detail/{id}` 无守卫 ⇒ 本模块**不使用**，只走 `/list` ✓
 */
export interface MessageItem {
  id?: number
  counselorId?: number
  counselorName?: string
  content?: string
  sentAt?: string
  replyContent?: string
  repliedAt?: string
}

/**
 * 我的留言：`GET /counselorMessage/list`
 * DataScope 生效（实测 a1 → ids 6,2；a2 → ids 4,1）✓
 */
export async function listMyMessages(): Promise<MessageItem[]> {
  const data = await unwrap<unknown>(http.get('/counselorMessage/list'))
  const raw = pickList<Record<string, unknown>>(data).list

  // 字段白名单投影：响应含 studentPhone / studentEmail / counselorPhone / counselorEmail（PII）✗
  // → 不纳入前端业务模型（后端返回 ≠ 前端应建模）
  return raw.map((r) => ({
    id: r.id as number | undefined,
    counselorId: r.counselorId as number | undefined,
    counselorName: r.counselorName as string | undefined,
    content: r.content as string | undefined,
    sentAt: r.sentAt as string | undefined,
    replyContent: r.replyContent as string | undefined,
    repliedAt: r.repliedAt as string | undefined
  }))
}

/**
 * 提交留言：`POST /counselorMessage/add`
 *
 * · **仅提交 `counselorId` + `content`** ✓
 *   `studentId` 由服务端强制为当前用户（实测：未提交也落库为 1 ✓）
 * · **绝不提交** `replyContent` / `repliedAt` / `studentId` ✗
 *   （后端不拦，属 `SEC-MESSAGE-REPLY-SPOOF`；前端只能保证本方不制造）
 */
export async function addMessage(payload: {
  counselorId: number | string
  content: string
}): Promise<void> {
  await unwrap<unknown>(
    http.post('/counselorMessage/add', {
      counselorId: Number(payload.counselorId),
      content: payload.content
    })
  )
}
