import { http, unwrap, pickList } from './http'

/**
 * 收藏类型 —— **业务契约常量**（唯一来源 ✓）
 *
 * 字典 `counselor_favorite_type` 当前仅 `1 = 收藏`。
 * ⚠️ 后端查重形如 `eq("favorite_type", …)`：若传 `null`，SQL 永不匹配 ⇒ **查重被绕过** ✗
 * ⇒ 固定为 1，**不接受调用方各自传参**（避免语义分裂）。
 */
const FAVORITE_TYPE = 1

/** 收藏项（字段按后端实际返回；响应中的学生/老师联系方式属 PII，前端**不渲染**且不纳入类型 ✗） */
export interface FavoriteItem {
  id?: number
  counselorId?: number
  studentId?: number
  favoriteType?: number
  favoriteTypeLabel?: string
  favoritedAt?: string
  createdAt?: string
  counselorName?: string
  counselorAvatarUrl?: string
  counselorExpertise?: string
  counselorIntroduction?: string
}

/** 我的收藏：GET /counselorFavorite/list（DataScope ⇒ 学生仅本人 ✓） */
export async function listMyFavorites(): Promise<FavoriteItem[]> {
  const data = await unwrap<unknown>(http.get('/counselorFavorite/list'))
  return pickList<FavoriteItem>(data).list
}

/**
 * 是否已收藏某老师：GET /counselorFavorite/list?counselorId=
 * 实测：该过滤**生效** ✓（A 已收藏老师1 → 1 条；未收藏 → 0 条）
 */
export async function findFavoriteByCounselor(
  counselorId: number | string
): Promise<FavoriteItem | undefined> {
  const data = await unwrap<unknown>(
    http.get('/counselorFavorite/list', { params: { counselorId } })
  )
  return pickList<FavoriteItem>(data).list[0]
}

/**
 * 收藏老师：POST /counselorFavorite/add
 * `studentId` 由服务端强制本人 ✓（前端不提交）；重复收藏 → `code=511`
 */
export async function addFavorite(counselorId: number | string): Promise<void> {
  await unwrap<unknown>(
    http.post('/counselorFavorite/add', {
      counselorId: Number(counselorId),
      favoriteType: FAVORITE_TYPE
    })
  )
}

/**
 * 取消收藏：POST /counselorFavorite/delete （body 为 id 数组）
 *
 * ⚠️ 该端点**无归属校验**（`SEC-FAVORITE-DELETE-IDOR` 🔴 P1，Identified / Unfixed）：
 *    因此**只允许传入 `/list` 返回的自有记录 id** ✓ —— 绝不由 counselorId 自行推导 ✗
 */
export async function removeFavorite(id: number): Promise<void> {
  await unwrap<unknown>(http.post('/counselorFavorite/delete', [id]))
}
