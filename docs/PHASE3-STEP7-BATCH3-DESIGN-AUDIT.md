# Phase 3 / Step 7 · Batch 3 —— 访客侧 R4–R9 设计与覆盖审计（**只读**）

> **性质**：只读审计 + 设计 ✓ —— **零代码改动** ✗ · 零提交 ✗
> **裁定依据**：Batch 3 = 访客侧 R4–R9 ✓ · 403 **仅自然业务流验证**（不为测试造页面 ✗）· `/assessmentRecords → /me/records`（**Step 6 路由表不改** ✓）· C1/C2 独立批 ✓ · **G3 不塞入本批** 🔴
> **基线**：`128de9d`（Batch 2 已推送 ✓）· worktree CLEAN ✓

---

## 1. 实测能力矩阵（本轮新取证 ✓）

| # | 路由 | 所需 API | 匿名可访问 | 实测数据 | 字段 | 判定 |
| :-: | --- | --- | :-: | :-: | --- | :-: |
| R4 | `/knowledge` | `GET /knowledgeArticle/list` | ✅ `code=0` | **5**（= DB ✓）| `id,title,coverUrl,category,publishedAt,content,createdAt,categoryLabel` | ✅ 可直接做 |
| R5 | `/knowledge/:id` | 同上（**G1**）| ✅ | — | **含 `content` 正文** ✓ | ⚠️ 见 §2 |
| R6 | `/notifications` | `GET /notification/list` | ✅ `code=0` | **5**（= DB ✓）| 同 R4（同构）| ✅ |
| R7 | `/notifications/:id` | 同上（**G1**）| ✅ | — | 含 `content` ✓ | ⚠️ 见 §2 |
| R8 | `/counselors` | `GET /counselor/list` | ✅ `code=0` | **3**（= DB ✓）| `id,username,name,phone,avatarUrl,gender,email,expertise,resume,introduction,createdAt,genderLabel` | ✅ **但含 PII** ⚠️ 见 §3 |
| R9 | `/counselors/:id` | 同 R8（**G1**）| ✅ | — | 含 `expertise/resume/introduction` ✓ | ⚠️ 见 §2 + §4 |

**对照 `/info/{id}`**（匿名）：`/knowledgeArticle/info/5` · `/notification/info/1` · `/counselor/info/1` → **全部 `code=401`** ✓ ⇒ **G1 成立**（Step 6 §5 已登记 ✓）

---

## 2. G1 方案落地结论（**本轮新事实，修正 Step 6 的假设** ⚠️）

Step 6 §5 写的是「`/list` + 客户端筛选」✓，本轮进一步实测：

| 尝试 | 结果 |
| --- | --- |
| `/knowledgeArticle/list?id=5` | ❌ 仍返回 **5 条**（**`id` 参数被忽略** ✗）|
| `/notification/list?id=1` | ❌ 仍返回 **5 条** ✗ |
| `/counselor/list?id=1` | ❌ 仍返回 **3 条** ✗ |

⇒ **`?id=` 过滤不成立** ✗（不能靠后端筛选单条 ✓）。

**确定方案** ✓：

```text
GET /xxx/list（匿名 ✓ 全量 5 / 5 / 3 条）
        ↓
前端按 id 精确匹配（数据量极小 ✓ 5 条）
        ↓
命中 → 渲染详情；未命中 → 空态「未找到该内容」
```

* **不改后端** ✗ ✓ · **不新增接口** ✗ ✓
* 代价：详情页首屏多拉一次全表 ✓（5 条，可忽略 ✓）
* 风险：若未来列表增长到千级 ✗ → 需后端补公开 `info` ✓（登记为 **G1-future** ⚠️，非本批 ✗）

---

## 3. 字段安全（**已知挂账，本批不修** ✗）

`/counselor/list` 匿名返回 `phone` / `email` / `username` ✗。

**这不是新发现** ✓ —— `docs/jiaojie.md:253` 已记载：

> 「明确公开 4（`jiankangzhishi`/`tongzhi`/`config` + **`xinlilaoshi`** ⚠️ 首页要它但**含 PII** → 归 **phone-email 批次**）」

⇒ 处置（本批范围 ✓）：

| 做 | 不做 |
| --- | --- |
| ✅ 前端**只渲染** `name / avatarUrl / genderLabel / expertise / resume / introduction` | ❌ **不渲染** `phone` / `email` / `username` |
| ✅ 静态验收：counselor 视图内 `phone`/`email` 命中 = **0** | ❌ 不改后端字段投影（属 phone-email 批次 ✓）|

---

## 4. R9 的「收藏 / 预约 / 留言」入口（**边界收紧** ✓）

Step 6 R9 原文：「收藏/预约/留言入口（**需登录** ✓）」。

本批实现为**纯入口** ✓：

```text
未登录 → 按钮点击 → /login?redirect=/counselors/{id}      （不调用任何写 API ✓）
已登录 → 按钮点击 → 提示「该功能将在后续批次开放」          （R11/R12/R13 未实现 ✗）
```

