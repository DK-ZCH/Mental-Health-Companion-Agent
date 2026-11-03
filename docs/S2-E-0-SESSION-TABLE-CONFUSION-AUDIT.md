# S2-E-0 · Session 身份表混淆只读取证

> **性质**：只读取证 · **零代码 · 零 DB · 零 commit**
> **基线**：`HEAD = origin = 39f336d` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：`SEC-COUNSELOR-SESSION-TABLE-ID-CONFUSION`（S2-D3-0 发现，用户提为最高优先级 P1）

---

## 0. 一句话结论

**3 个** `/session` 端点（`student` / `counselor` / `adminUser`）**全部只按 `userId` 查各自的表**，不校验身份类型；
而拦截器写入 Session 的 `tableName` 在**全仓库从未被读取**（0 处）。
实测：`student(id=1) ↔ counselor(id=1)` **双向 PII 泄露**（3/3 id 全碰撞）；`admin_user(id=6)` 当前无碰撞，但机制同样脆弱。

> 这**不是** `counselor/session` 单点问题，而是**3 个端点共享同一根因的系统性身份表混淆**。

---

## 1. 根因链路（静态证据）

```java
// AuthorizationInterceptor.java L72-77 —— 身份写入 Session 的唯一来源
request.getSession().setAttribute("userId",    tokenEntity.getUserId());
request.getSession().setAttribute("role",      tokenEntity.getRole());      // "学生"/"心理老师"/"管理员"
request.getSession().setAttribute("tableName", tokenEntity.getUserType());  // "student"/"counselor"/"admin_user"
request.getSession().setAttribute("username",  tokenEntity.getUsername());
```

```text
Session 有 tableName ✓（写入正确）
        ↓
全仓库 getAttribute("tableName") = 0 处   ← 关键：写而不用
        ↓
3 个 /session 都只取 userId
        ↓
userId 是「每表独立 id 空间」，三表之间无全局唯一性
        ↓
同一 userId 在错误表中命中 ⇒ 跨表身份混淆
```

**根因定论**（回答取证问题 ①）：

| 可能原因 | 是否成立 |
| --- | :-: |
| Controller 忽略 `tableName` | ✅ **成立**（3 处）|
| Service 忽略身份类型 | ✅ 成立（直接 `getById`，无类型参数）|
| Session 本身字段缺失 | ❌ **不成立**（`tableName` 已正确写入）|
| 多因素叠加 | ✅ 即「写入了但无人读」|

---

## 2. 三个端点静态对照

| 端点 | Controller: 行 | 查询表 | 读取 `tableName` | 归属/角色守卫 |
| --- | --- | --- | :-: | :-: |
| `GET /student/session` | `StudentController:399-402` | `student` | ❌ | ❌ 无 |
| `GET /counselor/session` | `CounselorController:373-376` | `counselor` | ❌ | ❌ 无 |
| `GET /adminUser/session` | `AdminUserController:123-126` | `admin_user` | ❌ | ❌ 无 |

代码形态完全同构：

```java
Integer id = CurrentUserProvider.currentUserIdOrNull(request);
XxxEntity e = xxxService.getById(id);   // ← 无角色/表名校验
return R.ok().put("data", e);
```

**认证层**：三者均**无 `@IgnoreAuth`** → 匿名被拦截器拒绝（实测 `code=401`）✓

---

## 3. 实测 3×3 矩阵（真实 HTTP，8080 / context-path `/xinlijiankangxitong`）

| token \ 端点 | `/student/session` | `/counselor/session` | `/adminUser/session` |
| --- | --- | --- | --- |
| **student（id=1）** | ✅ student(1) 本人<br>`username=a1` / `phone=17703786901` / `email=1@qq.com` | 🔴 **counselor(1) 全字段**<br>`name=心理老师妹妹1` / `phone=17703786901` / `email=1@qq.com` | ⚪ `code=0` · `data=null` |
| **counselor（id=1）** | 🔴 **student(1) 全字段**<br>`name=学生妹妹1` / `phone=17703786901` / `email=1@qq.com` | ✅ counselor(1) 本人 | ⚪ `code=0` · `data=null` |
| **admin（id=6）** | ⚪ `code=511`「查不到数据」 | ⚪ `code=511` | ✅ admin(6)<br>`username=admin` |
| **anonymous** | `code=401` ✓ | `code=401` ✓ | `code=401` ✓ |

图例：✅ 正常（本人）· 🔴 **越权实锤** · ⚪ 未命中（id 不存在）

**⇒ 双向越权均已实测成立**（不只是 `counselor/session` 单点）。

**`password` 屏蔽**：三表实体 `getPassword()` 均标 `@JsonIgnore`（`StudentEntity` / `CounselorEntity` / `AdminUserEntity`）→ 实测 `pwd=False` ✓

