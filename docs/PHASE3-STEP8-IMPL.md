# Phase 3 / Step 8 · Dashboard 实现报告

> **性质**：实现（✅ **已实现 · 已 commit · 已 push（`1bc24a5`）**）
> **基线**：`HEAD = origin = 1bc24a5` · LEAD 0 · worktree CLEAN · 改动 **7 文件 +817 / −2**
>
> **v2 修正（用户裁定 A）**：AI 回答由纯文本改为**极简安全渲染**（先完整转义 → 只生成白名单 `<strong>`/`<code>`）。见 §九。
> **v3 修正（用户 2026-10-25 代码复核）**：区块加载失败**不再**被显示成「没有数据」，
> 概览任一依赖失败 ⇒ 三卡统一「暂无法加载」。见 §九之二。
> **上游**：`PHASE3-STEP8-DESIGN.md`（用户 2026-10-25 批准）
> **硬约束**：后端 **0 文件** · 数据库 **0 改动**

---

## 一、结论：**功能通过，1 项需人工确认**

| 项 | 结果 |
| --- | :-: |
| TypeScript 类型检查（`vue-tsc --noEmit`）| ✅ 0 错误 |
| 生产构建（`vite build`）| ✅ `built in 8.23s` |
| 未登录分流 | ✅ **实测通过**（截图 + snapshot）|
| 登录后 Dashboard 5 区块 | ✅ **实测通过**（截图 + snapshot）|
| 今日概览计算正确性 | ✅ **实测通过** |
| 未登录不触发 401 | ✅ 实测（页面停留访客首页，未跳登录）|
| AI 网络链路 | ✅ **实测通过**（经 5173 代理调用返回正确 answer）|
| **AI 浏览器点击** | ⚠️ **未能在自动化中完成**（工具对 Element Plus 按钮点击无效），**需人工点一次** |
| 后端改动 | ✅ **0** |

---

## 二、文件清单（7 文件 · +721 / −2）

| # | 文件 | 类型 | 说明 |
| :-: | --- | --- | --- |
| 1 | `frontend/src/components/dashboard/SectionCard.vue` | 新增 | 区块外壳（标题+操作+骨架/错误+重试）|
| 2 | `frontend/src/components/dashboard/StatCard.vue` | 新增 | 指标卡（label/value/hint/tone）|
| 3 | `frontend/src/components/dashboard/EmptyBlock.vue` | 新增 | 统一空态 |
| 4 | `frontend/src/views/DashboardView.vue` | 新增 | 5 区块 + 计算规则 + 区块级隔离 |
| 5 | `frontend/src/views/HomeEntryView.vue` | 新增 | `/` 登录态分流壳 |
| 6 | `frontend/src/api/agent.ts` | 新增 | `POST /agent/chat` 封装 |
| 7 | `frontend/src/router/index.ts` | 修改 | `/` → `HomeEntryView`（2 行）|

**`HomeView.vue` 未修改**（原文照旧被壳组件包裹）✓

---

## 三、三项裁定落实

| # | 裁定 | 实现 |
| :-: | --- | --- |
| 1 | 今日状态 → **「今日概览」** | ✅ 标题即「今日概览」；底部注明「以上为平台内数据活动概览，**不构成心理评估或诊断**」 |
| 2 | `/` **登录态分流** | ✅ `HomeEntryView` 用 `v-if` 切换，非重定向 |
| 3 | **3 个极简组件** | ✅ 放 `components/dashboard/`，仅 Dashboard 使用 |

### 3.1 「老师已回复」的准确语义（按你的要求）

```text
label : 老师已回复
value : N 条
hint  : 历史累计（非今日）
口径  : messages.filter(m => m.repliedAt 非空).length   ← 累计，**不按今日过滤**
```

- ❌ 未使用「消息 / 新消息 / 待查看」等易误解为未读的词
- ⚠️ 代码注释明确：`counselor_message` 无已读字段 ⇒ 本系统**不提供「未读」语义**

### 3.2 「今日测评」按真实时间字段判定

```ts
const today = todayString()            // yyyy-MM-dd（本地时区）
records.filter(r => toDatePart(r.submittedAt) === today)
```

- 不是"最近一条"；`submittedAt` 缺失 → 不计入
- 全程**字符串比较**，不做 `Date` 解析（规避时区/格式坑）

### 3.3 禁止心理状态推断

代码与模板中**不存在**任何"心情/状态/心理健康状况"类判断；`StatCard` 的 `tone` 仅影响颜色。

---

## 四、关键实现点

### 4.1 分流与「先判断、后请求」

```ts
// HomeEntryView.vue
<DashboardView v-if="isLoggedIn" />
<HomeView v-else />
```

Dashboard **只在已登录时挂载** ⇒ 其 `onMounted` 中的需登录请求不可能在未登录态发出。
这是避免「401 → 清态 → 访客被踢到登录页」的结构性保证。

