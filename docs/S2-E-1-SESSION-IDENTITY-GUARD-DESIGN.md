# S2-E-1 · Session 身份一致性守卫实现设计

> **性质**：实现设计（📐 待批准 · **不写代码**）
> **基线**：`HEAD = origin = 39f336d` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-E-0-SESSION-TABLE-CONFUSION-AUDIT.md`](./S2-E-0-SESSION-TABLE-CONFUSION-AUDIT.md)（P1 · 方案 B 已批准）

---

## 0. 设计目标（单一）

3 个 `/session` 端点入口处，校验**当前已认证身份的 `role` 与 `tableName` 同时等于该端点要求的身份**；
不匹配 → `403`。**仅此一件事。**

不做：查库、判归属、改 Session、自动推断角色、处理业务权限、改 Interceptor。

---

## 1. 新增原语（`OwnershipGuard`）

### 1.1 签名

```java
/**
 * /session 专用：当前认证身份必须同时匹配 role + tableName。
 * 双字段均来自同一条 auth_token（一致性检查，非两层独立认证）。
 */
public static void assertCurrentIdentity(HttpServletRequest request,
                                         String expectedRole,
                                         String expectedTableName)
```

### 1.2 实现形态（草案）

```java
public static void assertCurrentIdentity(HttpServletRequest request,
                                         String expectedRole,
                                         String expectedTableName) {
    // ① 角色一致性（null / 不匹配 → deny，fail-closed）
    if (!expectedRole.equals(currentRole(request))) {
        deny();
    }
    // ② 表类型一致性（null / 不匹配 → deny，fail-closed）
    String tableName = currentTableName(request);
    if (!expectedTableName.equals(tableName)) {
        deny();
    }
}
```

- `deny()` 复用既有 `ForbiddenException` → `HTTP 403 + code=403`（**不新增契约**）
- 匿名请求 **到不了这里**（拦截器先返回 `code=401`），此处仅覆盖「已认证但身份不符」

### 1.3 新增常量（与既有 `ROLE_*` 并列）

```java
public static final String TABLE_STUDENT    = "student";
public static final String TABLE_COUNSELOR  = "counselor";
public static final String TABLE_ADMIN_USER = "admin_user";
```

> 取值来源：`auth_token.user_type` 实测值 `student` / `counselor` / `admin_user`（S2-E-0 §4）。

### 1.4 ⚠️ 待裁定 #1：`tableName` 的读取点放在哪

`CurrentUserProvider` 目前**没有** `tableName` 读取方法（只有 `currentRole` / `currentUserIdOrNull` / `requireCurrentUserId`）。要读 `tableName`，**必须新增一个读取点**：

| 选项 | 形态 | 影响 | 评价 |
| :-: | --- | --- | --- |
| **2a（推荐）** | `CurrentUserProvider` 新增 `currentTableName(request)`（3 行，纯读取）| 改动 **1 个额外文件**；保持「身份读取唯一实现」原则 | 与既有架构一致；**但字面上违反「不改 CurrentUserProvider」** |
| **2b** | `OwnershipGuard` 内联 `request.getSession().getAttribute("tableName")` | 不动 `CurrentUserProvider` | Session 读取点从 **1 处变 2 处**，破坏 `CurrentUserProvider` 类注释声明的「身份读取只有一份实现」 |

**本设计默认选 2a**，理由：新增的是**纯读取方法**，不改变任何既有语义 / 写入源 / 拦截器行为，与「依赖反转」设计一致。
**若你坚持 2b**，改动文件数从 4 → 3，但需接受读取点分裂（建议同时在类注释登记）。

> 说明：`tableName` 与 `role` 同源于 `auth_token`，**不是独立安全因子**；双字段校验的价值是「防止 Session 内角色与表类型不一致」，而非提供两层认证。

---

## 2. 三个调用点（逐处落点）

| Controller | 方法 | 插入位置 | 调用 |
| --- | --- | --- | --- |
| `StudentController` | `getCurrStudent`（L399-413） | 方法体**第 1 行** | `assertCurrentIdentity(request, ROLE_STUDENT, TABLE_STUDENT)` |
| `CounselorController` | `getCurrCounselor`（L373-384） | 方法体**第 1 行** | `assertCurrentIdentity(request, ROLE_COUNSELOR, TABLE_COUNSELOR)` |
| `AdminUserController` | `getCurrUser`（L123-128） | 方法体**第 1 行** | `assertCurrentIdentity(request, ROLE_ADMIN, TABLE_ADMIN_USER)` |

### 2.1 diff 预览（示意）

```java
// StudentController.java  L400+
public R getCurrStudent(HttpServletRequest request){
+   OwnershipGuard.assertCurrentIdentity(request, OwnershipGuard.ROLE_STUDENT, OwnershipGuard.TABLE_STUDENT);
    Integer id = CurrentUserProvider.currentUserIdOrNull(request);
    StudentEntity yonghu = yonghuService.getById(id);
```

```java
// CounselorController.java  L374+
public R getCurrCounselor(HttpServletRequest request){
+   OwnershipGuard.assertCurrentIdentity(request, OwnershipGuard.ROLE_COUNSELOR, OwnershipGuard.TABLE_COUNSELOR);
    Integer id = CurrentUserProvider.currentUserIdOrNull(request);
```

```java
// AdminUserController.java  L124+
public R getCurrUser(HttpServletRequest request){
+   OwnershipGuard.assertCurrentIdentity(request, OwnershipGuard.ROLE_ADMIN, OwnershipGuard.TABLE_ADMIN_USER);
    Integer id = CurrentUserProvider.currentUserIdOrNull(request);
```

**顺序要求**：守卫**必须在 `getById` 之前**（先授权后查询，与 S2-C1 / S2-D3-1 一致）。

**import 状态**：3 个 Controller 均已 `import com.security.OwnershipGuard;` ✓（无需新增 import）

---

## 3. 文件清单

| # | 文件 | 改动 |
| :-: | --- | --- |
| 1 | `src/main/java/com/security/CurrentUserProvider.java` | **+1 方法**（`currentTableName`，仅当选 2a）|
| 2 | `src/main/java/com/security/OwnershipGuard.java` | **+3 常量 +1 方法 +1 私有辅助** |
| 3 | `src/main/java/com/controller/StudentController.java` | **+1 行** |
| 4 | `src/main/java/com/controller/CounselorController.java` | **+1 行** |
| 5 | `src/main/java/com/controller/AdminUserController.java` | **+1 行** |

选 2b 则为 **4 个文件**（无 #1）。

**预计净增**：约 **+25 / −0**（含注释与 Javadoc）。

---

## 4. Acceptance Matrix（固定）

| 身份 | `/student/session` | `/counselor/session` | `/adminUser/session` |
| --- | :-: | :-: | :-: |
| **anonymous** | `code=401` | `code=401` | `code=401` |
| **student** | **200** ✅ | **403** | **403** |
| **counselor** | **403** | **200** ✅ | **403** |
| **admin** | **403** | **403** | **200** ✅ |

### 4.1 碰撞验证（本批核心证据）

当前三表 id 状态：`student{1,2,3}` · `counselor{1,2,3}` · `admin_user{6}`

```text
student(id=1)  token + GET /counselor/session  → 403   ← 修复前 200 + 老师 PII
counselor(id=1) token + GET /student/session   → 403   ← 修复前 200 + 学生 PII
```

**ID 完全相同仍拒绝** ⇒ 证明修复的是**身份类型**，而非「碰巧 ID 不同」。

### 4.2 正向业务保持（不得只测 403）

| 用例 | 断言 |
| --- | --- |
| student → `/student/session` | 200 + **原字段集不变**（含 `phone`/`email`，`password` 仍不出现）|
| counselor → `/counselor/session` | 200 + **原字段集不变** |
| **admin → `/adminUser/session`** | **200 + 原字段集不变**（防止把管理员自己的 session 一并锁死）|

### 4.3 匿名契约

3 个端点匿名 → `HTTP 200 + body.code=401`（拦截器形态，**保持不变**）

---

## 5. 回归与部署

| 步骤 | 内容 |
| --- | --- |
| 编译 | `mvn -q -DskipTests compile` |
| 回归 | `mvn test` → **47/47**（身份 30 + 基线 17）|
| 部署 | 停旧 jar → `mvn -DskipTests package` → 启新 jar → 确认 **PID 变化 + jar 时间戳更新** |
| HTTP | 4×3 = **12 格矩阵** + 2 碰撞用例 + 3 正向字段集 |

**本批无数据写入**（全部 GET）→ **无探针、无恢复、无 AUTO_INCREMENT 漂移**。

---

## 6. 风险与影响评估

| 风险 | 评估 | 缓解 |
| --- | --- | --- |
| 老前端 `` `${userTable}/session` ``（12+ 处）| 正常情况 `userTable` = 本次登录表名 → **200** ✓ | 实测 3 条正向 |
| 老前端硬编码 `student/session`（2 处）· `counselor/session`（1 处）| 分别在学生中心页 / 预约确认页 / 老师中心页，**角色天然匹配** | 实测正向 |
| `login.html:262` `data.role + '/session'` | `data.role` = 登录选择的表名 → 匹配 | admin 登录 → `/adminUser/session` 200 ✓ |
| Vue3（2 处，仅 `/student/session`）| 学生专用 → **零影响** | — |
| Admin 管理端 | **0 调用** `/session` | — |
| `DataScope` / `OwnershipGuard` 其他方法 | 只读 `role`，**不受影响** | 47/47 |
| 管理员误入学生页面 | 由 511 → **403**（语义正确）| 属异常路径，无业务依赖 |
| 回滚 | 单提交，`git revert` 即可 | 无 DB / 无 schema |

---

## 7. 明确不做（边界锁死）

```text
❌ 不修改 AuthorizationInterceptor（已正确写入 role + tableName）
❌ 不修改 auth_token 表 / Session 结构
❌ 不修改其他业务接口（/list · /detail · /info · /page …）
❌ 不修改前端（Vue3 / 老前端 / Admin）
❌ 不修改 DB schema · 不写数据
❌ 不重构 Controller · 不合并 /session 端点
❌ 不改 401/511 之外的既有契约（511 分支保留，仅在其前增加授权）
```

---

## 8. 待你批准的设计项

| # | 项 | 本设计取值 |
| :-: | --- | --- |
| 1 | **`tableName` 读取点**：2a（`CurrentUserProvider.currentTableName`，+1 文件）/ 2b（`OwnershipGuard` 内联）| **默认 2a** |
| 2 | 原语命名 | `assertCurrentIdentity(request, expectedRole, expectedTableName)` |
| 3 | 是否拆成两个 public 原语（`assertCurrentRole` + `assertCurrentIdentity`）| **不拆** —— 只保留 `assertCurrentIdentity`，避免「只用 role 就够」的弱化用法扩散 |
| 4 | 常量 | `TABLE_STUDENT` / `TABLE_COUNSELOR` / `TABLE_ADMIN_USER` |
| 5 | 拒绝形态 | `403`（复用 `deny()` / `ForbiddenException`），**不新增契约** |
| 6 | 文件数 | **5 个**（选 2a）/ 4 个（选 2b）|
| 7 | admin 跨角色 | **403**（不是 511）|
| 8 | 是否允许真实写测试 | **不需要**（本批全只读）|

---

## 9. 状态

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = 39f336d` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓（设计阶段）|
| 文档 | 新增本文件 |

---

## 10. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-E-1 设计：新增 `assertCurrentIdentity(role, tableName)` 原语 + 3 常量；3 处 `/session` 首行调用；4×3 acceptance matrix + 碰撞验证 + 3 正向；5 文件（2a）/ 4 文件（2b）；**提出待裁定 #1（`tableName` 读取点）**；零改动 |
