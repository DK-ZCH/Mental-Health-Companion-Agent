import { http, unwrap, pickList } from './http'

/** 健康知识（列表即含正文；详情页数据不超出列表接口 —— G1 过渡策略） */
export interface KnowledgeArticle {
  id?: number
  title?: string
  content?: string
  coverUrl?: string
  category?: number
  categoryLabel?: string
  publishedAt?: string
  createdAt?: string
}

/** 通知（与知识同构） */
export interface NotificationItem {
  id?: number
  title?: string
  content?: string
  coverUrl?: string
  category?: number
  categoryLabel?: string
  publishedAt?: string
  createdAt?: string
}

/** 健康知识列表：GET /knowledgeArticle/list（@IgnoreAuth 公开 ✓）
 *  Step ⑥：该接口为分页（service 缺省 page=1/limit=10），语料超过 10 篇后会溢出第一页 ⇒
 *  显式传 page/limit 一次拉全（裁定 A：仅此处最小修改，不加分页 UI、不动后端） */
export async function listKnowledge(): Promise<KnowledgeArticle[]> {
  const data = await unwrap<unknown>(http.get('/knowledgeArticle/list', { params: { page: 1, limit: 100 } }))
  return pickList<KnowledgeArticle>(data).list
}

/** 通知列表：GET /notification/list（@IgnoreAuth 公开 ✓） */
export async function listNotifications(): Promise<NotificationItem[]> {
  const data = await unwrap<unknown>(http.get('/notification/list'))
  return pickList<NotificationItem>(data).list
}

// 注：G1 的「拉全表 + 按 id 匹配」工具 `findById` 已移入 `api/http.ts`（共享层）
