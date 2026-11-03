# S2-F-4-3 · 删除明文 fallback 实现报告

> **性质**：实现（✅ **已实现 · 已 commit · 已 push（`d30cceb`）**）
> **上游**：`S2-F-3` 设计冻结 · `S2-F-4-1` 门面 + 双格式 · `S2-F-4-2` 数据迁移 CLOSED
> **基线**：`HEAD = origin = aa98363` · LEAD 0 · 开工前「未暂存 / 未跟踪 = 0」· DB **7/7 BCrypt · plain=0**
> **当前 Git 状态（措辞精确化）**：
> ```
> HEAD / origin          : aa98363 / aa98363   LEAD=0
> 未暂存修改              : 0
> 未跟踪文件              : 0
> 已暂存（index）         : 2 files, +66 / −29
> commit                  : ❌ 未提交
> ⇒ repository 非 clean（index 有变更），故不写「工作区 CLEAN」
> ```
> **本批定位**：F-4 三批次的**最后一批** —— 彻底切断「明文也能登录」这条路径
> **下一批**：**Phase 4 / Step 11 Spring AI 最小闭环**（用户 2026-10-23 裁定，提前于 Step 8）

---

## 一、改动范围（严格 4 项，无扩散）

| # | 项 | 落实 |
| :-: | --- | :-: |
| ① | 删除 plaintext fallback | ✅ `storedPassword.equals(rawPassword)` 整段移除 |
| ② | 非 BCrypt → `false` + `[SEC][NON-BCRYPT-STORED]` | ✅ 新 WARN 标签替换旧 `PLAINTEXT-PASSWORD-HIT` |
| ③ | `BCRYPT_SHAPE` 增加 **cost 04–31** 校验 | ✅ 正则 `(?:0[4-9]|[12]\d|3[01])`，与 `PasswordMigrator` 口径一致 |
| ④ | 正常 BCrypt `matches` 行为不变 | ✅ 用例 8/9/12/16 全绿，7/7 实登通过 |

**未改动**：`hash()` · 调用方（3 login + 9 写入）· 数据库 · DDL · 前端 · Interceptor · Session · `PasswordMigrator`

---

## 二、代码 diff（2 文件 · +66 / −29）

### 2.1 `PasswordHasher.java`（+38 / −?）

```diff
-        // ③ 历史明文（兼容窗口，F-4-3 删除）
-        boolean matched = storedPassword.equals(rawPassword);
-        if (matched) {
-            logger.warn("[SEC][PLAINTEXT-PASSWORD-HIT] ...", storedPassword.length());
-        }
-        return matched;
+        // ③ 非 BCrypt 家族 ⇒ 一律拒绝（F-4-3 已切断明文 fallback）
+        //    ⚠️ 日志只记长度，不记任何内容片段 —— 存储值可能就是明文密码本身。
+        logger.warn("[SEC][NON-BCRYPT-STORED] 存储值不是合法 BCrypt，拒绝比对（该账号需重置密码）。storedLen={}",
+                storedPassword.length());
+        return false;
```

```diff
 private static final Pattern BCRYPT_SHAPE =
-        Pattern.compile("^\\$2[abxy]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
+        Pattern.compile("^\\$2[abxy]?\\$(?:0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}$");
```

> **日志安全性收紧**（对设计的一处细化）：
> 旧 `BCRYPT-MALFORMED` 记 `storedLen + prefix4` 是安全的（BCrypt 前缀无敏感信息）；
> 但**非 BCrypt 存储值可能是明文密码本身** ⇒ 新分支**只记 `storedLen`**，不记任何片段。

### 2.2 `PasswordHasherTest.java`（+57 / −?）

| 变更 | 说明 |
| --- | --- |
| 用例 10 **反转** | `matches("$password123","$password123")` → `true` **改为 `false`**（fallback 已删）|
| 用例 13 **反转** | `matches("$1$abc","$1$abc")` → `true` **改为 `false`** |
| **用例 14 新增** | 普通明文完全等值 → 必须 `false`（F-4-3 核心护栏）|
| **用例 15 新增** | cost `99` / `03` / `32` 越界 → `false`（不做无意义 BCrypt 计算）|
| **用例 16 新增** | cost `04` / `31` 边界内结构通过 → 交 `checkpw`，不抛异常；真实 hash 仍 `true` |

---

## 三、测试：**63/63**

```text
30/30 IdentityAuthorizationRegressionTest
17/17 Phase2CoreRegressionTest
16/16 PasswordHasherTest（原 13 + 新 3；其中 2 例语义反转）
63/63 BUILD SUCCESS
```

