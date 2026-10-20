import { http, unwrap, pickList } from './http'

/** 字典项（后端 sys_dict_item：dict_code / item_code / item_name） */
export interface DictItem {
  id?: number
  dictCode?: string
  itemCode?: string
  itemName?: string
  dictName?: string
}

/**
 * 字典项：GET /sysDictItem/page?dictCode=（公开白名单 ✓，实测过滤生效 ✓）
 *
 * ⚠️ 前端**不硬编码**业务字典（如 time_slot 的 8 个时段）✗ ——
 *    一律「取字典 → 显示 itemName → 提交 itemCode」，避免把库内配置复制一份到前端。
 */
export async function listDictItems(dictCode: string): Promise<DictItem[]> {
  const data = await unwrap<unknown>(
    http.get('/sysDictItem/page', { params: { dictCode, page: 1, limit: 200 } })
  )
  return pickList<DictItem>(data).list
}
