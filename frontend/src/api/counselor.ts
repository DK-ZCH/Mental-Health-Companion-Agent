import { http, unwrap, pickList } from './http'

/**
 * 心理老师 —— **仅安全字段白名单** ✓
 *
 * 后端 `/counselor/list` 实际还返回 `phone` / `email` / `username` ✗：
 *   · 该暴露属【既有挂账】（docs/jiaojie.md:253 —— 归 phone-email 批次，后端投影治理）
 *   · 前端**不把这些字段纳入业务模型** ✗（后端返回 ≠ 前端应建模）
 */
export interface Counselor {
  id?: number
  name?: string
  avatarUrl?: string
  genderLabel?: string
  expertise?: string
  resume?: string
  introduction?: string
}

/** 心理老师目录：GET /counselor/list（@IgnoreAuth 公开 ✓，实测 3 条） */
export async function listCounselors(): Promise<Counselor[]> {
  const data = await unwrap<unknown>(http.get('/counselor/list'))
  const raw = pickList<Record<string, unknown>>(data).list

  // 字段白名单投影：只保留安全字段，PII 不进入前端业务模型
  return raw.map((r) => ({
    id: r.id as number | undefined,
    name: r.name as string | undefined,
    avatarUrl: r.avatarUrl as string | undefined,
    genderLabel: r.genderLabel as string | undefined,
    expertise: r.expertise as string | undefined,
    resume: r.resume as string | undefined,
    introduction: r.introduction as string | undefined
  }))
}
