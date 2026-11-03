import { http, ApiError } from './http'

/**
 * Agent 对话 + 多轮会话（Phase 4 · Step ⑤）
 *
 * ⚠️ 响应结构与常规 `{ code, msg, data }` **不同**：
 *    后端 `R.ok().put("answer", ...)` ⇒ `{ code: 0, answer: "...", conversationId }`
 *    （answer 在**顶层**）⇒ 不能用 `unwrap()`，必须自行解析。
 *
 * 失败语义（后端已实现）：
 *   · 未登录        → HTTP 200 + code=401（由 http.ts 拦截 → 清态跳登录）
 *   · message 空/超长 → HTTP 400 + code=400
 *   · 会话不存在/不属于我 → HTTP 403 + code=403（防 IDOR；也防存在性预言机）
 *   · 上游/模型异常 → HTTP 200 + code=500（统一文案，不含内部细节）
 */
export interface AgentChatResult {
  code: number
  msg?: string
  answer?: string
  /** 本次请求所属会话；首次对话由服务端创建 */
  conversationId?: number
  safety?: { triggered?: boolean; resourceStatus?: string }
}

/** 会话列表项（仅本人会话） */
export interface AgentConversationItem {
  id: number
  title?: string | null
  createdAt?: string
  lastMsgAt?: string | null
}

/** 会话历史消息（role: user / assistant） */
export interface AgentMessageItem {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
}

/** 单轮提问；conversationId 缺省 = 新会话（响应中返回服务端创建的 id） */
export async function askAgent(
  message: string,
  conversationId?: number
): Promise<{ answer: string; conversationId?: number }> {
  const payload: { message: string; conversationId?: number } = { message }
  if (conversationId != null) {
    payload.conversationId = conversationId
  }
  const res = await http.post<AgentChatResult>('/agent/chat', payload)
  const body = res.data

  if (!body || body.code !== 0) {
    throw new ApiError(body?.code ?? -1, body?.msg || `请求失败（code=${body?.code}）`)
  }
  return { answer: body.answer || '', conversationId: body.conversationId }
}

/** 我的会话列表（按最近消息倒序；绝不包含他人会话） */
export async function listConversations(): Promise<AgentConversationItem[]> {
  const res = await http.get<{ code: number; data?: AgentConversationItem[] }>('/agent/conversations')
  const body = res.data
  if (!body || body.code !== 0) {
    throw new ApiError(body?.code ?? -1, body?.msg || `请求失败（code=${body?.code}）`)
  }
  return body.data ?? []
}

/** 某会话的历史消息（时间正序；他人会话 ⇒ 403） */
export async function getConversationMessages(conversationId: number): Promise<AgentMessageItem[]> {
  const res = await http.get<{ code: number; data?: AgentMessageItem[] }>(
    `/agent/conversations/${conversationId}/messages`
  )
  const body = res.data
  if (!body || body.code !== 0) {
    throw new ApiError(body?.code ?? -1, body?.msg || `请求失败（code=${body?.code}）`)
  }
  return body.data ?? []
}

/** 删除会话（物理删除，含全部消息；他人会话 ⇒ 403） */
export async function deleteConversation(conversationId: number): Promise<void> {
  const res = await http.delete<{ code: number }>(`/agent/conversations/${conversationId}`)
  const body = res.data
  if (!body || body.code !== 0) {
    throw new ApiError(body?.code ?? -1, body?.msg || `请求失败（code=${body?.code}）`)
  }
}
