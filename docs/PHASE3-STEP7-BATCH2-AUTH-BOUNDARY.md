# Phase 3 / Step 7 · Batch 2 —— 新前端认证边界（已实现 · **待提交** ✗）

> **范围**：a–f（已批准 ✓）· **不改后端** ✗ · 不改 `front/` ✗ · 不做 `npm run build` ✗ · 不碰 SEC-*/G3 ✗
> **基线**：`b847630`（已推送 ✓）→ 本批工作区 **9 个代码文件**（5 M + 4 新增）· **未提交** ✗

---

## 1. 实现清单（a–f）

| 项 | 文件 | 内容 |
| :-: | --- | --- |
| **a** | `api/http.ts`（重写）| **`ApiError(code, msg, httpStatus)`** ✓ · 成功分支处理 `code===401/403` ✓ · 错误分支兼容 HTTP 401/403 ✓ · `setAuthFailureHandler()` 回调注入 ✓ · `pickList()` 列表归一 ✓ |
| **b** | `router/index.ts` | `/me` · `/assessmentRecords` + `meta.requiresAuth` ✓ · `beforeEach` ✓ · 未登录 → `/login?redirect=<fullPath>` ✓ |
| **c** | `stores/auth.ts`（**未改动** ✗）| 刷新恢复已由 `state()` 初始化器读 localStorage 完成 ✓ ⇒ **不新增 session 调用、不重构** ✓ |
| **d** | `views/MeView.vue`（新）| `/me` → `getCurrentStudent()` ✓（`/student/session`）|
| **e** | `views/AssessmentRecordsView.vue`（新）| `/assessmentRecords` → `listAssessmentRecords()` ✓（`/assessmentRecord/list`）· **不渲染 `studentPhone/studentEmail`** ✓ |
| **f** | `api/student.ts` · `api/assessment.ts`（新）| View → API → `http.ts` → 后端 ✓（页面**不直接** `axios.*` ✓，静态检查 hits=**0** ✓）|
| 附带 | `main.ts` | 注入 auth-failure 回调：`useAuthStore(pinia).logout()` + `router.replace('/login?redirect=…')` ✓ |
| 附带 | `LoginView.vue` | 登录后 `redirectTarget()` 回跳（**仅接受站内绝对路径** ✓ 防开放重定向 ✓）|
| 附带 | `PublicLayout.vue` | 新增导航（健康知识 / 我的信息 / 测评记录）✓ · 受保护页退出 → `router.replace('/login')` ✓ |

### 设计要点（回应审阅）

1. **成功分支也要判 `code`** ✓ —— 后端 401 是 `HTTP 200 + code=401`，Axios 不会进 error 分支 ✓（这正是审计的核心结论）。
2. **403 绝不清登录态** ✓ —— `http.ts` L77-78 / L92-93 只有 `Promise.reject(new ApiError(403, …))`，**无任何 logout 调用** ✓（静态检查 ✓）。
3. **循环依赖规避** ✓ —— `http.ts` 不 import store/router，改为 `setAuthFailureHandler()` 由 `main.ts` 注入 ✓。
4. **守卫职责不越界** ✓ —— 守卫只判「本地有无 token」；「服务端认不认」由 API 层 401 处理 ✓。

---

## 2. 验收 A–H（实测）

| 项 | 内容 | 结果 |
| :-: | --- | --- |
| **A** 静态 | views/layouts 无手写 `Token` / `axios.` | **hits = 0** ✓ |
| | 403 分支无 logout | ✓ 仅 `reject(ApiError(403))` ✓ |
| | 无 mock | `mock_like = 0` ✓ |
| | 类型检查 `vue-tsc --noEmit` | **exit = 0** ✓ |
| **B** 未登录 | `GET /me`（无 token）| → **`/login?redirect=/me`** ✓ |
| **C** 回跳 | 登录成功后 | → **`/me`** ✓（`http://localhost:5173/me` ✓）|
| **D** 401 清态 | 伪造 `mh_token` → `GET /me` | → **`/login?redirect=/me`** ✓ · **localStorage 全空** ✓✓ |
| **E** 退出 | 受保护页点「退出」 | → **`/login`** ✓ · localStorage **全空** ✓ · 再访问 `/me` → `/login?redirect=/me` ✓ |
| **F** 真实数据 | `/me` | `用户名=a1` ✓ · `姓名=学生姓名1` ✓（真实 `/student/session` ✓）|
| | `/assessmentRecords` | **`共 1 条`** ✓ · `recordNo=1648530233813` ✓（= DB ✓ **非 Mock** ✓）|
| **G** Console | 浏览器 | **Errors 0 / Warnings 0** ✓ |
| | 后端回归 | **47 / 47**（failures 0 · errors 0）✓（本批**未改后端** ✗）|
| **H** Git | — | **未提交** ✗（等你审计 ✓）· 9 个代码文件 ✓ · `.playwright-cli/` 已移出 ✓ |

