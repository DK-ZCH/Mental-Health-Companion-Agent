# S2-F-4-3 · 删除明文 fallback 分支（实现设计）

> **性质**：设计（📐 **待批准 · 不写代码**）
> **上游**：`S2-F-4-2` 已 CLOSED（`plain=0` / `bcrypt=7` / 9 路径全 hash）
> **基线**：`HEAD = origin = aa98363` · worktree CLEAN · DB **7 行全 `$2a$`**
> **本批定位**：F-4 三批次中的**最后一批** —— 把「明文也能登录」彻底关掉
>
> **🗺️ 路线图位置（用户裁定 2026-10-23）**：
> ```
> F-4-3（本批）→ **Phase 4 / Step 11 Spring AI 最小闭环** → Step 8 Dashboard → Phase 4 后续 → RAG
> ```
> 即 **Step 11 提前于 Step 8**：先回答「Agent 能否嵌入既有系统」这个技术路线问题，
> 而不是再补页面。F-4-3 是本批唯一剩余的「已打开安全链」，做完即转主线。

---

## ① 范围

```text
✅ 修改 PasswordHasher.matches()：删除「非 BCrypt → 明文 equals」分支
✅ 更新 PasswordHasherTest：2 处断言反转 + 新增「明文存储值必须拒绝」用例
✅ 运行时负向验证：明文存储值必须登录失败

❌ 不改 hash()（BCrypt 生成逻辑不变）
❌ 不改 3 处 login 的调用形态（仍调 matches）
❌ 不改 9 个写入路径
❌ 不改 DB / schema / 前端 / 拦截器
❌ 不改 PasswordMigrator（迁移已完成，工具保留但不再使用）
```

---

## ② 当前实现（精确位置）

```java
// PasswordHasher.java L77-110
public static boolean matches(String rawPassword, String storedPassword) {
    // ① 空值 fail-closed（L79-84）              ← 保留
    // ② BCrypt 家族 → 结构校验 → checkpw（L87-101）← 保留
    // ③ 历史明文兜底（L103-109）                ← 🎯 本批删除
    boolean matched = storedPassword.equals(rawPassword);
    if (matched) {
        logger.warn("[SEC][PLAINTEXT-PASSWORD-HIT] ...");
    }
    return matched;
}
```

**当前语义**：非 `$2` 开头的存储值**允许**明文等值登录。

---

## ③ 变更设计

### 3.1 改动后的 `matches()`

```java
public static boolean matches(String rawPassword, String storedPassword) {
    // ① 空值 fail-closed（不变）
    if (rawPassword == null || rawPassword.isEmpty())       return false;
    if (storedPassword == null || storedPassword.isEmpty()) return false;

    // ② BCrypt 家族 → 结构校验 → checkpw（不变）
    if (isBcryptFamily(storedPassword)) {
        // ...（结构非法 / 解析异常 / checkpw）
    }

    // ③ 🎯 非 BCrypt 存储值：一律拒绝（明文兜底已删除）
    logger.warn("[SEC][NON-BCRYPT-STORED] 存储值不是合法 BCrypt，拒绝比对。storedLen={} prefix4={}",
            storedPassword.length(), safePrefix(storedPassword));
    return false;
}
```

| 项 | 变化 |
| --- | --- |
| 删除 | `storedPassword.equals(rawPassword)` 及其返回 |
| 删除 | `PLAINTEXT-PASSWORD-HIT` 日志（语义已不适用）|
| 新增 | `NON-BCRYPT-STORED` WARN（**脏数据告警**，不再是"兼容命中"）|
| 保留 | 空值短路、`$2` 家族判定、结构校验、`checkpw`、异常捕获 |

### 3.2 语义对照

