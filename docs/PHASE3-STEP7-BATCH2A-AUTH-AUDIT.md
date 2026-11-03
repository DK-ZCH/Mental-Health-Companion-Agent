# Phase 3 / Step 7 · Batch 2-A —— 新前端认证边界审计（**只读**）

> **性质**：Read-only 审计 ✓ —— **零代码改动** ✗ · 零提交 ✗
> **目的**：在写 Batch 2 代码前，先确认 Batch 1 到底留下了什么可复用实现、缺什么、后端真实 401/403 合同是什么
> **基线**：`b847630`（已完成并推送 ✓）· 工作区 CLEAN ✓

---

## 1. 现有实现（逐文件）

### 1.1 `frontend/src/api/http.ts`（49 行）

| 能力 | 状态 | 说明 |
| --- | :-: | --- |
| `baseURL: '/api'` · `timeout: 15000` | ✅ | 经 Vite proxy ✓ |
| **请求注入 `Token` 头** | ✅ | 从 `localStorage[TOKEN_KEY]` 读取 ✓（`logout()` 清 localStorage 后自动失效 ✓）|
| 响应错误：`err.response.status === 401` → 删 token | ⚠️ **永不触发** ✗ | 后端未登录是 **HTTP 200 + body.code=401** ✗ ⇒ 该分支对本项目**无实际作用** ✗ |
| 处理 **body `code=401`** | ❌ 缺 | 必须新增 ✓ |
| 处理 **HTTP 403 / `code=403`** | ❌ 缺 | 必须新增（且**不得**误判为登录失效 ✗）|
| 清 **Pinia** 状态 | ❌ 缺 | 现在只删 localStorage 一个 key ✗ |
| 跳转 `/login` | ❌ 缺 | — |
| `unwrap()` 错误信息 | ⚠️ 仅 `Error(msg)` ✗ | **丢失 `code`** ✗ ⇒ 上层无法区分 401/403 ✓（Batch 2 需带 `code` 的错误类型 ✓）|

### 1.2 `frontend/src/stores/auth.ts`（56 行）

| 能力 | 状态 |
| --- | :-: |
| state：`token / userId / username / role` | ✅ |
| 持久化：`mh_token` / `mh_user_id` / `mh_username` / `mh_role` | ✅ |
| getter `isLoggedIn` | ✅ |
| `login()`：存 4 项 + **立即 `/student/session` 校验**（防"登录成功但会话失效"）| ✅ |
| `logout()`：清 state + 4 个 localStorage key | ✅ **核心已具备** |
| `401 → 自动 logout` 联动 | ❌ 缺 |
| 启动时用 `/student/session` **校验** token（而非仅信任 localStorage）| ❌ 缺 |
| `403` 相关 | 不需要（✅ 正确：403 不应清登录态 ✓）|

### 1.3 `frontend/src/router/index.ts`（25 行）

```text
/        → PublicLayout > HomeView   （公开 ✓）
/login   → LoginView                 （公开 ✓）
/:pathMatch(.*)* → redirect '/'      （兜底 ✓）
```

| 能力 | 状态 |
| --- | :-: |
| `beforeEach` 导航守卫 | ❌ 缺 |
| `meta.requiresAuth` 声明式保护 | ❌ 缺 |
| 未登录 → `/login?redirect=…` 回跳 | ❌ 缺 |

### 1.4 视图/布局

| 文件 | 已有 | 缺 |
| --- | --- | --- |
| `PublicLayout.vue` | 顶栏显示 `username` ✓ · `退出` → `auth.logout()` + `router.push('/')` ✓ | 受保护页退出后**强制**回登录（依赖守卫 ✗）|
| `LoginView.vue` | 表单校验 ✓ · `error` 展示 ✓ · 成功后 `router.push('/')` ✓ | `redirect` 参数回跳 ✗ |
| `HomeView.vue` | `listKnowledge()` ✓ · loading/empty/error 三态 ✓ | — |

---

## 2. 对照 Batch 2 五项：完成度