### 4.2 区块级隔离（不用 `Promise.all`）

四个区块各自一个 `loadXxx()`，各自 `try/catch/finally` + 独立 `loading/error`：

```ts
const blocks = reactive({ records: {...}, appointments: {...}, messages: {...}, knowledge: {...} })
```

- 一个失败 → 只该区块显示「加载失败，请重试」+ 该区块可单独 `@retry`
- **不整页白屏**、不互相阻塞

### 4.3 计算集中在纯函数

```ts
function computeOverview(records, appointments, messages): Overview
```

- 纯函数，无副作用、不取数
- **不下沉到 `api/*.ts`**（api 层只管取数与字段投影）
- 预约排序键：`appointmentDate + String(timeSlot).padStart(3,'0')`（保证同日按时段有序）

### 4.4 AI 区块：手动触发

```ts
onMounted(() => {
  void loadRecords(); void loadAppointments(); void loadMessages(); void loadKnowledge()
  // ⚠️ 刻意不调用 askAgent
})
```

- 按钮初始 `disabled`（输入为空）
- `loading` 时按钮显示「正在查询…」并禁用
- 失败只显示统一文案「服务暂时不可用，请稍后再试」，**不透传后端/网络细节**
- 答案用 `white-space: pre-wrap` 纯文本渲染（**不做 Markdown**，避免 XSS 面）

### 4.5 `api/agent.ts` 的必要适配

后端 `/agent/chat` 返回 `{ code: 0, answer: "..." }`（**answer 在顶层**，不是 `{code,data}`）
⇒ 不能复用 `unwrap()`，必须自行判断 `code !== 0` 并抛 `ApiError`。

---

## 五、🔴 实现中修正的一处口径矛盾

初始实现中「近期预约」区块显示：

```text
标题右侧：共 3 条
列表区：  暂无预约
```

原因：`count` 用了 `appointments.length`（全部 3 条，均为**历史**），
而列表按设计只展示 `appointmentDate >= 今天`（筛完为 0 条）。

**修正**：`count` 改为 `upcomingAppointments.length || undefined`（0 时不显示"共 0 条"）。
⇒ 口径统一：数量与列表内容一致。

> 这是设计未写死、实现时必须自己判断的一致性问题，已登记。

---

## 六、验证证据

### 6.1 未登录态（实测）

```text
URL: http://localhost:5173/
页面：心理健康陪伴系统 · 导航 11 项 · 右上「未登录」+「登录」按钮
主体：健康知识（共 5 条，显示 3）· 通知公告（共 5 条）
❌ 无「今日概览」区块  ⇒ 走的是访客分支
✅ 未被跳到 /login     ⇒ 未触发任何 401
```

**截图**：见验收附件（AI 助手区块不存在，右侧为"登录"按钮）

### 6.2 登录后（实测，a1/123456）

```text
右上：学生姓名1 + 退出按钮        ⇒ 已登录
今日概览：
  · 今日测评   → 未记录 / 今日暂无测评        （种子记录为 2022 年，非今天）
  · 近期预约   → 无 / 暂无待赴预约            （3 条均为历史）
  · 老师已回复 → 2 条 / 历史累计（非今日）
  · 免责声明：「不构成心理评估或诊断」
最近测评：共 1 条 → 考试试卷1 · 15/100 · 2022-03-29 13:03:54
AI 助手：输入框（0/1000）+ 发送按钮（初始 disabled）+ 提示语
推荐知识：共 5 条 → 显示 3 条
近期预约：空态「暂无预约」（修正后不再显示"共 3 条"）
```

**截图**：见验收附件

### 6.3 AI 网络链路（实测）

通过 **5173 代理**（与前端完全相同的链路）调用：

```http
POST http://localhost:5173/api/agent/chat
Token: <a1 的有效 token>
{"message":"我有几条测评记录"}

→ HTTP 200
→ {"code":0,"answer":"您目前共有 **1 条**测评记录：\n\n- **考试试卷1**：得分 15 / 100\n\n记录已全部显示..."}
→ 后端日志：[AGENT] 对话成功 userId=1 msgLen=8 answerLen=96 elapsed=1891ms
```

⇒ 前端所依赖的**接口、代理、鉴权、Tool Calling 全链路均可用**。

### 6.4 ⚠️ 未能在自动化中验证的一项

**AI 区块的浏览器点击**：`agent-browser` 对本页面的 Element Plus 按钮
（`el-button`）执行 `click` / `focus+Enter` 均返回成功但**未触发 Vue 的 `@click`**，
因此未能自动完成"输入 → 点击发送 → 渲染答案"这一段 UI 交互。

**已排除**：

