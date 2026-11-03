# S2-F-4-1 · PasswordHasher 门面 + 双格式登录兼容 + 新密码写入 BCrypt（实现设计）

> **性质**：设计 **v2**（✅ 有条件批准 → **2 处边界已按裁定修正** · 待最终批准进实现 · **不写代码**）
> **上游**：`S2-F-3-PASSWORD-HASH-MIGRATION-DESIGN.md` v3（已批准 · 设计冻结）
> **本批定位**：F-4 三批次中的 **第一批（兼容层）** —— 只让系统「能读两种格式 + 只写 hash」
> **基线**：`HEAD = origin = 41c2b7b` · worktree CLEAN · `SEC-PLAINTEXT-PASSWORD-STORAGE` **OPEN**

---

## ① 本批范围（严格锁死）

```text
✅ 新增 com/security/PasswordHasher.java（唯一新文件）
✅ 3 处 login 改为 PasswordHasher.matches(...)
✅ 9 处密码写入改为 PasswordHasher.hash(...)
✅ 4 处测试断言改为「可匹配」语义
✅ BCrypt 前缀支持实测（§6.1.1 要求）

❌ 数据迁移（F-4-2）
❌ 删除明文 fallback（F-4-3）
❌ DB / DDL / 列宽
❌ 前端（Vue3 / 老前端 / Admin）
❌ AuthorizationInterceptor / CurrentUserProvider / Session
❌ 默认口令治理（SEC-ADMIN-DEFAULT-CREDENTIAL 独立）
❌ 登录时静默升级明文（F-3 §6.3 已否决）
```

---

## ② 新增类 `com.security.PasswordHasher`

### 2.1 为什么放 `com.security`

现有 `com.security` 已承载 6 个安全原语（`CurrentUserProvider` / `OwnershipGuard` / `UpdateGuard` / `DataScope` / `ForbiddenException` / `BadRequestException`）。
密码哈希属同类横切安全能力，**放此处与既有分层一致**，且 `com.utils` 已含 14 个通用工具类（语义不专）。

### 2.2 API（**仅 2 个 public 方法**）

```java
public final class PasswordHasher {

    /** BCrypt 候选 cost（最终值由 §2.1 benchmark 决定，F-4-1 先按 10 实现） */
    private static final int COST = 10;

    /** 原始密码 → BCrypt hash（60 字符，含随机盐） */
    public static String hash(String rawPassword);

    /** 校验：BCrypt 优先；非 BCrypt 走明文兼容窗口（F-4-3 后删除） */
    public static boolean matches(String rawPassword, String storedPassword);
}
```

**刻意不暴露**：
- ❌ `isBcrypt()`（判断逻辑内聚，避免调用方自行分支）
- ❌ `needsUpgrade()`（不实现静默升级）
- ❌ `verify()` / `check()` 等别名（防语义分裂）

**私有成员**（仅内部使用，§3 详述）：

```java
private static final int     COST = 10;
private static final Pattern BCRYPT_SHAPE =
        Pattern.compile("^\\$2[abxy]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
private static boolean isBcryptFamily(String stored);   // startsWith("$2")
```

### 2.3 语义契约（必须逐条实现）

| 场景 | `hash(raw)` | `matches(raw, stored)` |
| --- | --- | :-: |
| `raw` 正常 | 返回 `$2a$10$...`（60 字符）| — |
| **`raw == null`** | **抛 `IllegalArgumentException`**（不静默生成空密码 hash）| **`false`**（fail-closed，**在任何比较之前返回**）|
| **`raw.isEmpty()`** | **抛 `IllegalArgumentException`**（防制造弱口令）| **`false`**（同上，空密码不参与认证）|
| `stored == null` | — | **`false`**（不 NPE）|
| **`stored.isEmpty()`** | — | **`false`**（空串不得与空输入"意外匹配"）|
| `stored` 属 **BCrypt 家族**（`$2` 开头）+ 结构完整 | — | `BCrypt.checkpw(raw, stored)` |
| `stored` 属 **BCrypt 家族** + **结构非法**（如 `$2a$xx`）| — | 🔴 **`false` + WARN，禁止 fallback 明文** |
| `stored` 不属 BCrypt 家族（历史明文，含 `$password123`）| — | `stored.equals(raw)` + `PLAINTEXT-PASSWORD-HIT` WARN |
| `raw == null` 且 `stored` 为明文 | — | **`false`**（前置短路已覆盖，防 `null.equals` NPE）|

