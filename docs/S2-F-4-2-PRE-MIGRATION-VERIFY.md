# S2-F-4-2 · 迁移前安全验证报告（4 步全部 PASS）

> **性质**：执行前验证 · **真实库零写入** · `HEAD = origin = 6c90562` · worktree CLEAN
> **结论**：**① 7/7 基线 ✅ · ② 备份 ✅ · ③ 临时库恢复 ✅ · ④-A 成功路径 ✅ · ④-B 真实 rollback（故障注入）✅** ⇒ 满足「真实库首次写入」的前置条件
> 🔒 **密码纪律**：本报告**不记录任何密码值**，仅记录「账号 → 结果」。

---

## ① 迁移前 7/7 原密码登录基线

> 使用**迁移前的真实密码**逐账号登录（❌ 未做任何重置），仅记录结果。

| # | 账号 | 结果 |
| :-: | --- | :-: |
| 1 | `student / a1` | ✅ `code=0` · token 32 字符 |
| 2 | `student / a2` | ✅ `code=0` · token 32 |
| 3 | `student / a3` | ✅ `code=0` · token 32 |
| 4 | `counselor / a1` | ✅ `code=0` · token 32 |
| 5 | `counselor / a2` | ✅ `code=0` · token 32 |
| 6 | `counselor / a3` | ✅ `code=0` · token 32 |
| 7 | `admin_user / admin` | ✅ `code=0` · token 32 |

**7/7 通过** ✓ ⇒ 迁移后须用**同一组密码**复验，形成前后对照。

### 1.1 备份前 DB 快照

| 表 | 行数 | NULL | empty | BCrypt | **plain** |
| --- | :-: | :-: | :-: | :-: | :-: |
| student | 3 | 0 | 0 | 1 | **2** |
| counselor | 3 | 0 | 0 | 1 | **2** |
| admin_user | 1 | 0 | 0 | 0 | **1** |
| **合计** | **7** | **0** | **0** | **2** | **5** |

`auth_token = 11`（7 次登录的 upsert 副作用，非业务数据）。

---

## ② 备份

| 项 | 值 |
| --- | --- |
| 命令 | `mysqldump --no-create-info --skip-extended-insert --no-tablespaces --result-file=<path> ... student counselor admin_user` |
| 文件 | `target/backup-pwd-f42-20261023_001723.sql` |
| 大小 | **3,339 bytes** |
| **SHA256** | `C3113114665281A162C68034CCF7202BB093B78BD5D9C37CF47408F9B128333A` |
| INSERT 语句 | **7** |
| 涉及表 | `student` · `counselor` · `admin_user`（三表齐全）|
| exit | **0** |

> 用 `--skip-extended-insert` 让每行一条 INSERT 便于人工核对；
> 但**验收仍以「恢复后实际行数」为准**（不使用语句数）。

---

## ③ 临时库恢复验证

```text
CREATE DATABASE f42_drill (utf8mb4)
CREATE TABLE f42_drill.{student,counselor,admin_user} LIKE 业务表结构
mysql -D f42_drill < backup-pwd-f42-20261023_001723.sql
```

| 校验 | 期望 | 实测 |
| --- | :-: | :-: |
| 建库 / 建表 exit | 0 / 0 | **0 / 0** ✓ |
| 表数量 | 3 | **3** ✓ |
| 导入 exit | 0 | **0** ✓ |
| `student` 行数 | 3 | **3** ✓ |
| `counselor` 行数 | 3 | **3** ✓ |
| `admin_user` 行数 | 1 | **1** ✓ |
| 密码形态 | `0 null / 0 empty / 5 plain / 2 bcrypt` | **完全一致** ✓ |

> **备份可用性成立** —— 恢复后的**三表行数**及 **`password` 字段形态**与业务库一致。
> ⚠️ **证据边界**：本次**未做**全表逐字段比对（未执行 `EXCEPT` / checksum / 全列 diff），
> 因此**不宣称「逐字段一致」**。结论仅限：行数正确 + 密码字段形态正确。

---

## ④ 临时库迁移验证（**成功路径 + 真实 rollback 路径**）

> ⚠️ **口径修正（v2）**：v1 把「成功迁移 + 幂等」称作 Rollback Drill，**名不副实** ——
> 那两项只证明「能成功」，**未证明「中途失败会整体回滚」**。本版补做故障注入。

**A. 成功路径（4.1 – 4.3）**

