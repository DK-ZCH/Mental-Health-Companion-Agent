import axios, { type AxiosInstance, type AxiosError, type AxiosResponse } from 'axios'

/**
 * 统一 HTTP 客户端（Step 7 / Batch 2）
 *
 * 后端 **实测合同**（见 docs/PHASE3-STEP7-BATCH2A-AUTH-AUDIT.md §3）：
 *   未登录 / token 失效 → HTTP 200 + body.code=401 (msg=请先登录)
 *   已登录无权访问       → HTTP 403 + body.code=403 (msg=无权访问)
 *   业务异常            → HTTP 200 + body.code=其它非 0
 *
 * ⇒ 因此**不能**只判断 `error.response.status === 401` ✗：
 *    当前后端的 401 走的是 **2xx 成功分支**，Axios 根本不会进 error 分支。
 *
 * 判定规则（同时兼容当前合同与未来标准 HTTP 401/403）：
 *   登录失效 = (200 && code===401) || HTTP 401  ⇒ 清认证态 + 跳 /login?redirect=
 *   无权访问 = (403 && code===403) || HTTP 403  ⇒ **保持登录态**，仅抛 ApiError(403)
 */
export const TOKEN_KEY = 'mh_token'

/** 带业务码的错误：让上层能区分 401 / 403 / 其它，而不只是一个 message */
export class ApiError extends Error {
  /** 业务码（body.code）；无 body 时为 HTTP 状态码 */
  code: number
  /** HTTP 状态码（0 表示无响应，如网络错误） */
  httpStatus: number

  constructor(code: number, message: string, httpStatus = 200) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

/**
 * 登录失效回调：由 main.ts 注入（清理 Pinia + 跳登录），
 * 避免 http.ts → stores → api → http.ts 的循环依赖。
 */
type AuthFailureHandler = (redirectPath: string) => void
let onAuthFailure: AuthFailureHandler | null = null

export function setAuthFailureHandler(handler: AuthFailureHandler | null): void {
  onAuthFailure = handler
}

function notifyAuthFailure(): void {
  const current = window.location.pathname + window.location.search
  onAuthFailure?.(current)
}

export const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

/** 请求：自动注入 Token（唯一注入点；业务代码不得手写 Token） */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers = config.headers ?? {}
    config.headers['Token'] = token
  }
  return config
})

http.interceptors.response.use(
  (res: AxiosResponse) => {
    const body = res.data as { code?: number; msg?: string } | undefined
    const code = body?.code

    // 未登录（后端既有合同：HTTP 200 + code=401）
    if (code === 401) {
      notifyAuthFailure()
      return Promise.reject(new ApiError(401, body?.msg || '登录已失效', res.status))
    }
    // 无权访问：**绝不清登录态**
    if (code === 403) {
      return Promise.reject(new ApiError(403, body?.msg || '无权访问', res.status))
    }
    return res
  },
  (err: AxiosError) => {
    const status = err.response?.status ?? 0
    const body = err.response?.data as { code?: number; msg?: string } | undefined

    // 兼容标准 HTTP 401
    if (status === 401) {
      notifyAuthFailure()
      return Promise.reject(new ApiError(401, body?.msg || '登录已失效', status))
    }
    // 兼容标准 HTTP 403：保持登录态
    if (status === 403) {
      return Promise.reject(new ApiError(403, body?.msg || '无权访问', status))
    }

    const code = body?.code ?? status
    const msg = body?.msg || err.message || '请求失败'
    return Promise.reject(new ApiError(code || 0, msg, status))
  }
)

export interface ApiResult<T = unknown> {
  code: number
  msg?: string
  data?: T
}

/** 解包 { code, msg, data }；非 0 抛 ApiError（保留 code） */
export async function unwrap<T>(p: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const res = await p
  const body = res.data
  if (!body || body.code !== 0) {
    throw new ApiError(body?.code ?? -1, body?.msg || `请求失败（code=${body?.code}）`)
  }
  return body.data as T
}

/**
 * 列表归一：后端 `/list` 可能返回数组，也可能返回分页对象 { total, list, ... }。
 * （实测 /knowledgeArticle/list、/assessmentRecord/list 均为分页对象）
 */
export function pickList<T>(data: unknown): { list: T[]; total: number } {
  if (Array.isArray(data)) return { list: data as T[], total: (data as T[]).length }
  const maybe = data as { list?: T[]; total?: number } | null
  const list = Array.isArray(maybe?.list) ? (maybe!.list as T[]) : []
  const total = typeof maybe?.total === 'number' ? maybe!.total! : list.length
  return { list, total }
}
