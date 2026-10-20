# Phase 3 / Step 7 · Batch 3 —— 访客侧 R4–R9（已实现 · **待提交** ✗）

> **范围（已批准 ✓）**：R4 知识列表 · R5 知识详情 · R6 通知列表 · R7 通知详情 · R8 老师目录 · R9 老师详情（仅入口）+ `/assessmentRecords → /me/records`
> **不做（按裁定 ✗）**：R1 首页补齐 ✗ · 后端公开 `info` ✗ · R11–R13 写操作 ✗ · `npm run build` ✗ · G3 ✗ · phone-email 后端投影 ✗
> **基线**：`128de9d`（Batch 2 已推送 ✓）· 后端 **0 改动** ✗

---

## 1. 改动清单（实测 **11 文件** = 7 新增 + 4 修改）

| # | 文件 | 类型 | 说明 |
| :-: | --- | :-: | --- |
| 1 | `api/content.ts` | M | `+listNotifications()` · 改用共享 `pickList` |
| 2 | `api/counselor.ts` | **A** | `listCounselors()` + **仅安全字段**类型 ✓ |
| 3 | `api/http.ts` | M | **`+findById()`**（G1 共享工具；计划外第 11 个文件 ⚠️ 见 §5）|
| 4 | `views/KnowledgeListView.vue` | **A** | R4 |
| 5 | `views/KnowledgeDetailView.vue` | **A** | R5（G1）|
| 6 | `views/NotificationListView.vue` | **A** | R6 |
| 7 | `views/NotificationDetailView.vue` | **A** | R7（G1）|
| 8 | `views/CounselorListView.vue` | **A** | R8（仅安全字段 ✓）|
| 9 | `views/CounselorDetailView.vue` | **A** | R9 + 三入口（**不调用写 API** ✗）|
| 10 | `router/index.ts` | M | +6 路由 · `/me/records` · 旧路径 redirect |
| 11 | `layouts/PublicLayout.vue` | M | 导航 6 项 |

**后端 0** ✗ · `front/` 0 ✗ · `node_modules`/`dist` 0 ✗

---

## 2. 验收 A–H（实测）

| 项 | 内容 | 结果 |
| :-: | --- | --- |
| **A** 静态 | 6 个视图无 `axios.` / `Token` 手写 | **0** ✓ |
| | **counselor 视图 PII 命中（`phone|email|username`）** | **0** ✓✓ |
| | `counselor.ts` 类型中 PII 字段 | **0** ✓ |
| | mock | **0** ✓ |
| | `vue-tsc --noEmit` | **exit 0** ✓（首轮 1 处 import 错误 ✗ → 已修正 ✓）|
| **B** 路由 | `/knowledge` | 渲染 ✓（`共 5 条`）|
| | `/knowledge/5` | `健康知识名称5` ✓ |
| | `/knowledge/999` | **`未找到该内容`** ✓（空态 ✓）|
| | `/notifications` | 渲染 ✓（标题 `通知公告`）|
| | `/counselors` | **`共 3 位`** ✓ |
| | `/counselors/1` | `擅长领域 = 擅长1` ✓ |
| | `/assessmentRecords` | → **`/me/records`** ✓（未登录时再 → `/login?redirect=/me/records` ✓）|
| **C** 数据 | 知识 **5** / 通知 **5** / 老师 **3** | = DB ✓（**非 Mock** ✓）|
| **D** 权限 | 访客可访问 R4–R9（无需登录）| ✓ |
| | 未登录点「预约」 | → **`/login?redirect=/counselors/1`** ✓✓ |
| | 写操作 | **0 次** ✓（R9 仅入口 ✓）|
| **E** 403 | 自然场景 | **未出现** ✗ ⇒ 按裁定登记（见 §3）|
| **F** Console | 浏览器 | **Errors 0 / Warnings 0** ✓ |
| **G** 回归 | `mvn test` | **47 / 47**（failures 0 · errors 0 ✓，后端未改 ✗）|
| **H** Git | — | **未提交** ✗ · 11 文件 · `.playwright-cli/` 已移出 ✓ |

