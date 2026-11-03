# S2-F-4-2 · 真实库密码迁移执行结果（14 项 AC 全部 PASS）

> **性质**：真实库首次写入 · **已执行** · 基线 `HEAD = origin = aa98363`
> **结果**：**三表 7 行全部 `$2a$` / 60 字符 · plain = 0** ⇒ 迁移成功
> **关闭判定**：**14 项 Acceptance Criteria 逐项 PASS** ⇒ `SEC-PLAINTEXT-PASSWORD-STORAGE` 可标 **CLOSED**
> 🔒 密码纪律：本报告不记录任何密码值。

---

## ① 执行输出（真实库 `--execute`）

```text
=== PREFLIGHT ===
  student     total=3  null=0  empty=0  bcrypt=1  malformed=0  plain=2
  counselor   total=3  null=0  empty=0  bcrypt=1  malformed=0  plain=2
  admin_user  total=1  null=0  empty=0  bcrypt=0  malformed=0  plain=1
  待迁移候选(plain 合计) = 5
>>> preflight PASS

=== MIGRATION（单事务）===
  [1/5] student id=1 → BCrypt
  [2/5] student id=2 → BCrypt
  [3/5] counselor id=1 → BCrypt
  [4/5] counselor id=2 → BCrypt
  [5/5] admin_user id=6 → BCrypt
  COMMIT 成功：5 行已迁移（单事务）

=== POST-VERIFY ===
  student bcrypt=3 plain=0 · counselor bcrypt=3 plain=0 · admin_user bcrypt=1 plain=0
>>> POST-VERIFY PASS：三表全部为合法 BCrypt。
exit=0
```

**恰好一次 COMMIT**（循环外）· 每行 `updateCount=1` · exit 0 ✓

---

## ② 独立 SQL 验证（不依赖工具自报）

```text
t          | id | p4    | len | username
admin_user |  6 | $2a$  | 60  | admin
counselor  |  1 | $2a$  | 60  | a1
counselor  |  2 | $2a$  | 60  | a2
counselor  |  3 | $2a$  | 60  | a3
student    |  1 | $2a$  | 60  | a1
student    |  2 | $2a$  | 60  | a2
student    |  3 | $2a$  | 60  | a3
```

| 汇总 | 值 |
| --- | --- |
| **total** | **7** |
| **plain** | **0** ← 硬指标 |
| **bcrypt** | **7** |
| NULL / empty | 0 / 0 |
| `MIN/MAX(CHAR_LENGTH)` | **60 / 60** |

---

## ③ 迁移后 7/7 原密码登录（**未重置**）

| 账号 | 结果 |
| --- | :-: |
| student `a1` / `a2` / `a3` | ✅ `code=0` · token 32 |
| counselor `a1` / `a2` / `a3` | ✅ `code=0` · token 32 |
| admin | ✅ `code=0` · token 32 |

```text
PLAINTEXT-PASSWORD-HIT 新增 = 0   ← 已无明文，不再进入 fallback
BCRYPT-MALFORMED        = 0
```

> 与迁移前 7/7 使用**同一组真实密码** ⇒ 证明「存储表示变了，用户实际密码没变」。

---

## ④ 回归与幂等

```text
Tests run: 30 (IdentityAuthorization) + 17 (Phase2Core) + 13 (PasswordHasher) = 60/60
BUILD SUCCESS
```

```text
幂等复跑 --execute：
  >>> preflight PASS
  >>> 已全为 BCrypt，无需迁移（幂等退出）。
  exit=0
```

---

## ⑤ 数据完整性（**非密码列逐行对比**）

用同一备份恢复到 `f42_verify`，与业务库逐行比对 `id / username / name / phone / email / created_at`：

```text
student     3 行 —— 完全一致 ✓
counselor   3 行 —— 完全一致 ✓
admin_user  1 行 —— 完全一致 ✓
```

> **仅 `password` 列发生变化**；行数 `3 / 3 / 1` 未变；无新增/删除行。

---

