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
