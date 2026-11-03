# Phase 3 / Step 8 · Dashboard 现状确认报告

> **性质**：现状确认（📐 **零代码 · 零改动**）
> **基线**：`HEAD = origin = 2207f9b` · worktree CLEAN · Step 11 已 CLOSED
> **结论摘要**：4/5 区块数据源就绪；🔴 **「今日状态」无数据源** + **`/` 路由归属** + **Design System 缺口** 三项待裁定

---

## 一、Step 8 的官方定义

来源：`docs/PROJECT2-MASTER-PLAN.md:530`

> **8** | 重做 Dashboard（**今日状态 / 最近测评 / AI 助手 / 推荐知识 / 近期预约**）| Dashboard 页 | 首页不再是「欢迎登录 + 菜单」

**注意**：Step 9 是「AI 助手页面（快捷动作 + Tool Calling 结构化反馈）」——**与 Step 8 不同批次**。

---

## 二、前端工程现状

### 2.1 技术栈（`frontend/package.json`）

```text
vue 3.5.12 · vue-router 4.4.5 · pinia 2.2.4
element-plus 2.8.4 · axios 1.7.7
vite 5.4.9 · typescript 5.6.3
```

### 2.2 目录结构（`frontend/src`，32 文件）

```text
api/        10 个模块（appointment / assessment / auth / content / counselor /
                        dict / favorite / http / message / student）
layouts/    PublicLayout.vue
router/     index.ts（15 条路由）
stores/     auth.ts（token/userId/username/role · isLoggedIn getter）
views/      16 个 .vue
App.vue · main.ts · env.d.ts
```

### 2.3 ⚠️ 没有独立 Design System

| 事实 | 影响 |
| --- | --- |
| **无 `components/` 目录** | 无共享组件（卡片/空状态/加载）|
| **无 `design-system` 工程**（Step 7 计划项）| 各页面直接用 `el-card` / `el-empty` / `el-skeleton` + `<style scoped>` 手写 |
| 样式散落在每个 view 的 scoped style | Dashboard 多区块会出现**重复样式**（`.block` / `.block-head` / `.count` 在多个 view 重复）|

> Step 7 实际产出是「**用 Element Plus 直接拼页面**」，不是「建立组件库」。
> Step 8 若要有 5 个区块，需要决定：**先补轻量基础组件，还是继续手写**。

### 2.4 当前首页 `HomeView.vue`（R1）

```text
三块：健康知识（前 3 条）· 通知公告（前 3 条）· 心理老师（前 3 位）
数据：listKnowledge() / listNotifications() / listCounselors()
特点：**全部公开 API，未登录即可浏览**
```

⇒ 当前 `/` **不是 Dashboard**，而是「访客内容首页」。这直接引出 §四的裁定项 ②。

### 2.5 `PublicLayout` 导航（11 项）

```text
首页 / 知识 / 通知 / 老师 / 试卷 / 我的信息 / 我的记录 / 错题 / 收藏 / 预约 / 留言
```

无 Dashboard 入口；`isLoggedIn` 已可从 `useAuthStore()` 取得。

### 2.6 已完成路由（15 条）

```text
R1 /  R2 /login  R4 /knowledge  R5 /knowledge/:id
R6 /notifications  R7 /notifications/:id  R8 /counselors  R9 /counselors/:id
R10 /me  R14 /assessment  R16 /me/records  R17 /me/records/:recordNo
R18 /me/wrong-questions  + /me/favorites  /me/appointments  /me/messages
（旧口径 /assessmentRecords → redirect /me/records）
```

**未实现**：R3 `/register`（注册）、R11–R13、R15（答题，已裁定暂缓）

---

## 三、五个区块的数据可行性（**关键结论**）

### 3.1 数据库实际表清单（16 张）

```text
admin_user · assessment_answer · assessment_paper · assessment_question
assessment_record · assessment_wrong_question · auth_token
counseling_appointment · counselor · counselor_favorite · counselor_message
knowledge_article · notification · student · sys_config · sys_dict_item
```

### 3.2 逐区块判定

| # | 区块 | 数据源 | 现有前端 API | 判定 |
| :-: | --- | --- | --- | :-: |
| 1 | **今日状态** | 🔴 **无任何表**（无 `mood` / `status` / `checkin` / `diary`）| — | 🔴 **无数据源** |
| 2 | **最近测评** | `assessment_record`（**1 条**种子）| `listAssessmentRecords()`（需登录 + DataScope）| ✅ |
| 3 | **AI 助手** | `POST /agent/chat`（Step 11，`2207f9b`）| ❌ **无 `api/agent.ts`**，需新增 | ✅ 可行 |
| 4 | **推荐知识** | `knowledge_article`（**5 条**）| `listKnowledge()`（公开）| ✅ |
| 5 | **近期预约** | `counseling_appointment`（**3 条**）| `listMyAppointments()`（需登录）| ✅ |

### 3.3 各表数据量（实测）

```text
knowledge_article      5     notification          5
assessment_paper       2     assessment_record     1
counseling_appointment 3     counselor_favorite    6
counselor_message      6     counselor             3
student                3
```

> 数据量足够渲染 Dashboard 预览（每区块 3 条左右）。

---

## 四、🔴 必须裁定的 3 项

### 裁定 1：「今日状态」如何处理

**问题**：MASTER-PLAN 写的是「今日状态」，但库里**没有任何承载它的表**。