## ⑥ 14 项 Acceptance Criteria 逐项判定

| # | 验收项 | 证据 | 结论 |
| :-: | --- | --- | :-: |
| 1 | 迁移前 7/7 原密码基线 | 7 账号全部 `code=0` | ✅ |
| 2 | preflight `0 null / 0 empty / 0 malformed / 5 plain` | 工具输出 + SQL | ✅ |
| 3 | 迁移行数与逐行乐观保护 | 5 行 · 每行 `updateCount=1` | ✅ |
| 4 | 单事务（唯一 COMMIT） | 结构（循环外）+ 日志恰好一次 | ✅ |
| 5 | 迁移后 `plain=0 / bcrypt=7 / len=60` | 独立 SQL | ✅ |
| 6 | 迁移后 7/7（同组密码） | 7 账号全部 `code=0` | ✅ |
| 7 | 回归 60/60 | mvn test | ✅ |
| 8 | 明文 fallback 不再命中 | WARN 新增 = 0 | ✅（日志观察）|
| 9 | 回滚演练（临时库故障注入） | 前 2 行 UPDATE 后回滚为明文 | ✅ |
| 10 | 行数 3/3/1 · 其他列未变 | 备份 vs 现状逐行一致 | ✅ |
| 11 | 幂等 | 重复 `--execute` → 「已全为 BCrypt」exit 0 | ✅ |
| 12 | 备份 + SHA256 + 恢复 3/3/1 | 3,339 B · `C3113114…` | ✅ |
| 13 | 临时库与触发器零残留 · 无硬编码口令 | `f42%` schema=0 / trigger=0 · grep=0 | ✅ |
| 14 | **9 个写入路径未回退** | **静态**：`hash=9` · `matches=3` · 旧 `equals=0`<br>**运行时**：2 项由用例 9 覆盖 + **7 项新探针 7/7 落 `$2a$`/60** | ✅ |

```text
14 / 14 PASS ⇒ SEC-PLAINTEXT-PASSWORD-STORAGE = CLOSED
```

### 6.1 #14 运行时复验（v2 补做 · 9 路径覆盖矩阵）

> v1 仅做静态复查，**不能记为 PASS**（证据等级不足）。本版补齐运行时。

| # | 写入端点 | 运行时证据来源 | 结果 |
| :-: | --- | --- | :-: |
| 1 | `student/register` | **新探针** `zzf42vS1` | ✅ `code=0` · DB `$2a$`/60 |
| 2 | `counselor/register` | **新探针** `zzf42vC1` | ✅ `code=0` · DB `$2a$`/60 |
| 3 | `student/save` | **新探针** `zzf42vS2` | ✅ `code=0` · DB `$2a$`/60 |
| 4 | `student/add` | **新探针** `zzf42vS3` | ✅ `code=0` · DB `$2a$`/60 |
| 5 | `counselor/save` | **新探针** `zzf42vC2` | ✅ `code=0` · DB `$2a$`/60 |
| 6 | `counselor/add` | **新探针** `zzf42vC3` | ✅ `code=0` · DB `$2a$`/60 |
| 7 | `adminUser/save` | **新探针** `zzf42vA1` | ✅ `code=0` · DB `$2a$`/60 |
| 8 | `student/resetPassword` | **60/60 用例 9**（`startsWith("$2")` 断言）| ✅ 已覆盖，不重复 |
| 9 | `counselor/resetPassword` | **60/60 用例 9** | ✅ 已覆盖，不重复 |

**探针实测汇总**：

```text
7 条记录：probe_total=7  bcrypt=7  non_bcrypt=0  minlen=60  maxlen=60
```

**hash 账号可登录抽查（4/4）**：

| 账号 | 来源 | 密码来源 | 结果 |
| --- | --- | --- | :-: |
| `zzf42vS1` | register | 自设 | ✅ |
| `zzf42vS2` | save | 默认 | ✅ |
| `zzf42vC1` | register | 自设 | ✅ |
| `zzf42vA1` | adminUser save | 默认 | ✅ |

