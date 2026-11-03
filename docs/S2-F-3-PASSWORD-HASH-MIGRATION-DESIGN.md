# S2-F-3 · 密码存储迁移设计（BCrypt）

> **性质**：设计 **v3**（✅ **已批准 · 设计冻结** —— v2 六处必改 + v3 三项微调均已落实 · **F-4 尚未开始** · 零代码 · 零 DB · 零 commit）
> **基线**：`HEAD = origin = 41c2b7b` · worktree CLEAN
> **日期**：2026-10-22
> **对象**：`SEC-PLAINTEXT-PASSWORD-STORAGE`（🔴 P1 · **仍 OPEN**）
> **后续**：F-4 实现（**本批不得开始**）

---

## ① 现状与目标状态

### 1.1 现状（实测确认）

| 维度 | 事实 |
| --- | --- |
| 数据 | `student` 3 · `counselor` 3 · `admin_user` 1（共 **7 条**），**全部明文** |
| 列宽 | `admin_user varchar(100)` · `counselor/student varchar(200)` |
| 登录 | 3 处 `storedPassword.equals(input)`（L321 / L301 / L50）|
| 写入 | 7 个可写路径（见 §3）|
| 哈希库 | **项目无 spring-security-crypto**，但 **Hutool 5.8.25 内置 BCrypt**（已用 `javap` 确认 API）|
| 测试依赖 | 2 处**直接断言明文 `"123456"`**（见 §11.1）|

### 1.2 Hutool BCrypt API（已确认，零新增依赖）

```java
cn.hutool.crypto.digest.BCrypt
    public static String  hashpw(String password)                       // 自动 gensalt（默认 logRounds=10）
    public static String  hashpw(String password, String salt)
    public static String  gensalt()
    public static String  gensalt(int logRounds)
    public static boolean checkpw(String plaintext, String hashed)
```

### 1.3 目标状态

```text
三表 password 列：只存 $2a$10$...（60 字符）
所有写入路径：BCrypt.hashpw(raw)
所有登录校验：BCrypt.checkpw(raw, stored)
迁移完成后：SELECT ... WHERE password NOT LIKE '$2%' → 0 行
⇒ SEC-PLAINTEXT-PASSWORD-STORAGE 才能 CLOSED
```

---

## ② BCrypt 参数选择

| 参数 | 取值 | 理由 |
| --- | --- | --- |
| 算法 | **BCrypt** | 项目已有 Hutool，**零新增依赖**；迁移成本最低；工业标准 |
| `logRounds`（cost）| **候选 10**（2^10 = 1024 轮）| Hutool/jBCrypt 默认；OWASP 建议 ≥10。**实际耗时必须 benchmark 确定（§2.1），不得凭算法名推断** |
| 盐 | 每行**独立随机盐**（`hashpw` 自动生成）| 防彩虹表；同密码不同 hash |
| 输出长度 | **60 字符** | < 列宽 100 ✓（含 `admin_user varchar(100)`）|
| 字符集 | `$ a–z A–Z 0–9 . /` | `varchar` 安全，无需转义/Base64 |

**为何不选 12（初版理由，已修正）**：本项目 7 条数据、单机部署；cost=10 满足安全基线。
**登记为可选增强**：若未来面向公网高并发，可提升至 12（需重哈希全量，独立批次）。

**为何不选 Argon2**：Hutool 无内置；需新增依赖（BouncyCastle 等）；迁移复杂度高，收益对本项目有限。

### 2.1 ⚠️ 耗时与 cost 必须 benchmark（**不得写死**）

`cost=10 约 60–100ms` 属**未经测量的推断**，不可作为事实写入设计。BCrypt 实际耗时高度依赖 CPU / JVM / JDK / 负载 / 并发 / 部署形态。

**F-4 实施前必做的 benchmark**：

```text
在目标（或代表性）环境执行 N 次（建议 N ≥ 100）BCrypt.hashpw
记录 p50 / p95 / p99 与吞吐（hash/s）
分别评估：
  ① 单次注册/登录的延迟可接受性
  ② 批量迁移的批大小与总耗时
  ③ 并发登录时的 CPU 压力
```

**产出**：`cost` 最终取值 + 批大小（§12）均以该 benchmark 为准；本设计只固定「候选值 = 10」。

#### 2.1.1 阈值来源（**必须先定阈值，再测**）

