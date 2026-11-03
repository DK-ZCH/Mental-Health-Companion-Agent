# S2-F-4-1 · PasswordHasher 实现报告（兼容层上线）

> **性质**：实现 · **6 文件（2 新增 + 4 修改）· +294 / −17** · **✅ 已提交 `05ed077` · 已推送 ✓**
> **基线**：`41c2b7b` → `05ed077` · 部署前 PID `8144` → 部署后 `52276`
> **本批定位**：F-4 三批次的第一批 —— **只做「能读两种格式 + 只写 hash」**，不做数据迁移

---

## ① Hutool BCrypt 实测结论（设计 §3.4 前置要求）

工具：JDK 17 单文件运行，`cn.hutool:hutool-all:5.8.25`（项目现有依赖，零新增）

```text
hashpw("123456") = $2a$10$<53>  → 长度 60 · 前缀 $2a$10$
```

| 前缀 / 形态 | `checkpw("123456", ...)` | 结论 |
| --- | :-: | --- |
| `$2a$` | **true** | ✅ 支持 |
| `$2b$` | **true** | ✅ 支持 |
| `$2x$` | **true** | ✅ 支持 |
| `$2y$` | **true** | ✅ 支持 |
| `$2$`（59 字符，无版本字母）| **false** | ⚠️ 不支持（归家族，结构不匹配 → 拒绝）|
| `$2a$10$xxxx`（长度不足）| **false（不抛异常）** | ✅ 安全 |
| `$2a$10$` + 53 个 `!` | **false（不抛异常）** | ✅ 安全 |
| `$2a$XX$...`（cost 非数字）| **false（不抛异常）** | ✅ 安全 |
| `null` 作为 stored | **false（不 NPE）** | ✅ 安全 |
| `null` 作为 raw | **false（不 NPE）** | ✅ 安全 |
| **`checkpw("123456","123456")`** | **false** | 🔑 **证明不能靠 checkpw 兜明文，必须显式 equals** |

**由此确定的两条实现要点**：
1. 正则 `[abxy]?` 覆盖实测支持集（`$2$` 虽不支持，但归家族后由结构校验拦截，不会误判为明文）；
2. Hutool 对非法输入返回 `false` 而非抛异常，但 **try/catch 仍保留**（防御不同版本/实现）。

---

## ② 新增 `com.security.PasswordHasher`（121 行）

```java
public static String  hash(String rawPassword);
public static boolean matches(String rawPassword, String storedPassword);
```

| 语义 | 实现 |
| --- | --- |
| `hash(null / "")` | **抛 `IllegalArgumentException`** |
| `matches(null / "", *)` | **`false`**，方法最前短路 |
| `matches(*, null / "")` | **`false`** |
| `$2` 家族 + 结构合法 | `checkpw` |
| `$2` 家族 + 结构非法 | **`false` + `[SEC][BCRYPT-MALFORMED]`，禁止 fallback** |
| 非 `$2` 家族 | `equals` + 命中时 `[SEC][PLAINTEXT-PASSWORD-HIT]` |

**日志脱敏**：只记录 `storedLen` 与 `prefix4`，**不记录密码本体**。

---

## ③ 改造点（16 处）

### 3.1 登录（3 处）

| 文件 | 方法 | 改动 |
| --- | --- | --- |
| `StudentController` | `login` | `getPassword().equals(pwd)` → `PasswordHasher.matches(pwd, getPassword())` |
| `CounselorController` | `login` | 同上 |
| `AdminUserController` | `login` | 同上 |

**顺带修复**：原 `getPassword().equals(...)` 在 `password` 列为 `NULL` 时抛 NPE → HTTP 500；改造后为正常「账号或密码不正确」。

### 3.2 密码写入（9 处，全部落 hash）