### 4.1 迁移执行（单事务）

```text
=== MIGRATION（单事务）===
  [1/5] student   id=1 → BCrypt
  [2/5] student   id=2 → BCrypt
  [3/5] counselor id=1 → BCrypt
  [4/5] counselor id=2 → BCrypt
  [5/5] admin_user id=6 → BCrypt
  COMMIT 成功：5 行已迁移（单事务）
```

- 恰好 **一次 COMMIT**（循环外）✓
- 每行 `updateCount == 1`（乐观保护未冲突）✓
- exit = **0** ✓

### 4.2 POST-VERIFY

| 表 | bcrypt | plain | malformed |
| --- | :-: | :-: | :-: |
| student | **3** | **0** | 0 |
| counselor | **3** | **0** | 0 |
| admin_user | **1** | **0** | 0 |
| **合计** | **7** | **0** | **0** |

`MIN(CHAR_LENGTH(password)) = 60`（三表均为标准 BCrypt 长度）✓

### 4.3 幂等复跑

```text
>>> 已全为 BCrypt，无需迁移（幂等退出）。
exit=0
```

✓ 重复执行不产生任何写入。

### 4.B 🔴 真实 Rollback 路径（故障注入 · **单事务的核心证据**）

**目的**：证明「第 3 行失败时，第 1–2 行不会残留为 BCrypt」。

**手段**（**不改工具**）：在临时库 `counselor` 表建 `BEFORE UPDATE` 触发器，任何更新即 `SIGNAL SQLSTATE '45000'`。

```sql
CREATE TRIGGER trg_f42_fail BEFORE UPDATE ON counselor
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'F42 rollback drill injected failure';
```

**重建临时库**（从同一备份恢复，形态回到 `5 plain / 2 bcrypt`）后执行 `--execute`：

```text
=== PREFLIGHT ===
student 3(1 bc / 2 pl) · counselor 3(1 bc / 2 pl) · admin_user 1(0 bc / 1 pl)
待迁移候选 = 5
>>> preflight PASS

=== MIGRATION（单事务）===
[1/5] student   id=1 → BCrypt      ← UPDATE 已执行（未提交）
[2/5] student   id=2 → BCrypt      ← UPDATE 已执行（未提交）
ROLLBACK 完成：2 次 UPDATE 已执行，最终持久化变更 = 0，数据库保持迁移前状态
[ERROR] SQLException: F42 rollback drill injected failure
exit=1
```

（第 3 行 `counselor id=1` 被触发器拦截 → 抛 `SQLException` → 整体 `ROLLBACK`）

**回滚后逐行实测**：

| 表 | id | 前缀 | 长度 | 判定 |
| --- | :-: | :-: | :-: | --- |
| student | **1** | `1234` | 6 | **明文（已回滚 ✓）** |
| student | **2** | `1234` | 6 | **明文（已回滚 ✓）** |
| student | 3 | `$2a$` | 60 | 原 hash（未动 ✓）|
| counselor | 1 | `1234` | 6 | 明文（未被写 ✓）|
| counselor | 2 | `1234` | 6 | 明文 |
| counselor | 3 | `$2a$` | 60 | 原 hash |
| admin_user | 6 | `admi` | 5 | 明文 |

```text
汇总：student 1 bc / 2 pl · counselor 1 bc / 2 pl · admin_user 0 bc / 1 pl
 = 5 plain / 2 bcrypt  ← 与迁移前完全一致
```

```text
✅ 结论：第 1、2 行的 UPDATE 确实执行过（日志可见），
        但因第 3 行失败而【整体回滚】，两行均恢复为明文。
        ⇒ 单事务语义真实有效；不存在「部分提交」残留。
```

> **措辞修正（v3）**：原先输出「ROLLBACK 完成：0 行变更」易被误读为「一次 UPDATE 都没执行」。
> 已改为 **「N 次 UPDATE 已执行，最终持久化变更 = 0」**，区分「执行次数」与「持久化变更」。
> 实测（`aa98363` 之后重跑同一故障注入）：输出 **`2 次 UPDATE 已执行，最终持久化变更 = 0`**，
> 且 `student id=1/2` 仍为明文、汇总仍 `5 plain / 2 bcrypt`。

### 4.5 清理

```text
DROP DATABASE f42_drill  → exit 0
information_schema: f42% schema = 0 · f42% trigger = 0  ✓
```