仅有 p50/p95/p99 **不足以决策** —— 没有阈值，最终仍会退化为「测完凭感觉选 cost」。因此 F-4-1 开始前必须先书面确定阈值，并记录来源：

| 阈值项 | 取值来源（按优先级） |
| --- | :-: |
| 单次 `hashpw` 可接受上限（p95）| ① 登录接口**既有性能目标/SLO** → ② 与本系统其他重操作（如导出、批量查询）的既有实测对比 → ③ **本批自定工程阈值 + 理由** |
| 批量迁移可接受总时长 | ① 运维窗口约束（是否允许在线迁移）→ ② 低峰期时长 → ③ 可中断性（幂等重跑）折算 |
| 并发登录 CPU 余量 | ① 现有并发登录量（可按 7 条账号规模估算）→ ② 目标环境核数/负载水位 |

**硬要求**：

```text
若项目无既有 SLO：
⇒ 必须在本设计文档中显式记录「本批采用的工程阈值 + 选择理由 + 决策人」
⇒ 阈值一经确定，不得在 benchmark 结果出来后事后调整（防"倒推结论"）
```

> 若 benchmark 显示 p95 超出**已确定阈值**，应下调至 9 或改用更快的 KDF 方案（需重新评估），
> **而不是**在没有数据的情况下断言「大约 60–100ms」，也**不是**放宽阈值来迁就结果。

---

## ③ 新密码写入策略（**9 个密码写入端点**）

| # | 路径 | 当前写入值 | F-4 后 |
| :-: | --- | --- | --- |
| 1 | `student/register` | 用户输入（F-2-1 起）| `hashpw(input)` |
| 2 | `counselor/register` | 用户输入（F-2-1 起）| `hashpw(input)` |
| 3 | `student/save` | `"123456"` | `hashpw("123456")` |
| 4 | `student/add` | `"123456"` | `hashpw("123456")` |
| 5 | `counselor/save` | `"123456"` | `hashpw("123456")` |
| 6 | `counselor/add` | `"123456"` | `hashpw("123456")` |
| 7 | `adminUser/save` | `"123456"` | `hashpw("123456")` |
| 8 | `student/resetPassword`（admin）| `"123456"` | `hashpw("123456")` |
| 9 | `counselor/resetPassword`（admin）| `"123456"` | `hashpw("123456")` |
| — | `resetPass` ×2 | **已下线 404** | — |
| — | `*/update` | **不可绑定 password** | **保持不可绑定**（改密属 P2 独立）|

> 建议在 F-4 中**引入统一入口**（如 `PasswordHasher.hash/verify` 静态门面），避免 9 处各自调用 Hutool；
> 但**不引入 Spring Security 全栈**。

---

## ④ 老明文账户迁移策略（核心决策）

### 4.1 候选

| 方案 | 形态 | 评价 |
| --- | --- | :-: |
| A 一次性批量 | Java 脚本遍历 7 行 → hash → 更新 | ✅ 覆盖"从未登录"账户 |
| B 登录时渐进 | 明文校验成功后升级 | ❌ **未登录账户永久停留明文** |
| **C（推荐）** | **A 批量迁移 + 短期双格式兜底** | ✅ **兼顾覆盖与安全网** |

### 4.2 推荐：**兼容代码先行 + 全量批量迁移 + 短期双格式兼容**

> ⚠️ **顺序不可颠倒**：**必须先部署「双格式兼容代码」，再迁移数据**。
> 若先迁移数据再部署新代码，旧代码无法识别 `$2a$...` ⇒ 全量账号登录失败（详见 §9）。

```text
① 备份
   mysqldump 三表 → 归档（含校验和）
        ↓
② 部署「双格式兼容」代码（F-4-1）
   hash → checkpw；plain → equals
   验证：现有明文账号仍可登录
        ↓
③ 停止产生新的明文密码
   所有写入路径改为 hash（register / save / add / resetPassword）
        ↓
④ 批量迁移历史明文（F-4-2）
   WHERE password IS NOT NULL AND password <> '' AND password NOT LIKE '$2%'
   逐行 hashpw → 更新（幂等）
        ↓
⑤ 验证：DB 明文=0 / NULL=0 / empty=0 / 7/7 登录 / 47/47
        ↓
⑥ 观察期（≥1 批次）确认无明文回潮
        ↓
⑦ 回滚演练（从备份恢复 → 再迁移一次）
        ↓
⑧ 删除明文 fallback（**独立批次 F-4-3**，不随 F-4 一起做）
```

