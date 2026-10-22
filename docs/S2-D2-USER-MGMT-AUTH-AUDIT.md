# S2-D2 —— 用户账号管理授权（学生/老师 save/add/delete）只读审计

> 日期：2026-10-22 ｜ 基线：`90f27d8`（S2-D1 已推送，LEAD=0）
> 模式：**只读审计 / 设计** —— 不改 Controller/DB/前端/测试 ✗，不 commit ✗
> 上游：`S2-D-REMAINING-P1-AUDIT.md`（v3）§3 —— 用户管理端点无守卫（P2 聚合发现，需拆到具体 endpoint）

---

## 0. 结论速览

1. **6 个端点需要 `assertAdminOnly`**：`student/{save,add,delete}` + `counselor/{save,add,delete}` —— 均为「新增/删除用户账号」的管理功能。
2. **`/register` 与 `/add` 必须区分**：`/register` 是**自助注册**（旧 front `register.html`，匿名，业务允许）→ **保留**；`/add` 是**管理端「添加学生/老师」页**（`add.html` title 明确）→ **admin-only**。
3. **`/update` 已收口**（`assertSelfOrAdmin`）：个人资料修改 = self/admin，**不动**。
4. 新 frontend（Vue3）**无** `save/add/delete/register` 调用；旧 front 仅 `add`（管理页）+ `register`（注册页）。
5. 学生/老师**无「删除自己账号」业务入口** ⇒ `delete` 是纯管理功能。

---

## 1. 端点 × 调用方矩阵（实测）

### 1.1 Student

| 端点 | 管理端 `admin/src` | 旧 front | 新 `frontend/src` | 测试 | 语义 | 应授权 |
| --- | --- | :-: | :-: | :-: | --- | --- |
| `/student/save` | `yonghu/add-or-update.vue:268`（新增）| — | — | — | 管理端新增学生 | **admin-only** |
| `/student/add` | — | `yonghu/add.html:388`（title=「学生添加」）| — | — | 旧版管理端新增学生 | **admin-only** |
| `/student/delete` | `yonghu/list.vue:716` | — | — | — | 删除学生 | **admin-only** |
| `/student/update` | `yonghu/add-or-update.vue:268`（编辑）| — | 学生自助资料 | — | 编辑资料 | ✅ 已有 `assertSelfOrAdmin` |
| `/student/register` | — | `yonghu/register.html:186` | — | — | **自助注册** | 匿名（业务允许，保留）|
| `/student/resetPassword` | `yonghu/list.vue:738` | — | — | — | 管理端重置密码 | ✅ 已有 `assertAdminOnly` |
| `/student/resetPass` | — | 忘记密码 | — | — | 匿名重置 | ⚠️ D3 已登记，不动 |

### 1.2 Counselor

| 端点 | 管理端 `admin/src` | 旧 front | 新 `frontend/src` | 测试 | 语义 | 应授权 |
| --- | --- | :-: | :-: | :-: | --- | --- |
| `/counselor/save` | `xinlilaoshi/add-or-update.vue:300`（新增）| — | — | — | 管理端新增老师 | **admin-only** |
| `/counselor/add` | — | `xinlilaoshi/add.html:400`（title=「心理老师添加」）| — | — | 旧版管理端新增老师 | **admin-only** |
| `/counselor/delete` | `xinlilaoshi/list.vue:728` | — | — | — | 删除老师 | **admin-only** |
| `/counselor/update` | `xinlilaoshi/add-or-update.vue:300`（编辑）| — | 老师自助资料 | — | 编辑资料 | ✅ 已有 `assertSelfOrAdmin` |
| `/counselor/register` | — | `xinlilaoshi/register.html:189` | — | — | **自助注册** | 匿名（业务允许，保留）|
| `/counselor/resetPassword` | `xinlilaoshi/list.vue:750` | — | — | — | 管理端重置密码 | ✅ 已有 `assertAdminOnly` |
| `/counselor/resetPass` | — | 忘记密码 | — | — | 匿名重置 | ⚠️ D3 已登记，不动 |

> 新 `frontend/src`：`student/`、`counselor/` 的 `save|add|delete|register` 检索结果 **0** ✓（新前端无这些调用）。

---

## 2. 关键区分：`/register`（自助）vs `/add`（管理）

| 对比 | `/register` | `/add` |
| --- | --- | --- |
| 旧 front 页面 | `yonghu/register.html` / `xinlilaoshi/register.html`（按钮「注册」）| `yonghu/add.html` / `xinlilaoshi/add.html`（title「学生添加」/「心理老师添加」）|
| 注解 | `@IgnoreAuth @PostMapping("/register")` | `@RequestMapping("/add")`（**需登录**）|
| 提交后行为 | 注册后跳登录 | 跳 `list.html`（管理列表）|
| 语义 | **自助注册** | **管理端新增** |
| S2-D2 处理 | **保留匿名**（业务允许）| **加 `assertAdminOnly`** |

