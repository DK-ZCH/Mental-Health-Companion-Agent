# Phase 3 / Step 8 · Dashboard 设计

> **性质**：设计（📐 **待批准 · 零代码**）
> **基线**：`HEAD = origin = 2207f9b` · worktree CLEAN · Step 11 已 CLOSED
> **上游**：`PHASE3-STEP8-DASHBOARD-RESEARCH.md`（3 项已裁定为 A）
> **本批定位**：把首页从「内容罗列」变成「个人工作台」；**不新增业务模型、不改后端**

---

## 一、裁定落地与两处事实修正

| # | 裁定 | 落地 |
| :-: | --- | --- |
| 1 | 今日状态 → **A，改名为「今日概览」** | ✅ 只做活动/事项聚合，**不声称"心理状态"** |
| 2 | `/` → **A，按登录态分流** | ✅ 未登录走访客首页，已登录走 Dashboard |
| 3 | Design System → **A，3 个极简组件** | ✅ `SectionCard` / `StatCard` / `EmptyBlock` |

### 🔴 修正 1：`counselor_message` **没有已读字段** ⇒ 不能叫「未读消息」

实测表结构：

```text
counselor_message: id · counselor_id · student_id · content · sent_at
                   reply_content · replied_at · created_at
```

**无 `read` / `is_read` / `read_at`**。前端 `api/message.ts` 注释也已明确：

> 「无已读字段 ⇒ 本模块**不做聊天流、不做未读** ✗」

| 原设想指标 | 可行性 | 修正 |
| --- | :-: | --- |
| 未读留言 | ❌ | 改为 **「老师已回复」** = `repliedAt != null` 的留言条数 |

### 🔴 修正 2：预约 `status=2` 可被客户端伪造

`SEC-APPOINTMENT-STATUS-SPOOF` 🔴 P1（未修）：后端 `/add`、`/update` 均不限制 `status`。

⇒ 「待赴预约」的 `status === 2` 判断**只能用于展示**，
**不得**作为权限判断、业务决策或安全依据。Design 与实现注释必须写明。

---

## 二、页面结构（`/` 已登录时）

```text
┌────────────────────────────────────────────────────────┐
│ 今日概览                                                │  ← 3 个 StatCard
│ [今日测评 1 次] [待赴预约 明天 14:00] [老师已回复 2 条]  │
├────────────────────────────────────────────────────────┤
│ 最近测评                                   查看更多 →   │  ← SectionCard + 列表
├────────────────────────────────────────────────────────┤
│ AI 助手                                                │  ← SectionCard + 输入框
│ [ 想问点什么…            ] [ 发送 ]                     │
│ （结果就地展示，不入库、刷新即失）                        │
├────────────────────────────────────────────────────────┤
│ 推荐知识                                   查看更多 →   │
├────────────────────────────────────────────────────────┤
│ 近期预约                                   查看更多 →   │
└────────────────────────────────────────────────────────┘
```

**顺序理由**：先「我现在的状态」→ 再「我的数据」→ 再「我能做什么（AI）」→ 最后「可探索内容」。
把 AI 放在第 3 位而非第 1 位，避免 Dashboard 变成 AI 页（见 §八）。

---

## 三、五个区块：展示内容 / 数据源 / 字段投影

### 3.1 今日概览（3 个 `StatCard`）

| 卡片 | 标签 | 值 | 计算规则（**详见 §四**）| 数据源 |
| :-: | --- | --- | --- | --- |
| 1 | 今日测评 | 今日已测 / 未测 | `submitted_at` 的日期 == 今天 | `assessmentRecord/list` |
| 2 | 待赴预约 | 「明天 14:00」/ 无 | `status===2 && appointmentDate >= 今天` 取最近一条 | `counselingAppointment/list` |
| 3 | 老师已回复 | N 条 / 无 | `repliedAt != null` 计数 | `counselorMessage/list` |

> ⚠️ 卡片 3 **不叫「未读」**，叫「老师已回复」（原因见 §一修正 1）。