**为什么必须有 ⑥**：若迁移期间有遗漏/并发新增明文行，兜底分支可避免"用户突然登录失败"。

**为什么不能纯 B（登录时渐进）**：7 条账号中可能有人长期不登录；依赖登录触发会导致明文长期残留。

### 4.3 🔴 `NOT LIKE '$2%'` 的 NULL / empty / 异常数据治理

初版条件 `WHERE password NOT LIKE '$2%'` **不足以用于生产**：

```sql
-- ❌ 危险：NULL 参与 NOT LIKE 时为 UNKNOWN，会被静默排除
WHERE password NOT LIKE '$2%'
```

| 数据形态 | 初版条件行为 | 正确处置 |
| --- | --- | --- |
| 正常明文 | 命中 → 迁移 ✓ | ✅ migrate |
| **`NULL`** | **静默排除**（`NULL NOT LIKE ...` = UNKNOWN）| 🔴 **迁移错误/单独修复**（`hashpw(null)` 会 NPE/异常）|
| **空串 `''`** | 命中 → `hashpw("")` 生成**空密码 hash** | 🔴 **迁移错误/单独修复**（等于制造弱口令）|
| 已 bcrypt | 排除 ✓ | ✅ skip |
| 形似 hash 但非法（如 `$2a$xx`）| 排除（可能漏迁移）| 🟡 需单独校验形态 |

**生产级迁移条件（推荐）**：

```sql
-- 迁移候选：仅「非 NULL、非空、非 bcrypt」
WHERE password IS NOT NULL AND password <> '' AND password NOT LIKE '$2%'
```

### 4.3.1 🚧 F-4-2 **migration preflight gate**（硬门禁，不可跳过）

```text
迁移开始
   ↓
Preflight（只读统计，不改数据）
   ├─ NULL       > 0  → ABORT（不进入正式迁移）
   ├─ empty ''   > 0  → ABORT
   ├─ malformed bcrypt > 0 → ABORT
   └─ 全部 = 0
        ↓
   正式迁移
```

**规定**：

| 规则 | 内容 |
| --- | --- |
| 门禁位置 | **正式迁移之前**，独立只读步骤 |
| 触发条件 | 任一异常计数 **> 0** ⇒ **F-4-2 不得进入正式迁移** |
| 禁止行为 | ❌ 用 `WHERE IS NOT NULL AND <> ''` 过滤后「只迁剩下的」，最后因异常行被过滤而宣布「明文=0 完成」 |
| 处置 | 异常数据须先单独治理（或显式记录为已知遗留），再重新过 gate |
| 留痕 | gate 输出（三表计数）必须归档进 F-4-2 报告 |

> 关键区别：**过滤 = 掩盖**；**gate = 阻塞**。前者会让 NULL/empty 永远看不见，后者强制在迁移前解决。

**当前开发库状态**：`NULL=0` / `empty=0`（已实测）⇒ 本环境可通过 gate；生产环境必须重新执行 gate。

---

## ⑤ 从未登录账户处理

- **由 §4 的批量迁移直接覆盖**（不依赖登录）✓
- 迁移后全部 7 行均为 hash，**无需用户参与**，**不需要"首次登录改密"**
- 明确声明：**不采用"首次登录强制改密"作为迁移手段**（那是默认口令治理，见 §8）

---

## ⑥ 登录兼容窗口

### 6.1 双格式识别（推荐实现形态）

```java
boolean ok;
if (stored != null && isBcryptHash(stored)) {
    ok = BCrypt.checkpw(input, stored);
} else {
    // 兼容窗口：仅用于迁移遗漏的兜底
    ok = stored != null && stored.equals(input);
    if (ok) {
        // 仅记录 WARN 日志（不静默升级，避免掩盖未迁移行）
    }
}
```

### 6.1.1 ⚠️ `$2a$` / `$2b$` / `$2y$` 前缀支持必须**实测**

不能凭「BCrypt 标准都支持」推断 Hutool 的兼容性：

- `$2a$` —— 最常见（jBCrypt / Hutool 默认输出）
- `$2b$` —— 部分实现修正版
- `$2y$` —— **PHP 生态变体**，Java 库支持程度不一