| `storedPassword` 形态 | F-4-2（当前）| **F-4-3（本批）** |
| --- | :-: | :-: |
| 合法 BCrypt（cost 任意，形状匹配）| `checkpw` | `checkpw`（不变）|
| `$2` 家族但结构非法 | `false` + WARN | `false` + WARN（不变）|
| **普通明文 `123456`** | **`equals` → 可能 true** | 🔴 **`false` + WARN** |
| **`$password123`**（`$` 开头但非 `$2`）| **`equals` → 可能 true** | 🔴 **`false` + WARN** |
| **`$1$abc`**（MD5-crypt 等）| **`equals` → 可能 true** | 🔴 **`false` + WARN** |
| `null` / `""` | `false` | `false`（不变）|

> **核心价值**：即便未来因任何原因（误操作/回滚/新写入路径遗漏）数据库里出现明文密码，
> **该账号也无法登录** —— 从「兼容」变为「故障可见」而不是「静默弱化」。

---

## ④ 调用面影响（3 处，形态不变）

| 文件 | 行 | 现状 | F-4-3 后 |
| --- | :-: | --- | --- |
| `StudentController` | 324 | `PasswordHasher.matches(password, yonghu.getPassword())` | **同一行不变** |
| `CounselorController` | 304 | 同上 | 不变 |
| `AdminUserController` | 52 | 同上 | 不变 |

**业务代码 0 改动** —— 行为变化完全由 `matches()` 内部语义决定。

---

## ⑤ 测试改动

### 5.1 必须反转的 2 处（`PasswordHasherTest`）

| 行 | 用例 | 现断言 | F-4-3 后 |
| :-: | --- | --- | --- |
| 103 | 用例 10：`$` 开头历史明文 | `matches("$password123","$password123")` → **true** | 🔴 **false** |
| 136 | 用例 13：`$1$abc` 非 BCrypt 家族 | `matches("$1$abc","$1$abc")` → **true** | 🔴 **false** |

> 用例 10 的名称与注释需同步改写：从「**护栏：$ 开头历史明文仍可登录**」
> 改为「**护栏：非 BCrypt 存储值一律拒绝（含 $ 开头明文）**」。

### 5.2 必须新增的用例

| # | 用例 | 期望 | 意义 |
| :-: | --- | :-: | --- |
| **NEW-1** | `matches("123456", "123456")` | 🔴 **false** | **明文存储值不得登录**（本批核心）|
| NEW-2 | `matches("admin", "admin")` | 🔴 **false** | 同上（管理员短口令）|
| NEW-3 | `matches("123456", "12345")` | `false` | 明文不匹配（原本也 false，作对照）|

### 5.3 不需要改（已验证不受影响）

| 位置 | 原因 |
| --- | --- |
| `PasswordHasherTest` 用例 1–9、11、12 | 只涉及 BCrypt / 空值 / 非法 hash，语义未变 |
| `IdentityAuthorizationRegressionTest` 用例 9（L236-250）| DB 全 hash ⇒ `matches` 走 `checkpw`，明文分支未被触及 |
| `Phase2CoreRegressionTest` | 不直接断言密码存储形态 |

### 5.4 项数变化

```text
当前：60/60（30 + 17 + 13）
F-4-3 后：60/60 + NEW-1/2/3 = 63 项（PasswordHasherTest 13 → 16）
```

> 若新增用例合并进现有方法，则仍为 61 项 —— **具体口径待批准时确定**。

---

## ⑥ 验收矩阵

### 6.1 单元测试（静态证据）

| # | 项 | 期望 |
| :-: | --- | --- |
| 1 | 合法 BCrypt 正确/错误密码 | `true` / `false` |
| 2 | `$2` 家族结构非法 | `false` |
| 3 | **明文存储值（3 组）** | **全 `false`** |
| 4 | `$password123` / `$1$abc` | **全 `false`** |
| 5 | 空值 4 组 | 全 `false` |

### 6.2 运行时负向验证（**本批关键证据**）

