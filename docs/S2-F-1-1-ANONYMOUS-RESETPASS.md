# S2-F-1-1 · 匿名 `resetPass` 安全下线实现报告

> **性质**：实现 · **4 文件 · +45 / −50** · **已提交 `aaf9bc0` · 已推送 ✓**
> **基线**：`d27c10a` → `aaf9bc0` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-F-1-1-ANONYMOUS-RESETPASS-DESIGN.md`](./S2-F-1-1-ANONYMOUS-RESETPASS-DESIGN.md)（已批准，4 文件 + `/counselor/list` 断言改 200）

---

## 0. 一句话结论

`student/resetPass` + `counselor/resetPass` **已整段删除（含 `@IgnoreAuth`）**，匿名请求均 **404**；
合法管理重置 `resetPassword` **admin-only 行为完全保持**；
47/47 回归通过；**数据零写入**；前端 / DB / 密码字段 / 哈希 **零改动**。

---

## 1. 改动清单（4 文件 · +45 / −50）

| # | 文件 | 动作 | 行数 |
| :-: | --- | --- | :-: |
| 1 | `controller/StudentController.java` | 删 `resetPass` 整段 + 保留下线注释 | **−18 / +9** |
| 2 | `controller/CounselorController.java` | 同上 | **−18 / +9** |
| 3 | `test/regression/IdentityAuthorizationRegressionTest.java` | 用例 20 扩展（三端点 404 + 锚点 200）| **−13 / +26** |
| 4 | `controller/AdminUserController.java` | L81 过时注释校准（**仅 1 行**）| **−1 / +1** |

`git diff --check`：**无空白错误** ✓

---

## 2. 关键改动

### 2.1 删除的两个端点（含 `@IgnoreAuth`，不可执行逻辑全部移除）

```diff
-    /**
-     * 忘记密码
-     */
-    @IgnoreAuth
-    @RequestMapping(value = "/resetPass")
-    public R resetPass(String username, HttpServletRequest request) {
-        ... getOne(eq("username", username))
-        ... setPassword("123456")
-        ... updateById(...)
-    }
+    // ── S2-F-1-1：已【下线】匿名密码重置端点 ──
+    // 下线依据（S2-F-1-0 调用面核查，全部为零）...
```

| 文件 | 原行范围 | 现状 |
| --- | --- | --- |
| `StudentController` | L376–393 | **0 处 `/resetPass` 映射、0 个 `resetPass(` 方法** |
| `CounselorController` | L353–370 | 同上 |

**下线注释统一文案**（按裁定，不硬编码各自 URL，避免维护偏差）。

### 2.2 测试用例 20 扩展（47 项不增不删）

| 断言 | 期望 |
| --- | --- |
| `/adminUser/resetPass?username=admin` | **404**（D7 既有）|
| `/student/resetPass?username=not-exist-user-xyz` | **404**（S2-F-1-1 新增）|
| `/counselor/resetPass?username=not-exist-user-xyz` | **404**（S2-F-1-1 新增）|
| **`/counselor/list`（对照锚点）** | **`isEqualTo(200)`** ← 按裁定强化（原 `isNotEqualTo(404)`）|
| `usersPassword("admin")` 前后 | **一致** |

用例名同步更新为 `anonymousResetEndpointsAreRemoved`。

### 2.3 `AdminUserController` L81 注释校准（仅 1 行）

```diff
-//  （批 2 已限管理员），与本端点无关；用户/老师端的「忘记密码」走各自的 /resetPass（按 D3 不动）。
+//  （批 2 已限管理员），与本端点无关；用户/老师端的 /resetPass 亦已于 S2-F-1-1 下线。
```

**未改动**：`adminUser/resetPass`（下线状态）· `adminUser/resetPassword` · Admin 登录 / 权限 / 其他注释。

---

## 3. 回归（47/47）

```text
Tests run: 30, Failures: 0, Errors: 0 -- IdentityAuthorizationRegressionTest
Tests run: 17, Failures: 0, Errors: 0 -- Phase2CoreRegressionTest
Tests run: 47, Failures: 0, Errors: 0
BUILD SUCCESS
```

> **用例 20 改后仍为 47 项**（未新增 `@Test`）✓

---

## 4. 部署证据

| 项 | 旧 | 新 |
| --- | --- | --- |
| PID | `46704` | **`17316`** ✓ |
| jar 时间戳 | 18:31:44 | **19:14:01** ✓ |
| jar 大小 | 143,201,238 | 143,200,959 |
| 8080 | — | 就绪（5s）✓ |

---

## 5. HTTP 9 项矩阵（实测）

### 5.1 已下线端点（应 404）

| # | 用例 | 实测 |
| :-: | --- | :-: |
| 1 | 匿名 `POST /student/resetPass?username=a1` | **HTTP 404** ✓ |
| 2 | 匿名 `POST /counselor/resetPass?username=a1` | **HTTP 404** ✓ |
| 3 | 匿名 `POST /adminUser/resetPass?username=admin` | **HTTP 404** ✓（D7 不回归）|

### 5.2 合法管理重置（admin-only 保持）

| # | 用例 | 实测 |
| :-: | --- | :-: |
| 4 | 匿名 `GET /student/resetPassword?id=3` | HTTP 200 · `code=401` ✓ |
| 5 | **student** `GET /student/resetPassword?id=3` | **HTTP 403** ✓ |
| 6 | **admin** `GET /student/resetPassword?id=3` | **HTTP 200 · code=0** ✓ |
| 7 | 匿名 `GET /counselor/resetPassword?id=3` | HTTP 200 · `code=401` ✓ |
| 8 | **counselor** `GET /counselor/resetPassword?id=3` | **HTTP 403** ✓ |
| 9 | **admin** `GET /counselor/resetPassword?id=3` | **HTTP 200 · code=0** ✓ |

**⇒ 验证了关键边界：删除的是危险匿名端点，合法管理员重置能力完整保留。**

---

## 6. 数据零写入核对

| 表 | 记录数 | 默认值计数 | 与审计前 |
| --- | :-: | :-: | :-: |
| `student` | 3 | 3 | **一致** ✓ |
| `counselor` | 3 | 3 | **一致** ✓ |
| `admin_user` | 1 | 1 | **一致** ✓ |

> **措辞边界（复核修正）**：上表证明的是「**最终数据库值未变化**」（`password='123456'` 计数前后一致）；
> **不能**据此推断底层 ORM 未执行过 `UPDATE`。用例 6/9 调用的 `admin/resetPassword` 本就是**被允许的管理操作**，
> 其是否触发一次 UPDATE 不影响 S2-F-1-1 结论 —— 本批核心是「删除 `resetPass` 的探测与回归未造成**意外**密码变更」。

---

## 7. 残留搜索结果

| 检索 | 期望 | 实测 |
| --- | --- | :-: |
| `src/main/java` 中 `value = "/resetPass"` | 0 | **0** ✓ |
| `src/main/java` 中 `public R resetPass(` | 0 | **0** ✓ |
| `src/main/java` 中 `resetPass` 字样 | 仅注释 / `resetPassword` 子串 | **9 处，全部符合** ✓ |
| `src/main/resources/front`（老前端）| 0 | **0** ✓ |
| `frontend/src`（Vue3）| 0 | **0** ✓ |
| `src/test` | 仅用例 20 断言路径 | 15 处（断言 + 注释）✓ |

**9 处明细**：
- `AdminUserController:74/80/81` —— 下线注释（D7 + S2-F-1-1）
- `StudentController:363/364` `CounselorController:341/342` —— `resetPassword`（**合法端点，非删除对象**）
- `StudentController:382` `CounselorController:359` —— 下线注释中的 `resetPassword` 字样

---

## 8. 未改动（边界核对）

| 项 | 状态 |
| --- | :-: |
| `resetPassword`（两个 admin-only 端点）| ✗ 未改 |
| `login`（3 处明文比较）| ✗ 未改 |
| `register`（2 处）| ✗ 未改 |
| `password` 字段 / `@JsonIgnore` / Entity / View | ✗ 未改 |
| 密码哈希（bcrypt/Argon2/PBKDF2）| ✗ **完全未引入** |
| 前端（老前端 / Vue3 / Admin）| ✗ 0 改动 |
| DB schema / 任何密码重置 | ✗ 未改（数据核对一致）|
| 验证码 / 短信 / 邮件 / reset token | ✗ 未新增 |
| `AdminUserController` 除 L81 外的任何行 | ✗ 未改 |

---

## 9. 已闭环 / 未闭环

| 项 | 状态 |
| --- | :-: |
| **`SEC-ANONYMOUS-PASSWORD-RESET`（🔴 P1）** | ✅ **本批收口**（两端点 404）|
| `SEC-PLAINTEXT-PASSWORD-STORAGE`（🔴 P1）| ❌ 未处理（→ S2-F-3/F-4）|
| `DEF-REGISTER-PASSWORD-DROPPED`（🔴 P1\* 功能阻断）| ❌ 未处理（→ S2-F-2）|
| `DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE`（🟡 P2）| ❌ 未处理 |
| `DEF-LOGIN-PASSWORD-IN-QUERY`（🟠 P2）| ❌ 未处理 |
| `DEF-PASSWORD-IN-TOSTRING`（🟡 P3）| ❌ 未处理 |
| `DEF-NO-SELF-SERVICE-PASSWORD-RECOVERY`（🟡）| ✅ 已登记（本批产生的功能降级）|
| `DEF-NO-SELF-SERVICE-PASSWORD-CHANGE`（🟠 P2）| ✅ 已登记 |
| `SEC-ADMIN-DEFAULT-CREDENTIAL`（🟡）| ❌ 未处理 |

---

## 10. 状态

| 项 | 值 |
|---|---|
| `git status --short` | **(空)** ✓ |
| `git diff --cached --check` | 无空白错误 ✓ |
| `git show --stat` | **4 files changed, +45 / −50** |
| HEAD / origin | **`aaf9bc0` / `aaf9bc0`** · LEAD **0** ✓ |
| 回归 | **47/47** ✓ |
| 服务 | PID `17316` · 8080 就绪 ✓ |
| 提交 | **✅ `aaf9bc0`**（`fix(security/s2-f-1-1)`）· **已推送 ✓** |

---

## 11. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-F-1-1 实现：删除 2 个匿名 `resetPass`（含 `@IgnoreAuth`）+ 保留统一文案下线注释 + 用例 20 扩展（三端点 404 + `/counselor/list` 200）+ AdminUser L81 注释校准 1 行；47/47；HTTP 9 项全绿；数据零写入；残留搜索 0 映射/0 方法定义；**未 commit** |