**F-4-1 前必须用真实样本验证**：

```text
① 用 Hutool hashpw 生成样本 → 观察实际前缀（预期 $2a$）
② 构造 $2b$ / $2y$ 样本 → 调 Hutool checkpw → 记录是否支持
③ 若不支持，则 isBcryptHash 只接受实际支持的前缀，
   并把「跨实现 hash 兼容」列为已知限制（登记）
```

**不得**在 `isBcryptHash()` 中盲目接受三种前缀而不验证 —— 否则会把「不支持的 hash」误判为合法，导致该账号**永久无法登录**。

### 6.2 窗口期策略

| 阶段 | 行为 |
| --- | --- |
| F-4 上线 | 双格式并存（hash 优先）|
| 观察期（≥1 批次）| 每次明文命中打 WARN；监控应为 0 |
| 观察期结束 | **删除明文分支**（独立小批，登记）|
| **代码回滚窗口** | **F-4 不得在同一提交删除明文分支** —— 见 §9 |

### 6.3 不做"登录时静默升级"

理由：会掩盖"仍有明文行"的事实，使迁移完成判定失真。
若未来需要，可作为"过渡期加速"手段，但必须配套告警。

---

## ⑦ resetPassword / 注册 / 管理员重置边界

| 场景 | 边界 |
| --- | --- |
| **注册**（F-2-1 DTO）| 保留 DTO；仅把 `setPassword(req.getPassword())` 改为 `setPassword(hashpw(...))` |
| **管理员创建**（save/add）| 仍设 `123456`，但存 `hashpw("123456")` |
| **管理员重置**（resetPassword）| 同上（admin-only 已就位，不改授权）|
| **update** | **仍不可绑定 password**（`@JsonIgnore` 未改）→ 改密功能仍属 `DEF-NO-SELF-SERVICE-PASSWORD-CHANGE`（P2 独立）|
| **resetPass** | 已下线（`aaf9bc0`），不复活 |

---

## ⑧ 默认口令独立治理（不得混淆）

```text
SEC-PLAINTEXT-PASSWORD-STORAGE   ← F-4 解决
    数据库不再保存可直接使用的明文

SEC-ADMIN-DEFAULT-CREDENTIAL     ← 独立，不在 F-4
    系统是否仍存在默认/已知凭据
```

**关键事实**：`hashpw("123456")` 仍是**弱密码**，只是存储方式安全了。

**因此 F-4 完成后**：
- ✅ 可以关闭 `SEC-PLAINTEXT-PASSWORD-STORAGE`
- ❌ **不能**关闭 `SEC-ADMIN-DEFAULT-CREDENTIAL`
- 默认凭据治理（部署时强制设初始密码 / 首登改密）**另行立项**

---

## ⑨ 失败与回滚（**阶段化模型**，非"随时可回滚"）

> 🔴 **核心修正（v2）**：初版称「代码回滚 ✅ 可行」是**不成立的**。
> 仅在**新代码里**保留明文 fallback，**并不能让旧版本代码识别 hash 数据** ——
> 旧代码执行 `"$2a$10$...".equals("123456")` 必然失败。
> **数据迁移完成后，不能单独回滚到不支持 BCrypt 的旧代码。**

### 9.1 三阶段与各自的回滚边界

| 阶段 | 代码 | 数据 | 可回滚性 |
| --- | --- | --- | --- |
| **A · 兼容代码上线** | 双格式（hash → checkpw；plain → equals）| 明文（+ 少量新 hash）| ⚠️ **有条件**：仅当「尚未产生 hash 账号」或「可恢复 DB 到兼容快照」时，才能退回旧代码 |
| **B · 批量迁移** | 同 A | **全量 hash** | ❌ **不可单独回滚代码**：旧代码无法识别 hash ⇒ 必须「恢复备份 + 部署旧代码」双动作 |
| **C · 删除 fallback**（独立批次）| 纯 hash | 全量 hash | ❌ 不可回滚到 A/B |

### 9.1.1 🔴 回滚失效的真实触发点（**不是 F-4-2，而是 F-4-1**）

> **F-4-1 一旦产生第一条 BCrypt 写入，旧版本（仅支持明文）的应用代码即刻失去直接回滚能力。**
> 此后若需回到旧版本，**必须同时恢复到兼容的数据库快照**。