```text
① 创建一个临时探针账号（正常注册 → password 为 BCrypt）
② 手动把该账号 password 改为明文（模拟"脏数据/回退"）
③ 用该明文登录 → 必须失败（code≠0）
④ 恢复/删除探针账号
⑤ 核对基线 3/3/1 · 全 $2a$ · plain=0
```

> **这是 F-4-3 与 F-4-2 的本质区别**：F-4-2 证明「明文能迁移」，
> F-4-3 必须证明「明文已不能登录」。

### 6.3 正向回归

| # | 项 | 期望 |
| :-: | --- | --- |
| 1 | 7/7 真实账号登录 | 全部成功（DB 全 hash）|
| 2 | 回归 | 63/63（或 61/61，视口径）|
| 3 | 数据基线 | 3/3/1 · 全 `$2a$` · plain=0 |
| 4 | 探针清理 | 残留 0 |

---

## ⑦ 风险与回滚

| 风险 | 级别 | 评估 |
| --- | :-: | --- |
| 仍有明文账号未迁移 ⇒ 该账号无法登录 | 🟢 | **已实测 plain=0**，无此类账号 |
| 老前端/Admin 受影响 | 🟢 | 均走 `login → matches → BCrypt`，DB 全 hash |
| 未来新写入路径漏掉 `hash()` ⇒ 该账号登不进 | 🟡 | **这正是期望行为**（fail-closed 暴露问题，而非静默弱化）|
| 回滚能力 | 🟢 | **`git revert` 即可**（数据已全 hash，revert 后兼容 BCrypt + 明文，7/7 仍可登录）|

> **与 F-4-2 的关键差异**：F-4-2 回滚需「备份 + 旧代码」双动作；
> **F-4-3 是纯代码变更，单 `git revert` 即可回滚**（因为不涉及数据）。

---

## ⑧ 文件清单

| # | 文件 | 改动 | 规模 |
| :-: | --- | --- | :-: |
| 1 | `src/main/java/com/security/PasswordHasher.java` | 删 ③ 分支 + 改日志 + 更新类注释/JavaDoc | **−6 / +4** |
| 2 | `src/test/java/com/security/PasswordHasherTest.java` | 2 处反转 + 新增 2–3 例 | **+20 / −6** |
| — | 业务代码 / DB / 前端 / 迁移工具 | **0 改动** | — |

---

## ⑨ 待裁定（4 项）

| # | 问题 | 建议 |
| :-: | --- | --- |
| **1** | 非 BCrypt 的 WARN 语义 | 改名 `[SEC][NON-BCRYPT-STORED]`（脏数据告警），**不再叫** `PLAINTEXT-PASSWORD-HIT` |
| **2** | 是否**同时**给 `PasswordHasher.BCRYPT_SHAPE` 加 cost 4–31 校验（与迁移工具一致）| **建议加** —— 目前 `$2a$99$...` 在 `PasswordHasher` 里仍会进 `checkpw`（返回 false，行为正确但多一次无效计算）；加上后与工具口径统一。**但需你确认是否属于本批范围** |
| **3** | 新增测试数量口径 | 合并进现有方法（**61 项**）vs 独立新增（**63 项**）—— 建议**独立新增**，用例名更清晰 |
| **4** | 是否保留 `PasswordMigrator` | **保留**（已审计、有回滚价值；生产同类迁移可复用），但标注「本环境已完成，不再执行」|

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-23 | F-4-3 设计：删除 `matches()` 第 ③ 段明文兜底 → **非 BCrypt 一律 `false` + `NON-BCRYPT-STORED` WARN**；3 处 login 调用形态不变（业务代码 0 改动）；测试影响 **2 处反转 + 2–3 例新增**（`matches("123456","123456")` 必须 false）；新增**运行时负向验证**（手动写明文 → 登录必须失败）；回滚为**纯代码 revert**（数据已全 hash，可安全回滚）；4 项待裁定（WARN 命名 / 是否顺带加 cost 校验 / 测试口径 / 迁移工具去留）；**零代码改动** |