* **不实现** `counselorFavorite/save` · `counselingAppointment/save` · `counselorMessage/save` ✗（属 R11–R13 ✓）
* **不产生写操作** ✗ ⇒ 无副作用 ✓

---

## 5. 🔴 403 自然场景预判（**结论：本批预计无自然触发** ✗）

| 路由 | 可能产生 403 的操作 | 本批是否存在 |
| --- | --- | :-: |
| R4–R7 | 全公开读取 | ❌ 无 |
| R8 | 公开目录 | ❌ 无 |
| R9 | 收藏/预约/留言**写** | ❌ 本批**不调用**（仅入口 ✓）|

⇒ **按裁定登记**（不为测试造页面 ✓）：

> **后端 403 合同已实测（`403 + code=403` ✓）；前端处理逻辑已静态验证；当前访客侧业务流暂无自然 403 触发场景 ⇒ 留待出现真实业务 UI（如 R11–R13 写操作、或管理端入口）时验证。**

**不会**因此新增无业务意义页面 ✗ ✓。

---

## 6. 路由口径修正（`/assessmentRecords` → `/me/records`）

| 项 | 内容 |
| --- | --- |
| 新路径 | **`/me/records`** ✓（= Step 6 R16 定义 ✓）|
| 旧路径 | `/assessmentRecords` → **`redirect: '/me/records'`** ✓（不失效 ✓）|
| Step 6 路由表 | **不修改** ✓ |
| API | 仍为 `GET /api/assessmentRecord/list` ✓（**页面 IA 与后端资源名解耦** ✓）|
| 组件 | 沿用 `AssessmentRecordsView.vue` ✓（仅路由 path 调整，重命名非必要 ✗）|

---

## 7. 拟定实现清单（**待批准后才写** ✗）

| # | 文件 | 内容 |
| :-: | --- | --- |
| 1 | `api/content.ts` | `+listNotifications()` ✓ · `+findById<T>(list, id)` 小工具 ✓ |
| 2 | `api/counselor.ts`（新）| `listCounselors()` + `Counselor` 类型（**仅安全字段** ✓）|
| 3 | `views/KnowledgeListView.vue`（新）| R4 |
| 4 | `views/KnowledgeDetailView.vue`（新）| R5（G1：`/list` + 匹配 ✓）|
| 5 | `views/NotificationListView.vue`（新）| R6 |
| 6 | `views/NotificationDetailView.vue`（新）| R7 |
| 7 | `views/CounselorListView.vue`（新）| R8（**不渲染 PII** ✓）|
| 8 | `views/CounselorDetailView.vue`（新）| R9 + 三入口 ✓ |
| 9 | `router/index.ts` | +6 路由 ✓ · `/me/records` ✓ · 旧路径 redirect ✓ |
| 10 | `layouts/PublicLayout.vue` | 导航扩展（知识/通知/老师/我的记录 ✓）|

> **总计 10 文件**（8 新增 + 2 修改）· **后端 0** ✗ · `front/` 0 ✗ · `npm run build` 0 ✗

**可选（需你单独批准 ✗）**：R1 首页补齐「通知 + 老师」两块（把 R1 从 🟡 补成 ✅）—— 因为三个列表本批都要实现，成本很低 ✓，但**不默认纳入** ✗。

---

## 8. 拟定验收（A–H）

| 项 | 内容 |
| :-: | --- |
| **A 静态** | 6 个视图无 `axios.`/`Token` 手写 ✓ · **counselor 视图 `phone`/`email` 命中 = 0** ✓ · 无 mock ✓ · `vue-tsc --noEmit` = 0 ✓ |
| **B 路由** | 6 条新路由可渲染 ✓ · `/assessmentRecords` → **`/me/records`** ✓ · 旧链接不失效 ✓ |
| **C 数据** | 知识 **5** / 通知 **5** / 老师 **3**（= DB ✓）· 详情按 id 命中 ✓ · 不存在 id → 空态 ✓（**非 Mock** ✓）|
| **D 权限** | 访客可访问 R4–R9 ✓（匿名 ✓）· 未登录点「预约」→ `/login?redirect=/counselors/{id}` ✓ · **无写操作** ✓ |
| **E 403** | 若出现自然场景 → 按 Batch 3 必验链路验证 ✓；否则按 §5 登记 ✓ |
| **F Console** | 0 error / 0 warning ✓ |
| **G 回归** | `mvn 47/47` ✓（后端未改 ✗）|
| **H Git** | 单一提交 ✓（10 文件）· 不含 `node_modules`/`dist` ✓ |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | Batch 3 只读审计：R4–R9 能力矩阵（5/5/3 条，全部匿名 ✓）· **`?id=` 过滤不成立** ✗ ⇒ G1 = 前端全表匹配 ✓ · `counselor/list` **PII 属既有挂账** ✓（前端不渲染 ✓）· R9 仅入口不写 ✓ · **403 预判无自然场景** ✗ · 路由口径修正方案 ✓ · 10 文件清单 + A–H 验收（**待批准** ✗）|