| # | Batch 2 目标 | 现状 | 缺口 |
| :-: | --- | :-: | --- |
| 1 | 受保护路由（未登录 → `/login`）| ❌ **全缺** | 守卫 + meta + redirect |
| 2 | Axios 认证注入（自动带 token）| ✅ **已实现** | 可保持；建议改读 store 以单一数据源（可选，非必须）|
| 3 | 401/403 处理 | ❌ **全缺** | 当前 401 逻辑与后端形态**不匹配** ✗ |
| 4 | 退出登录清态 | 🟡 **90%** | 清态已通 ✓；缺"受保护页退出 → 回登录"（由守卫覆盖）|
| 5 | 至少一个真实受保护业务页面 | ❌ 未实现 | 见 §4 候选 |

> 结论：**Batch 2 的真实工作量 = 守卫 + 401/403 归一 + 1 个受保护页**（认证注入与登出清态已存在 ✓，不需要重写 ✗）。

---

## 3. 后端 401/403 **权威合同**（实测 + 代码双证 ✓）

### 3.1 实测（只读 GET，全部 **HTTP 状态 + body code 同时记录**）

| 场景 | 请求 | HTTP | body |
| --- | --- | :-: | --- |
| 无 token | `/assessmentRecord/list` | **200** | `code=401, msg=请先登录` |
| 无 token | `/student/session` | **200** | `code=401, msg=请先登录` |
| 无 token | `/adminUser/page` | **200** | `code=401, msg=请先登录` |
| **伪造 token** | `/assessmentRecord/list` | **200** | `code=401, msg=请先登录` |
| 公开接口（对照）| `/knowledgeArticle/list` | 200 | `code=0` |
| **已登录无权**（学生 token）| `/adminUser/page` | **403** | `code=403, msg=无权访问` |
| **已登录无权**（学生 token）| `/adminUser/list` | **403** | `code=403, msg=无权访问` |
| **他人资源**（学生1 → 学生2 资料）| `/student/info/2` | **403** | `code=403, msg=无权访问` |
| 本人资源（对照）| `/assessmentAnswer/info/1` | 200 | `code=0` |

### 3.2 代码依据

* `AuthorizationInterceptor`：未登录 → **HTTP 200 + `body.code=401`**（`GlobalExceptionHandler` 文档明确"不处理未登录，保持既有契约" ✓）
* `GlobalExceptionHandler.handleForbidden`：`ForbiddenException` → **HTTP 403 + `code=403`** ✓（Step 5 批 2 新增行为契约 ✓）
* 其他：参数错误 **400** / 资源不存在 **404** / 未捕获 **500** ✓

### 3.3 ⇒ 前端判定规则（Batch 2 必须按此实现）

```text
未登录失效 = (HTTP 200 && body.code === 401)   ← 主形态 ✓
           || (HTTP 401)                        ← 兼容保险 ✓
    ⇒ 清 Pinia + 清 localStorage + 跳 /login?redirect=<当前路径>

无权访问   = (HTTP 403 && body.code === 403)
           || (HTTP 200 && body.code === 403)   ← 兼容保险
    ⇒ 【不清登录态】✗ 只提示"无权访问"

其他错误   = 其余情况 ⇒ 仅提示 msg（不改登录态）
```

> ⚠️ **绝不允许**把 403 当作登录失效 ✗ —— 否则用户会被踢出登录 ✓（这是本批最容易犯的错 ✓）。

---

## 4. 受保护业务页面候选（实测数据 · 学生 A `userId=1`）

| 候选页面 | API | 学生 A 数据量 | 归属语义 | 评价 |
| --- | --- | :-: | :-: | --- |
| **测评记录** ⭐ | `/assessmentRecord/list` | **1** | ✅ `studentId`（B=0 已在 5c/Batch 2 验证 ✓）| **推荐** |
| 答题明细 | `/assessmentAnswer/list` | 5 | ✅ | 可作第二页（可选）|
| 错题 | `/assessmentWrongQuestion/list` | 3 | ✅ | 可选 |
| 预约 | `/counselingAppointment/list` | 3 | ✅ `studentId`/`counselorId` | 可选 |
| 收藏 | `/counselorFavorite/list` | 2 | ✅ | 可选 |
| 个人信息 | `/student/session` | 对象 | 本人 ✓ | 适合 `/me`（证明 token → CurrentUserProvider ✓）|

**建议 Batch 2 范围（最小且可判别）**：

