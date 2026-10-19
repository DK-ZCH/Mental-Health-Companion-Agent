import { http, unwrap } from './http'

export interface LoginResult {
  code: number
  role?: string
  userId?: number
  token?: string
  username?: string
  tableName?: string
}

/**
 * 学生登录：GET /student/login?username=&password=
 *
 * ⚠️ 契约事实（实测）：登录接口把 token / role / userId / tableName 放在**响应顶层**，
 *    而 /knowledgeArticle/list 等业务接口把结果放在 `data` 中 ⇒ 两种形状都兼容。
 *    （Step 7 首批曾假设统一在 data 内，导致 r.token undefined —— 已修正并登记。）
 */
export async function loginStudent(username: string, password: string): Promise<LoginResult> {
  const res = await http.get('/student/login', { params: { username, password } })
  const body = res.data as LoginResult & { msg?: string; data?: LoginResult }
  if (!body || body.code !== 0) {
    throw new Error(body?.msg || `请求失败（code=${body?.code}）`)
  }
  const inner = body.data
  return inner && inner.token ? inner : body
}

/** 会话校验：GET /student/session（结果在 data 内） */
export async function fetchStudentSession(): Promise<Record<string, unknown>> {
  return unwrap<Record<string, unknown>>(http.get('/student/session'))
}