> ⚠️ **关键防御 1**：Hutool `BCrypt.checkpw` 在 `stored` 非合法 hash 时抛 `IllegalArgumentException`。
> 若不捕获，一次脏数据即导致**登录接口 500**。必须 try/catch → `false`。
>
> ⚠️ **关键防御 2（fail-closed）**：`raw == null || raw.isEmpty()` **必须放在方法最前**，
> 早于任何 BCrypt / 明文分支 —— 否则 `checkpw(null, ...)` 抛异常、空串可能与空明文意外匹配。

### 2.3.1 方法骨架（固定顺序，不得调换）

```java
public static boolean matches(String rawPassword, String storedPassword) {
    // ① fail-closed：空凭据一律拒绝，不进入任何比较逻辑
    if (rawPassword == null || rawPassword.isEmpty()) {
        return false;
    }
    if (storedPassword == null || storedPassword.isEmpty()) {
        return false;
    }
    // ② BCrypt 家族 → 结构校验 → checkpw（非法即 false，绝不回退明文）
    if (isBcryptFamily(storedPassword)) {
        if (!BCRYPT_SHAPE.matcher(storedPassword).matches()) {
            warnMalformedBcrypt(storedPassword);
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, storedPassword);
        } catch (IllegalArgumentException e) {
            warnMalformedBcrypt(storedPassword);
            return false;
        }
    }
    // ③ 历史明文（兼容窗口）
    warnPlaintextHit(storedPassword);
    return storedPassword.equals(rawPassword);
}
```

### 2.4 明文命中 WARN 日志

```text
[SEC][PLAINTEXT-PASSWORD-HIT] table=unknown storedPrefix=<前4字符> at=...
```

- **只记录前缀**，不记录密码本体（含明文与 hash 全文）
- 用途：F-4-2 迁移遗漏监控（F-3 §6.2）
- **不触发自动升级**（F-3 §6.3：静默升级会掩盖未迁移事实）

---

## ③ 🔴 BCrypt 判断模型：**候选结构**，不是「任意 `$` 开头」

### 3.1 为什么不能用 `startsWith("$")`（v1 方案的致命缺陷）

```text
历史明文：$password123   ← 完全合法的旧密码
        ↓
startsWith("$")  → true
        ↓
checkpw("$password123", "$password123") → IllegalArgumentException
        ↓
false + 禁止 fallback
        ↓
🔴 该用户即使输入正确密码也【永远无法登录】
```

这直接违背 F-4-1 的核心目标：**兼容窗口必须同时支持历史明文与 BCrypt**。

> 结论：**判断条件不能是「任意 `$`」，必须是「BCrypt 候选结构」。**

### 3.2 两级判断模型（宽家族 + 严结构）

| 层级 | 条件 | 处理 | 是否允许 fallback 明文 |
| :-: | --- | --- | :-: |
| **L1 家族判定**（宽）| `stored.startsWith("$2")` | 进入 BCrypt 分支 | ❌ **绝不** |
| **L2 结构校验**（严）| `BCRYPT_SHAPE` 正则完整匹配 | 通过 → `checkpw` | ❌ |
| L2 不通过（`$2` 开头但残缺，如 `$2a$xx`）| — | **`false` + WARN** | ❌ **绝不** |
| **非 L1**（`123456` / `admin` / `$password123` / `$1$...`）| — | `equals` 明文兼容 + WARN | ✅ 兼容窗口 |

```java
/** BCrypt 家族前缀：$2 / $2a / $2b / $2x / $2y（不含 $1$ / $5$ / $6$ / argon2） */
private static boolean isBcryptFamily(String stored) {
    return stored.startsWith("$2");
}

/** 完整 BCrypt 结构：$2[abxy]?$<2位cost>$<53字符salt+hash> = 共 60 字符 */
private static final Pattern BCRYPT_SHAPE =
        Pattern.compile("^\\$2[abxy]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
```

### 3.3 三类输入的行为（**必须逐条验收**）

| 输入 `stored` | 属家族 | 结构合法 | 行为 | 说明 |
| --- | :-: | :-: | --- | --- |
| `$2a$10$<53合法字符>` | ✅ | ✅ | `checkpw` | 正常 hash |
| `$2a$10$xxxx` | ✅ | ❌ | **`false` + WARN** | 🔴 **禁止 fallback**（否则 `$2a$10$xxxx` 可被当作明文登录）|
| `$password123` | ❌ | — | `equals` + WARN | ✅ 历史明文可正常登录 |
| `123456` / `admin` | ❌ | — | `equals` + WARN | ✅ |
| `$1$abc`（MD5-crypt）| ❌ | — | `equals` + WARN | 本项目从无此格式；登记为已知形态 |

