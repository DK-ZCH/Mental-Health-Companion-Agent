# S2-F-4-2 · 历史明文密码迁移（执行方案 + 工具设计）

> **性质**：设计 **v2**（✅ 原则批准 · **4 项硬修订已落实** · 待最终批准后创建工具 · **迁移未执行**）
> **上游**：`S2-F-3` v3 §4.2 / §4.3.1 / §10.4 / §12.5（已冻结）+ `S2-F-4-1` 已推送 `05ed077`
> **本批定位**：F-4 三批次中的**第二批** —— 只做「把 5 条历史明文转成 BCrypt」，**不删 fallback**
> **基线**：`HEAD = origin = 05ed077` · worktree CLEAN · 应用在线（8080 PID `52276`）
> **⚠️ 本批会真实修改数据库**，需显式批准后执行

---

## ① 范围

```text
✅ 新增离线迁移工具 com.tool.PasswordMigrator（独立 main，不进 Spring 上下文）
✅ 备份三表 → preflight gate → 迁移 5 条明文 → 验证 → 回滚演练 → 再迁移
✅ 完成后按 F-3 §10.4 判定是否可关闭 SEC-PLAINTEXT-PASSWORD-STORAGE

❌ 不删除明文 fallback（F-4-3）
❌ 不改任何 Controller / 业务代码
❌ 不改 DB schema / 列宽 / 索引
❌ 不改前端 / Entity / 拦截器
❌ 不处理默认口令（SEC-ADMIN-DEFAULT-CREDENTIAL）
```

---

## ② 迁移时刻 preflight 实测（2026-10-22，**本批基准快照**）

| 表 | total | NULL | empty | BCrypt | **plain** |
| --- | :-: | :-: | :-: | :-: | :-: |
| student | 3 | 0 | 0 | 1 | **2** |
| counselor | 3 | 0 | 0 | 1 | **2** |
| admin_user | 1 | 0 | 0 | 0 | **1** |
| **合计** | **7** | **0** | **0** | **2** | **5** |

列定义（容量足够，无需 DDL）：

```text
admin_user.password  varchar(100) NOT NULL
counselor.password   varchar(200) NULL
student.password     varchar(200) NULL
```

> ⚠️ 该快照是**当前时刻**实测。**执行前必须由工具重新跑一次**（F-3 §4.3.1），
> 不得直接把本表当作执行时基线（中间可能有登录/重置产生新行）。

---

## ③ 迁移工具设计 `com.tool.PasswordMigrator`

### 3.1 为什么是「独立 main」而不是 `CommandLineRunner`

| 方案 | 评价 |
| --- | :-: |
| **A. 独立 `main`（推荐）** | 不污染 Spring 上下文；不可能被业务启动误触发；生产可复用；可 `--dry-run` |
| B. `CommandLineRunner` + profile | 需隔离 profile，误配置即可能在正常启动时跑迁移 ⇒ **风险不可接受** |
| C. 一次性临时脚本（不入库） | 不可复现、无审计痕迹、生产无法复用 |

### 3.2 关键约束

```text
· 复用 PasswordHasher.hash()（不直接调 Hutool）⇒ 保证与运行时同一 cost / 同一实现
· 纯 JDBC（不引入 MyBatis 上下文）· 只碰 password 一列
· 参数化 JDBC（PreparedStatement）· 禁止字符串拼 SQL
· 幂等：WHERE password IS NOT NULL AND password <> '' AND password NOT LIKE '$2%'
· 零删除、零新增、零 DDL
```

#### 3.2.1 🔴 数据库密码**不得硬编码**（v2 新增）

```text
优先级：① 命令行参数 --url/--user/--password
        ② 环境变量 F42_DB_URL / F42_DB_USER / F42_DB_PASSWORD
        ③ 从 application.yml 读取 spring.datasource.*（只读解析，不硬编码）
❌ 源码中不得出现任何明文数据库口令
❌ 日志中不得打印口令（连接串需脱敏：仅打 host/db，隐去 password）
```

