<script setup lang="ts">
/**
 * Dashboard（Phase 3 / Step 8）—— 登录后首页
 *
 * 【定位】用户**数据活动概览**，不是心理评估。
 *   ❌ 禁止出现「今天心理状态不错」「心理健康状况良好」等推断性文案
 *   ✅ 只陈述平台内发生了什么：「今日完成 1 次测评」「老师已回复 2 条」
 *
 * 【数据加载】四个区块**独立加载、独立失败**（等价区块级隔离）：
 *   一个接口失败不影响其他区块，也不整页白屏。
 *   ⚠️ 本视图仅在**已登录**时渲染（由 HomeEntryView 分流），
 *      因此可以安全调用需登录 API —— 未登录时不会进入本组件。
 *
 * 【计算规则】全部集中在下方纯函数中，不下沉到 api 层（api 只取数+字段投影）。
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { listAssessmentRecords, type AssessmentRecord } from '@/api/assessment'
import { listMyAppointments, type AppointmentItem } from '@/api/appointment'
import { listMyMessages, type MessageItem } from '@/api/message'
import { listKnowledge, type KnowledgeArticle } from '@/api/content'
import {
  askAgent,
  submitPlanAction,
  listConversations,
  getConversationMessages,
  deleteConversation,
  type AgentConversationItem,
  type PlanState,
  } from '@/api/agent'
import {
  remainingSeconds,
  planStateOnTick,
  planStateOnFailure,
  canConfirm,
} from '@/utils/planState'
import SectionCard from '@/components/dashboard/SectionCard.vue'
import StatCard from '@/components/dashboard/StatCard.vue'
import EmptyBlock from '@/components/dashboard/EmptyBlock.vue'

/** 每个区块最多展示条数 */
const MAX_PREVIEW = 3
/** 与后端 AgentController.MAX_MESSAGE_LENGTH 保持一致 */
const MAX_MESSAGE_LENGTH = 1000
/** 预约状态：2 = 老师已同意（字典 appointment_status） */
const STATUS_APPROVED = 2

const MSG_LOAD_FAILED = '加载失败，请重试'
const MSG_AI_FAILED = '服务暂时不可用，请稍后再试'

// ==================== 数据与区块状态 ====================

const records = ref<AssessmentRecord[]>([])
const appointments = ref<AppointmentItem[]>([])
const messages = ref<MessageItem[]>([])
const knowledge = ref<KnowledgeArticle[]>([])

interface BlockState {
  loading: boolean
  error: string
}

const blocks = reactive<Record<'records' | 'appointments' | 'messages' | 'knowledge', BlockState>>({
  records: { loading: true, error: '' },
  appointments: { loading: true, error: '' },
  messages: { loading: true, error: '' },
  knowledge: { loading: true, error: '' }
})

// ==================== 计算（纯函数，集中在此） ====================

/** 取日期部分 yyyy-MM-dd；用于**字符串比较**，避免 Date 解析的时区/格式坑 */
function toDatePart(value?: string): string {
  return value ? String(value).slice(0, 10) : ''
}

