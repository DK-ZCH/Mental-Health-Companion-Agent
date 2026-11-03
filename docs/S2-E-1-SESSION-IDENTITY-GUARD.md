# S2-E-1 · Session 身份一致性守卫实现报告

> **性质**：实现 · **5 文件 +64 / −0** · **已提交 `6da1c0b` · 已推送 ✓**
> **基线**：`39f336d` → `6da1c0b` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-E-1-SESSION-IDENTITY-GUARD-DESIGN.md`](./S2-E-1-SESSION-IDENTITY-GUARD-DESIGN.md)（2a 已批准）

---

## 0. 一句话结论

3 个 `/session` 端点首行加 `assertCurrentIdentity(role, tableName)`：
**跨角色 403、本角色 200、匿名 401**，24 格/12 格矩阵全绿；
SQL 日志证明守卫**在 `getById` 之前短路**（跨身份请求 `FROM counselor` = **0 次**）；
47/47 回归通过；`/session` 之外零改动。

---

## 1. 改动清单（5 文件 · +64 / −0）

| # | 文件 | 改动 | 行数 |
| :-: | --- | --- | :-: |
| 1 | `security/CurrentUserProvider.java` | 新增 `currentTableName(request)`（纯读取）| **+17** |
| 2 | `security/OwnershipGuard.java` | 新增 `TABLE_STUDENT` / `TABLE_COUNSELOR` / `TABLE_ADMIN_USER` + `assertCurrentIdentity(...)` | **+41** |
| 3 | `controller/StudentController.java` | `getCurrStudent` 首行守卫 | **+2** |
| 4 | `controller/CounselorController.java` | `getCurrCounselor` 首行守卫 | **+2** |
| 5 | `controller/AdminUserController.java` | `getCurrUser` 首行守卫 | **+2** |

`git diff --check`：**无空白错误** ✓

---

## 2. 关键实现点

### 2.1 原语（职责单一）

```java
public static void assertCurrentIdentity(HttpServletRequest request,
                                         String expectedRole,
                                         String expectedTableName) {
    // ① 角色一致性（常量在前，避免 currentRole() 为 null 时 NPE）
    if (!expectedRole.equals(currentRole(request))) {
        deny();
    }
    // ② 表类型一致性（同上：常量在前）
    if (!expectedTableName.equals(CurrentUserProvider.currentTableName(request))) {
        deny();
    }
}
```

- **短路顺序**：role 不匹配 → 直接拒绝，不读 `tableName` ✓
- **fail-closed**：任一字段为 `null` → `expectedXxx.equals(null)` = false → 403 ✓
- **拒绝形态**：复用 `deny()` → `ForbiddenException` → `HTTP 403 + code=403`（不新增契约）✓

### 2.2 读取点集中（2a 已批准）

```java
// CurrentUserProvider —— 身份读取的唯一实现
public static String currentTableName(HttpServletRequest request) {
    Object v = request.getSession().getAttribute("tableName");
    return v == null ? null : String.valueOf(v);
}
```

### 2.3 调用顺序（守卫必须先于查询）

```java
public R getCurrStudent(HttpServletRequest request){
    OwnershipGuard.assertCurrentIdentity(request, ROLE_STUDENT, TABLE_STUDENT);  // ← 先授权
    Integer id = CurrentUserProvider.currentUserIdOrNull(request);
    StudentEntity yonghu = yonghuService.getById(id);                            // ← 后查询
```

三处同构，落点均为方法体**第 1 行**。

---

## 3. 回归（47/47）

```text
Tests run: 30, Failures: 0, Errors: 0 -- IdentityAuthorizationRegressionTest
Tests run: 17, Failures: 0, Errors: 0 -- Phase2CoreRegressionTest
Tests run: 47, Failures: 0, Errors: 0
BUILD SUCCESS
```

---

## 4. 部署证据

| 项 | 旧 | 新 |
| --- | --- | --- |
| PID | `38436` | **`42864`** ✓ |
| jar 时间戳 | 11:39:25 | **17:27:03** ✓ |
| jar 大小 | 143,199,485 | 143,199,816 |
| 8080 | — | 就绪（5s）✓ |

---

## 5. Acceptance Matrix（12 格实测）

| 身份 | `/student/session` | `/counselor/session` | `/adminUser/session` |
| --- | :-: | :-: | :-: |
| **anonymous** | `code=401` ✓ | `code=401` ✓ | `code=401` ✓ |
| **student(id=1)** | **200** ✓ | **403** ✓ | **403** ✓ |
| **counselor(id=1)** | **403** ✓ | **200** ✓ | **403** ✓ |
| **admin(id=6)** | **403** ✓ | **403** ✓ | **200** ✓ |

**12/12 全绿**，与设计矩阵**逐格一致**。

### 5.1 正向字段集（未破坏业务）

| 用例 | 结果 |
| --- | --- |
| student → `/student/session` | 200 · `id=1` · `n=9` · `phone=True` · `email=True` · `password=False` |
| counselor → `/counselor/session` | 200 · `id=1` · `n=12` · `phone=True` · `email=True` · `password=False` |
| **admin → `/adminUser/session`** | **200** · `id=6` · `n=4` · `password=False` ← **未把自己锁死** ✓ |

---

## 6. 🔑 碰撞验证（核心证据）

```text
student(id=1)   token + GET /counselor/session → 403   （修复前：200 + 老师 PII）
counselor(id=1) token + GET /student/session   → 403   （修复前：200 + 学生 PII）
```

**ID 完全相同仍被拒绝** ⇒ 证明修复的是**身份类型**，而非「碰巧 ID 不同」。

---

## 7. 🔑 守卫先于查询的证据（SQL 日志，非仅 HTTP）

> 用户要求：必须证明是「守卫 → 403」，而不是「`getById` 查到 → 再 403」。

### 7.1 跨身份请求（student → `/counselor/session`，403）

```text
新增 SQL：
  SELECT ... FROM auth_token WHERE (token = ?)     ← 拦截器校验 token（正常）

FROM counselor 次数 = 0     ← 关键：未发生跨表读取
FROM student   次数 = 0
FROM auth_token 次数 = 1
```

### 7.2 对照组：正向请求（student → `/student/session`，200）

```text
新增 SQL：
  SELECT ... FROM auth_token WHERE (token = ?)
  SELECT ... FROM student  WHERE id=?              ← 命中目标表

FROM student 次数 = 1
FROM counselor 次数 = 0
```

**⇒ 日志取证方法有效；跨身份请求在 DAO 之前即被短路，零跨表读取。**

---

## 8. 未改动（边界核对）

| 项 | 状态 |
| --- | :-: |
| `AuthorizationInterceptor`（已正确写入 role + tableName）| ✗ 未改 |
| `auth_token` 表 / Session 结构 | ✗ 未改 |
| `/student/list` `/counselor/list` `/counselor/detail` `/counselor/info` 等业务接口 | ✗ 未改 |
| Vue3 / 老前端 / Admin 管理端 | ✗ 0 改动 |
| DB schema / 数据 | ✗ 零写入（全 GET）|
| 其他 Controller / 端点合并 | ✗ 未改 |

**本批无需探针、无需数据恢复**（全部 GET，SQL 日志 `FROM ...` 均为 SELECT）。

---

## 9. 已闭环 / 未闭环

| 项 | 状态 |
| --- | :-: |
| **`SEC-SESSION-TABLE-ID-CONFUSION`（P1）** | ✅ **本批收口**（3 端点，双向越权已堵）|
| `DEF-COUNSELOR-LIST-PUBLIC-PII`（⚠️ 高优先级待裁定）| ❌ 未处理 |
| `SEC-COUNSELOR-DETAIL-CROSS-USER-PII`（P2）| ❌ 未处理 |
| `DEF-COUNSELOR-CASCADE-FIELD-MISMATCH`（🟡）| ❌ 未处理 |
| `DEF-USER-CREATE-ID-BINDABLE` / `DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE` / 两个 404 | ❌ 未处理 |

---

## 10. 状态

| 项 | 值 |
|---|---|
| `git status --short` | **(空)** ✓ |
| `git diff --cached --check` | 无空白错误 ✓ |
| `git diff --stat` | **5 files changed, 64 insertions(+)** |
| HEAD / origin | **`6da1c0b` / `6da1c0b`** · LEAD **0** ✓ |
| 回归 | **47/47** ✓ |
| 服务 | PID `42864` · 8080 就绪 ✓ |
| 提交 | **✅ `6da1c0b`**（`fix(security/s2-e-1)`）· **已推送 ✓** |

---

## 11. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-E-1 实现：5 文件 +64/−0；`assertCurrentIdentity` + `currentTableName` + 3 常量；3 处首行守卫；47/47；12 格矩阵全绿；ID=1 碰撞双向 403；SQL 日志证明跨身份请求 `FROM counselor=0`（对照正向 `FROM student=1`）；部署 PID 38436→42864 · jar 17:27:03；**未 commit** |