---

## 4. id 碰撞分析（只读 SQL，零写入）

```sql
SELECT 'student x counselor', COUNT(*) FROM student s JOIN counselor c ON s.id=c.id
UNION ALL 'student x admin_user'   ...
UNION ALL 'counselor x admin_user' ...
```

| 身份表对 | 碰撞数 | 当前可达性 |
| --- | :-: | --- |
| **student × counselor** | **3**（id 1/2/3 **全部重合**）| 🔴 **双向越权 100% 可达** |
| student × admin_user | 0 | ⚪ 当前不可达 |
| counselor × admin_user | 0 | ⚪ 当前不可达 |

**表规模基线**：

```text
student    : id = 1,2,3        (3 条)
counselor  : id = 1,2,3        (3 条)
admin_user : id = 6            (1 条)
auth_token : 3 条 (6-admin_user / 1-student / 1-counselor)
```

### admin 侧为何当前不泄露，但仍须修

- `admin_user` 只有 id=6，student/counselor 只有 1..3 → 数值不交集。
- **机制完全相同**：若 ①`admin_user` 新增 id ≤ 3 的记录，或 ②student/counselor 增长到 id=6，
  则**学生 token 可直接读到管理员账号行**（`username` 等）。
- 因此不能因"当前没碰撞"判为低危 —— 这是**时序偶然，不是设计安全**。

---

## 5. 影响面

| 越权方向 | 可读字段 | 当前规模 |
| --- | --- | --- |
| student → counselor/session | `id` `username` `name` `phone` `email` `avatarUrl` `gender` `expertise` `resume` `introduction` `createdAt` | **3/3 老师全部** |
| counselor → student/session | `id` `username` `name` `phone` `email` `avatarUrl` `gender` + 学生业务字段 | **3/3 学生全部** |
| student/counselor → adminUser/session | `id` `username` 等 | 当前 0（id 不碰撞）· **机制存在** |

**共性**：`username` + `phone` + `email` 三件 PII，与 S2-D3 的 `info` 问题同族，但**这里是双向的，且任何登录账号都能打任意 session 端点**。

---

## 6. 真实调用方（确认修复不会破坏业务）

### 6.1 老前端（`src/main/resources/front/front/pages/`）—— 19 处

| 形态 | 示例 | 说明 |
| --- | --- | --- |
| `${table}/session`（`table = localStorage.userTable`）| `yonghu/add.html:373` · `xinlilaoshiOrder/add.html:347` · `recharge/recharge.html:123` · `exampaper/exam.html:226` 等 **12+ 处** | `userTable` 由登录时写入（`login.html:258`）|
| 硬编码 `student/session` | `yonghu/center.html:489` · `xinlilaoshiOrder/confirm.html:123` | 学生中心页 / 预约确认页 |
| 硬编码 `counselor/session` | `xinlilaoshi/center.html:499` | 老师中心页 |
| `data.role + '/session'` | `login.html:262` | 登录后立即取 `userId` |

**关键**：老前端始终用"本次登录的表名"拼接，**正常路径不会跨表**；越权只能由**直接构造 HTTP 请求**触发（后端不校验）。

### 6.2 Vue3（`frontend/src`）—— 2 处，仅 `/student/session`

| 文件 | 调用 |
| --- | --- |
| `api/student.ts:21` | `http.get('/student/session')` |
| `api/auth.ts:31` | `http.get('/student/session')` |

**⇒ 只要"角色与端点匹配"的请求继续放行，Vue3 与老前端零影响。**

### 6.3 Admin 管理端

**0 处** `/session` 调用（管理端用 `adminUser/login` + localStorage）。

### 6.4 旧路径

`/yonghu/session` · `/xinlilaoshi/session` · `/users/session` → **全部 404** ✓（Phase 3 rename 已生效，无旧路径绕过）

---

## 7. 同类模式排查（回答取证问题 ②：是否还有其他地方）

| 检索项 | 结果 | 判定 |
| --- | --- | --- |
| `getAttribute("tableName")` | **0 处** | 写而不用的铁证 |
| `currentUserIdOrNull(...)` | **3 处** = **仅 3 个 `/session`** | 越权面就是这 3 个端点 |
| `session.getAttribute("userId")`（旧式直读）| 13 处，**全部为 `/batchInsert` 的 Excel 导入**（`studentId` 变量实际用于日志/赋值，不查询其他身份表）| 🟡 属另外的历史遗留（无跨表读）|
| `requireCurrentUserId(...)` | 12 处，**全部为写路径归属赋值**（`setStudentId`/`setCounselorId`）| ✅ 不构成跨表读 |
| `getById(<sessionUserId>)`（除 3 个 session 外）| **0 处** | ✅ 无第四处同类读取 |

