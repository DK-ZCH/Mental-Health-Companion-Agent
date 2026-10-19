import axios, { type AxiosInstance } from 'axios'

/**
 * 统一 HTTP 客户端（Step 6 设计 §6：Token 头 + 错误归一）
 * - 后端所有业务响应形如 { code, msg, data }
 * - code === 0 视为成功；401 视为未登录
 */
export const TOKEN_KEY = 'mh_token'

export const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers = config.headers ?? {}
    config.headers['Token'] = token
  }
  return config
})

export interface ApiResult<T = unknown> {
  code: number
  msg?: string
  data?: T
}

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
    }
    return Promise.reject(err)
  }
)

/** 解包 { code, msg, data }；非 0 抛错（附后端 msg） */
export async function unwrap<T>(p: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const res = await p
  const body = res.data
  if (!body || body.code !== 0) {
    const msg = body?.msg || `请求失败（code=${body?.code}）`
    throw new Error(msg)
  }
  return body.data as T
}