> **核心原则**：**「像 BCrypt」⇒ 只走 BCrypt，失败即拒绝；「不像 BCrypt」⇒ 才允许明文比较。**
> 二者互斥，不存在"BCrypt 失败后再试明文"的路径（否则非法 hash 可被当明文利用）。

### 3.4 前置实测（F-3 §6.1.1 要求，仍必须做）

**写 `matches()` 之前**必须实测 Hutool 对家族内各前缀的实际支持：

```text
① BCrypt.hashpw("123456") → 记录实际输出前缀（预期 $2a$）
② 构造 $2b$ / $2x$ / $2y$ 完整样本 → checkpw → 记录 true / 异常
③ 结果写入 F-4-1 实现报告：
   · Hutool 实际支持集 = { ... }
   · 不支持但结构合法的前缀 ⇒ 该账号 checkpw 抛异常 → false + WARN
     ⇒ 🔴 属【已知限制】，须在报告中登记，并由运维手工重置该账号
④ 建议 F-4-2 preflight 增加「password 前缀分布统计」，
   若出现 Hutool 不支持的前缀，迁移前即暴露（而不是登录时才失败）
```

**禁止**：未实测就把 `$2a$ / $2b$ / $2y$` 全部断言为"支持"。

### 3.5 已知形态说明（登记，不扩大范围）

| 形态 | 本批处理 |
| --- | --- |
| `$1$`（MD5-crypt）· `$5$`/`$6$`（SHA-crypt）· `$argon2*` | 非本项目历史格式 ⇒ 走明文分支；若真出现，等值才通过（安全上等价于明文）|
| `$2x$` | 归入家族（正则 `[abxy]?` 已含）；Hutool 支持性由 §3.4 实测 |

---

## ④ 3 处登录改造

| # | 文件 | 行 | 现代码 | 改为 |
| :-: | --- | :-: | --- | --- |
| 1 | `StudentController.java` | 322 | `!yonghu.getPassword().equals(password)` | `!PasswordHasher.matches(password, yonghu.getPassword())` |
| 2 | `CounselorController.java` | 302 | `!xinlilaoshi.getPassword().equals(password)` | `!PasswordHasher.matches(password, xinlilaoshi.getPassword())` |
| 3 | `AdminUserController.java` | 50 | `!user.getPassword().equals(password)` | `!PasswordHasher.matches(password, user.getPassword())` |

**保持不变（重要）**：

```java
return R.error("账号或密码不正确");   // 三条路径统一消息，不泄露账号是否存在
```

**顺带修掉一个既有 NPE 风险**：现代码 `user.getPassword().equals(...)` 在 `password` 列为 `NULL` 时抛 NPE（→ 500）。
改造后 `matches()` 对 `stored == null` 返回 `false` ⇒ 变为正常「密码不正确」。**属安全修复的一部分，不是范围扩张**。

---

## ⑤ 9 处密码写入改造（F-3 §3 已确认清单）

| # | 文件 | 行 | 当前值 | 改为 |
| :-: | --- | :-: | --- | --- |
| 1 | `StudentController` | 136 | `"123456"`（save 新增）| `PasswordHasher.hash("123456")` |
| 2 | `StudentController` | 354 | `req.getPassword()`（register）| `PasswordHasher.hash(req.getPassword())` |
| 3 | `StudentController` | 386 | `"123456"`（resetPassword）| `PasswordHasher.hash("123456")` |
| 4 | `StudentController` | 505 | `"123456"`（add）| `PasswordHasher.hash("123456")` |
| 5 | `CounselorController` | 139 | `"123456"`（save）| `PasswordHasher.hash("123456")` |
| 6 | `CounselorController` | 334 | `req.getPassword()`（register）| `PasswordHasher.hash(req.getPassword())` |
| 7 | `CounselorController` | 365 | `"123456"`（resetPassword）| `PasswordHasher.hash("123456")` |
| 8 | `CounselorController` | 486 | `"123456"`（add）| `PasswordHasher.hash("123456")` |
| 9 | `AdminUserController` | 143 | `"123456"`（save）| `PasswordHasher.hash("123456")` |