> `IdentityAuthorizationRegressionTest` **无需改动** —— 它调用 `PasswordHasher.matches`，
> 而 DB 已全量 BCrypt ⇒ 语义不受影响（这正是 F-4-1 引入门面的收益）。

### 3.1 ✅ 复核项确认：cost=31 测试**未**执行高成本 BCrypt 计算

用户指出「若真跑 cost=31，2^31 轮会让测试套件不可接受」—— 已实测确认**不存在该问题**：

| cost | `checkpw` 实测耗时 | 内部轮数 | 说明 |
| :-: | :-: | --- | --- |
| 4 | 4 ms | `1<<4 = 16` | 正常 |
| 10 | **83 ms** | `1<<10 = 1024` | 正常（线上 cost）|
| **31** | **0 ms** | `1<<31 = -2147483648` | 🔴 **int 溢出为负数 ⇒ 内层循环不执行** |

**根因**：jBCrypt 实现为 `rounds = 1 << log_rounds`，Java `int` 左移 31 位得到 `Integer.MIN_VALUE`，
`for (i = 0; i < rounds; i++)` 条件立即为假 ⇒ **一次 BCrypt 运算都不做**，直接返回 `false`。

**因此**：

```text
✅ 测试 16 不会给套件埋下慢用例（16 例整体 0.9–1.1s）
✅ cost=31 不会误放行（结果恒 false）
⚠️ 但 cost=31 在本实现下不是「可用但慢」，而是「空转返回 false」
   ⇒ 已作为已知边界写入测试注释（PasswordHasherTest 用例 16）
   ⇒ 与 PasswordMigrator 的 4–31 口径仍保持一致（Migrator 只做静态分型，不跑 checkpw）
```

**口径等价性复核**：

| 位置 | 写法 | 语义 |
| --- | --- | --- |
| `PasswordMigrator` | `(\d{2})` 捕获 + `cost < 4 \|\| cost > 31` | 接受 **04–31** |
| `PasswordHasher` | `(?:0[4-9]\|[12]\d\|3[01])` | 接受 **04–31** |
| 结论 | — | ✅ **完全等价**（Migrator 的 `Integer.parseInt("04")` = 4）|

---

## 四、部署

| 项 | 旧 | 新 |
| --- | --- | --- |
| PID | `9572`（10:30:05）| **`4744`**（11:10:31）✓ |
| jar | — | **11:10:30** ✓ |
| 8080 | — | 就绪（6s）✓ |

---

## 五、正向验证：**7/7 通过**

| 账号 | 存储 | 登录 |
| --- | --- | :-: |
| student a1 / a2 / a3 | `$2a$` | ✅ ✅ ✅ |
| counselor a1 / a2 / a3 | `$2a$` | ✅ ✅ ✅ |
| admin | `$2a$` | ✅ |

错误密码 2/2 一致拒绝 ⇒ 正常 BCrypt 路径**未被破坏**。

---

## 六、🔴 运行时负向验证（本批最关键证据）

### 6.1 场景 A：迁移遗漏的明文脏数据

```text
① admin 创建探针账号 → DB: $2a$ / 60 字符（写路径已 hash）
② UPDATE password='123456'            ← 人为制造明文
③ 用【正确密码 123456】登录
   → 🔴 拒绝 code=500（无 token）
   → 日志 WARN [SEC][NON-BCRYPT-STORED] storedLen=6
④ 删除探针 → 残留 0
```

### 6.2 场景 B：同账号三态对照（排除「账号本身有问题」的解释）

| 阶段 | DB 存储 | 登录结果 |
| :-: | --- | --- |
| **T1** | `$2a$...`（60）| ✅ **PASS** |
| **T2** | `123456`（6）| 🔴 **REJECT code=500** |
| **T3** | 恢复原 `$2a$`（60）| ✅ **PASS** |

> **结论**：拒绝的**唯一变量是存储格式**，不是账号、不是密码值、不是服务状态。
> ⇒ F-4-3 的 fallback 切断在**运行时**被证实，而非仅靠静态代码检查。

---

## 七、日志证据

| 指标 | 值 | 判定 |
| --- | :-: | --- |
| `[SEC][NON-BCRYPT-STORED]` | **1**（`storedLen=6`）| ✅ 精确对应负向探针 |
| `[SEC][PLAINTEXT-PASSWORD-HIT]` | **0** | ✅ 旧标签已彻底消失 |
| `[SEC][BCRYPT-MALFORMED]` | 0 | ✅ |
| `[SEC][BCRYPT-PARSE-FAIL]` | 0 | ✅ |
| `NullPointerException` | **0** | ✅ 无 500 堆栈 |
| `IllegalArgumentException` | **0** | ✅ |

