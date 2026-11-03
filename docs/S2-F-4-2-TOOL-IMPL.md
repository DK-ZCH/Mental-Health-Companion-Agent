# S2-F-4-2 · 密码迁移工具实现 + Staged Review

> **性质**：工具实现 · **1 文件 · 466 insertions** · **✅ 已提交 `6c90562` · 已推送 ✓**
> **基线**：`05ed077` → `6c90562` · worktree CLEAN · DB **5 plain / 2 BCrypt（未改动）**
> 🔴 **本阶段仅创建工具** —— **未备份、未执行迁移、未改任何业务数据**

---

## ① 交付物

| # | 文件 | 类型 | 行数 |
| :-: | --- | --- | :-: |
| 1 | `src/main/java/com/tool/PasswordMigrator.java` | **新增** | **386 行** |

```text
业务代码    : 0 改动
DB schema  : 0 改动
前端        : 0 改动
运行中 jar  : 未触碰（工具用 target/classes 运行）
```

> 预估 230 行 → 实际 386 行：差额为**逐条注释**（每个门禁都标注了「为什么」），逻辑代码约 200 行。

---

## ② 七项安全门禁逐一核对（**带行号，供 review**）

| # | 门禁 | 实现位置 | 自检结果 |
| :-: | --- | --- | --- |
| 1 | **默认 dry-run** | `run()` L82 起 · `Config.execute` 默认 `false` | ✅ 无 `--execute` 不写库 |
| 2 | **preflight 硬拦截** | `preflight()` L160 · `hasBlocking()` → `return 2` | ✅ NULL/empty/malformed 任一 >0 即 ABORT |
| 3 | **格式语义由 Java 判断** | L164 `SELECT id, password ... ORDER BY id`（**全表读取，无 `NOT LIKE` 过滤**）| ✅ SQL 只取数 |
| 4 | **单事务** | `setAutoCommit(false)` **L207** · `commit()` **L227（唯一）** | ✅ grep 计数 = 1 |
| 5 | **乐观保护** | L211-212 `WHERE id = ? AND password = ?` · L218 `n != 1` | ✅ 冲突 → `OptimisticLockException` → exit 4 |
| 6 | **异常全覆盖 rollback** | `catch (Exception e)` **L231-232** | ✅ 含 `SQLException` / `RuntimeException` / 哈希异常 |
| 7 | **零硬编码凭据** | `Connector.yml()` 读 classpath `application.yml` · `describe()` 脱敏 | ✅ 源码无 `jdbc:` / `123456` |
| **8** | **BCrypt cost 4–31 校验**（v2 新增）| `BCRYPT_SHAPE` 捕获组 + `COST_MIN/COST_MAX` | ✅ `$2a$99$` → malformed + blocking |

### 2.0.1 v2 三项修正（复核后落实）

| # | 问题 | 修正 | 验证 |
| :-: | --- | --- | --- |
| ① | `Preflight.malformed` 恒 0（诊断失真）| 删除该字段，改 `malformedCount()` 按 `TableStat` 汇总 | `int malformed;` 仅剩 1 处 = `TableStat`（唯一状态源）· `after.malformed` 命中 0 |
| ② | `Config.hasAnyExplicitCredential()` 死代码 | 整段删除 | 全文件命中 **0** |
| ③ | 正则只判形状，`$2a$00`/`$2a$99` 会被当合法 | 正则加捕获组 `(\d{2})` + **cost 4–31** 判定 | 反射实测：04/10/31 → BCRYPT；00/03/32/99 → **MALFORMED(cost)** |

### 2.1 事务结构（实际代码）

```java
// L203 static boolean migrate(...)
boolean oldAutoCommit = conn.getAutoCommit();
conn.setAutoCommit(false);              // L207
try {
    for (Candidate c : candidates) {
        String hash = PasswordHasher.hash(c.plaintext);
        // L211-212: UPDATE <table> SET password=? WHERE id=? AND password=?
        int n = ps.executeUpdate();
        if (n != 1) { throw new OptimisticLockException(...); }   // L218
    }
    conn.commit();                      // L227 ← 唯一 COMMIT
    return true;
} catch (Exception e) {
    conn.rollback();                    // L232 ← 覆盖所有异常
    throw ...;
} finally {
    conn.setAutoCommit(oldAutoCommit);
}
```

**结构保证（而非日志证明）**：
- 循环体内 **无 commit**
- 唯一 `commit()` 位于 try 末尾
- `catch` 覆盖 `Exception`（非仅 `SQLException`）
- 无隐式提交操作（无 DDL、无 `SET autocommit=1` 中途调用）

---

## ③ 危险操作扫描（全部为 0）

| 模式 | 命中 |
| --- | :-: |
| `INSERT INTO` | **0** |
| `DELETE FROM` | **0** |
| `CREATE TABLE` | **0** |
| `DROP` | **0** |
| `ALTER TABLE` | **0** |
| `TRUNCATE` | **0** |

**唯二 SQL**：

```text
L164  SELECT id, password FROM <table> ORDER BY id          ← 只读
L211  UPDATE <table> SET password=? WHERE id=? AND password=?  ← 只改一列
```