---

## 3. 🔴 403：**无自然触发场景**（按裁定登记 ✓）

| 路由 | 可能的 403 操作 | 本批 |
| --- | --- | :-: |
| R4–R8 | 全公开读取 | ❌ 无 |
| R9 | 收藏/预约/留言（写）| ❌ **未接入** ✓ |

> **后端 403 合同已实测（`403 + code=403` ✓）；前端处理逻辑已静态验证；当前访客侧业务流暂无自然 403 触发场景 ⇒ 留待出现真实业务 UI（R11–R13 写操作、管理端入口）时按 Batch 3 必验链路验证。**
> **未为凑测试新增任何无业务意义页面** ✗ ✓

---

## 4. G1 过渡策略（**明确定性，非"详情 API"** ✗）

| 事实 | 证据 |
| --- | --- |
| `/info/{id}` 需登录 | 三个 `info` 匿名 → `code=401` ✓ |
| `/list` 公开 | 匿名 `200/code=0` ✓（5/5/3 条）|
| **`?id=` 被后端忽略** | `?id=5` 仍返回 **5 条** ✗ |

**策略**：拉公开全表 + 前端 `findById()` 精确匹配 ✓；未命中 → 空态 ✓。

**代价（如实登记 ⚠️）**：
1. 数据量增长后不适用 ✗
2. **详情页不能声称拥有比 `/list` 更多的后端数据** ✗
3. **不新增公开 `info` API** ✗ —— 否则把 G1（已知技术债）变成新的后端安全面 ✓

---

## 5. 计划偏差（如实登记 ⚠️）

| 项 | 计划 | 实际 |
| --- | :-: | :-: |
| 文件数 | **10**（8 新 + 2 改）| **11**（7 新 + 4 改）✗ |
| 原因 | — | `findById` 最初放在 `content.ts` ✓ → 类型检查发现 `CounselorDetailView` 跨模块引用不当 ✗ → 移入共享层 `api/http.ts` ✓（更干净 ✓，但多 1 个改动文件 ✓）|
| 范围影响 | — | **无** ✓（未增加功能 ✓，纯归属调整 ✓）|

---

## 6. R9 入口状态（**避免"假完成"** ✓）

```text
未登录 → 点击 → /login?redirect=/counselors/{id}     （已实测 ✓）
已登录 → 点击 → ElMessage「该功能将在后续批次开放」     （无写操作 ✓）
```

页面明示：「以上三个入口当前仅做状态判断，**尚未接入写操作**」✓

---

## 7. 路由口径

| 项 | 值 |
| --- | --- |
| 新路径 | **`/me/records`** ✓（= Step 6 R16 ✓）|
| 旧路径 | `/assessmentRecords` → `redirect: '/me/records'` ✓ |
| Step 6 路由表 | **未修改** ✓ |
| 组件 | 沿用 `AssessmentRecordsView.vue` ✓ |

**当前路由总数**：`/` · `/login` · `/knowledge` · `/knowledge/:id` · `/notifications` · `/notifications/:id` · `/counselors` · `/counselors/:id` · `/me` · `/me/records` + 旧路径 redirect = **10 条有效路由** ✓

---

## 8. 未验证（✗）

| # | 项 |
| :-: | --- |
| 1 | **403 前端浏览器运行时**（无自然场景 ✗ → Batch 3 必验项继续挂账 ⚠️）|
| 2 | 本批**未截图** ✗ |
| 3 | `npm run build`（= C1 独立批 ✗）|
| 4 | R1 首页补齐（按裁定**不在本批** ✗）|

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | Batch 3 实现完成（11 文件）· **A–H 验收**（6 路由渲染 ✓ · 数据 5/5/3 = DB ✓ · 空态 ✓ · 未登录入口跳转 ✓ · 零写操作 ✓ · PII 0 ✓ · Console 0 error ✓ · 47/47 ✓）；**403 无自然场景** ⇒ 登记 ✓；G1 过渡策略定性 ✓；**未提交** ✗ |