### 3.2 最近测评

- 展示：`paperName` · `totalScore / paperTotalScore` · `submittedAt`
- 取前 **3** 条（按 `submittedAt` 倒序）
- 点击 → `/me/records/:recordNo`
- 数据源：`listAssessmentRecords()`（需登录 + DataScope）
- **渲染字段白名单**：不含 `studentId` / `studentPhone` / `studentEmail`（后端会返回，但前端不建模不渲染）

### 3.3 AI 助手（**入口卡片，不是完整页**）

- 组成：一个 `el-input`（`maxlength=1000`）+ 发送按钮 + 结果区
- 调用：`POST /agent/chat { message }` → `{ code, answer }`
- 展示：`answer` 纯文本（**不做 Markdown 渲染**，避免 XSS 面）
- 状态：`idle` → `loading`（禁用按钮 + 文案「正在查询…」）→ `answer` / `error`
- **不持久化**：刷新即失；无历史、无多轮

### 3.4 推荐知识

- 展示：`title` · `categoryLabel` · `publishedAt`
- 取前 **3** 条
- 点击 → `/knowledge/:id`
- 数据源：`listKnowledge()`（**公开**）

### 3.5 近期预约

- 展示：`counselorName` · `appointmentDate` + `timeSlotLabel` · `statusLabel`
- 取前 **3** 条（`appointmentDate` 升序，仅未来）
- 点击 → `/me/appointments`
- 数据源：`listMyAppointments()`（需登录 + DataScope）
- ⚠️ 注释写明 `status` 可被伪造（§一修正 2）

---

## 四、🔴「今日概览」计算规则（必须写死，避免变成隐含业务规则）

### 4.1 「今天」的定义

```text
时区：浏览器本地时区（项目全站 GMT+8）
基准：todayStr = yyyy-MM-dd（本地时间，补零）
比较：**字符串比较**，不做 Date 解析（规避时区/格式坑）
```

### 4.2 指标 1：今日测评

```text
records = listAssessmentRecords()            // 已含 DataScope，仅本人
hit     = records.some(r => startsWith(toDateStr(r.submittedAt), todayStr))
value   = hit ? "已完成" : "未记录"
```

- 后端 `submittedAt` 形如 `yyyy-MM-dd HH:mm:ss` ⇒ 取前 10 字符比较即可
- `submittedAt` 缺失 → 视为未命中（不猜测）

### 4.3 指标 2：待赴预约

```text
list  = listMyAppointments()
upcoming = list.filter(a => a.status === 2 && a.appointmentDate >= todayStr)
sort by (appointmentDate, timeSlot) 升序
value = upcoming[0] ? `${相对日期} ${timeSlotLabel}` : "无"
```

- **只用 `status === 2`（老师已同意）**；`1`（待审核）/`3`（拒绝）不计入
- `appointmentDate` 缺失 → 排除
- ⚠️ **`status` 可被客户端伪造** ⇒ 本判断仅用于展示

### 4.4 指标 3：老师已回复

```text
msgs = listMyMessages()
value = msgs.filter(m => m.repliedAt != null && String(m.repliedAt).trim() !== '').length
```

- **不提供"未读"语义**（无已读字段）
- 若为 0 → 显示「无」

### 4.5 空状态（三个都空）

```text
今日测评「未记录」· 待赴预约「无」· 老师已回复「无」
```

> **不显示"今天心情不错"之类推断文案** —— 我们没有心理状态数据模型。
> 今日概览只回答「今天在平台上发生了什么」，不回答「你今天感觉如何」。

### 4.6 计算放在哪

```text
components/（不含业务）
      ↑
views/DashboardView.vue  ← 计算规则集中在此，纯函数式派生（computed）
      ↑
api/*.ts                 ← 只负责取数与字段投影
```

- **不新增后端接口**（Step 8 硬约束）
- **不把计算下沉到 `api/*.ts`**（保持 api 层"只取数"）
- 计算函数写成**纯函数**（输入列表 → 输出概览），便于阅读与后续测试