> WARN 属**安全观测证据**，不作测试断言（沿用 F-4-1 口径）。

---

## 八、DB 终态（未改动）

```text
student      total=3  bcrypt=3  plain=0
counselor    total=3  bcrypt=3  plain=0
admin_user   total=1  bcrypt=1  plain=0
探针残留 = 0     基线 = 3 / 3 / 1 ✓
```

`PasswordMigrator` **保留未执行**（按裁定：本环境已完成迁移，不再重复运行）。

---

## 九、安全链路终态

```text
发现明文密码存储（SEC-PLAINTEXT-PASSWORD-STORAGE）
        ↓
PasswordHasher 门面（F-4-1）
        ↓
双格式兼容窗口（F-4-1 观察期）
        ↓
PasswordMigrator 批量迁移（F-4-2，14/14 AC PASS）
        ↓
运行时验证 9 路径全 hash
        ↓
确认 0 plaintext（三表 null=0 / empty=0 / plain=0）
        ↓
删除 plaintext fallback（F-4-3 ← 本批）
        ↓
⚠️ 密码体系现在【只接受 BCrypt】
        ↓
SEC-PLAINTEXT-PASSWORD-STORAGE → 可 CLOSED ✅
```

---

## 十、风险与残留

| 项 | 说明 |
| --- | --- |
| **历史明文账号无法登录** | 设计预期行为。若生产存在未迁移脏数据，该账号需**重置密码**（`resetPassword` 会写 BCrypt）。`[SEC][NON-BCRYPT-STORED]` 即定位手段 |
| **旧版本代码回滚** | 不可单独回滚（F-3 §9）：DB 已全量 BCrypt，回退旧代码必须同时恢复迁移前备份 |
| `SEC-ADMIN-DEFAULT-CREDENTIAL` | **仍 OPEN**，独立治理（F-4-3 不涉及）|
| R15 / Step 10 / 剩余 P1-P2 | 挂账，不阻塞主线 |

---

## 十一、Commit message 建议

```text
feat(security/s2-f-4-3): 删除明文密码 fallback，密码体系只接受 BCrypt

- PasswordHasher.matches 移除 equals 明文分支，非 BCrypt 一律 false
- 非 BCrypt 存储值记录 [SEC][NON-BCRYPT-STORED]（只记长度，不泄露内容）
- BCRYPT_SHAPE 增加 cost 04-31 校验，与 PasswordMigrator 口径一致
- 测试：用例 10/13 语义反转，新增 14/15/16；63/63 全绿
- 运行时负向验证：同账号 T1 hash→PASS / T2 明文→REJECT / T3 恢复→PASS
- 7/7 原密码登录正常；探针清理回基线 3/3/1；DB 仍 7/7 BCrypt
```

---

## 十二、状态

| 项 | 值 |
|---|---|
| 文件 | **2**（`PasswordHasher.java` · `PasswordHasherTest.java`）|
| 变更量 | **+72 / −29**（含用例 16 的 cost=31 实测注释）|
| 测试 | **63/63** |
| Git 状态 | HEAD=origin=`aa98363` · LEAD 0 · **未暂存/未跟踪 = 0** · **已暂存 2 files** · **未 commit** ⇒ **repository 非 clean** |
| DB | 7/7 BCrypt · **plain=0** · 探针残留 0 · 基线 3/3/1 |
| `SEC-PLAINTEXT-PASSWORD-STORAGE` | 🟢 **已 CLOSED**（用户 2026-10-23 验收通过）|
| F-4-2 迁移工具 | 保留，**未重跑** |
| 下一批 | **Phase 4 / Step 11 Spring AI 最小闭环**（先出设计再实现）|

---

## 十三、复核意见落实（用户 2026-10-23）

| # | 复核意见 | 落实 |
| :-: | --- | --- |
| ① | 「工作区干净」措辞不准确（同时存在 staged 变更）| ✅ 本文档全部改为「未暂存/未跟踪 = 0 · 已暂存 2 files · 未 commit ⇒ repository 非 clean」|
| ② | 确认 cost=31 测试未真的跑高成本 BCrypt | ✅ **实测确认**（§3.1）：`1<<31` 溢出为负 ⇒ 0ms、循环不执行；已在用例 16 写入注释 |
| ③ | `[SEC][NON-BCRYPT-STORED]` 只记长度 | ✅ 保留，实测 `storedLen=6` 未泄露 `123456` |
| ④ | 登录失败 `code=500` 语义债 | ✅ **挂账** `SEC/AUTH-LOGIN-ERROR-STATUS`，**本批不修**（防扩散）|
| ⑤ | 不得顺手重构密码体系 | ✅ 变更严格限于 4 项，未扩散 |