#### 3.2.2 🔴 事务边界：**本批 5 条走单事务**（v2 修订）

| 方案 | 判断 |
| --- | :-: |
| 逐行 + 每批 commit | ❌ **本批不采用** —— 5 行数据没有任何理由引入「部分提交」风险 |
| **单事务（本批采用）** | ✅ `BEGIN → 5 行 UPDATE → COMMIT`；任一失败 **全量 ROLLBACK**，DB 回到 5 条明文 |
| `--batch=N` 分批 | ⚠️ 仅保留为**未来大表**参数；本批执行**不传**该参数 |

```text
本批行为：
  plainCount = 5 ≤ SINGLE_TX_THRESHOLD(默认 1000)
  ⇒ 自动单事务，全部成功才 COMMIT
  ⇒ 中途异常：ROLLBACK，一条都不改
```

> 依据：当前数据量极小，「原子性」的价值远高于「避免长锁」。
> 未来数据量大时，显式传 `--batch=500` 才切换为分批模式（届时接受部分提交语义）。

#### 3.2.3 🔴 UPDATE 乐观保护（防并发覆盖，v2 新增）

```sql
UPDATE <table>
   SET password = ?
 WHERE id = ?
   AND password = ?        -- ← 原明文值
```

**要求 `updateCount == 1`**：

```text
updateCount == 0 ⇒ 该行在 preflight 与 UPDATE 之间已被其他操作修改
                ⇒ 立即抛异常 → 整个事务 ROLLBACK → exit 4
updateCount  > 1 ⇒ 异常（id 为主键，不可能发生）⇒ 同样 ROLLBACK
```

**为什么需要**：避免工具运行期间（用户登录/管理员重置）改了密码，而被迁移程序用旧值覆盖。

### 3.3 命令行契约

```text
java -cp "target/classes;<cp.txt>" com.tool.PasswordMigrator [options]

  --dry-run            # 只做 preflight + 统计，不写库（默认）
  --execute            # 真正执行迁移（必须显式给出）
  --batch=500          # 批大小（默认 500；当前 5 行无实质影响）
  --tables=a,b,c       # 默认 student,counselor,admin_user
  --url=...  --user=...  --password=...   # 默认取 application.yml 同值
```

**安全默认**：**不传 `--execute` 时一律 dry-run**（防止误执行）。

### 3.4 preflight gate（硬门禁，不可跳过）

```text
① 统计三表：total / null / empty / malformedBcrypt / bcrypt / plain
② 判定：
   null_n      > 0  → ABORT(exit 2)
   empty_n     > 0  → ABORT(exit 2)
   malformed   > 0  → ABORT(exit 2)
   plain_n     = 0  → 已是全 hash（幂等成功，exit 0，不做任何写）
③ 通过 → 打印「PASS」并进入迁移
```

**malformed 定义**：以 `$2` 开头但**不匹配** BCrypt 结构（同 `PasswordHasher` 的正则）；
此类值既不能迁移（不是明文）也不能登录（checkpw 必 false）⇒ **必须阻塞**，由人工处置。

> 工具内会复制同一个正则常量，并在注释标注「与 `PasswordHasher.BCRYPT_SHAPE` 保持一致」。
> **不为此给 `PasswordHasher` 增加 public 判断方法** —— F-4-1 明确「不暴露 isBcrypt」，
> 且离线工具不参与运行时认证，复制常量属可接受的取舍。

### 3.5 执行主循环（单事务版）

```text
// ① 一次性读取全部候选（本批 5 行，无分页必要）
candidates = []
for table in tables:
    rows = SELECT id, password FROM <table>
           WHERE password IS NOT NULL AND password <> ''
             AND password NOT LIKE '$2%'
           ORDER BY id
    candidates += rows

if candidates.isEmpty:
    log "已全 hash，无需迁移"
    exit 0

// ② 单事务：全部成功才提交
conn.setAutoCommit(false)
try {
    for (table, row) in candidates:
        newHash = PasswordHasher.hash(row.password)     // 60 字符
        n = UPDATE <table> SET password = ?
             WHERE id = ? AND password = ?              // 乐观保护
        if (n != 1) throw new ConcurrentModificationException(...)
    conn.commit()                                        // ← 唯一提交点
} catch (Exception e) {
    conn.rollback()                                      // ← 全量回滚
    log "迁移失败，已回滚，DB 保持原状"
    exit(e is concurrent ? 4 : 1)
}
```