| # | 端点 | 文件:行 |
| :-: | --- | --- |
| 1 | `student/register` | `StudentController:355` |
| 2 | `counselor/register` | `CounselorController:335` |
| 3 | `student/save` | `StudentController:137` |
| 4 | `student/add` | `StudentController:507` |
| 5 | `counselor/save` | `CounselorController:140` |
| 6 | `counselor/add` | `CounselorController:488` |
| 7 | `adminUser/save` | `AdminUserController:145` |
| 8 | `student/resetPassword` | `StudentController:388` |
| 9 | `counselor/resetPassword` | `CounselorController:367` |

**校验**：`PasswordHasher.hash` 调用 **9** 处 · `matches` 调用 **3** 处 · 旧 `getPassword().equals` 残留 **0** 处。

### 3.3 测试契约（4 处 + 2 处增强）

| 行 | 原断言 | 现断言 |
| :-: | --- | --- |
| 233/234（前置）| `== "123456"` | `matches("123456", ...)` 为 true |
| 239/240（重置后）| `== "123456"` | `startsWith("$2")` **且** `matches(...)` 为 true |

> 239/240 因 `resetPassword` 使用随机盐，hash 字符串必然变化 —— 断言由「值相等」改为「可匹配 + 已落 hash」。

---

## ④ 测试结果

```text
Tests run: 30, Failures: 0, Errors: 0 -- IdentityAuthorizationRegressionTest
Tests run: 17, Failures: 0, Errors: 0 -- Phase2CoreRegressionTest
Tests run: 13, Failures: 0, Errors: 0 -- PasswordHasherTest（新增）
Tests run: 60, Failures: 0, Errors: 0
BUILD SUCCESS
```

**原 47 项全绿 + 新增 13 项全绿 = 60/60**。

### 4.1 `PasswordHasherTest` 13 例（含两条关键护栏）

| # | 用例 | 结果 |
| :-: | --- | :-: |
| 1–2 | `hash(null)` / `hash("")` 抛异常 | ✅ |
| 3 | hash 60 字符 · `$2` 开头 · 两次不同（随机盐）| ✅ |
| 4–7 | `matches` 四类空值 → false | ✅ |
| 8–9 | 正确 / 错误密码 vs BCrypt | ✅ |
| **10** | **`matches("$password123","$password123")` → true** | ✅ **防误杀历史明文** |
| **11** | **`matches("$2a$10$xxxx", 同值)` → false** | ✅ **防非法 hash 当明文** |
| 12 | hash 与明文互不串通 | ✅ |
| 13 | `$1$` 等非 BCrypt 家族按明文处理 | ✅ |

---

## ⑤ 部署证据

| 项 | 旧 | 新 |
|---|---|---|
| PID | `8144` | **`52276`** ✓ |
| jar 时间戳 | 19:56:14 | **22:00:56** ✓ |
| jar 大小 | — | 143,206,374 |
| 8080 | — | 就绪（4s）✓ |
| 旧进程 | — | 已 `Stop-Process` 并确认端口释放 ✓ |

---

## ⑥ 混合态验证（**本批核心**）

部署时 DB 天然处于混合态（测试用例 9 已把 `student 3` / `counselor 3` 重置为 hash）：

| 账号 | 存储形态 | 登录 | 判定 |
| --- | --- | :-: | :-: |
| `student a1` | 明文（len 6）| ✅ 200 + token | 兼容分支 |
| `student a3` | **BCrypt（len 60）** | ✅ 200 + token | BCrypt 分支 |
| `counselor a1` | 明文 | ✅ 200 + token | 兼容分支 |
| `counselor a3` | **BCrypt** | ✅ 200 + token | BCrypt 分支 |
| `admin` | 明文（len 5）| ✅ 200 + token | 兼容分支 |

### 6.1 失败路径一致性

| 场景 | student(明文) | student(hash) | admin(明文) |
| --- | :-: | :-: | :-: |
| 错误密码 | `code=500`「账号或密码不正确」| 同左 | 同左 |
| 空密码 | 同左 | 同左 | — |

**三分支行为完全一致**（不泄露账号是否存在 / 存储形态）。

### 6.2 WARN 观测证据（非测试断言）