1. `/me` —— 个人信息（`/student/session` ✓，只读 ✓，证明 **token → CurrentUserProvider** ✓）
2. `/assessmentRecords` —— 测评记录列表（`/assessmentRecord/list` ✓，**学生 A=1 条 / 未登录 401** ⇒ 天然判别 ✓）

> 两者都在**前端新增** ✓，**不改后端** ✗。

---

## 5. 403 的**只读**验证方案（Batch 2 验收用）

```text
学生 token → GET /adminUser/page      ⇒ HTTP 403 + code=403   （只读 ✓ 无副作用 ✓）
学生 token → GET /student/info/2      ⇒ HTTP 403 + code=403   （他人资源 ✓）
学生 token → GET /assessmentRecord/list ⇒ 200 + code=0 + 1 条 （对照 ✓）
无 token  → GET /assessmentRecord/list ⇒ HTTP 200 + code=401   （对照 ✓）
```

⇒ 无需任何写操作即可覆盖 401/403 两条分支 ✓（**不触碰 `SEC-PASS-RESET`** ✗）。

---

## 6. ⚠️ 审计中发现的既有授权不对称（**未定级，待裁定** ✗）

| 观察 | 证据 | 说明 |
| --- | --- | --- |
| `/student/page`（用户列表）**学生 token 可访问** ✗ | student token → `200 code=0`，首条含 `id,username,name,phone,avatarUrl,gender,email` ✓（**无 `password`** ✓）| 对照 `/adminUser/page` **有** `assertAdminOnly` ✓ ⇒ **同类接口授权策略不一致** ✗ |

* **不属 Batch 2 范围** ✗（Batch 2 只做前端认证边界 ✓）
* **未修复** ✗ · **未定级** ✗ · **不宣称是新发现** ✗（Phase 2 可能已登记 ✓）
* 建议：作为**独立观察项**由你裁定是否登记为 backlog ✓

---

## 7. Batch 2 拟定范围（**待批准后才实施**）

| 项 | 内容 | 类型 |
| --- | --- | --- |
| a | `http.ts`：统一错误归一（带 `code` 的 `ApiError` ✓ + 401 清态跳登录 ✓ + 403 仅提示 ✓）| 前端 |
| b | `router/index.ts`：`meta.requiresAuth` + `beforeEach` + `?redirect=` 回跳 ✓ | 前端 |
| c | `stores/auth.ts`：`logout()` 复用 + （可选）启动会话校验 ✓ | 前端 |
| d | `views/MeView.vue`（`/me`，`/student/session`）✓ | 前端 |
| e | `views/AssessmentRecordsView.vue`（`/assessmentRecords`，`/assessmentRecord/list`）✓ | 前端 |
| f | `api/`：`student.ts`（session ✓）· `assessment.ts`（records ✓）| 前端 |
| — | **不改后端** ✗ · 不改 `front/` ✗ · 不做 `npm run build` ✗ · 不碰 SEC-* ✗ · 不碰 G3 ✗ | — |

**验收（拟）**：

```text
A 静态：无 mock 数据源；token 仅由拦截器注入；403 分支不含 logout 调用
B 功能：未登录访问 /me 或 /assessmentRecords → 重定向 /login?redirect=…
        登录后自动回跳原路径 ✓
C 401：手动清 token 后访问 → 跳 /login ✓（且 Pinia 已清）
D 403：学生 token 访问 /adminUser/page ⇒ 403 且【仍保持登录】✓（关键判别 ✓）
E 退出：点"退出" → 4 项 localStorage 清空 ✓ 且访问 /me 被重定向 ✓
F 真实数据：/assessmentRecords 显示 A 的 1 条（≠ Mock ✓）· /me 显示 A 的身份 ✓
G 回归：mvn 47/47 ✓（后端未改 ✓）· Console 0 error ✓
H Git：单一提交，`frontend/` + 必要 docs ✓
```

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | Batch 2-A **只读**审计：现状（注入 ✅ / 登出清态 🟡 / 守卫 ❌ / 401-403 ❌）；**后端合同实测**（未登录 = 200+`code=401`；无权 = **403+`code=403`**）；受保护页候选与**只读 403 验证方案**；登记 `/student/page` 授权不对称（**未定级** ✗）；给出 Batch 2 拟定范围（**待批准** ✗）|