```text
F-4-1 部署前
    │  数据全部明文
    └─→ 旧代码可回滚 ✅（无 hash 数据）

F-4-1 部署后
    │  新注册 / 管理员创建 / 重置 已产生 BCrypt
    └─→ 旧代码不能直接回滚 ❌
        旧代码：stored.equals(input)
                "$2a$10$xxxx".equals("123456") → false
        ⇒ 必须「恢复兼容 DB 快照 + 部署旧代码」双动作

F-4-2 批量迁移后
    │  全量 BCrypt
    └─→ 更不能旧代码直接回滚 ❌（影响面 7/7 而非个别新账号）
```

**因此 F-4-1 上线即进入「代码回滚需附带数据回滚」状态** —— 这是 F-4-1 本身的部署决策，而非 F-4-2 才出现的限制。

```text
迁移前：   旧代码 + 明文 DB
A 阶段：   新兼容代码 + 明文/hash 混合 DB
           → 未产生不可兼容数据前，可安全回退
B 阶段后： 新代码 + 全量 hash DB
           → ❌ 不允许 git revert 回旧登录代码
           → ✅ 唯一安全路径：恢复迁移前备份（DB）+ 部署旧代码
C 阶段后： 纯 hash 代码 + hash DB
           → 回退需同时恢复代码与备份
```

### 9.2 两种"回滚"必须彻底分开

| 类型 | 可行性 | 真实手段 |
| --- | :-: | --- |
| **应用代码回滚** | ⚠️ **受迁移阶段限制** | A 阶段可回退（条件见 9.1）；**B 完成后不可单独回滚** |
| **数据回滚** | ❌ **不可行** | BCrypt 单向：`hash → 明文` 不可能。**唯一手段 = 迁移前备份 + 从备份恢复** |
| **中途失败** | ✅ 可恢复 | 迁移脚本**幂等**（`IS NOT NULL AND <> '' AND NOT LIKE '$2%'`），逐行独立事务，可重跑 |

### 9.3 必须写进 F-4 实现的硬约束

```text
✅ 必须先部署「双格式兼容代码」，再迁移数据（顺序不可颠倒）
✅ 迁移前 mysqldump 备份三表 + 归档（含校验和）
✅ 迁移脚本幂等（已 hash 行跳过；NULL/空串单独报错）
✅ 保留明文比对分支（仅在 A/B 阶段；删除属独立批次 C）
❌ 不得声称"可以从 hash 恢复明文"
❌ 不得声称"数据迁移后 git revert 即可回滚"
❌ 不得用 ALTER TABLE 或任何 DDL 修改列宽/类型（当前 100/200 已足够）
```

---

## ⑩ 迁移完成判定（关闭 P1 的条件）

> **"三表非 BCrypt = 0" 只是必要条件，不是充分条件。**
> 即使某时刻明文=0，只要仍有写入路径执行 `setPassword("123456")`，下一分钟就会重新产生明文。

### 10.1 数据状态（四项均为 0）

```sql
SELECT 'student' t,
       COUNT(*) total,
       SUM(password IS NULL)                     null_n,
       SUM(password = '')                        empty_n,
       SUM(password NOT LIKE '$2%')              plain_n
FROM student
UNION ALL SELECT 'counselor', COUNT(*), SUM(password IS NULL), SUM(password = ''), SUM(password NOT LIKE '$2%') FROM counselor
UNION ALL SELECT 'admin_user', COUNT(*), SUM(password IS NULL), SUM(password = ''), SUM(password NOT LIKE '$2%') FROM admin_user;

-- 期望：三行均为 null_n = 0 · empty_n = 0 · plain_n = 0
-- 另需校验 hash 形态合法（长度 60 · 前缀为实测支持集）
```

### 10.2 写入路径证据（**9 个密码写入端点逐一探针**，不能只看 DB）

> 口径修正（v3）：初版称「6 类路径」，与实际枚举的 9 个端点不一致 ⇒ **统一按 9 个端点计**。