**产出报告字段**（写入实现报告）：
`候选行数 / 每表迁移行数 / updateCount 校验结果 / 提交或回滚 / 耗时 / 最终 plain 总数`

> ⚠️ 未来分批模式的伪代码（**本批不启用**）保留在实现的注释中，以 `--batch=N` 显式开启。

### 3.6 退出码

| 码 | 含义 |
| :-: | --- |
| 0 | 成功（含「已全 hash，无需迁移」）|
| 2 | **preflight 拦截**（NULL / empty / malformed > 0）|
| 3 | 迁移后复核失败（plain ≠ 0）|
| **4** | **乐观保护冲突**（`updateCount != 1`）→ 已整体 ROLLBACK |
| 1 | 连接/参数/IO 异常 |

---

## ④ 执行步骤（6 步，严格顺序 · v2 已修订）

```text
① 前置检查（含「迁移前 7/7 登录基线」）
   · 8080 在线（迁移期间双格式分支必须在线，F-3 §12.2）
   · mysqldump / mysql 客户端可用
   · 记录 preflight 基线：5 plain / 2 bcrypt / 0 null / 0 empty
   · 🔴 **迁移前 7/7 登录基线**（v2 新增）：
       student a1/a2/a3（123456）· counselor a1/a2/a3（123456）· admin（admin）
       ⇒ 必须 **全部成功**，证明这组「真实密码」在迁移前确实可用
       ⇒ 迁移后用**同一组密码**再验一次，形成前后对照
       ❌ 禁止为了验证方便而先重置密码（那验证的是重置功能，不是迁移保真）

② 备份 + **行数级验证**（v2 修订）
   mysqldump --no-create-info --skip-extended-insert \
       mental_health_companion_agent student counselor admin_user \
       > target/backup-pwd-f42-<ts>.sql

   · 记录：文件存在 · 大小 · SHA256
   · 🔴 **不得用「INSERT 语句数」作为验收**（即使加 --skip-extended-insert，
     更可靠的做法是恢复后数行）
   · ✅ **恢复验证（临时库）**：
       CREATE DATABASE f42_drill;
       CREATE TABLE f42_drill.student   LIKE mental_health_companion_agent.student;
       CREATE TABLE f42_drill.counselor LIKE mental_health_companion_agent.counselor;
       CREATE TABLE f42_drill.admin_user LIKE mental_health_companion_agent.admin_user;
       mysql -D f42_drill < backup-pwd-f42-<ts>.sql
       SELECT COUNT(*) → student 3 / counselor 3 / admin_user 1
       DROP DATABASE f42_drill;
   · 行数不符 ⇒ 停止，不执行迁移

③ preflight（dry-run）
   java -cp ... com.tool.PasswordMigrator --dry-run
   · 必须输出 PASS 且 plain=5 / null=0 / empty=0 / malformed=0
   · 任一异常 ⇒ 停止，不做任何写

④ 执行迁移（**单事务**）
   java -cp ... com.tool.PasswordMigrator --execute
   · 期望：student 2 · counselor 2 · admin_user 1 = 共 5 行
   · 每行 updateCount 必须 = 1（乐观保护）
   · 期望 exit 0 且日志显示 COMMIT

⑤ 验证
   硬指标：三表 plain = 0 · bcrypt = 7 · 长度 60 · 前缀 $2
   登录：用 ① 的**同一组真实密码** → **7/7 成功**
   回归：mvn test → **60/60**
   日志观察（非硬指标）：PLAINTEXT-PASSWORD-HIT **不新增**
   数据完整性：行数 3/3/1 · 其他列未变

⑥ 回滚演练（🔴 **在临时库上进行，不碰业务库** · v2 修订）
   a. CREATE DATABASE f42_drill2; 三表 LIKE 复制结构
   b. 导入 backup → 验证 COUNT = 3/3/1 且 plain=5 / bcrypt=2
   c. 对临时库跑：migrator --execute --url=...f42_drill2
      → 验证 plain=0 / bcrypt=7（证明「可恢复 + 可再迁移」闭环）
   d. DROP DATABASE f42_drill2
   e. 业务库重新复核：plain=0 / bcrypt=7（**演练全程未改动业务库**）

   ⚠️ 演练验证的是【数据恢复路径 + 迁移可重复】，不代表代码可单独回滚（F-3 §9.1.1）
```