**不计入**（F-3 已界定）：
- `resetPass` ×2（已下线 404）
- `*/update`（不可绑定 `password`，F-1-1 已收口）
- `StudentController:234` / `CounselorController:230`（**注释行**）

**注入点保留**：`hashpw` 使用随机盐 ⇒ 同一 `"123456"` 每次产生不同 hash（**这是正确行为**，不是不确定性缺陷）。

---

## ⑥ 🔴 测试契约变更：**实际 4 处，不是 2 处**（对 F-3 的修正）

F-3 原写「2 处」，实测 `IdentityAuthorizationRegressionTest` 共 **4 处** 硬编码 `isEqualTo("123456")`：

| 行 | 当前断言 | 迁移后行为 | 必须改为 |
| :-: | --- | --- | --- |
| 233 | `studentPassword(3) == "123456"`（前置）| 失败（已是 hash）| `matches("123456", studentPassword(3)) == true` |
| 234 | `counselorPassword(3) == "123456"`（前置）| 失败 | 同上 |
| 239 | 重置后 `== "123456"` | **失败**（新 salt ⇒ hash 变化）| `matches("123456", ...) == true` |
| 240 | 同上 | 失败 | 同上 |

> ⚠️ **239/240 是必改的隐蔽陷阱**：`resetPassword` 每次生成**不同 hash**，
> 「字符串相等」语义在迁移后**必然不成立**，但「密码仍是 123456」的**业务语义仍成立**。
> 断言必须从「值相等」改为「可匹配」。

**不需要改**（用「前后快照相等」而非硬编码值）：

| 行 | 断言 | 为何仍成立 |
| :-: | --- | --- |
| 246/255 | `studentPassword(2) == before`（越权后不变）| 未发生写入，快照相等 ✓ |
| 247/256 | `counselorPassword(2) == before` | 同上 ✓ |
| 262/269 | `studentPassword(3) == before`（未认证后不变）| 同上 ✓ |

**增强建议（非必需）**：在重置后补 `assertThat(studentPassword(3)).startsWith("$2")`，用于同时证明「写路径已 hash」。
但**不得只靠 startsWith** —— 必须配合 `matches(...) == true`（F-3 已要求）。

---

## ⑦ 验收矩阵（F-4-1 独立可验）

### 7.1 兼容性（**本批核心**）

| 场景 | 期望 |
| --- | :-: |
| 迁移前明文账号 `a1/123456` 登录（student）| ✅ 200 + token（**明文分支生效**）|
| 同上 counselor | ✅ 200 + token |
| `admin/admin`（明文）| ✅ 200 + token |
| 🔴 **历史明文以 `$` 开头**（构造 `$password123`）| ✅ **200**（证明判断不是「任意 `$`」，否则此账号永久锁死）|
| 🔴 **非法 BCrypt 形态**（构造 `$2a$10$xxxx`）| ❌ **`false`**（**不得**被当作明文 `$2a$10$xxxx` 匹配通过）|

> **关于 WARN 的口径**：`PLAINTEXT-PASSWORD-HIT` / `malformed bcrypt` 属**安全观测证据**，
> 记录在 F-4-1 实现报告中；**不作为测试断言**（避免业务测试与日志实现耦合）。
> 测试只验证行为：`matches(...)` 返回值 + HTTP 状态。

### 7.1.1 纯单元测试（`PasswordHasherTest`，无 Spring / 无 DB）

上述两条 🔴 场景无法通过真实 HTTP 构造（不能把 DB 改成脏数据），
因此必须新增**纯静态单元测试**直接验证 `matches()` 分支：

| # | 用例 | 期望 |
| :-: | --- | :-: |
| 1 | `hash(null)` | 抛 `IllegalArgumentException` |
| 2 | `hash("")` | 抛 `IllegalArgumentException` |
| 3 | `hash("123456")` | 非空 · 60 字符 · `$2` 开头 |
| 4 | `matches(null, "$2a$...")` | `false` |
| 5 | `matches("", "$2a$...")` | `false` |
| 6 | `matches("123456", null)` | `false` |
| 7 | `matches("123456", "")` | `false` |
| 8 | `matches("123456", hash("123456"))` | `true` |
| 9 | `matches("wrong", hash("123456"))` | `false` |
| 10 | **`matches("$password123", "$password123")`** | **`true`**（`$` 开头历史明文必须可登录）|
| 11 | **`matches("$2a$10$xxxx", "$2a$10$xxxx")`** | 🔴 **`false`**（非法 BCrypt **不得** fallback 明文）|
| 12 | `matches("$2a$10$xxxx", "$2a$10$yyyy")` | `false` |
| 13 | `matches("123456", "$1$abc")` | `false`（非家族 → 明文 `equals`，不等即 false）|