| # | 写入端点 | 验证方式 | 期望 |
| :-: | --- | --- | --- |
| 1 | `student/register` | 注册探针 → 查 DB | `$2` 开头 |
| 2 | `counselor/register` | 同上 | `$2` 开头 |
| 3 | `student/save` | admin 创建探针 | `$2` 开头 |
| 4 | `student/add` | admin 创建探针 | `$2` 开头 |
| 5 | `counselor/save` | admin 创建探针 | `$2` 开头 |
| 6 | `counselor/add` | admin 创建探针 | `$2` 开头 |
| 7 | `adminUser/save` | admin 创建探针 | `$2` 开头 |
| 8 | `student/resetPassword` | admin 重置 → 查 DB | `$2` 开头 |
| 9 | `counselor/resetPassword` | admin 重置 → 查 DB | `$2` 开头 |

> `/update` 不可绑定 `password`（F-1-1 已收口）；`resetPass` ×2 已下线 404 ⇒ **均不计入**。

### 10.3 代码扫描（防绕过门面）

```text
全仓库检索 setPassword( 的调用点 ⇒ 全部应走 PasswordHasher.hash
不得存在：
  setPassword(req.getPassword())      ← 原样明文
  setPassword("123456")               ← 原样默认口令
  setPassword(raw)                    ← 未经 hash 的变量
```

### 10.4 完整关闭条件

```text
① 数据：三表 null=0 / empty=0 / plain=0 / hash 形态合法
② 写入路径：10.2 **9 个密码写入端点**全部落 hash（运行时探针）
③ 代码扫描：10.3 无绕过（静态）
④ 登录：7/7 迁移账号 checkpw 成功
⑤ 回归：47/47
⑥ 备份已归档 + 回滚演练通过
```

```text
⇒ 上述全部满足，才可将 SEC-PLAINTEXT-PASSWORD-STORAGE 标记 CLOSED
⇒ 「WARN=0」属**增强证据**，不能单独作为关闭依据（日志可能未覆盖所有路径）
⇒ 删除兜底分支属独立批次 F-4-3（不阻塞关闭）
```

---

## ⑪ 开发环境验证方案（当前 7 条）

```text
0. 基线快照
   student 3 / counselor 3 / admin_user 1
   逐行记录 password 长度（6/6/5）与明文分类计数

1. 备份
   mysqldump --no-create-info mental_health_companion_agent student counselor admin_user > target/backup-pwd-f4.sql

2. 执行迁移（Java 程序，7 条）

3. 验证
   SELECT ... WHERE password LIKE '$2a$10$%'  → 7
   SELECT ... WHERE password NOT LIKE '$2%'   → 0
   长度均为 60

4. 登录验证
   student a1/123456 · counselor a1/123456 · admin/admin → 均 200 + token

5. 回归
   mvn test → 47/47（需先改 §11.1 的 2 处断言）

6. 回滚演练
   从 backup 恢复 → 验证 7 条恢复明文 → 再迁移一次 → 复核 0 明文

   ⚠️ 该演练验证的是「**数据恢复路径可用**」，
   不代表「代码可单独回滚」—— 代码回滚边界见 §9（B 阶段后必须"备份 + 旧代码"双动作）。
```

### 11.1 ⚠️ 必然的测试契约变更（F-4 必须包含）

`IdentityAuthorizationRegressionTest`：

```java
// L233-234 / L239-240 —— 迁移后必失败
assertThat(studentPassword(3)).isEqualTo("123456");
assertThat(counselorPassword(3)).isEqualTo("123456");
```

**改为**（保留"密码仍是 123456"的业务语义）：

```java
assertThat(BCrypt.checkpw("123456", studentPassword(3))).isTrue();
assertThat(BCrypt.checkpw("123456", counselorPassword(3))).isTrue();
```

> 与 S2-F-1-0 的 `isNotEqualTo(404)` 同类：**这是安全修复的必然测试改动**，不是回归失败。
> `Phase2CoreRegressionTest` 的登录调用（`password=123456`）**无需改动**（bcrypt 校验仍通过）。

---

## ⑫ 未来生产规模方案

### 12.1 关键约束：MySQL **无 bcrypt 内置函数**

⇒ **迁移必须由 Java 应用执行**（不能纯 SQL 一条 `UPDATE`）。

### 12.2 分批迁移（幂等、可中断）

```text
loop:
  rows = SELECT id, password FROM t WHERE password NOT LIKE '$2%' LIMIT 500
  if rows.empty: break
  for each row: newPwd = BCrypt.hashpw(row.password); UPDATE t SET password=? WHERE id=?
  commit per batch
```