表名拼接安全：`ALLOWED_TABLES = {student, counselor, admin_user}` 白名单校验，
`--tables` 只接受白名单子集 ⇒ **不存在表名注入面**。

---

## ④ dry-run 实测（**零写库**）

```text
=== S2-F-4-2 密码迁移工具 ===
  模式      : DRY-RUN（不写库）
  目标表    : [student, counselor, admin_user]
  连接      : jdbc:mysql://127.0.0.1:3306/mental_health_companion_agent?...  (user=root)

=== PREFLIGHT ===
  student     total=3  null=0  empty=0  bcrypt=1  malformed=0  plain=2
  counselor   total=3  null=0  empty=0  bcrypt=1  malformed=0  plain=2
  admin_user  total=1  null=0  empty=0  bcrypt=0  malformed=0  plain=1
  待迁移候选(plain 合计) = 5
>>> preflight PASS

>>> DRY-RUN：计划迁移 5 行，本次未写库。
    如需执行，请显式加 --execute
exit=0
```

| 校验 | 结果 |
| --- | :-: |
| 从 `application.yml` 解析连接（无硬编码）| ✅ |
| 三表统计与 SQL 交叉一致（2+2+1=5）| ✅ |
| preflight PASS | ✅ |
| **DB 前后一致（5 plain / 2 BCrypt）** | ✅ |
| 未传 `--execute` 时零写入 | ✅ |

### 4.1 实现期发现并修复的 2 个问题（如实登记）

| # | 问题 | 修复 |
| :-: | --- | --- |
| 1 | `YamlUtil.loadByPath("src/main/resources/...")` 抛 `NoResourceException` —— 该方法**走 classpath 解析，不接受文件系统路径** | 改 `loadByPath("application.yml")`（编译后在 `target/classes/`）+ 注释记录该陷阱 |
| 2 | `YamlUtil` 返回 `Dict` 而非 `Map<Object,Object>`，泛型 cast 编译失败 | 改用通配符 `Map<?,?>` 逐层取值 |

---

## ⑤ Staged Review 证据

```text
staged --name-status:
  A  src/main/java/com/tool/PasswordMigrator.java

staged --stat: 1 file changed, 386 insertions(+)
staged --check: 无空白错误 ✓
lint: 0 错误 ✓
未暂存/未跟踪: (无) ✓
HEAD / origin: 05ed077 / 05ed077 · LEAD 0
```

---

## ⑥ 明确未执行的事项

```text
❌ mysqldump 备份          —— 未执行
❌ 临时库 f42_drill 验证    —— 未执行
❌ --execute 迁移           —— 未执行
❌ 回滚演练                 —— 未执行
❌ 任何业务数据写入          —— 未发生（DB 仍 5 plain / 2 BCrypt）
```

**原因**：用户裁定 —— **先审查工具源码，通过并 commit 后，才进入 backup / preflight / migration**。

---

## ⑦ 代码审查指引（建议重点看这几处）

| 看什么 | 位置 |
| --- | --- |
| 参数解析 / 默认 dry-run | `Config.parse()` |
| **事务唯一 commit / rollback 覆盖** | `migrate()` **L203-247** |
| 乐观保护 SQL | **L211-212** |
| `updateCount != 1` 判定 | **L218** |
| **preflight 是否真的全表读取**（而非 SQL 过滤）| `preflight()` **L160-186** |
| 表名白名单 | `ALLOWED_TABLES` + `Config.parseTables()` |
| 连接解析优先级 | `Connector.open()` |
| 日志脱敏 | `Connector.describe()` |

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v2 | 2026-10-22 | **源码级复核 PASS → 已 commit `6c90562`（未 push）**；落实 3 项修正：①删除 `Preflight.malformed` 死字段 → `malformedCount()` 汇总（单一状态源）②删除 `hasAnyExplicitCredential()` 死代码 ③**正则加捕获组 + cost 4–31 校验**（`$2a$99$` 不再被误判合法，反射实测 7 个 cost 样本全部符合预期）；**未扩大任何范围**（不拆类/不改 CLI/不改事务/不改 exit code/不改 PasswordHasher）；dry-run 行为不变（5 plain / 2 bcrypt / 0 malformed）|
| v1 | 2026-10-22 | S2-F-4-2 工具实现：`com.tool.PasswordMigrator` **386 行** · 七项门禁逐一落实（默认 dry-run · preflight 硬拦截 · **Java 判断格式语义 / SQL 只取数** · **单事务唯一 COMMIT（L227）** · **乐观保护 `WHERE id=? AND password=?` + updateCount 校验（L211/L218）** · 异常全覆盖 rollback（L232）· 零硬编码凭据）· 危险 SQL 全 0（仅 SELECT + UPDATE password 一列）· **dry-run 实测 PASS（5 plain / 0/0/0）且 DB 零变化** · 实现期修复 2 个问题（`YamlUtil` classpath 语义 / `Dict` 泛型）· **已 staged，未 commit；备份/迁移/演练全部未执行** |
