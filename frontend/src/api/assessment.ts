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

/**
 * 按 recordNo 取本人记录 —— **从 /list 客户端匹配** ✓
 *
 * ⚠️ 严禁改用记录模块的 detail 端点：
 *    该端点缺 `OwnershipGuard`（`SEC-DETAIL-IDOR` 🔴 P1），实测学生 B 可读到学生 A 的记录。
 *    `/list` 有 DataScope（A=1 / B=空 ✓），因此以 list 为唯一可信数据源。
 */
export async function getRecordByNo(recordNo: string): Promise<AssessmentRecord | undefined> {
  const { list } = await listAssessmentRecords()
  return list.find((r) => String(r.recordNo) === String(recordNo))
}

/** 答题明细项（字段按后端实际返回；**不渲染** studentPhone/studentEmail） */
export interface AssessmentAnswerItem {
  id?: number
  recordNo?: string
  studentId?: number
  questionId?: number
  studentAnswer?: string
  score?: number
  createdAt?: string
  questionContent?: string
  questionOptions?: string
  questionScore?: number
  questionAnswer?: string
  questionAnalysis?: string
  questionTypeLabel?: string
  questionSortOrder?: number
}

/**
 * 答题明细：GET /assessmentAnswer/list?recordNo=
 * 服务端强制本人（实测 A=5 条 / B=空 ✓）；`?recordNo=` 过滤生效 ✓
 */
export async function listAnswersByRecordNo(recordNo: string): Promise<AssessmentAnswerItem[]> {
  const data = await unwrap<unknown>(http.get('/assessmentAnswer/list', { params: { recordNo } }))
  return pickList<AssessmentAnswerItem>(data).list
}

/** 错题项（字段按后端实际返回；**不渲染** studentPhone/studentEmail） */
export interface WrongQuestionItem {
  id?: number
  studentId?: number
  paperId?: number
  questionId?: number
  studentAnswer?: string
  answeredAt?: string
  paperName?: string
  questionContent?: string
  questionOptions?: string
  questionScore?: number
  questionAnswer?: string
  questionAnalysis?: string
  questionTypeLabel?: string
  questionSortOrder?: number
}

/**
 * 错题列表：GET /assessmentWrongQuestion/list
 * 服务端强制本人（实测 A=3 条 / B=空 / A 伪造 studentId 仍 3 条 ✓）
 * ⚠️ 身份**只**来自 token，前端不得把 studentId 当可信身份来源 ✗
 */
export async function listWrongQuestions(): Promise<WrongQuestionItem[]> {
  const data = await unwrap<unknown>(http.get('/assessmentWrongQuestion/list'))
  return pickList<WrongQuestionItem>(data).list
}

/** 试卷项（公开只读；**不含题目内容** ✗） */
export interface AssessmentPaperItem {
  id?: number
  name?: string
  durationMinutes?: number
  totalScore?: number
  status?: number
  statusLabel?: string
}

/**
 * 试卷列表：`GET /assessmentPaper/list`（**`@IgnoreAuth` 公开** ✓）
 *
 * ⚠️ 2026-10-27「资源可见性」批次后，本端点对**非管理视角**只返回
 *    「`status=1(启用)` 且 `is_deleted=0(未删除)`」的试卷。
 *    当前种子数据两张试卷均为 `is_deleted=1` ⇒ 实测 `total=0`
 *    —— 这是**规则生效**，不是接口故障（管理员视角仍可见全部）。
 *
 * ⚠️ R14 边界 = **试卷浏览**：
 *  · **不调用** `/assessmentQuestion/list` —— 题目内容不属于「试卷浏览」所需
 *    （该端点的 `answer` / `analysis` 现已被 `QuestionPublicDTO` 剥离，
 *      `SEC-QUESTION-ANSWER-PUBLIC` 状态由 `Unfixed` 更正为
 *      **PENDING REVIEW / 待复核关闭**；但「不需要就不调用」的边界不变）
 *  · **不提供**开始答题 / 不创建测评记录 / 不做任何计分
 *    （R15 暂缓：`SEC-ANSWER-SCORE-SPOOF` —— 分值由客户端提交并被服务端累加 ✗）
 */
export async function listPapers(): Promise<AssessmentPaperItem[]> {
  const data = await unwrap<unknown>(http.get('/assessmentPaper/list'))
  const raw = pickList<Record<string, unknown>>(data).list

  // 字段白名单：仅试卷元信息（无题目、无答案、无 PII）
  return raw.map((r) => ({
    id: r.id as number | undefined,
    name: r.name as string | undefined,
    durationMinutes: r.durationMinutes as number | undefined,
    totalScore: r.totalScore as number | undefined,
    status: r.status as number | undefined,
    statusLabel: r.statusLabel as string | undefined
  }))
}