---

## 3. ⚠️ 未直接验证（如实登记 ✗）

| # | 项 | 原因 / 处置 |
| :-: | --- | --- |
| 1 | **403 前端浏览器运行时** | **403 后端合同已实测；前端处理逻辑已静态验证；缺少由真实 Vue/Axios 请求触发的浏览器级运行时证据，登记为 Batch 3 必验项。**（未为凑测试新增无业务意义的页面 ✗ —— 那样会污染本批范围 ✓）|
| 2 | `/me`、`/assessmentRecords` 的**截图** | 本批未截图 ✗（DOM 文本证据 ✓）|
| 3 | `npm run build` / `dist` | **本批不做** ✗（按裁定 ✓）|

---

## 3bis. 🔴 **Batch 3 必验项**：403 的浏览器级运行时链路

Batch 3 找到第一个**真正会产生 403 的业务 UI** 后，按以下链路验收（缺一不可）：

```text
登录状态存在
      ↓
在 Vue 页面中执行一次【无权操作】（真实 Axios 请求，非手写 fetch）
      ↓
后端返回 HTTP 403 + body.code=403
      ↓
http.ts 拦截器 → ApiError(403)（reject，不 logout）
      ↓
页面提示「无权访问」
      ↓
【关键】仍保持登录：再访问 /me 正常（localStorage 的 4 个 key 仍在）
```

> 判定要点：**403 之后 `mh_token` 必须仍存在** ✓；若被清除即为缺陷 ✗。

## 3ter. 本批状态（**不得宣称 100% 通过** ✗）

> **Batch 2 代码实现完成，核心认证边界运行时验证通过，403 前端运行时证据尚缺；允许形成独立提交，但 Batch 2 尚不能宣称"所有验收项 100% 通过"。**

---

## 4. `/student/page` 观察项 —— 证据已补（**不是越权** ✓）

| 学生 | `total` | 返回 `ids` |
| --- | :-: | --- |
| a1（userId=1）| **1** | **`1`** |
| a2（userId=2）| **1** | **`2`** |

⇒ **DataScope 生效** ✓（学生只能看到自己 ✓），与 `/adminUser/page`（`assertAdminOnly` ✓）策略不同但**不构成越权** ✓。

**登记（按裁定 ✅）**：

> **`SEC-STUDENT-PAGE-POLICY`**（P2 / 待业务确认）
> 学生端 `/student/page` 的访问策略与其他管理端分页接口不一致，需确认其业务意图及是否应保留该入口。
> **当前证据不支持"越权漏洞"定性** ✗ —— 若后续实测出现学生可见他人（`total>1` 或 `ids` 含他人 id），**立即升级** ⚠️。

**不进入 Batch 2** ✗ · **未修改** ✗

---

## 5. 附：本批未做（边界守护 ✓）

`npm run build` ✗ · `dist` 重建 ✗ · 全部旧页面迁移 ✗ · `SEC-PASS-RESET` ✗ · `SEC-ADMIN-REG` ✗ · G3 ✗ · Agent/RAG ✗ · **后端 API 改造** ✗

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | Batch 2 a–f 实现完成（9 文件）· **A–H 验收**（B/C/D/E 重定向与清态 ✓ · F 真实数据 `共 1 条` ✓ · Console 0 error ✓ · 47/47 ✓ · `vue-tsc` 0 ✓）；**403 浏览器级触发未验证** ✗（仅静态 + API 层）；`/student/page` 证据补齐（**非越权** ✓）并登记 `SEC-STUDENT-PAGE-POLICY` P2；**未提交** ✗ |
