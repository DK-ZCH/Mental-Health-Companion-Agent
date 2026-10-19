import { http, unwrap } from './http'

export interface KnowledgeArticle {
  id: number
  title?: string
  content?: string
  coverUrl?: string
  createdAt?: string
}

/** 后端 /list 可能返回数组，也可能返回分页对象 { total, list }（实测为后者） */
interface ListShape<T> {
  list?: T[]
  total?: number
}

function toArray<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[]
  const maybe = data as ListShape<T> | null
  if (maybe && Array.isArray(maybe.list)) return maybe.list
  return []
}

/** 健康知识列表：GET /knowledgeArticle/list（@IgnoreAuth 公开 ✓，Step 5c 已验证 ✓） */
export async function listKnowledge(): Promise<KnowledgeArticle[]> {
  const data = await unwrap<unknown>(http.get('/knowledgeArticle/list'))
  return toArray<KnowledgeArticle>(data)
}
