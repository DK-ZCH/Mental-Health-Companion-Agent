import { http, ApiError } from './http'

/**
 * Agent 对话（Phase 4 / Step 11 后端：POST /agent/chat）
 *
 * ⚠️ 响应结构与常规 `{ code, msg, data }` **不同**：
 *    后端 `R.ok().put("answer", ...)` ⇒ `{ code: 0, answer: "..." }`
 *    （answer 在**顶层**，不在 data 里）⇒ 不能用 `unwrap()`，必须自行解析。
 *
 * 失败语义（后端已实现）：
 *   · 未登录        → HTTP 200 + code=401（由 http.ts 拦截 → 清态跳登录）
 *   · message 为空/超长 → HTTP 400 + code=400
 *   · 上游/模型异常 → HTTP 200 + code=500（统一文案，不含内部细节）
 */
export interface AgentChatResult {
  code: number
  msg?: string
  answer?: string
}

/** 单轮提问（无历史、无多轮 —— Step 8 边界） */
export async function askAgent(message: string): Promise<string> {
  const res = await http.post<AgentChatResult>('/agent/chat', { message })
  const body = res.data

  if (!body || body.code !== 0) {
    throw new ApiError(body?.code ?? -1, body?.msg || `请求失败（code=${body?.code}）`)
  }
  return body.answer || ''
}