> 用例 10 / 11 是本设计**最关键的回归护栏** —— 前者防「误杀历史明文」，后者防「非法 hash 被当明文」。

### 7.2 写入路径（9 端点）

| 场景 | 期望 |
| --- | :-: |
| `student/register` 探针 | DB 中 `$2` 开头 · 新账号可登录 |
| `counselor/register` 探针 | 同上 |
| `student/save` · `student/add` 探针 | `$2` 开头 |
| `counselor/save` · `counselor/add` 探针 | `$2` 开头 |
| `adminUser/save` 探针 | `$2` 开头 |
| `student/resetPassword` · `counselor/resetPassword` | `$2` 开头 · 且 `matches("123456", ...)` 为 true |

> 探针产生的测试数据**必须清理**，并核对三表行数回到基线（student 3 / counselor 3 / admin_user 1）。

### 7.3 混合态登录（**F-4-1 特有验收**）

F-4-1 部署后 DB 为**混合态**（历史明文 + 新增 hash）。必须验证：

```text
① 明文账号登录 → 200（兼容分支）
② hash 账号登录 → 200（BCrypt 分支）
③ 错误密码 → 仍返回「账号或密码不正确」（两分支一致）
④ password 为 NULL 的脏行 → 不再 500，而是「密码不正确」✓
```

### 7.4 回归

```text
47/47（含上述 4 处断言改造）
```

### 7.5 数据基线

```text
F-4-1 后：三表仍为「历史明文 + 探针已清理」
❌ 本批不做全量迁移 ⇒ 明文行数 ≠ 0 是预期状态
```

---

## ⑧ 文件清单与规模

| # | 文件 | 改动 | 估计 |
| :-: | --- | --- | :-: |
| 1 | `com/security/PasswordHasher.java` | **新增** | ~65 行 |
| 2 | `StudentController.java` | login 1 + 写入 4 = **5 处** | +5 / −5 |
| 3 | `CounselorController.java` | login 1 + 写入 4 = **5 处** | +5 / −5 |
| 4 | `AdminUserController.java` | login 1 + 写入 1 = **2 处** | +2 / −2 |
| 5 | `IdentityAuthorizationRegressionTest.java` | **4 处断言** | +4 / −4 |
| 6 | `PasswordHasherTest.java` | **新增单元测试**（13 例，§7.1.1）| ~80 行 |

**合计 6 文件（2 新增 + 4 修改）· 改动 16 处单行替换 + 197 行新代码**

> 第 6 个文件是 v2 新增：**没有它，§3.3 的「非法 BCrypt 不回退明文」与「`$password123` 可登录」两条最关键边界无法验证**
> （这两条不能通过真实 HTTP 构造，否则要往 DB 写脏数据）。属必要验证手段，非范围扩张。

---

## ⑨ 风险清单

| 风险 | 评估 | 缓解 |
| --- | :-: | --- |
| `checkpw` 对非法 hash 抛异常 → 登录 500 | 🔴 高 | `try/catch` → `false` + WARN（§2.3）|
| `hash(null)` 生成空密码 hash | 🔴 高 | 显式抛 `IllegalArgumentException` |
| **`$` 开头历史明文被误判 BCrypt → 账号永久锁死** | 🔴 **高（v1 实际存在）** | ✅ **v2 已修**：判定改为 `$2` 家族 + 结构正则（§3.1/§3.2）；单测用例 10 护栏 |
| **非法 BCrypt 被当作明文匹配通过** | 🔴 **高（等价于认证绕过）** | ✅ **v2 已修**：命中家族即**禁止 fallback**（§3.3）；单测用例 11 护栏 |
| `matches("")` 未定义 → 空密码参与认证 | 🟠 中 | ✅ **v2 已修**：方法最前 fail-closed（§2.3.1）|
| 迁移后 `resetPassword` hash 变化导致测试失败 | 🟠 中 | §6 已识别 4 处，改为 `matches` |
| 老前端/Admin 登录受影响 | 🟢 低 | 明文分支保留 ⇒ 行为不变 |
| `$2y$` 不被 Hutool 支持 | 🟡 中 | §3 实测 + 走异常→false + 登记 |
| 性能（每次登录多一次 BCrypt，~cost 10）| 🟡 中 | F-3 §2.1 benchmark；本批先按 10 |
| 探针数据残留 | 🟡 中 | 7.2 明确清理 + 行数回基线 |