---

## ⑤ 业务库确认（**本阶段全程零写入**）

| 项 | 值 |
| --- | --- |
| student | 3 行 · **1 bcrypt / 2 plain** |
| counselor | 3 行 · **1 bcrypt / 2 plain** |
| admin_user | 1 行 · **0 bcrypt / 1 plain** |
| **合计** | **5 plain / 2 bcrypt**（与验证前完全一致）✓ |
| `auth_token` | 11（登录副作用）|
| 备份文件 | **保留**（3,339 bytes）✓ |

> ✅ **真实库零写入** —— `--execute` 只对 `f42_drill` 临时库执行过。

---

## ⑥ 四项前置条件判定

| # | 条件 | 结果 |
| :-: | --- | :-: |
| 1 | 7/7 原密码登录基线 | ✅ **7/7** |
| 2 | 备份 + SHA256 + 结构校验 | ✅ 7 行 / 3 表 / 3,339 B |
| 3 | 临时库恢复 + 行数 3/3/1 + 形态一致 | ✅ 全部一致 |
| 4 | 成功路径（单事务 + POST-VERIFY + 幂等）| ✅ 全部 PASS |
| **5** | **真实 rollback 路径（故障注入 → 前 2 行回滚为明文）** | ✅ **PASS（v2 补做）** |
| 6 | 临时库/触发器清理 | ✅ 零残留 |

```text
四项全部 PASS ⇒ 允许进入真实库首次写入
```

---

## ⑦ 下一阶段（**待批准后执行**）

```text
⑤ 真实库 preflight（--dry-run，必须 PASS：5 plain / 0 null / 0 empty / 0 malformed）
        ↓
⑥ 真实库迁移（--execute，单事务 5 行）
        ↓
⑦ POST-VERIFY（plain=0 / bcrypt=7 / len=60）
        ↓
⑧ 7/7 原密码登录（同一组密码）
        ↓
⑨ 60/60 回归
        ↓
⑩ 幂等复跑（已全 hash，exit 0）
        ↓
⑪ 数据完整性（行数 3/3/1 · 其他列未变 · 无新明文 WARN）
        ↓
⑫ 14 项 Acceptance Criteria 全部 PASS
        ↓
关闭 SEC-PLAINTEXT-PASSWORD-STORAGE
```

**本阶段未触碰真实库** ✓ · **未执行真实库 `--execute`** ✓

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v3 | 2026-10-23 | **措辞再修正（`aa98363`）**：rollback 输出由「0 行变更」→ **「N 次 UPDATE 已执行，最终持久化变更 = 0」**；重跑同一故障注入实测输出 **`2 次 UPDATE 已执行`** 且回滚结果不变（`student id=1/2` 明文 · `5 plain / 2 bcrypt`）；**execute 前最终快照与真实库 dry-run 均 PASS**（`7 行 / 5 plain / 2 bcrypt / 0 null / 0 empty`，候选 5，exit 0，dry-run 前后一致）|
| v2 | 2026-10-23 | **用户指出两处证据-结论不匹配，已修正**：①③ 的措辞由「与业务库逐字段一致」收紧为 **「三表行数 + password 字段形态一致」**，并明确声明**未做全表逐字段比对** ②v1 的「Rollback Drill」实为**成功路径 + 幂等**，名不副实 → **补做真实 rollback 故障注入**：在临时库 `counselor` 建 `BEFORE UPDATE` 触发器 `SIGNAL SQLSTATE '45000'`，使第 3 行（counselor id=1）失败 → 程序**整体 ROLLBACK（exit 1）** → 逐行实测 **student id=1/2 恢复为明文**（UPDATE 确曾执行）→ 汇总回到 **5 plain / 2 bcrypt**；临时库与触发器**零残留**；业务库仍 **5 plain / 2 bcrypt** · 前置条件由 4 项扩为 **6 项** |
| v1 | 2026-10-23 | 迁移前 4 步验证：**7/7 原密码基线全通过**（密码不入报告）· **备份** 3,339 B / SHA256 / 7 行 / 3 表 · **临时库 f42_drill 恢复 3/3/1 且形态一致** · **rollback drill 单事务 5 行 → 0 plain / 7 bcrypt / len=60 · 幂等 exit 0 · 临时库已 DROP** · **业务库全程零写入（仍 5 plain / 2 bcrypt）** · 四项前置条件全部 PASS |