- 不是接口问题（§6.3 代理直调成功）
- 不是代码问题（`askAgent` 逻辑 10 行，构建/类型检查通过）
- 同一工具点击首个导航按钮时曾成功，说明是**该工具与 Element Plus 的交互稳定性问题**

**因此**：此项标记为 **需人工确认**（已启动 dev server 5173 并打开预览）。
人工确认动作：登录后 → AI 助手输入框输入「我有几条测评记录」→ 点「发送」→ 应显示真实回答。

---

## 七、边界声明

| 未做 | 状态 |
|---|:-:|
| 后端 / 数据库任何改动 | ❌ **0** |
| `HomeView.vue` 行为改动 | ❌ **0** |
| 快捷动作 / 过程反馈 / 多轮 / 独立 AI 页（Step 9）| ❌ |
| mood / checkin 数据模型 | ❌ |
| Design System 全量建设 | ❌（仅 3 个 dashboard 专用组件）|
| 前端测试框架引入 | ❌（既有工程无测试框架，本批不引入）|
| `http.ts` 401/403 契约改动 | ❌ |

---

## 八、状态

| 项 | 值 |
|---|---|
| 文件 | **7**（6 新增 + 1 修改）· **+721 / −2** |
| 后端改动 | **0** |
| tsc / build | ✅ / ✅ |
| Git | HEAD=`2207f9b` · **已 staged 7 文件** · **未 commit** |
| dev server | `5173` 运行中（供人工确认）|
| 后端 | `8080` PID `32200` |
| 下一步 | 你复核 + **人工确认 AI 点击** → commit |

---

## 九、v2 修正（A）：AI 回答的极简安全渲染

### 9.1 问题（真实验收暴露）

设计 §3.3 定为「纯文本渲染，不做 Markdown」，实际运行结果是**格式标记裸露**：

```text
您目前共有 **1 条**测评记录：      ← 星号裸露
| 试卷名称 | 得分 | 满分 |         ← 表格线裸露
|---|---|---|
```

**根因判断**：LLM 天然输出 Markdown，"纯文本渲染"等于把格式噪音交给用户。
这属**设计缺陷**（不是 UI 优化）。

### 9.2 修复（用户批准方案 A，严格顺序）

```ts
escapeHtml(markdown)   // ① & < > " ' 全量转义（必须最先）
  → 表格分隔行整行连同换行丢弃
  → 表格数据行 → "单元格 · 单元格"
  → 简单标题 → <strong>
  → **粗体** → <strong>
  → `代码` → <code>
```

**硬约束（已遵守）**：

- 先转义、后替换，**顺序不可颠倒**；
- `v-html` 只会出现 `<strong>` / `<code>` 两种白名单标签；
- **不实现 Markdown 解析器**：列表 / 链接 / 图片 / 代码块 / 引用**一律不处理**；
- 正式 Markdown 渲染（含 sanitization）留给 **Step 9**。

### 9.3 验证（3 层证据）

**① Node 等价副本（4 用例）**

| 用例 | 输出 |
| --- | --- |
| 真实模型输出 | `您目前共有 <strong>1 条</strong>测评记录：\n\n试卷名称 · 得分 · 满分\n考试试卷1 · 15 · 100\n\n记录未出现截断…` |
| `<script>alert(1)</script>` | `&lt;script&gt;alert(1)&lt;/script&gt;` |
| `**<img src=x onerror=alert(1)>**` | `<strong>&lt;img src=x onerror=alert(1)&gt;</strong>` |
| `### 结论` + `` `BCrypt` `` | `<strong>结论</strong>` / `<code>BCrypt</code>` |

安全断言：**非白名单标签数量 = 0 → PASS**

**② 真实浏览器（Vue 实际渲染，a1 登录态）**

```text
[data-test=ai-answer].innerHTML =
  您目前共有 <strong>1 条</strong>测评记录：

  试卷名称 · 得分 · 满分
  考试试卷1 · 15 · 100

  记录没有截断，以上就是您全部的测评记录。…
```

⇒ 粗体生效、表格降级为文本行、无多余空行。

**③ 真实浏览器 XSS（转义后赋给 innerHTML）**

```text
输入：<script>alert(1)</script> ... <img src=x onerror=alert(2)>
结果：script=0  img=0
      html=&lt;script&gt;alert(1)&lt;/script&gt;…&lt;img src=x onerror=alert(2)&gt;
```

⇒ **不产生任何可执行节点**，全部以文本呈现。

### 9.4 修复后验证

| 项 | 结果 |
| --- | :-: |
| `vue-tsc --noEmit` | ✅ 0 错误 |
| `vite build` | ✅ `7.79s` |
| 真实对话渲染 | ✅ 见 9.3 ② |
| XSS 不执行 | ✅ 见 9.3 ③ |

---

## 九之二、v3 修正：区块失败**不得**被显示成「没有数据」