| 要点 | 说明 |
| --- | --- |
| 批大小 | **初始候选 500**；实际吞吐/批耗时由 §2.1 benchmark 决定，**不得写死** |
| 执行窗口 | 低峰期 |
| 可中断 | 重跑即可（已 hash 自动跳过）|
| 与登录兼容 | 迁移期间**双格式分支必须在线** |
| 监控 | `明文行数` 应单调下降至 0 |
| 锁风险 | 逐行 UPDATE（主键）+ 小批提交，避免长事务 |

---

## 12.5 🆕 F-4 执行顺序（三批次拆分，不可合并）

> 按用户裁定：**不得把「密码已安全存储」与「旧格式兼容代码可删除」混成一个不可逆大批次。**

```text
┌─ S2-F-4-1 · PasswordHasher + 双格式登录兼容 ─────────────┐
│ 1. 新增 PasswordHasher 门面（hash / matches / isBcrypt） │
│ 2. 3 处 login 改为 matches()（hash 优先，plain 兜底）    │
│ 3. 9 处写入路径改为 hash()                                │
│ 4. 2 处测试断言改 checkpw                                 │
│ 5. 47/47 → 部署 → 验证：                                 │
│    · 现有明文账号仍能登录（兼容生效）                     │
│    · 新注册/重置已产生 hash                               │
└──────────────────────────────────────────────────────────┘
                        ↓
┌─ S2-F-4-2 · 数据迁移（7 条开发数据） ───────────────────┐
│ 1. mysqldump 备份三表 + 归档（含校验和）                  │
│ 2. 🚧 Preflight gate（§4.3.1）：                          │
│    NULL>0 / empty>0 / malformed>0 ⇒ ABORT，不得迁移       │
│ 3. 执行迁移（幂等）                                       │
│ 4. 验证 10.1 数据四项 + 10.4 完整条件                     │
│ 5. 7/7 登录 · 47/47                                      │
│ 6. 回滚演练（备份恢复 → 再迁移一次）                      │
└──────────────────────────────────────────────────────────┘
                        ↓
┌─ S2-F-4-3 · 删除明文 fallback（独立批次）───────────────┐
│ · 仅删除 matches() 中的 plain 分支                        │
│ · 前提：观察期确认无明文回潮 + 旧代码已无回滚需求         │
└──────────────────────────────────────────────────────────┘
```

**关键约束**：`F-4-1` 必须**先于** `F-4-2` 上线；`F-4-3` 独立提交，不得与 F-4-2 合并。

---

## 13. 已裁定 + v2 必改项（用户 2026-10-22）

### 13.1 已批准（保留）

| # | 项 | 裁定 |
| :-: | --- | :-: |
| 1 | 算法 | ✅ **BCrypt**（Hutool 内置，**零新增依赖**）|
| 2 | cost | ✅ **候选 10**（实际值 benchmark 后定，见 §2.1）|
| 3 | 迁移方式 | ✅ **兼容代码先行 + 全量批量 + 短期双格式** |
| 4 | 未登录账户 | ✅ 批量迁移覆盖，**不做首登强制改密** |
| 5 | 双格式窗口 | ✅ 保留观察期，之后独立批次删除 |
| 6 | 明文分支是否随 F-4 删 | ✅ **不删**（但回滚模型已改为阶段化，见 §9）|
| 7 | `PasswordHasher` 门面 | ✅ **批准**（Controller 不得裸调 Hutool）|
| 8 | 测试契约 | ✅ F-4-1 必须把 2 处明文断言改为 `checkpw`（并加 `startsWith("$2")`）|
| 9 | 默认口令 | ✅ **不在 F-4**（`SEC-ADMIN-DEFAULT-CREDENTIAL` 独立）|
| 10 | 迁移执行 | ✅ 一次性全量（7 条）；分批仅作生产预案 |

### 13.2 v2 必须修正的 6 处（**已在本版落实**）