```text
[SEC][PLAINTEXT-PASSWORD-HIT] count = 3
  · storedLen=6（student a1）
  · storedLen=6（counselor a1）
  · storedLen=5（admin）
[SEC][BCRYPT-MALFORMED]      count = 0
[SEC][BCRYPT-PARSE-FAIL]     count = 0
```

**精确对应 3 个明文账号**；两个 BCrypt 账号登录**未产生**该 WARN（证明走了 checkpw 分支）✓

---

## ⑦ 9 端点写入探针

| # | 端点 | 响应 | DB 实测 |
| :-: | --- | :-: | --- |
| 1 | `student/register`（匿名）| `code=0` | `$2a$` / 60 ✓ |
| 2 | `counselor/register`（匿名）| `code=0` | `$2a$` / 60 ✓ |
| 3 | `student/save`（admin）| `code=0` | `$2a$` / 60 ✓ |
| 4 | `student/add`（admin）| `code=0` | `$2a$` / 60 ✓ |
| 5 | `counselor/save`（admin）| `code=0` | `$2a$` / 60 ✓ |
| 6 | `counselor/add`（admin）| `code=0` | `$2a$` / 60 ✓ |
| 7 | `adminUser/save`（admin）| `code=0` | `$2a$` / 60 ✓ |
| 8 | `student/resetPassword?id=3` | `code=0` | `$2a$` / 60 ✓ |
| 9 | `counselor/resetPassword?id=3` | `code=0` | `$2a$` / 60 ✓ |

**9/9 全部落 hash** ✓

### 7.1 新建 hash 账号可正常登录

| 账号 | 密码 | 结果 |
| --- | --- | :-: |
| `zzf41probeS1` | `Probe123456` | ✅ |
| `zzf41probeS2` | `123456`（默认）| ✅ |
| `zzf41probeC1` | `Probe123456` | ✅ |
| `zzf41probeA1` | `123456`（默认）| ✅ |

### 7.2 探针清理

```text
DELETE 后残留：student 0 / counselor 0 / admin_user 0  ✓
基线行数：student 3 / counselor 3 / admin_user 1  ✓
```

---

## ⑧ 数据现状（**F-4-1 后，明文仍存在 = 预期**）

| 表 | 明文 | BCrypt |
| --- | :-: | :-: |
| `student` | 2（id 1,2）| 1（id 3）|
| `counselor` | 2（id 1,2）| 1（id 3）|
| `admin_user` | 1（admin）| 0 |

> ✅ **本批不迁移数据**（属 F-4-2）。明文 ≠ 0 是**预期状态**，不代表 S2-F-4-1 未完成。
> `SEC-PLAINTEXT-PASSWORD-STORAGE` **仍 OPEN**，需 F-4-2 完成后才可能关闭。

`auth_token` = 9 条（多轮登录产生的 token，非脏数据）。

### 8.1 ⚠️ 上表数字**不是** F-4-2 的迁移前正式基线

F-4-2 开始时**必须重新执行 preflight gate**（F-3 §4.3.1），不得直接沿用本报告的数字：

```text
NULL = 0 · empty = 0 · malformed bcrypt = 0
plaintext = ?（F-4-1 时为 5）
bcrypt    = ?（F-4-1 时为 2）
```

原因：F-4-1 与 F-4-2 之间可能发生任何操作（登录、重置、手动改动），
preflight 必须是**迁移时刻的实测快照**，而非历史记录。

---

## ⑨ Staged Review 证据

```text
staged --name-status:
  M  src/main/java/com/controller/AdminUserController.java
  M  src/main/java/com/controller/CounselorController.java
  M  src/main/java/com/controller/StudentController.java
  A  src/main/java/com/security/PasswordHasher.java
  M  src/test/java/com/regression/IdentityAuthorizationRegressionTest.java
  A  src/test/java/com/security/PasswordHasherTest.java

staged --stat: 6 files changed, 294 insertions(+), 17 deletions(-)
staged --check: 无空白错误 ✓
```

### 9.1 commit-after-review