| 选项 | 做法 | 代价 |
| --- | --- | --- |
| **A（推荐）** | 改为**聚合态**：「今日」视角的 3 个状态点 —— 今日是否已测评 / 是否有待赴预约 / 是否有未读留言。纯前端由现有 API 计算，**零后端改动** | 与字面「今日状态」略有偏差，但语义成立 |
| B | 换区块：改为「我的收藏」「最新消息」等有数据源的内容 | 偏离 MASTER-PLAN 原意 |
| C | **新增 `mood` / `checkin` 表** + 后端接口（心情打卡） | 🔴 超 Step 8 范围，属**新业务功能**，需独立设计（表设计 + API + 权限 + 测试）|

> **我的建议：A**。理由：Step 8 目标是把首页从「内容罗列」变成「个人工作台」，聚合态能达成这个目标且零后端风险；C 应作为独立产品需求另开批次。

### 裁定 2：`/` 路由归属（公开首页 vs 登录后 Dashboard）

**问题**：当前 `/` 是**公开**访客页；Dashboard 必然需要登录态数据（最近测评/近期预约）。

| 选项 | 做法 | 影响 |
| --- | --- | --- |
| **A（推荐）** | `/` **按登录态分流**：未登录渲染现有访客首页；已登录渲染 Dashboard | 保留 SEO/公开浏览；改动集中在一个 view |
| B | `/` 保持访客页，新增 `/dashboard`，登录后自动跳转 | 多一条路由，但「首页」语义分裂 |
| C | `/` 改为 Dashboard（`requiresAuth`），访客内容移到 `/discover` | 🔴 破坏现有公开浏览与既有链接 |

> **我的建议：A**。实现上可用一个壳组件按 `auth.isLoggedIn` 切换，或路由守卫改写。
> ⚠️ 需确认：**未登录用户访问 `/` 时不应触发任何需登录 API**（否则 401 → 被 `http.ts` 清态跳登录，破坏公开浏览）。

### 裁定 3：Design System 缺口是否本批补齐

**现状**：无 `components/`，5 个区块会产生重复样式（`.block` 系列已在多个 view 重复）。

| 选项 | 做法 |
| --- | --- |
| **A（推荐）** | 新增 **3 个极简组件**：`SectionCard`（标题+操作+内容）/ `StatCard`（数字+标签）/ `EmptyBlock`（空状态）—— 仅 Dashboard 使用，不追求全局组件库 |
| B | 不补组件，继续在 Dashboard 单文件内手写（样式集中在一个 view，也可接受）|
| C | 正式建立 Design System（token + 全量组件）| 🔴 超 Step 8 范围 |

---

## 五、技术风险（已识别）

| # | 风险 | 说明 | 缓解 |
| :-: | --- | --- | --- |
| 1 | 未登录访问 Dashboard 触发 401 | `http.ts` 的 401 处理会清态并跳登录，可能打断公开浏览 | 裁定 2 选 A，且**未登录不调用需登录 API** |
| 2 | `/agent/chat` 同步阻塞 ~2.4s | Dashboard 里若自动调用会拖慢首屏 | **不自动调用**：AI 区块只做「输入框 + 按钮」，用户主动触发 |
| 3 | AI 区块与 Step 9 边界重叠 | Step 9 是「AI 助手完整页」 | Step 8 只做**入口卡片**（一个输入框 + 跳转/就地调用），不做快捷动作与过程反馈 |
| 4 | `assessment_record` 仅 1 条 | "最近测评"区块可能显得空 | 正常（a2 本来 0 条）；用空状态兜底 |
| 5 | 无 e2e/组件测试框架 | 现有前端**无任何测试**（`package.json` 无 vitest/playwright）| 本批不引入测试框架（超范围）；靠手工验证 + `data-test` 属性预留 |

---

## 六、建议的 Step 8 范围（待批准）

```text
✅ 做：
  · Dashboard 视图（5 区块，其中「今日状态」按裁定落地）
  · `/` 按登录态分流（若裁定 2 选 A）
  · 新增 api/agent.ts（封装 POST /agent/chat）
  · （可选）3 个极简组件
  · 手工验证：未登录/登录两态、5 区块数据正确、AI 区块可调用

❌ 不做：
  · 新增 mood/checkin 表与后端接口
  · 后端任何改动（Step 11 已提供 /agent/chat）
  · Step 9 的 AI 助手完整页（快捷动作 / 过程反馈 / 多轮）
  · Design System 全量建设
  · 前端测试框架引入
  · R3 注册页、R11–R13、R15
```

---

## 七、待裁定汇总

| # | 问题 | 建议 |
| :-: | --- | --- |
| 1 | 「今日状态」无数据源 | **A：改为聚合态**（今日是否测评/待赴预约/未读留言），零后端改动 |
| 2 | `/` 路由归属 | **A：按登录态分流** |
| 3 | Design System 缺口 | **A：补 3 个极简组件** |

---

## 八、状态

| 项 | 值 |
|---|---|
| 代码改动 | **0**（纯只读调研）|
| HEAD / origin | `2207f9b` / `2207f9b` |
| worktree | **CLEAN** |
| 服务 | 8080 就绪（PID `32200`）|
| 下一步 | 你裁定 3 项后，出 `PHASE3-STEP8-DESIGN.md`（仍不写代码）|