| # | 初版问题 | 修正 |
| :-: | --- | --- |
| ① | `cost=10 约 60–100ms` 当事实 | ✅ 改为 **benchmark 待验证**（§2.1）|
| ② | 「代码回滚 ✅ 可行」不成立 | ✅ 改为 **阶段化回滚模型**（§9）：**数据迁移完成后不能单独回滚到不支持 BCrypt 的旧代码** |
| ③ | `NOT LIKE '$2%'` 忽略 NULL/空串 | ✅ 增加 **NULL/empty/异常数据治理**（§4.3）：生产条件 `IS NOT NULL AND <> '' AND NOT LIKE '$2%'` |
| ④ | 完成判定只看 DB | ✅ 增加 **写入路径证据 + 代码扫描**（§10.2/10.3）|
| ⑤ | `$2a/$2b/$2y` 凭理论假设 | ✅ 要求 **F-4-1 前用 Hutool 实测前缀支持**（§6.1.1）|
| ⑥ | 生产 `500 条约 30–50s` 写死 | ✅ 改为 **benchmark 后确定**（§12.2）|

### 13.3 F-4 拆分（已批准）

✅ `F-4-1 兼容代码` → `F-4-2 数据迁移` → `F-4-3 删除 fallback`（**不可合并**，见 §12.5）

### 13.4 v3 微调（复核后落实，**3 项**）

| # | 复核意见 | 落实 |
| :-: | --- | --- |
| ① | benchmark 缺「阈值来源」 | ✅ 新增 §2.1.1：**先定阈值再测**；无既有 SLO 时须书面记录工程阈值 + 理由 + 决策人；**不得事后调整** |
| ② | 回滚边界应钉死在 **F-4-1** 而非 F-4-2 | ✅ 新增 §9.1.1：**F-4-1 产生第一条 BCrypt 写入后，旧代码立即失去直接回滚能力**，此后必须「恢复兼容 DB 快照 + 部署旧代码」 |
| ③ | 「6 类路径」与实际 9 个端点不一致 | ✅ §3 标题改「**9 个密码写入端点**」；§10.2 改为 **9 行逐端点表格**；§10.4 同步 |
| 附 | NULL/empty 需落成真正的 gate | ✅ 新增 §4.3.1 **F-4-2 migration preflight gate**：任一异常 > 0 ⇒ **ABORT**，禁止「过滤后只迁剩下的」 |

> **当前状态**：S2-F-3 **v3 已批准**（设计冻结）。**F-4 尚未开始** —— F-4-1 须先单独出设计并经审核。

---

## 14. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v3 | 2026-10-22 | **用户复核通过 → 设计冻结（批准进 F-4-1）**；3 项微调已落实：①benchmark 增加**阈值来源**（§2.1.1：先定阈值再测，无 SLO 须书面记录工程阈值+理由+决策人，不得事后调整）②**回滚失效触发点钉死在 F-4-1**（§9.1.1：产生第一条 BCrypt 写入后旧代码即失去直接回滚能力）③「6 类路径」→「**9 个密码写入端点**」（§3 标题 / §10.2 九行表 / §10.4）；附：新增 **F-4-2 migration preflight gate**（§4.3.1，异常>0 即 ABORT，禁止"过滤后只迁剩下的"）；**仍零改动** |
| v2 | 2026-10-22 | **用户裁定：有条件通过；6 处文字修正已落实** —— ①cost 耗时改 benchmark 待测（§2.1）②**回滚改为阶段化模型**（§9：数据迁移完成后不能单独 git revert 到旧代码，必须"恢复备份 + 部署旧代码"双动作）③`NOT LIKE '$2%'` 增加 NULL/empty 治理（§4.3）④完成判定增加写入路径+代码扫描证据（§10.2/10.3）⑤`$2a/$2b/$2y` 前缀须 Hutool 实测（§6.1.1）⑥生产批大小/耗时改 benchmark（§12.2）；新增 **F-4 三批次拆分**（§12.5：F-4-1 兼容代码 → F-4-2 数据迁移 → F-4-3 删 fallback，不可合并）；§13 改为已裁定；**仍零改动，F-4 未开始** |
| v1 | 2026-10-22 | S2-F-3 设计（12 节）：现状 7 条全明文 · Hutool 5.8.25 内置 BCrypt（**零新增依赖**，API 已 `javap` 确认）· cost=10 · 写入 9 路径 · **A 批量 + C 双格式兜底**（覆盖未登录账户）· 登录兼容窗口 · reset 边界 · **默认口令独立** · **代码回滚 ≠ 数据回滚**（bcrypt 单向，只能从备份恢复）· 完成判定 SQL · 开发 7 条验证方案 + **2 处测试契约必改** · 生产分批（MySQL 无 bcrypt 函数）· 10 项待裁定；**零改动** |