**清理与基线恢复**：

```text
DELETE 后残留：student 0 / counselor 0 / admin_user 0  ✓
基线：student 3 · counselor 3 · admin_user 1 · 全 $2a$ · plain=0  ✓
核心账号复测：student a1 / counselor a1 / admin 均 OK  ✓
AUTO_INCREMENT 漂移（登记，非验收项）：student 1000001 · counselor 1000000 · admin_user 8
```

> ⚠️ **证据等级声明**：第 8/9 项沿用 60/60 用例 9 的运行时断言（**未重复造探针**），
> 第 1–7 项为本次新增运行时探针 ⇒ **9/9 路径均有运行时或等价运行时证据**。

---

## ⑦ 执行期事故（如实登记）

| 项 | 内容 |
| --- | --- |
| 现象 | 迁移后首次 7/7 登录全部「无法连接到远程服务器」 |
| 定位 | **8080 未监听**；`app.log` 最后一条为 `01:59` 的 `HikariPool ... clock leap (delta≈1h40m)`；旧 PID `52276` 已不存在 |
| 归因 | **机器休眠/时钟跳变导致应用进程终止**，与迁移工具无关（工具为独立进程，且迁移已 `exit 0`）|
| 处置 | `mvn package` 重建（含 F-4-2 工具）→ 启动 → **PID `9572`** · jar `10:30:05` · 6s 就绪 → 7/7 通过 |
| 影响 | 无 —— 迁移结果已在 DB 中持久化，重启不改数据 |

---

## ⑧ 当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `aa98363` / `aa98363` · LEAD 0 |
| 工作区 | CLEAN |
| 服务 | PID `9572` · jar `10:30:05` · 8080 就绪 |
| **DB** | **7 行 · plain=0 · bcrypt=7 · 全 60 字符** |
| 备份 | `target/backup-pwd-f42-20261023_001723.sql`（3,339 B）|
| 临时库 | 全部 DROP（`f42%` 残留 0）|
| `SEC-PLAINTEXT-PASSWORD-STORAGE` | ✅ **CLOSED**（14/14）|
| 明文 fallback 代码 | **仍在**（删除属 **F-4-3**，不影响 CLOSED）|

---

## ⑨ 后续

```text
F-4-3（独立批次）：删除 PasswordHasher.matches() 中的明文 fallback 分支
                    ↓
                  此后仅 BCrypt 可比对，明文分支彻底移除
                    ↓
                  需重新评估「旧代码回滚能力」（届时已完全不可回退）
```

**未开始 F-4-3** ✗ · **未删除 fallback** ✗

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v2 | 2026-10-23 | **用户指出 #14 证据等级不足（仅静态），拒绝 CLOSED；已补运行时复验**：✅ 覆盖矩阵 **9/9** —— #8/#9 由 60/60 用例 9（`startsWith("$2")`）覆盖，**不重复造探针**；#1–#7 新增 7 个运行时探针 → **`code=0` 7/7 · DB `$2a$`/60 7/7 · hash 账号登录抽查 4/4**；探针清理 **残留 0** · 基线回 **3/3/1 全 `$2a$`/plain=0** · 核心账号复测 OK · `AUTO_INCREMENT` 漂移登记；⇒ **14/14 真正 PASS，`SEC-PLAINTEXT-PASSWORD-STORAGE` CLOSED** |
| v1 | 2026-10-23 | 真实库迁移执行：`--execute` 单事务 **5 行**（student 1,2 · counselor 1,2 · admin_user 6）→ `plain=0 / bcrypt=7 / len=60`；**独立 SQL 复核**通过；**迁移后 7/7 同组原密码登录**通过且 **WARN 新增 0**；**60/60**；**幂等 exit 0**；**非密码列逐行对比完全一致**、行数 3/3/1；临时库/触发器零残留；**14/14 AC PASS ⇒ CLOSED**；登记事故：机器休眠致进程终止（已重启 PID 9572）|