> **为什么回滚演练不在业务库做**：8080 应用仍在连接该库，
> 直接 restore 会与业务读写并发（虽有备份保护，但没必要引入风险）。
> 临时库方案可完整验证「备份可恢复 + 恢复后仍可迁移」，且**业务库零风险**。

---

## ⑤ 验收清单

| # | 项 | 期望 | 类型 |
| :-: | --- | --- | :-: |
| 1 | **迁移前登录基线** | **7/7** 成功（用各账号真实密码，非统一重置）| 硬 |
| 2 | 备份文件 | 存在 · 大小 · SHA256 已记录 | 硬 |
| 3 | **备份行数验证** | **临时库恢复后 `3 / 3 / 1`**（❌ 不用 INSERT 语句数）| 硬 |
| 4 | preflight | `NULL=0 / empty=0 / malformed=0 / plain=5` → PASS | 硬 |
| 5 | 迁移行数 | **5**（2+2+1）· 每行 `updateCount == 1` | 硬 |
| 6 | 事务 | **单事务** · 日志恰好一次 COMMIT（无分批提交）| 硬 |
| 7 | 迁移后 DB | `plain=0` · `bcrypt=7` · 长度 60 · 前缀 `$2a$` | 硬 |
| 8 | **迁移后登录** | **7/7** 用 ① 的**同一组真实密码** | 硬 |
| 9 | 回归 | **60/60** | 硬 |
| 10 | 明文 fallback 命中 | 迁移后 **不新增** `PLAINTEXT-PASSWORD-HIT` | **日志观察**（非硬指标）|
| 11 | 回滚演练 | **临时库**：恢复 5 plain → 迁移 0 plain；**业务库零改动** | 硬 |
| 12 | 数据完整性 | 行数不变 **3/3/1** · 无新增/删除行 · **其他列未变** | 硬 |
| 13 | 工具幂等 | 重复 `--execute` → 「已全 hash，无需迁移」exit 0 | 硬 |
| 14 | 无残留 | `f42_drill` / `f42_drill2` 均已 DROP · 源码无硬编码口令 | 硬 |

> **硬指标 vs 日志观察**：`plain=0`（第 7 项）才是「明文消失」的**硬证据**；
> `WARN 不新增`（第 10 项）只是运行时旁证 —— 因为已无明文，登录自然不进 fallback 分支。

### 5.1 `SEC-PLAINTEXT-PASSWORD-STORAGE` 的关闭条件（**不预先宣布结果**）

> 🔴 **唯一关闭标准：§5 的 14 项 Acceptance Criteria 全部 PASS。**
>
> **「执行流程跑完」不等于「关闭条件满足」** —— 二者必须严格分开：

```text
❌ 不成立：「6 步执行流程跑完了，所以关闭」
❌ 不成立：「--execute 返回 0，所以关闭」
❌ 不成立：「12 步迁移清单走完了，所以关闭」
✅ 成立  ：「§5 的 14 项 Acceptance Criteria 逐项 PASS」
```

**映射到 F-3 §10.4 的六类证据**（确保没有遗漏项）：

