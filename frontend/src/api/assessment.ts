import { http, unwrap, pickList } from './http'

/**
 * 测评记录（字段按后端实际返回，本批不收敛模型）
 * 注：后端还返回 studentPhone / studentEmail 等敏感字段 —— 页面**不渲染**这些字段。
 */
export interface AssessmentRecord {
  id?: number
  recordNo?: string
  studentId?: number
  paperId?: number
  totalScore?: number
  submittedAt?: string
  createdAt?: string
  paperName?: string
  paperDurationMinutes?: number
  paperTotalScore?: number
  paperStatus?: number
  [key: string]: unknown
}

/**
 * 测评记录列表：GET /assessmentRecord/list
 * 需登录 + DataScope（学生仅本人；实测 a1 → 1 条，a2 → 0 条）
 */
export async function listAssessmentRecords(
  params: Record<string, unknown> = {}
): Promise<{ list: AssessmentRecord[]; total: number }> {
  const data = await unwrap<unknown>(http.get('/assessmentRecord/list', { params }))
  return pickList<AssessmentRecord>(data)
}