### 9b.1 问题（用户代码复核发现）

原实现只有 `overviewLoading`，没有错误态：

```ts
const overviewLoading = computed(() => blocks.records.loading || ...)
// 卡片：if (loading) '…' else 按数据算 → 空数据时显示「未记录 / 无」
```

若 `records` 接口失败：`loading` 变 `false`、`records.value` 仍是 `[]`
⇒ 卡片显示 **「未记录 / 今日暂无测评」**，
而真实情况是 **「加载失败，我们不知道今天有没有测评」**。

这违反 Step 8 的设计原则：**一个区块失败，不应被解释成「没有数据」**。

### 9b.2 修复

```ts
/** 概览是三个数据源的聚合结果：任一失败 ⇒ 整体不可信 */
const overviewError = computed(
  () => !!(blocks.records.error || blocks.appointments.error || blocks.messages.error)
)

const OVERVIEW_UNAVAILABLE = { value: '暂无法加载', hint: '', tone: 'idle' as const }
const OVERVIEW_LOADING     = { value: '…',        hint: '', tone: 'idle' as const }

const todayCard = computed(() => {
  if (overviewError.value)   return OVERVIEW_UNAVAILABLE   // 失败优先于加载中
  if (overviewLoading.value) return OVERVIEW_LOADING
  ...
})
```

- 三卡**统一**显示「暂无法加载」（避免"部分真、部分假"的混读）
- 概览底部提示切换为 **「部分数据暂时无法加载，请稍后重试。」**（`data-state="error"`）
- **不给概览加独立重试按钮**：各区块自己的 `retry` 更新 `blocks.*.error` 后，概览会自动恢复
- **不吞掉整页**：仍保持区块级隔离

### 9b.3 端到端失败验证（真实浏览器）

临时让 `/api/assessmentRecord` 指向不可达端口（验证后立即 `git checkout` 还原），实测：

```json
{
  "cards": ["今日测评|暂无法加载", "近期预约|暂无法加载", "老师已回复|暂无法加载"],
  "noteState": "error",
  "note": "部分数据暂时无法加载，请稍后重试。",
  "alerts": ["加载失败，请重试"],
  "knowledge": "OK"
}
```

| 检查点 | 结果 |
| --- | :-: |
| 概览不再误报「未记录 / 无」 | ✅ 三卡均为「暂无法加载」|
| 概览提示切换 | ✅「部分数据暂时无法加载，请稍后重试。」|
| 失败区块自身提示 | ✅「加载失败，请重试」|
| **其他区块不受影响** | ✅ 推荐知识仍 `OK`（区块级隔离成立）|
| Dashboard 不白屏 | ✅ |
| 还原后恢复正常 | ✅ 三卡回到「未记录 / 无 / 2 条」· `noteState=ok` |

### 9b.4 附带：注释措辞修正

`escapeHtml` 的 javadoc 由「先替换再转义会导致 `<script>` 被还原」改为更准确的表述：

> **所有来自模型的原始内容必须先完成 HTML 转义；后续 Markdown 转换只允许生成预先白名单化的固定标签。**

（原文措辞容易让人误解为"顺序错误必然导致还原"。）

---

## 十、已知问题登记

| # | 问题 | 影响 | 处置 |
| :-: | --- | --- | :-: |
| 1 | 预览 WebView 偶发 `Cannot read properties of null (reading 'getBoundingClientRect')` | 当前**未观察到**影响 Dashboard / AI 功能的表现（渲染、调用、结果展示均正常）| **根因尚未定位**（不作推测性断言）；暂登记至 Step 9 排查 |
| 2 | 浏览器自动化工具 `agent-browser` 的 `click` 对 Element Plus 按钮不触发 `@click` | 仅影响自动化验证手段 | 已改用 `eval` + 原生 setter 绕过；人工点击正常（用户已截图确认）|

---

## 十一、建议 commit message

```text
feat(web/s8): Dashboard 重做（今日概览 + 4 区块）+ / 登录态分流

- 新增 components/dashboard/{SectionCard,StatCard,EmptyBlock}（仅 Dashboard 使用）
- 新增 DashboardView：今日概览 / 最近测评 / AI 助手 / 推荐知识 / 近期预约
- 新增 HomeEntryView：未登录 → 访客首页；已登录 → Dashboard（v-if 分流，不重定向）
- 新增 api/agent.ts：适配 /agent/chat 顶层 answer 结构
- 今日概览为「数据活动概览」而非心理评估：今日测评按 submittedAt 日期判定；
  老师已回复为历史累计（counselor_message 无已读字段，不提供未读语义）
- 区块级隔离：各自 loading/error/retry，单区块失败不影响其余
- AI 区块仅在用户主动发送时调用，不在首屏触发
- 后端/数据库 0 改动；HomeView 行为不变
```