---

## ⑩ 已裁定（用户 2026-10-22）

| # | 问题 | 裁定 |
| :-: | --- | --- |
| 1 | `PasswordHasher` 包位置 | ✅ **`com.security`**（认证安全原语，非通用工具）|
| 2 | BCrypt 判断策略 | ❌ **原「任意 `$` 开头」被否** → ✅ 改为 **BCrypt 候选结构**（§3：`$2` 家族 + 结构正则 + 非法不回退明文）|
| 3 | `hash(null/"")` | ✅ **抛 `IllegalArgumentException`** |
| 4 | `matches(null/"")` | ✅ **必须 `false`，且在方法最前短路**（fail-closed，§2.3.1）|
| 5 | 非法 BCrypt 是否可 fallback 明文 | ✅ **绝对禁止**（§3.3）|
| 6 | `$2a/$2b/$2y` 实测 | ✅ 保留为 F-4-1 前置（§3.4）|
| 7 | 测试与日志耦合 | ✅ **测试只验行为**；WARN 作为实现报告的观测证据（§7.1）|

### 10.1 v2 修正前后对照

| 项 | v1（已否）| v2（已改） |
| --- | --- | --- |
| BCrypt 判定 | `startsWith("$")` | **`startsWith("$2")` + `BCRYPT_SHAPE` 正则** |
| `$password123` | 🔴 永久锁死 | ✅ 走明文分支，正常登录 |
| `$2a$10$xxxx` | false（但原因不明）| ✅ **显式 false + WARN，禁止 fallback** |
| `matches("")` | 未定义 | ✅ **方法最前 `false`** |
| 验证手段 | 仅 HTTP 探针 | ✅ **新增 13 例纯单元测试**（含 2 条最关键护栏）|

---

## ⑪ F-4-1 与后续批次边界

```text
F-4-1（本批）
  · 只写 hash + 双格式读
  · DB = 混合（历史明文仍在）
  · 明文 fallback 必须保留

F-4-2（独立批次）
  · backup → preflight gate → 全量迁移 → 验证 → 回滚演练
  · 完成后 DB = 全量 hash

F-4-3（独立批次）
  · 删除 matches() 中的明文分支
  · 此后 SEC-PLAINTEXT-PASSWORD-STORAGE 可 CLOSE 的更彻底
```

> 🔴 **回滚边界提醒（F-3 §9.1.1）**：**F-4-1 一旦产生第一条 BCrypt 写入，旧版本代码即刻失去直接回滚能力。**
> 因此 F-4-1 的部署本身即属「进入需附带数据回滚的阶段」，必须在部署前确认备份就绪。

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v2 | 2026-10-22 | **用户裁定：条件批准，2 处设计文字必须改 → 已改**：①`matches(null/"")` → **`false` 且在方法最前短路**（fail-closed，§2.3.1 骨架固定顺序）②❌ **否决「任意 `$` 开头即 BCrypt」** → 改为 **BCrypt 候选结构两级判定**（L1 `$2` 家族 → L2 `BCRYPT_SHAPE` 正则 → 非法即 false **禁止 fallback 明文**；`$password123` 走明文可正常登录），§3 新增三类输入行为表 + 已知形态说明；新增 **`PasswordHasherTest` 13 例纯单元测试**（含「`$password123` 可登录」与「`$2a$10$xxxx` 不得 fallback」两条关键护栏）；WARN 改为**观测证据不作测试断言**；文件数 5 → **6**；§10 改为已裁定 + 修正前后对照 |
| v1 | 2026-10-22 | S2-F-4-1 实现设计：`PasswordHasher`（2 public 方法 · 6 条语义契约 · 非法 hash 捕获 · 明文 WARN）· 前缀支持实测前置（以 `$` 开头尝试 checkpw 优于枚举）· 3 处 login · **9 处写入** · 🔴 **测试契约实际 4 处（修正 F-3 的 2 处；239/240 因随机盐必然失败）** · 4 类验收矩阵（含混合态 + NULL 脏行不 500）· 5 文件 · 7 项风险 · 3 项待裁定；**零改动，不写代码** |