---

## 五、登录/未登录分流规则

### 5.1 路由与渲染

```text
GET /
 ├─ !auth.isLoggedIn  →  访客首页（现有 HomeView 行为：知识 / 通知 / 老师）
 └─  auth.isLoggedIn  →  Dashboard
```

**实现方式**：`/` 挂一个壳组件（`HomeEntryView`），依据 `useAuthStore().isLoggedIn` 决定渲染哪个子视图。

> 用 `v-if` 切换而非路由重定向 —— 避免刷新时闪烁跳转，也避免破坏 `/` 的既有链接语义。

### 5.2 🔴 数据加载必须"先判断，后请求"

```text
onMounted:
  if (!auth.isLoggedIn) {
      loadGuest()          // 仅 3 个公开 API
      return               // ⚠️ 绝不进入需登录分支
  }
  loadDashboard()          // 4 个需登录 + 1 个公开
```

**为什么必须这样**（`api/http.ts` 的实测行为）：

```text
任一端点返回 body.code=401
  → notifyAuthFailure()
  → 清 Pinia 认证态 + 跳 /login?redirect=
```

即：**在 `/` 上误请求需登录 API，会把正在公开浏览的访客直接踢到登录页** ——
这会破坏当前已验证的公开浏览能力。

### 5.3 登录态失效的边界

| 场景 | 行为 |
| --- | --- |
| 本地无 token | 走访客分支，**不发需登录请求** |
| 本地有 token 但已失效 | Dashboard 请求 → 401 → `http.ts` 清态跳登录（**既有行为，可接受**）|
| 403（无权） | 仅该区块报错，**不清登录态**（`http.ts` 已区分）|

---

## 六、空数据状态

| 区块 | 空态文案 | 组件 |
| --- | --- | --- |
| 今日概览 | 三个 `StatCard` 各自显示「未记录 / 无 / 无」 | `StatCard` 自带 `value` 兜底 |
| 最近测评 | 「还没有测评记录」 | `EmptyBlock` |
| AI 助手 | 初始态：提示语「可以问我：我有几条测评记录？」（**不自动调用**）| 内联提示，不用 EmptyBlock |
| 推荐知识 | 「暂无内容」 | `EmptyBlock` |
| 近期预约 | 「暂无预约」 | `EmptyBlock` |

> 空态必须是**中性描述**，不得推断用户心理状态。

---

## 七、🔴 API 加载失败状态

### 7.1 核心原则：**区块级隔离，不做整页失败**

```text
❌ 不用 Promise.all（一个失败 → 整页空白）
✅ 用 Promise.allSettled（每区块独立成败）
```

### 7.2 每区块三态

```text
loading  → el-skeleton（区块内骨架，不遮全页）
error    → 区块内 el-alert（type=error）+ 「重试」按钮
success  → 正常内容 / 空态
```

### 7.3 失败矩阵

| 失败范围 | 表现 |
| --- | --- |
| 单个需登录 API 失败（非 401） | 仅该区块显示错误 + 重试；其余正常 |
| 单个公开 API 失败 | 同上 |
| 401（登录失效） | `http.ts` 全局处理 → 跳登录（**不在区块内重复处理**）|
| 403 | 区块内显示「无权访问」，**不清登录态** |
| AI 调用失败 | 结果区显示统一文案「服务暂时不可用，请稍后再试」（不透传后端细节）|

### 7.4 错误文案

```text
统一：`加载失败，请重试`（区块级）
AI：`服务暂时不可用，请稍后再试`
禁止：把 axios/后端原始 message 直接渲染给用户
```

---

## 八、🔴 AI 区块为什么不自动调用 LLM