> 若不区分二者，会给 `/add` 加 admin-only 时误伤 `/register`，或反过来让管理端新增继续裸奔。

---

## 3. 当前守卫现状（代码取证）

| 端点 | 现状 | 位置 |
| --- | --- | --- |
| `/student/save` | **无守卫** ✗ | `StudentController` L113 |
| `/student/add` | **无守卫** ✗ | `StudentController` L475 |
| `/student/delete` | **无守卫** ✗ | `StudentController` L190 |
| `/counselor/save` | **无守卫** ✗ | `CounselorController` L111 |
| `/counselor/add` | **无守卫** ✗ | `CounselorController` L442 |
| `/counselor/delete` | **无守卫** ✗ | `CounselorController` L179 |

> 6 个端点均**无 `HttpServletRequest` 参数** ⇒ 加守卫前需补参数（同 S2-C2 模式）。
> `assertAdminOnly` 原语已存在（`OwnershipGuard` L117）。

---

## 4. 新登记（不修，待裁定）

| ID | 级别 | 事实 |
| --- | :-: | --- |
| **`SEC-USER-MGMT-SAVE-DELETE-NO-GUARD`** | 🟠 **P2（本批拆解为 6 端点）** | **认证用户越权执行账户管理操作**：任意已登录 student/counselor 可**创建/删除其他学生/老师账户**；**当前未形成管理员权限提升证据**（非匿名提权、非直接获得管理员角色），但具有**跨用户账户创建/删除能力**。六端点：`student/{save,add,delete}` · `counselor/{save,add,delete}`。⚠️ 默认弱口令 `123456` 属**独立密码策略债务**，本批不处理 |
| **`DEF-USER-CREATE-ID-BINDABLE`** | 🟠 **P2（实测确认，本批不修）** | `StudentEntity`/`CounselorEntity` 的 `id` 无 `@JsonIgnore`，admin `save` 探针提交 `id=999999`/`999998` → **DB 实际落库为客户端指定 id**（`student max_id=999999` / `counselor max_id=999998`）。⇒ 客户端可指定主键创建账号（绕过 AUTO_INCREMENT，存在 id 冲突/跳号风险）。**非提权**（Entity 无 `role` 字段）；按裁定**只登记，不本批修** |

> 既有、不重复登记：`SEC-ADMIN-DEFAULT-CREDENTIAL` P1（默认 admin/admin）· `resetPass` 匿名（D3）· `DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE`（S2-D1 发现）· 两个 404。

---

## 5. 待裁定项

| # | 问题 | 建议 |
| :-: | --- | --- |
| 1 | 6 个端点是否统一加 `assertAdminOnly` | ✅ 是（新增/删除用户 = admin-only）|
| 2 | `/register` 是否保留匿名 | ✅ 保留（自助注册业务允许，与 S2-D1 的 admin register 不同）|
| 3 | `/update` 是否改动 | ❌ 不动（已有 `assertSelfOrAdmin`）|
| 4 | 学生/老师是否有「删除自己账号」需求 | ❌ 当前无业务入口；如未来需要，单独设计（涉及数据级联清理）|
| 5 | 是否同一批处理 `resetPass` 匿名 | ❌ 不动（D3 已登记，属密码重置语义，需独立设计）|
| 6 | 新建账户默认弱口令 123456 | ❌ 本批不处理（授权 vs 密码策略分离，独立技术/安全债务）|
| 7 | 创建接口客户端身份/权限字段绑定 | ⚠️ **实现前须实测**（`id` 等）；若可绑定则**登记新 finding，不直接修**（参照 S2-D1 的 `role` 发现流程）|

---

## 6. 拟定实现范围（待批准，本批不实施 ✗）

```text
A. 6 个端点加 assertAdminOnly(request)：
   student/save · student/add · student/delete
   counselor/save · counselor/add · counselor/delete
   （每处补 HttpServletRequest request 参数）

B. 明确不动：
   ❌ /update（已有 assertSelfOrAdmin）
   ❌ /register（匿名自助注册，业务允许）
   ❌ /resetPassword（已有 assertAdminOnly）
   ❌ /resetPass（D3）
   ❌ 前端 · DB · 两个 404 · 默认凭据

C. 测试要求（4 角色 × 6 端点）：
   anonymous → 401
   student / counselor → 403
   admin → 200（探针，原业务行为）
```

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2 | 2026-10-22 | **S2-D2 实现后实测补充**：admin `save` 探针确认 **`id` 可绑定**（DB 落库客户端指定 id）⇒ 新登记 `DEF-USER-CREATE-ID-BINDABLE` P2（只登记不修）；`password` 仍强制 123456（不可绑定）· `role` 被忽略（Entity 无此字段）；P2 定义按审阅收紧 |
| v1 | 2026-10-22 | S2-D2 只读审计：6 端点（student/counselor × save/add/delete）无守卫；**`/register`（自助注册，匿名）与 `/add`（管理端新增）严格区分**；`/update` 已收口不动；新 frontend 无调用；5 项待裁定；**零改动** ✓ |