| F-3 §10.4 六类 | 本方案对应验收项 |
| --- | --- |
| ① 数据：null=0 / empty=0 / plain=0 / hash 形态合法 | §5 **#4 #7** |
| ② 写入路径 9 端点全部落 hash | §5 无独立项 → **本批后必须复查一次**（F-4-1 已验，确认未回退）|
| ③ 代码扫描 `setPassword(` 无绕过 | F-4-1 已验；本批不改业务代码 → **沿用** |
| ④ 登录 7/7 | §5 **#1 #8** |
| ⑤ 回归 60/60 | §5 **#9** |
| ⑥ 备份归档 + 回滚演练 | §5 **#2 #3 #11** |

```text
14 项全部 PASS ⇒ CLOSED
任一项未 PASS ⇒ 保持 OPEN，记录未满足项 + 原因
```

> ❗ **明文 fallback 仍在代码里** —— 删除属 **F-4-3**，不影响 CLOSED 判定（F-3 §10.4 口径）。
> ❗ 本方案**不得**在文档里提前写「F-4-2 完成后即安全」—— 先有证据，再有结论。
> ❗ **真实库第一次写入**必须在「7/7 基线 + backup + 临时库恢复 3/3/1 + rollback drill」全部 PASS 之后。

---

## ⑥ 风险与回滚

| 风险 | 级别 | 缓解 |
| --- | :-: | --- |
| 误执行（本不想迁移）| 🔴 | **默认 dry-run**，必须显式 `--execute` |
| **部分提交造成半迁移状态** | 🔴 | ✅ **单事务**：5 行全成功才 COMMIT，任一失败整体 ROLLBACK |
| **迁移期间数据被并发修改** | 🟠 | ✅ **乐观保护** `WHERE id=? AND password=原值` + `updateCount==1`，冲突即整体回滚（exit 4）|
| 备份不可用 / 备份不含完整数据 | 🔴 | ✅ **临时库恢复验证实际行数 3/3/1**（不用 INSERT 语句数）；未通过不执行 |
| 迁移后登录失败 | 🔴 | 双格式代码已在线；可立即从备份恢复（演练已证明可恢复）|
| `hashpw` 耗时导致超时 | 🟢 | 5 行 × cost 10 可忽略 |
| 误把 bcrypt 当明文二次 hash | 🟢 | 幂等 WHERE 排除 `$2%` + preflight 结构校验 + 单事务 |
| **回滚演练污染业务库** | 🔴 | ✅ **在临时库 `f42_drill2` 上演练**，业务库零改动 |
| 源码泄露数据库口令 | 🔴 | ✅ 参数 / 环境变量 / 读 yml，**不硬编码**；日志脱敏 |
| Windows jar 文件锁 | 🟡 | **本批不重新打包**（工具用 `target/classes`），不触碰运行中 jar |

**回滚手段（唯一）**：从 `backup-pwd-f42-<ts>.sql` 恢复三表。
**不可行**：从 hash 反推明文（BCrypt 单向）· `git revert` 单独回代码（F-3 §9.1.1）。

---

## ⑦ 文件清单

| # | 文件 | 类型 | 规模 |
| :-: | --- | --- | :-: |
| 1 | `src/main/java/com/tool/PasswordMigrator.java` | **新增** | **~230 行** |
| — | 业务代码 | **0 改动** | — |
| — | DB schema | **0 改动** | — |
| — | 前端 | **0 改动** | — |

**工具内部结构（供实现时自查）**：

```text
main(args)
 ├─ parseArgs()            默认 dry-run；--execute 才写
 ├─ resolveConnection()    参数 → 环境变量 → application.yml（不硬编码）
 ├─ preflight()            null/empty/malformed 判定 → ABORT(2)
 ├─ dryRunReport()         打印统计 + 预估行数
 └─ migrate()              单事务 + 乐观保护 → COMMIT / ROLLBACK(4)
```

### 7.1 组件扫描安全性（已实测确认）

```text
主类：@SpringBootApplication + @ServletComponentScan("com.ServletContextListener") + @MapperScan("com.dao")
包路径：com（默认扫描 com 及子包）⇒ com.tool 处于扫描范围「内」
但：PasswordMigrator 无 @Component/@Configuration/@Service 等任何 Spring 注解
   ⇒ 不会被注册为 bean，不会被实例化，不影响启动
结论：安全 ✓（放在 com.tool 只是为打包进 target/classes 方便运行）
```