```text
commit      : 05ed077  feat(security/s2-f-4-1): 密码哈希门面 + 双格式登录兼容
HEAD        : 05ed077a067174158e3d1b4d098785b8aa8e5e1f
origin      : 41c2b7b7ab760b7f903a636058c1551d228a4624
LEAD        : 1（未 push）
worktree    : (空) ✓
越界文件    : 0（无 docs/前端/sql）✓
门面调用    : hash=9 · matches=3 · 旧 equals=0 ✓
```

### 9.2 已知洁净度备注（**不阻塞，未修改**）

BCrypt 结构正则写作 `^\$2[abxy]?\$\d{2}\$[./A-Za-z0-9]{53}$`，其中 `[abxy]?` 为**可选**：

- 语义上把 `$2$`（59 字符）也纳入"家族"范围，使其因**长度不符**被结构校验拒绝（而非被当作明文）；
- 当前行为正确（实测 `$2$` → false），仅表达上"绕了一层"；
- 若后续维护，可显式改为 `[abxy]`（去掉 `?`）—— **本批不改**（零行为差异，避免无谓改动扩散）。

---

## ⑩ 边界声明（严格未动）

| 未动 | 状态 |
|---|:-:|
| ❌ 批量迁移历史明文（F-4-2）| 未做 |
| ❌ 删除明文 fallback（F-4-3）| 未做 |
| ❌ DB / DDL / 列宽 | 未改 |
| ❌ 前端（Vue3 / 老前端 / Admin）| 0 改动 |
| ❌ `AuthorizationInterceptor` / `CurrentUserProvider` / Session | 未改 |
| ❌ 默认口令治理（`SEC-ADMIN-DEFAULT-CREDENTIAL`）| 未做 |
| ❌ 登录时静默升级明文 | 未做（F-3 §6.3 已否决）|

---

## ⑪ 风险复核

| 风险 | 实测结论 |
| --- | --- |
| 非法 hash 导致登录 500 | ✅ **未发生**（Hutool 返回 false；try/catch 兜底）|
| `$` 开头历史明文被锁死 | ✅ **未发生**（`$password123` 单测 true，走明文分支）|
| 非法 BCrypt 被当明文 | ✅ **未发生**（单测用例 11 false）|
| 空密码通过认证 | ✅ **未发生**（空密码 `code=500` 拒绝）|
| 老前端/Admin 登录受影响 | ✅ **未发生**（5/5 登录成功）|
| 探针数据残留 | ✅ **已清理**（0/0/0，行数回基线）|

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v3 | 2026-10-22 | **commit-after-review 批准 → 已 push**（`41c2b7b..05ed077`）；push 后六项终检通过（HEAD=origin · worktree CLEAN · LEAD 0 · 服务 PID 52276）；新增 §8.1：**F-4-2 preflight 必须重新实测，不得沿用 F-4-1 数字**；S2-F-4-1 **正式闭环**，`SEC-PLAINTEXT-PASSWORD-STORAGE` **仍 OPEN**（待 F-4-2）|
| v2 | 2026-10-22 | **staged review 批准 → 已 commit `05ed077`（未 push）**；commit message 按裁定微调为「`$2a/$2b/$2x/$2y` **checkpw 可验证**；非法/不支持结构返回 false」；§9.1 补 commit-after-review 证据；§9.2 登记正则 `[abxy]?` 洁净度备注（**不阻塞、本批不改**）；`SEC-PLAINTEXT-PASSWORD-STORAGE` **仍 OPEN**（5 个明文账号待 F-4-2 迁移） |
| v1 | 2026-10-22 | S2-F-4-1 实现：Hutool BCrypt 前缀实测（`$2a/$2b/$2x/$2y` 支持 · `$2$` 不支持 · 非法输入返回 false 不抛 · **`checkpw` 对明文返回 false**）· 新增 `PasswordHasher`（121 行）· 3 login + **9 写入** 全部改造 · 4 处测试断言升级 · **60/60** · 部署 `8144→52276`（jar 22:00:56）· **混合态 5/5 登录成功** · 9 端点 9/9 落 hash · 探针清理回基线 · WARN 精确命中 3 个明文账号 · **未 commit** |