/** 本地时区的今天 yyyy-MM-dd */
function todayString(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 相对日期文案：今天 / 明天 / 原日期 */
function relativeDay(datePart: string, today: string): string {
  if (!datePart) return ''
  if (datePart === today) return '今天'
  const t = new Date(`${today}T00:00:00`)
  t.setDate(t.getDate() + 1)
  const pad = (n: number) => String(n).padStart(2, '0')
  const tomorrow = `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}`
  return datePart === tomorrow ? '明天' : datePart
}

interface Overview {
  /** 今日已完成的测评次数（按 submittedAt 的真实日期判定） */
  todayAssessmentCount: number
  /** 最近一个「老师已同意且未过期」的预约 */
  upcomingAppointment: AppointmentItem | null
  /** **历史累计**的老师已回复条数（非今日） */
  repliedMessageCount: number
}

function computeOverview(
  recordList: AssessmentRecord[],
  appointmentList: AppointmentItem[],
  messageList: MessageItem[]
): Overview {
  const today = todayString()

  // ① 今日测评：严格按 submittedAt 的日期部分 == 今天（不是「最近一条」）
  const todayAssessmentCount = recordList.filter(
    (r) => toDatePart(r.submittedAt) === today
  ).length

  // ② 待赴预约：老师已同意（status=2）且 appointmentDate >= 今天，取最近一条
  //    ⚠️ status 可被客户端伪造（SEC-APPOINTMENT-STATUS-SPOOF，P1 未修）
  //       ⇒ 仅用于展示，**不得**作为权限判断 / 预约有效性 / 任何安全决策依据
  const upcoming = appointmentList
    .filter((a) => {
      const d = toDatePart(a.appointmentDate)
      return a.status === STATUS_APPROVED && d !== '' && d >= today
    })
    .sort((x, y) => {
      const kx = toDatePart(x.appointmentDate) + String(x.timeSlot ?? 0).padStart(3, '0')
      const ky = toDatePart(y.appointmentDate) + String(y.timeSlot ?? 0).padStart(3, '0')
      return kx.localeCompare(ky)
    })

  // ③ 老师已回复：**历史累计**（repliedAt 非空即计入），刻意不按「今日」过滤
  //    ⚠️ counselor_message 无已读字段 ⇒ 本系统**不提供「未读」语义**
  const repliedMessageCount = messageList.filter(
    (m) => !!m.repliedAt && String(m.repliedAt).trim() !== ''
  ).length

  return {
    todayAssessmentCount,
    upcomingAppointment: upcoming[0] ?? null,
    repliedMessageCount
  }
}

const overview = computed(() => computeOverview(records.value, appointments.value, messages.value))

const overviewLoading = computed(
  () => blocks.records.loading || blocks.appointments.loading || blocks.messages.loading
)

/**
 * 概览的**错误态**。
 *
 * 「今日概览」是 records / appointments / messages 三个数据源的**聚合结果**：
 * 任一失败 ⇒ 该维度未知 ⇒ 概览整体不可信。
 *
 * 🔴 绝不允许把「加载失败」当成「没有数据」：
 *    否则 records 失败时会显示「今日暂无测评」，让用户以为今天确实没测评，
 *    这是**误导性**的错误语义。失败必须显示「暂无法加载」。
 */
const overviewError = computed(
  () => !!(blocks.records.error || blocks.appointments.error || blocks.messages.error)
)

/** 概览卡片在「失败 / 加载中」时的统一占位（三卡一致，避免部分真部分假的混读） */
const OVERVIEW_UNAVAILABLE = { value: '暂无法加载', hint: '', tone: 'idle' as const }
const OVERVIEW_LOADING = { value: '…', hint: '', tone: 'idle' as const }

// 概览卡片文案（中性描述，禁止推断）
const todayCard = computed(() => {
  // 失败优先于加载中：一旦确定某数据源失败，就应立即停止展示"未记录"这类肯定性描述
  if (overviewError.value) return OVERVIEW_UNAVAILABLE
  if (overviewLoading.value) return OVERVIEW_LOADING
  const n = overview.value.todayAssessmentCount
  return n > 0
    ? { value: `已完成 ${n} 次`, hint: '今日提交的测评', tone: 'ok' as const }
    : { value: '未记录', hint: '今日暂无测评', tone: 'idle' as const }
})

const upcomingCard = computed(() => {
  if (overviewError.value) return OVERVIEW_UNAVAILABLE
  if (overviewLoading.value) return OVERVIEW_LOADING
  const a = overview.value.upcomingAppointment
  if (!a) return { value: '无', hint: '暂无待赴预约', tone: 'idle' as const }
  const day = relativeDay(toDatePart(a.appointmentDate), todayString())
  const slot = a.timeSlotLabel ? ` ${a.timeSlotLabel}` : ''
  return { value: '有 1 个', hint: `${day}${slot}`, tone: 'warn' as const }
})

const repliedCard = computed(() => {
  if (overviewError.value) return OVERVIEW_UNAVAILABLE
  if (overviewLoading.value) return OVERVIEW_LOADING
  const n = overview.value.repliedMessageCount
  return n > 0
    ? { value: `${n} 条`, hint: '历史累计（非今日）', tone: 'ok' as const }
    : { value: '无', hint: '暂无老师回复', tone: 'idle' as const }
})

// 列表派生（只展示最近 3 条）
const recentRecords = computed(() =>
  [...records.value]
    .sort((a, b) => String(b.submittedAt ?? '').localeCompare(String(a.submittedAt ?? '')))
    .slice(0, MAX_PREVIEW)
)

const recentKnowledge = computed(() => knowledge.value.slice(0, MAX_PREVIEW))

const upcomingAppointments = computed(() => {
  const today = todayString()
  return [...appointments.value]
    .filter((a) => {
      const d = toDatePart(a.appointmentDate)
      return d !== '' && d >= today
    })
    .sort((x, y) => toDatePart(x.appointmentDate).localeCompare(toDatePart(y.appointmentDate)))
    .slice(0, MAX_PREVIEW)
})

// ==================== 加载（区块级隔离） ====================

async function loadRecords() {
  blocks.records.loading = true
  blocks.records.error = ''
  try {
    const r = await listAssessmentRecords()
    records.value = r.list
  } catch {
    blocks.records.error = MSG_LOAD_FAILED
  } finally {
    blocks.records.loading = false
  }
}

async function loadAppointments() {
  blocks.appointments.loading = true
  blocks.appointments.error = ''
  try {
    appointments.value = await listMyAppointments()
  } catch {
    blocks.appointments.error = MSG_LOAD_FAILED
  } finally {
    blocks.appointments.loading = false
  }
}

async function loadMessages() {
  blocks.messages.loading = true
  blocks.messages.error = ''
  try {
    messages.value = await listMyMessages()
  } catch {
    blocks.messages.error = MSG_LOAD_FAILED
  } finally {
    blocks.messages.loading = false
  }
}

async function loadKnowledge() {
  blocks.knowledge.loading = true
  blocks.knowledge.error = ''
  try {
    knowledge.value = await listKnowledge()
  } catch {
    blocks.knowledge.error = MSG_LOAD_FAILED
  } finally {
    blocks.knowledge.loading = false
  }
}

onMounted(() => {
  // 四个区块各自独立，互不阻塞
  void loadRecords()
  void loadAppointments()
  void loadMessages()
  void loadKnowledge()
  // ⚠️ 刻意**不**调用 askAgent —— AI 区块只在用户主动发送时请求
})

// ==================== AI 回答的极简安全渲染 ====================

/**
 * 完整 HTML 转义（**必须最先执行**）。
 *
 * 原则：**所有来自模型的原始内容必须先完成 HTML 转义**；
 * 后续 Markdown 转换只允许生成**预先白名单化**的固定标签（`<strong>` / `<code>`）。
 * 顺序不可颠倒 —— 这是本渲染路径唯一的注入面控制点。
 */
function escapeHtml(input: string): string {
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * Agent 回答的极简渲染（Phase 3 / Step 8）。
 *
 * 只解决**实际已发现**的两个问题：
 *   ① `**粗体**` 裸露
 *   ② Markdown 表格线裸露
 *
 * 严格约束：
 *   · 先完整 HTML 转义，再只生成白名单标签 `<strong>` / `<code>`；
 *   · **不实现 Markdown 解析器** —— 列表 / 链接 / 图片 / 代码块 / 嵌套 / 引用
 *     一律**不处理**（原样输出文本），留给 Step 9 的正式方案（含 sanitization）。
 *
 * ⇒ 模型返回的原始 HTML 永远不会被浏览器执行。
 */
function renderAgentText(markdown: string): string {
  let text = escapeHtml(markdown)

  // ① 表格分隔行（| --- | :---: |）整行连同换行一起丢弃（必须早于数据行处理，
  //    否则会留下空行，导致表头与数据行之间出现多余空行）
  text = text.replace(/^[ \t]*\|[ \t:|-]+\|[ \t]*(?:\r?\n)?/gm, '')

  // ② 表格数据行 → 「单元格 · 单元格 · 单元格」
  text = text.replace(/^[ \t]*\|(.+)\|[ \t]*$/gm, (_line: string, inner: string) => {
    const cells = inner
      .split('|')
      .map((c) => c.trim())
      .filter((c) => c !== '')
    return cells.length ? cells.join(' · ') : ''
  })

  // ③ 简单标题 → 加粗
  text = text.replace(/^[ \t]*#{1,6}[ \t]+(.+)$/gm, '<strong>$1</strong>')

  // ④ 粗体
  text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')

  // ⑤ 行内代码
  text = text.replace(/`([^`]+)`/g, '<code>$1</code>')

  return text
}

// ==================== AI 区块（多轮会话 · 手动触发） ====================

interface AgentChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const aiInput = ref('')
const aiError = ref('')
const aiLoading = ref(false)

/** 当前会话的完整消息流（历史 + 本轮）——渲染唯一来源 */
const agentMessages = ref<AgentChatMessage[]>([])
/** 会话列表（仅本人；按最近消息倒序） */
const conversations = ref<AgentConversationItem[]>([])
const conversationsLoading = ref(false)
/** 当前会话 id；null = 新会话（服务端在下一次请求时创建并返回 id） */
const currentConversationId = ref<number | null>(null)
const historyLoading = ref(false)

// ==================== Step 25：辅助计划（P-4 冻结） ====================
/** 服务端确定性给出的计划状态；null = 尚未发起过计划 */
const planState = ref<PlanState | null>(null)
/** 最近一份建议草案正文（展示用；真正状态以 planState 为准） */
const planDraftText = ref('')
/** 草案剩余有效秒数（每秒递减；只影响 UI，不决定是否生效） */
const planExpiresIn = ref(0)
/** 服务端 TTL 对应的绝对到期时间戳；null = 无有效草稿 */
const planExpiresAt = ref<number | null>(null)
const planLoading = ref(false)

let planTimer: ReturnType<typeof setInterval> | null = null

/** 停止倒计时并清空有效期展示（不触碰服务端） */
function stopPlanCountdown() {
  if (planTimer != null) {
    clearInterval(planTimer)
    planTimer = null
  }
  planExpiresAt.value = null
  planExpiresIn.value = 0
}

/** 按服务端返回的秒数启动真实倒计时；归零后隐藏确认按钮并提示过期 */
function startPlanCountdown(seconds: number) {
  stopPlanCountdown()
  if (seconds <= 0) {
    planState.value = planStateOnTick(planState.value, 0)
    return
  }
  planExpiresAt.value = Date.now() + seconds * 1000
  planExpiresIn.value = seconds
  planTimer = setInterval(() => {
    const remaining = remainingSeconds(planExpiresAt.value, Date.now())
    planExpiresIn.value = remaining
    planState.value = planStateOnTick(planState.value, remaining)
    if (remaining <= 0) stopPlanCountdown()
  }, 1000)
}

onUnmounted(() => stopPlanCountdown())

/** 本地清理；会话切换时调用，并以旧会话 id best-effort 通知服务端放弃草稿 */
function clearPlanState(discardOnServer: boolean) {
  const previousConversationId = currentConversationId.value
  stopPlanCountdown()
  planState.value = null
  planDraftText.value = ''
  if (discardOnServer && previousConversationId != null) {
    // best-effort：失败不影响切换会话主流程；不带 message
    void submitPlanAction('DISCARD', '', previousConversationId).catch(() => {})
  }
}

async function loadConversations() {
  conversationsLoading.value = true
  try {
    conversations.value = await listConversations()
  } catch {
    // 历史加载失败静默：不影响「发起新对话」主流程
  } finally {
    conversationsLoading.value = false
  }
}

onMounted(() => {
  void loadConversations()
})

async function sendToAgent() {
  const message = aiInput.value.trim()
  if (!message || aiLoading.value) return

  if (message.length > MAX_MESSAGE_LENGTH) {
    aiError.value = `消息不能超过 ${MAX_MESSAGE_LENGTH} 字`
    return
  }

  aiLoading.value = true
  aiError.value = ''
  agentMessages.value.push({ role: 'user', content: message })
  try {
    const result = await askAgent(message, currentConversationId.value ?? undefined)
    currentConversationId.value = result.conversationId ?? currentConversationId.value
    agentMessages.value.push({ role: 'assistant', content: result.answer })
    void loadConversations()   // 会话标题/时间可能变化
  } catch {
    // 不透传后端/网络原始错误信息
    aiError.value = MSG_AI_FAILED
  } finally {
    aiLoading.value = false
  }
}

/**
 * Step 25：三个显式动作。确认/放弃不经过输入框、不经过 askAgent，
 * 完全由服务端 Java 状态机决定结果（P-2 冻结）。
 */
async function handlePlanAction(action: 'PREVIEW' | 'CONFIRM' | 'DISCARD') {
  if (planLoading.value || aiLoading.value) return
  const message = aiInput.value.trim()
  if (action === 'PREVIEW') {
    if (!message) return
    if (message.length > MAX_MESSAGE_LENGTH) {
      aiError.value = `消息不能超过 ${MAX_MESSAGE_LENGTH} 字`
      return
    }
  }

  planLoading.value = true
  aiError.value = ''
  if (action === 'PREVIEW') {
    agentMessages.value.push({ role: 'user', content: message })
  }
  try {
    const result = await submitPlanAction(
      action,
      action === 'PREVIEW' ? message : '',
      currentConversationId.value ?? undefined
    )
    // 状态唯一来源：服务端响应（不可由前端推断）
    planState.value = result.plan.state
    if (action === 'PREVIEW' && result.plan.state === 'DRAFT') {
      currentConversationId.value = result.conversationId ?? currentConversationId.value
      agentMessages.value.push({ role: 'assistant', content: result.answer })
      planDraftText.value = result.answer
      // 真实倒计时：归零后自动隐藏确认按钮；最终仍以服务端校验为准
      startPlanCountdown(result.plan.expiresInSeconds)
    } else {
      agentMessages.value.push({ role: 'assistant', content: result.answer })
      planDraftText.value = ''
      stopPlanCountdown()
    }
    void loadConversations()
  } catch {
    // 🔴 审查修正：请求失败时服务端草稿状态未知，
    // 清空本地可确认态与倒计时，避免「失效草案仍显示可确认」。
    // 不擅自发 DISCARD（该请求可能已到达服务端），也不改服务端状态。
    stopPlanCountdown()
    planState.value = planStateOnFailure()
    planDraftText.value = ''
    aiError.value = MSG_AI_FAILED
  } finally {
    planLoading.value = false
  }
}

async function openConversation(conv: AgentConversationItem) {
  if (historyLoading.value || conv.id === currentConversationId.value) return
  historyLoading.value = true
  aiError.value = ''
  // 会话切换：旧会话草稿不可在新会话确认，先清理并通知服务端
  if (planState.value === 'DRAFT') clearPlanState(true)
  else clearPlanState(false)
  try {
    const items = await getConversationMessages(conv.id)
    agentMessages.value = items.map((m) => ({ role: m.role, content: m.content }))
    currentConversationId.value = conv.id
  } catch {
    aiError.value = MSG_LOAD_FAILED
  } finally {
    historyLoading.value = false
  }
}

function startNewConversation() {
  // 新会话：清本地状态 + best-effort DISCARD（服务端草稿绑定旧 conversationId）
  if (planState.value === 'DRAFT') clearPlanState(true)
  else clearPlanState(false)
  currentConversationId.value = null
  agentMessages.value = []
  aiError.value = ''
}

async function removeConversation(conv: AgentConversationItem) {
  if (historyLoading.value) return
  historyLoading.value = true
  try {
    await deleteConversation(conv.id)
    conversations.value = conversations.value.filter((c) => c.id !== conv.id)
    if (conv.id === currentConversationId.value) {
      if (planState.value === 'DRAFT') clearPlanState(true)
      else clearPlanState(false)
      currentConversationId.value = null
      agentMessages.value = []
    }
  } catch {
    aiError.value = MSG_LOAD_FAILED
  } finally {
    historyLoading.value = false
  }
}

/** 渲染：assistant 走极简安全渲染；user 只做转义（用户输入不解析任何标记） */
const renderedMessages = computed(() =>
  agentMessages.value.map((m) => ({
    role: m.role,
    html: m.role === 'assistant' ? renderAgentText(m.content) : escapeHtml(m.content),
  }))
)
</script>

<template>
  <div class="dashboard" data-test="dashboard">
    <!-- ① 今日概览（数据活动概览，非心理评估） -->
    <SectionCard title="今日概览">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <StatCard label="今日测评" :value="todayCard.value" :hint="todayCard.hint" :tone="todayCard.tone" />
        </el-col>
        <el-col :xs="24" :sm="8">
          <StatCard label="近期预约" :value="upcomingCard.value" :hint="upcomingCard.hint" :tone="upcomingCard.tone" />
        </el-col>
        <el-col :xs="24" :sm="8">
          <StatCard label="老师已回复" :value="repliedCard.value" :hint="repliedCard.hint" :tone="repliedCard.tone" />
        </el-col>
      </el-row>
      <p
        class="note"
        data-test="overview-note"
        :data-state="overviewError ? 'error' : 'ok'"
      >
        {{
          overviewError
            ? '部分数据暂时无法加载，请稍后重试。'
            : '以上为平台内数据活动概览，不构成心理评估或诊断。'
        }}
      </p>
    </SectionCard>

    <!-- ② 最近测评 -->
    <SectionCard
      title="最近测评"
      :count="records.length"
      more-to="/me/records"
      :loading="blocks.records.loading"
      :error="blocks.records.error"
      @retry="loadRecords"
    >
      <EmptyBlock v-if="!recentRecords.length" description="还没有测评记录" />
      <el-card v-else shadow="never" class="list-card">
        <div v-for="r in recentRecords" :key="String(r.recordNo) + '-' + r.id" class="row">
          <router-link
            v-if="r.recordNo"
            :to="'/me/records/' + r.recordNo"
            class="row-title"
            :data-test="'record-' + r.recordNo"
          >
            {{ r.paperName || '（未命名试卷）' }}
          </router-link>
          <span v-else class="row-title">{{ r.paperName || '（未命名试卷）' }}</span>
          <span class="row-meta">
            <span class="score">{{ r.totalScore ?? '—' }} / {{ r.paperTotalScore ?? '—' }}</span>
            <span class="time">{{ r.submittedAt || '' }}</span>
          </span>
        </div>
      </el-card>
    </SectionCard>

    <!-- ③ AI 助手（入口卡片；不自动调用） -->
    <SectionCard title="AI 助手">
      <div class="ai-box" data-test="ai-box">
        <!-- 历史会话（仅本人；标题来自首条消息前 20 字） -->
        <div class="ai-history" v-if="conversations.length || currentConversationId" data-test="ai-history">
          <div class="ai-history-head">
            <span class="ai-history-title">
              历史会话（{{ conversations.length }}）<span v-if="historyLoading">· 加载中…</span>
            </span>
            <el-button
              size="small"
              text
              type="primary"
              :disabled="aiLoading || historyLoading"
              @click="startNewConversation"
              data-test="ai-new-conversation"
            >
              新会话
            </el-button>
          </div>
          <div class="ai-history-list">
            <div
              v-for="c in conversations"
              :key="c.id"
              class="ai-history-item"
              :class="{ active: c.id === currentConversationId }"
              @click="openConversation(c)"
              data-test="ai-conversation-item"
            >
              <span class="ai-history-name">{{ c.title || '（无标题）' }}</span>
              <el-button
                size="small"
                text
                type="danger"
                :disabled="historyLoading"
                @click.stop="removeConversation(c)"
                data-test="ai-conversation-delete"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>

        <el-input
          v-model="aiInput"
          type="textarea"
          :rows="2"
          :maxlength="MAX_MESSAGE_LENGTH"
          show-word-limit
          placeholder="可以问我：我有几条测评记录？"
          :disabled="aiLoading"
          data-test="ai-input"
        />
        <div class="ai-actions">
          <el-button
            type="primary"
            :loading="aiLoading"
            :disabled="!aiInput.trim() || planLoading"
            @click="sendToAgent"
            data-test="ai-send"
          >
            {{ aiLoading ? '正在查询…' : '发送' }}
          </el-button>
          <el-button
            :loading="planLoading"
            :disabled="!aiInput.trim() || aiLoading"
            @click="handlePlanAction('PREVIEW')"
            data-test="plan-preview"
          >
            生成计划建议
          </el-button>
          <template v-if="canConfirm(planState, planExpiresIn)">
            <el-button
              type="success"
              :disabled="planLoading || aiLoading"
              @click="handlePlanAction('CONFIRM')"
              data-test="plan-confirm"
            >
              确认
            </el-button>
            <el-button
              :disabled="planLoading || aiLoading"
              @click="handlePlanAction('DISCARD')"
              data-test="plan-discard"
            >
              放弃
            </el-button>
          </template>
          <span class="ai-tip">由平台数据助手基于你自己的记录回答</span>
        </div>
        <!-- 计划状态由服务端确定性给出；确认前恒为「建议草案，未生效」 -->
        <div v-if="planState" class="ai-plan-state" data-test="plan-state">
          <span v-if="planState === 'DRAFT'">
            建议草案，未生效<template v-if="planDraftText">（可查看上方建议内容）</template>
            <template v-if="planExpiresIn > 0">（{{ planExpiresIn }} 秒内可确认）</template>
          </span>
          <span v-else-if="planState === 'CONFIRMED'">已确认（仅在当前会话内保留）</span>
          <span v-else-if="planState === 'DISCARDED'">已放弃该建议草案</span>
          <span v-else-if="planState === 'EXPIRED'">建议草案已过期，请重新生成</span>
          <span v-else>当前没有可确认的建议草案</span>
        </div>

        <el-alert
          v-if="aiError"
          :title="aiError"
          type="error"
          show-icon
          :closable="false"
          class="ai-result"
          data-test="ai-error"
        />
        <!-- 会话消息流（历史 + 本轮；assistant 走安全渲染，user 仅转义） -->
        <div v-if="renderedMessages.length" class="ai-transcript" data-test="ai-transcript">
          <div
            v-for="(m, i) in renderedMessages"
            :key="i"
            class="ai-msg"
            :class="'ai-msg-' + m.role"
          >
            <div class="ai-msg-role">{{ m.role === 'user' ? '我' : 'AI 助手' }}</div>
            <div class="ai-msg-content" v-html="m.html"></div>
          </div>
        </div>
      </div>
    </SectionCard>

    <!-- ④ 推荐知识 -->
    <SectionCard
      title="推荐知识"
      :count="knowledge.length"
      more-to="/knowledge"
      :loading="blocks.knowledge.loading"
      :error="blocks.knowledge.error"
      @retry="loadKnowledge"
    >
      <EmptyBlock v-if="!recentKnowledge.length" description="暂无内容" />
      <el-row v-else :gutter="16">
        <el-col v-for="k in recentKnowledge" :key="k.id" :xs="24" :sm="12" :md="8">
          <router-link :to="'/knowledge/' + k.id" class="card-link">
            <el-card class="item" shadow="hover" :data-test="'knowledge-' + k.id">
              <div class="item-title">{{ k.title || '（无标题）' }}</div>
              <div class="item-sub">
                <span>{{ k.categoryLabel || '' }}</span>
                <span class="time">{{ k.publishedAt || k.createdAt || '' }}</span>
              </div>
            </el-card>
          </router-link>
        </el-col>
      </el-row>
    </SectionCard>

    <!-- ⑤ 近期预约：count 用**筛选后**的数量，避免「共 3 条 + 暂无预约」的口径矛盾 -->
    <SectionCard
      title="近期预约"
      :count="upcomingAppointments.length || undefined"
      more-to="/me/appointments"
      :loading="blocks.appointments.loading"
      :error="blocks.appointments.error"
      @retry="loadAppointments"
    >
      <EmptyBlock v-if="!upcomingAppointments.length" description="暂无预约" />
      <el-card v-else shadow="never" class="list-card">
        <div v-for="a in upcomingAppointments" :key="a.id" class="row">
          <span class="row-title">{{ a.counselorName || '（未命名老师）' }}</span>
          <span class="row-meta">
            <span class="time">{{ a.appointmentDate || '' }} {{ a.timeSlotLabel || '' }}</span>
            <span class="status">{{ a.statusLabel || '' }}</span>
          </span>
        </div>
      </el-card>
    </SectionCard>
  </div>
</template>

<style scoped>
.dashboard {
  padding-bottom: 8px;
}
.note {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
.list-card {
  padding: 4px 0;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 4px;
  border-bottom: 1px dashed #ebeef5;
}
.row:last-child {
  border-bottom: none;
}
.row-title {
  color: #303133;
  text-decoration: none;
  font-size: 14px;
}
.row-title:hover {
  color: #409eff;
}
.row-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #909399;
  font-size: 12px;
}
.score {
  color: #606266;
}
.time {
  color: #909399;
  font-size: 12px;
}
.status {
  color: #606266;
}
.card-link {
  text-decoration: none;
  color: inherit;
}
.item {
  margin-bottom: 12px;
}
.item-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.item-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
}
.ai-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.ai-tip {
  color: #909399;
  font-size: 12px;
}
.ai-plan-state {
  margin-top: 8px;
  color: #606266;
  font-size: 12px;
}
.ai-answer {
  white-space: pre-wrap;
  word-break: break-word;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 14px;
  color: #303133;
  font-size: 14px;
  line-height: 1.6;
}
.ai-history {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 10px;
}
.ai-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.ai-history-title {
  color: #909399;
  font-size: 12px;
}
.ai-history-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 160px;
  overflow-y: auto;
}
.ai-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #303133;
}
.ai-history-item:hover {
  background: #f5f7fa;
}
.ai-history-item.active {
  background: #ecf5ff;
  color: #409eff;
}
.ai-history-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-transcript {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ai-msg {
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.6;
}
.ai-msg-user {
  background: #ecf5ff;
  color: #303133;
}
.ai-msg-assistant {
  background: #f5f7fa;
  color: #303133;
}
.ai-msg-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 2px;
}
.ai-msg-content {
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-result {
  margin: 0;
}
</style>