| 原因 | 说明 |
| --- | --- |
| **延迟** | `/agent/chat` 实测 **2.0–2.5s**（含一次 DeepSeek 往返）。首屏自动调用会拖慢整个 Dashboard |
| **成本** | 每次进入首页都会产生一次真实 API 计费调用 |
| **无意义** | 用户未提问时，模型只能回一句「你想问什么」，纯浪费 |
| **无数据** | 概览与列表已用结构化 API 表达，不需要 LLM 复述 |
| **错误放大** | LLM 偶发错误会污染首屏，而首屏应是最稳定的区域 |

**因此 AI 区块的定位**：

```text
✅ 一个「有输入框的入口卡片」：用户主动输入 → 主动点击 → 才调用
❌ 不是「自动生成的每日建议」「自动摘要」
```

> 若未来要做「每日自动建议」，必须作为独立能力（需缓存 + 成本控制 + 生成质量评估），不在 Step 8。

---

## 九、三个共享组件的职责与 Props

### 9.1 `SectionCard`（区块外壳）

```ts
props: {
  title: string
  count?: number | string        // 右上角「共 N 条」
  moreTo?: string                // 「查看更多」路由；缺省则不渲染
  moreText?: string              // 默认「查看更多 →」
  loading?: boolean              // 显示骨架
  error?: string                 // 显示错误 + 重试
}
emits: { retry: [] }
slots: { default }               // 内容
```

**职责**：统一的「标题 + 右侧操作 + 内容 / 骨架 / 错误」外壳。
**不职责**：不取数、不做业务判断。

### 9.2 `StatCard`（指标卡）

```ts
props: {
  label: string
  value: string | number
  hint?: string                  // 次要说明（如「明天 14:00」的补充）
  tone?: 'ok' | 'idle' | 'warn'  // 仅影响颜色，不影响语义
}
```

**职责**：单个指标的视觉呈现。
**不职责**：不计算、不取数。

### 9.3 `EmptyBlock`（空状态）

```ts
props: {
  description: string
  imageSize?: number             // 默认 60
}
```

**职责**：统一空态。
**不职责**：不智能生成文案。

### 9.4 边界声明

> 这三个组件**只服务 Dashboard**，放在 `frontend/src/components/dashboard/`。
> **不是**全站 Design System；不得在本批被其他页面复用改造（避免范围扩散）。

---

## 十、🔴 Step 8 与 Step 9 的边界

| 能力 | Step 8（本批）| Step 9 |
| --- | :-: | :-: |
| 输入框 + 单次提问 | ✅ | ✅ |
| 单条回答展示 | ✅ | ✅ |
| **快捷动作**（如「查看我的测评」「推荐知识」按钮）| ❌ | ✅ |
| **过程反馈**（"正在查询测评…/正在检索知识…"）| ❌ | ✅ |
| **多轮对话 / 历史** | ❌ | ✅ |
| **AI 独立页面**（`/assistant`）| ❌ | ✅ |
| 工具调用可视化 | ❌ | ✅ |
| 结构化反馈渲染（卡片式结果）| ❌ | ✅ |

**一句话边界**：

> Step 8 的 AI 区块 = **「能在首页问一句话」**；
> Step 9 = **「一个完整的 AI 助手页面」**。

实现时若发现需要"快捷动作"或"多轮"，**停止并与用户确认是否移入 Step 9**，不得就地扩张。

---

## 十一、硬约束：不修改后端 / 数据库

```text
❌ 不新增后端接口        ❌ 不修改任何 Controller / Service / Entity
❌ 不新增数据库表        ❌ 不修改表结构 / 种子数据
❌ 不修改 /agent/chat     ❌ 不修改 Prompt
❌ 不新增 mood/checkin 模型
❌ 不改 http.ts 的既有 401/403 契约
```

**唯一允许的后端侧变化**：无。

前端允许变更：

```text
✅ 新增 frontend/src/components/dashboard/*.vue（3 个）
✅ 新增 frontend/src/views/DashboardView.vue
✅ 新增 frontend/src/views/HomeEntryView.vue（壳，用于分流）
✅ 新增 frontend/src/api/agent.ts
✅ 修改 frontend/src/router/index.ts（`/` 指向壳组件）
✅ 修改 HomeView.vue（仅：被壳组件包裹，行为不变）
```