**⇒ 跨表身份混淆的读取面 = 恰好 3 个 `/session` 端点，无扩散。**

### 7.1 但共享根因不止于读

`DataScope` / `OwnershipGuard` / 写路径 `resolveWriteOwner` 都只读 **`role`**（不读 `tableName`）。
当前 `role` 与 `tableName` 由同一条 `auth_token` 记录同时写入，**二者天然一致**，故这些路径不存在同类问题。
→ 结论：**唯一的缺口是 `tableName` 完全未被使用**，而不是"role 也不可信"。

---

## 8. 定级建议

| ID | 级别 | 范围 | 依据 |
| --- | :-: | --- | --- |
| ~~`SEC-COUNSELOR-SESSION-TABLE-ID-CONFUSION`~~ → **`SEC-SESSION-TABLE-ID-CONFUSION`** | 🔴 **P1** | **3 个端点**（scope 扩大）| 双向越权**已实测**；3/3 id 全碰撞；`tableName` 写而不用；影响 PII（`username`/`phone`/`email`）|

**建议保留 P1，并将 ID 从单点 `COUNSELOR-` 改为 `SESSION-` 以覆盖 3 个端点。**

---

## 9. 已裁定修复方案（方案 B · 设计已产出）

| 方案 | 形态 | 影响面 | 裁定 |
| --- | --- | --- | :-: |
| A | 3 处各加一行「角色匹配」 | 3 文件 3 行 | ❌ 不选（规则复制 3 份，新增端点会漏）|
| **B** | 新增 `OwnershipGuard.assertCurrentIdentity(request, expectedRole, expectedTableName)`，3 处复用 | 1 原语 + 3 调用 | ✅ **已批准**（见 [`S2-E-1-SESSION-IDENTITY-GUARD-DESIGN.md`](./S2-E-1-SESSION-IDENTITY-GUARD-DESIGN.md)）|
| C | 合并为单一 `/session` 按 `tableName` 分派 | 大 | ❌ 不选（破坏 3 条前端 URL）|

**设计要点（已按用户裁定固化）**：
1. 原语**职责单一** —— 只校验 `role` + `tableName` 一致性；不查库、不判归属、不改 Session、不自动推断。
2. **role + tableName 双字段校验**，但**不塞进 `assertCurrentRole`**（避免方法名骗人）；不拆两个 public 原语。
3. 二者同源于 `auth_token` → 属**一致性检查**，不是两层独立认证。
4. 拒绝形态复用 `deny()` → **403**，不新增契约。

### 9.1 若批准，预计测试矩阵

| token \ 端点 | `/student/session` | `/counselor/session` | `/adminUser/session` |
| --- | --- | --- | --- |
| student | 200 ✓ | **403** | **403** |
| counselor | **403** | 200 ✓ | **403** |
| admin | **403**（或按裁定保留 511）| **403** | 200 ✓ |
| anonymous | 401 | 401 | 401 |

> ✅ **已裁定（2026-10-22）**：admin 对 `/student/session`、`/counselor/session` → **403**（**不再保留 511**）。
> 理由：511 语义是「目标数据不存在」，而此处是「身份合法但无权限」→ 403 才是正确语义；Admin 管理端 **0 调用** `/session`，无兼容性理由。
> **强制测试要求**：必须同时验证 `admin → /adminUser/session = 200`，防止把管理员自己的 session 一并锁死。

---

## 10. 已明确不做（本批）

```text
❌ 不修 /counselor/session
❌ 不修 /student/session · /adminUser/session
❌ 不修改 CurrentUserProvider
❌ 不修改 Session 结构 / AuthorizationInterceptor
❌ 不改 /counselor/list · /counselor/detail
❌ 不改 DB · 不改前端
```

---

## 11. 状态声明

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = 39f336d` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓ |
| 数据影响 | **无写入**（全部 GET + 只读 SQL `SELECT`）|
| 实测方式 | 真实 HTTP（PID 38436）· 3 token · 12 格矩阵 · 3 条旧路径 404 |
| 文档 | 新增本文件 |

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-E-0 取证：根因（`tableName` 写而不用，0 处读取）+ **3 个 session 端点全同构** + 3×3 实测（**student↔counselor 双向 PII 越权实锤**，admin 当前不碰撞）+ id 碰撞 SQL（student×counselor = 3）+ 调用方（老前端 19 处 / Vue3 2 处仅 student / Admin 0）+ 同类模式排查（无第四处）+ 定级扩为 `SEC-SESSION-TABLE-ID-CONFUSION` P1 + 3 方案（推荐 B）+ 1 项待裁定；**零改动** ✓ |