---

## ⑧ 裁定状态（v2 · 4 项修订已落实）

### 8.1 已批准

| # | 项 | 裁定 |
| :-: | --- | :-: |
| 1 | 独立 `main`（`CommandLineRunner` 被否）| ✅ 批准 |
| 2 | 默认 dry-run | ✅ 批准 |
| 3 | 工具入库 `src/main/java/com/tool/` | ✅ 批准（可审计可复现，生产可复用）|
| 4 | 回滚演练保留 | ✅ 批准，但改为**临时库执行** |

### 8.2 v2 必须修改的 4 项（**已全部落实**）

| # | 原方案 | 修订后 | 位置 |
| :-: | --- | --- | :-: |
| ① | 「校验 INSERT 语句数 = 7」| **临时库恢复后验证实际行数 3/3/1** | §4 步骤② / §5 #3 |
| ② | 逐行 + 每批 commit | **本批 5 行走单事务**，`--batch` 仅留作未来大表 | §3.2.2 / §4 步骤④ / §5 #6 |
| ③ | `UPDATE ... WHERE id=?` | **`WHERE id=? AND password=原值` + `updateCount==1`**，冲突整体回滚 exit 4 | §3.2.3 / §3.6 |
| ④ | 直接在业务库 restore 演练 | **临时库 `f42_drill2` 演练**，业务库零改动 | §4 步骤⑥ / §5 #11 |

### 8.3 附：本次新增约束

| # | 项 | 落实 |
| :-: | --- | :-: |
| ⑤ | **不得硬编码 DB 密码** | §3.2.1（参数/环境变量/读 yml，日志脱敏）|
| ⑥ | **7/7 登录必须用迁移前真实密码** | §4 步骤①（迁移前先做 7/7 基线）+ §5 #1/#8 |
| ⑦ | **WARN 降为日志观察项** | §5 #10（硬指标是 `plain=0`）|
| ⑧ | **CLOSED 条件 10 项全通过才关闭** | §5.1（不预先宣布结果）|

> **当前状态**：S2-F-4-2 **方案 v2 修订完成，待最终批准后创建工具**。**迁移尚未执行**。

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v2 | 2026-10-22 | **用户裁定：原则批准，4 项硬修订已落实** ①备份验收由「INSERT 语句数=7」改为**临时库恢复后实际行数 3/3/1**（`mysqldump` 可能 extended insert ⇒ 语句数≠行数）②事务由「逐批 commit」改为**本批 5 条单事务**（任一失败整体 ROLLBACK；`--batch` 仅留未来大表）③UPDATE 增加**乐观保护** `WHERE id=? AND password=原值` + `updateCount==1`（冲突 exit 4 整体回滚）④回滚演练**改在临时库 `f42_drill2` 执行**（业务库零改动）；附：**DB 密码不得硬编码**、**7/7 必须用迁移前真实密码并先做基线**、**WARN 降为日志观察项**、**CLOSED 需 10 项全通过（不预先宣布）**；验收扩为 14 项；**仍不执行迁移** |
| v1 | 2026-10-22 | S2-F-4-2 执行方案：迁移时刻 preflight 实测（**5 plain / 2 bcrypt / 0 null / 0 empty**）· 独立 main 工具（**默认 dry-run** · 复用 `PasswordHasher.hash` · 纯 JDBC 参数化 · 幂等 · 逐批 commit）· **preflight gate 硬门禁**（NULL/empty/malformed > 0 → ABORT exit 2）· 6 步执行（备份 → preflight → 迁移 → 验证 → **回滚演练 → 再迁移**）· 10 项验收 · 完成后可 CLOSE `SEC-PLAINTEXT-PASSWORD-STORAGE`（fallback 删除仍属 F-4-3）· **1 文件 ~190 行 · 零业务改动**；**本批不执行迁移** |