---

## 十二、文件清单（预估）

| # | 文件 | 类型 | 说明 |
| :-: | --- | --- | :-: |
| 1 | `frontend/src/components/dashboard/SectionCard.vue` | 新增 | ~70 行 |
| 2 | `frontend/src/components/dashboard/StatCard.vue` | 新增 | ~45 行 |
| 3 | `frontend/src/components/dashboard/EmptyBlock.vue` | 新增 | ~25 行 |
| 4 | `frontend/src/views/DashboardView.vue` | 新增 | ~230 行（含计算规则）|
| 5 | `frontend/src/views/HomeEntryView.vue` | 新增 | ~25 行（分流壳）|
| 6 | `frontend/src/api/agent.ts` | 新增 | ~35 行 |
| 7 | `frontend/src/router/index.ts` | 修改 | `/` → `HomeEntryView` |
| 8 | `frontend/src/views/HomeView.vue` | 修改（可选）| 无行为改动 |

**后端：0 文件**

---

## 十三、验收标准

### 13.1 未登录（回归）

```text
① 访问 / → 访客首页（知识/通知/老师三块），内容与现状一致
② 打开 DevTools Network：**无任何 code=401 的请求**
③ 不触发跳转登录
```

### 13.2 已登录

```text
④ 访问 / → Dashboard，5 区块全部渲染
⑤ 今日概览：a1 显示「今日测评 未记录 / 待赴预约 …/ 老师已回复 N 条」（依真实数据）
⑥ 最近测评：a1 显示 1 条「考试试卷1 15/100」；a2 显示空态
⑦ 推荐知识：显示 3 条，点击进详情
⑧ 近期预约：显示未来预约 3 条以内
⑨ AI 区块：初始**不调用**接口；输入并点击后返回答案
```

### 13.3 异常

```text
⑩ 停后端 → 各区块独立显示「加载失败，请重试」，页面不白屏
⑪ 单区块重试只重载该区块
⑫ AI 失败显示统一文案，不透传后端细节
⑬ 401 → 跳登录（既有行为）；403 → 区块内提示且不清登录态
```

### 13.4 AI 边界

```text
⑭ 页面加载后 Network 中**无 /agent/chat 请求**（证明不自动调用）
⑮ 无快捷动作按钮、无多轮历史、无过程反馈（Step 9 能力）
⑯ 后端代码 diff = 0
```

---

## 十四、风险

| # | 风险 | 级别 | 缓解 |
| :-: | --- | :-: | :-: |
| 1 | 未登录误发需登录请求 → 踢登录 | 🔴 高 | §5.2 先判断后请求 + 验收项 ② |
| 2 | 「今日概览」被实现成"心理状态推断" | 🟠 中 | §4.5 明确禁止推断文案；命名统一为「今日概览」|
| 3 | AI 区块扩张成半个 Step 9 | 🟠 中 | §十 边界表 + 验收项 ⑮ |
| 4 | 计算逻辑散落成隐含业务规则 | 🟠 中 | §4.6 集中为纯函数，仅放 DashboardView |
| 5 | 前端无测试框架，回归靠手工 | 🟡 低 | 预留 `data-test` 属性；本批不引入测试框架 |
| 6 | 预约 `status` 被伪造导致展示偏差 | 🟡 低 | 仅展示、不作决策；注释写明 |

---

## 十五、待你裁定

| # | 问题 | 建议 |
| :-: | --- | --- |
| 1 | 「今日概览」指标 3 改为「老师已回复」 | **建议采用**（无已读字段，唯一可行且诚实）|
| 2 | 概览卡片数量 | **3 个**（今日测评 / 待赴预约 / 老师已回复）|
| 3 | AI 区块位置 | **第 3 位**（概览 → 测评 → AI → 知识 → 预约）|

> 若你希望指标 3 换成其他含义（如「我的收藏数」），请指定。
