# Phase 4 · Round 4 设计冻结：受控创建预约

> **性质**：🏁 **CLOSED · 用户 2026-11-03 验收通过 · 正式基线 `16cd8bd`**
>
> **验收项（用户逐条确认 20 项）**：status 来源保护 ✅ · 合法 `1→2/1→3` 保留 ✅ · 非法转换拒绝 ✅ ·
> `slot 1..8` ✅ · Agent 日期窗口 ✅ · signed capability ✅ · uid 绑定 ✅ · TTL ✅ · Session 仅存草稿 ✅ ·
> **Session 不作为授权依据 ✅** · confirm 无模型可控参数 ✅ · 应用层查重 ✅ · DB 唯一约束并发兜底 ✅ ·
> `status=3` 释放槽位 ✅ · **真实跨 HTTP 两轮闭环 ✅（本轮最关键证据）** · 无草稿不误写 ✅ ·
> 草稿清理变异 M8 ✅ · 全量 291/291 ✅ · 提交后复跑 291/291 ✅ · 探针数据清理 ✅ · Git `HEAD=origin=16cd8bd` clean ✅
>
> **✅ 被批准的能力表述（原样使用）**：
> ```text
> Agent 本身仍为单轮无状态；预约确认通过 HttpSession 保存短生命周期的待确认编排状态，
> 并由服务端将「存在待确认草稿」作为当前请求的 system context 提示给模型。
> ```
> ❌ **不要**说「支持多轮预约」或「实现了 Memory」。
> **基线**：`HEAD = origin = 9e4d824` · LEAD 0 · 改动 **21 文件**（11 新增 + 10 修改）
> **DDL**：✅ 已执行（`active_slot_guard` + `uk_counselor_slot`）· **数据**：3 条种子（污染/探针行均已清理）
> **上游**：`PHASE4-ROUND4-CANDIDATES.md`（Q1/Q2 已裁定）· `PHASE4-ROUND4-APPOINTMENT-MODEL-EVIDENCE.md`（Q3 取证）
> **本轮唯一 Schema 变更**：`active_slot_guard` + `uk_counselor_slot`

---

## 一、裁定落地（用户 2026-11-03）

| 项 | 裁定 | 本设计中的落地 |
| --- | --- | --- |
| Q1 范围 | ✅ 只做「创建预约」 | 见 §二 非目标 |
| Q2 确认机制 | ✅ **签名凭证（signed capability）**，**不宣称天然一次性** | §五 Token 规格（重复提交由业务查重 + DB 唯一键拒绝）|
| Q3-a 容量 | ✅ **一师一槽一有效预约** | §三 R1 —— **这是 Round 4 建立的业务规则，不是「旧系统本来就有」** |
| Q3-b 唯一键 | ✅ **方案 B（生成列）** | §七 DDL |
| Q3-c | ⚠️ **有条件批准**，并改名为「**预约状态来源与状态转换最小修复**」 | §八 —— **必须保护咨询师合法审核流**，不是「禁止 update 改 status」 |
| Q3-d 窗口 | ✅ `today ~ today+30d` | §三 R2 —— **仅 Agent 路径**，不改旧 `/add` 全局行为 |

> **表述纪律（Q3-c）**：
> ```text
> ✅ 学生创建路径：服务端强制 status=1，客户端与 Agent 都不能决定 status
> ✅ 咨询师审核路径：保留 1→2 / 1→3
> ❌ 不是「/update 一律禁止改 status」
> ```

---

## 二、范围与非目标

### 2.1 本轮做什么

```text
1 个 Agent Tool 对（预览 + 确认）
1 个 Service 写入方法（复用既有 Service，不碰 Mapper）
1 个状态策略（创建强制 / 转换白名单）
1 个 Schema 变更（生成列 + 唯一键）
1 套测试（S1–S18）+ 变异（M1–M7）
```

### 2.2 明确不做

| 不做 | 原因 |
| --- | --- |
| ❌ 取消 / 删除 / 修改预约 | 用户裁定；删除是物理删除，语义与风险都不在本轮 |
| ❌ 收藏 / 留言等其它写操作 | 首次引入副作用，不做多写 Tool |
| ❌ Memory / RAG / 咨询师域 | 均未解前置 |
| ❌ 改 `/counselingAppointment/add` 的**日期窗口**与**其它校验** | Q3-d：+30 天只是 Agent 路径边界 |
| ❌ 学生同槽跨咨询师的限制 | 历史数据已存在该形态（student 1 同日同槽 2 条），改动即破坏兼容 |
| ❌ 完整状态机系统 | 只固化**当前已有**的 `1→2 / 1→3` |
| ❌ Redis / 新表 / 会话存储 | 用户明确：不为此引入新存储 |
| ❌ 前端改造 | 回答走既有 `answer` 文本；token 为纯文本 |

---

## 三、业务规则（冻结）

| ID | 规则 | 适用范围 |
| :-: | --- | --- |
| **R1** | 一个咨询师在**同一 `appointment_date` + 同一 `time_slot`** 下，只允许存在**一个有效预约**（`status ∈ {1,2}`）；`status=3` 不占用槽位 | **全局**（DB 唯一键强制）；应用层先查以给出友好错误 |
| **R2** | `today ≤ appointment_date ≤ today + 30 天`，`today` 取**服务端时间** | **仅 Agent 创建路径** |
| **R3** | `time_slot ∈ {1..8}`（字典 `time_slot`）| 仅 Agent 创建路径 |
| **R4** | `counselor_id` 必须存在 | 仅 Agent 创建路径 |
| **R5** | `student_id = 当前登录用户`，**绝不来自模型参数/客户端** | 全局 |
| **R6** | 创建时 `status = 1`（待审核），**服务端强制**，忽略客户端/模型值 | 全局创建路径 |
| **R7** | 状态转换仅允许：`1→2`、`1→3`（`心理老师` / `管理员`）；`old == new` 视为无变化；**其余一律拒绝**（含 `2→1`、`2→3`、`3→1`、`3→2`）| 全局更新路径 |
| **R8** | 学生**不得**修改 `status`（提交的值被忽略，保留原值）| 全局更新路径 |

> **R1 的措辞**：不是「旧系统已规定」，而是「**Round 4 为创建预约建立的明确业务约束**」。
> 取证已证明旧系统**没有表达容量规则**（无 capacity 字段、前端下拉不过滤已占用）。

---

## 四、两阶段流程（预览 → 确认）

> ⚠️ **本节是 R4-Q5 裁定前的设计（confirm 以 token 为参数）。**
> 因无状态 Agent 无法跨请求携带 token，**最终形态见 §4.4（C：Session 草稿 + confirm 无参数）**。

```text
① previewAppointment(counselorId, appointmentDate, timeSlot)
      ├─ 身份：ToolContext（studentId 只在此处产生）
      ├─ 校验：R3 / R2 / R4 / R1（只读查重）
      ├─ 组装：老师姓名 + 日期 + 时段标签（"09:00-10:00"）+ "待老师审核"
      ├─ 签发：confirmation token（TTL 5 分钟）
      └─ 【不写库】返回：预览文本 + token

② 用户明确确认（自然语言："确认" / "就这个"）

③ confirmAppointment(confirmationToken)   ← token 是【唯一参数】
      ├─ 验签 / 过期 / action / uidHash
      ├─ 从 token 读 cid / date / slot（模型无法篡改字段）
      ├─ 重新校验：R3 / R2 / R4 / R1（读取时点最新状态）
      ├─ 写入：status=1、studentId=当前用户、appointmentNo/时间戳由服务端生成
      └─ DB 唯一键兜底：DuplicateKeyException → 友好错误"该时段刚被预约"
```

### 4.1 为什么 `confirm` 只接受 token

若 `confirm` 同时接受 `counselorId/date/slot`，模型就有机会在预览与确认之间**替换参数**
（例如用户确认的是 A 老师，模型却提交 B 老师）。**token 内自带全部字段** ⇒ 参数漂移在结构上不可能发生。

### 4.2 为什么 token 不是一次性

签名是无状态的：服务端能验「未篡改 / 未过期 / 属于我 / 是这个操作」，
但**不知道它是否已被用过**。因此：

```text
重复提交同一 token
  → 验签通过
  → 业务查重（R1）命中
  → 拒绝
```

⇒ **重复性由业务唯一性保证，而不是由 token 保证。** 本文档与代码注释都必须这样写。

### 4.3 模型侧约束（Prompt，软）

| 约束 | 性质 |
| --- | --- |
| 必须先调用 `getAvailableCounselors`（T4）取得真实 `counselorId`，不得凭记忆编造 | 软（Tool 侧仍会校验存在性）|
| 缺少日期/时段时**追问用户**，不得自行假设 | 软 |
| 预览文本必须完整复述（老师、日期、时段），等用户明确确认后才可调 confirm | 软 |
| **不要向用户展示 token 原文** | 软（即便展示，token 绑定用户 + 5 分钟）|

> 与 Round 3 同样的纪律：**软约束只写「要求」，不写「保证」**。

---

### 4.4 ✅ R4-Q5 裁定 C（最终形态 · 已实施）

```text
① previewAppointment(counselorId, date, slot)
      ├─ 只读校验（R1–R4）
      ├─ 签发 signed token
      ├─ 写入 HttpSession：PENDING_APPOINTMENT_CONFIRMATION = token（单会话仅一个，新的覆盖旧的）
      └─ 【不写库】

② 用户表达确认（「确认」「好的」「可以」「就这样」）

③ confirmAppointment()            ← 【无任何参数】
      ├─ 从 HttpSession 取 token（由 Controller 放入 ToolContext，模型无法提供）
      ├─ 无论成功/失败：【清理草稿】
      ├─ 验签 / 过期 / action / uidHash
      ├─ 重新校验业务规则 + 查重
      └─ 写入（status=1、studentId=服务端身份）
```

**六条边界（用户裁定，全部落实）**：

| # | 边界 | 落地 |
| :-: | --- | --- |
| 1 | Session **不是授权依据** | 值只是签名 token；确认时**全部重新验签 + 重新校验**（C5 用例 + M8 变异）|
| 2 | **不是 Memory** | Agent 仍无会话记忆；只是**短生命周期业务编排状态**，请求结束即由 Controller 写/清 |
| 3 | 单 Session 仅一个草稿 | 新的预览覆盖旧的（C2 用例）|
| 4 | 成功/过期/冲突都清理 | `markConsumed()` 在任何确认尝试时置位 → Controller `removeAttribute`（C1/C3 用例）|
| 5 | Token 规格不变 | `v1.payload.sig` · uidHash · TTL 300s · **非一次性** |
| 6 | 不引入 Redis / 新表 / conversationId | 仅复用既有 `HttpSession` |

**为什么这样更强**：确认阶段模型连「确认哪个预约」都无法表达 —— 老师/日期/时段/学生/状态
全部来自服务端草稿与身份，**模型可控参数为零**。

> ⚠️ **仍属模型中介**：模型需要愿意调用 `confirmAppointment()`。
> 无状态模型不知道「上一轮留下了草稿」，因此 Controller 在**存在草稿**时
> 向 **system prompt 末尾**追加一句服务端状态提示（**不改用户输入**）。
> 实测：不加提示 → 模型不调用；加提示 → 成功调用（见 §14.5）。
> **确定性保证仍在服务端**：无草稿/凭证无效/重复/冲突一律拒绝。

---

## 五、Token 规格（冻结）

### 5.1 格式

```text
v1.<base64url(payload)>.<base64url(HMAC-SHA256(secret, "v1." + base64url(payload)))>
```

### 5.2 payload（JSON，字段名固定）

| 字段 | 含义 |
| --- | --- |
| `v` | 版本 = 1 |
| `act` | 动作 = `CREATE_APPOINTMENT`（防跨操作复用）|
| `uid` | **不是裸 userId**，而是 `HMAC(secret, "uid:" + userId)` 的十六进制前 16 位 |
| `cid` | `counselor_id` |
| `d` | `yyyy-MM-dd` |
| `s` | `time_slot` |
| `iat` / `exp` | 签发 / 过期（epoch 秒，TTL = 300s）|
| `n` | 随机 nonce（**仅用于日志排查，不做一次性校验**）|

> **为什么 `uid` 不存裸 id**：token 会出现在模型文本/客户端里；
> 存 `HMAC(uid)` 既能校验「是不是同一个用户」，又不泄露用户标识。

### 5.3 校验顺序（任一失败 ⇒ 拒绝，且**不写库**）

```text
① 格式（3 段）           → 失败：invalid
② 签名（常量时间比较）    → 失败：invalid
③ exp > now              → 失败：expired
④ act == CREATE_APPOINTMENT
⑤ uid 与当前用户匹配
⑥ 业务规则 R2–R4 + R1 重新校验
```

### 5.4 密钥来源

| 项 | 设计 |
| --- | --- |
| 配置项 | `agent.confirm.secret`（`application.yml` / 环境变量）|
| 缺省行为 | 启动时生成随机密钥 + **WARN**（重启后旧 token 失效，属可接受降级）|
| **WARN 内容** | ⚠️ **只说明后果，不得包含 secret 本身**；也不得写进异常、调试日志或 token 日志 |
| 不引入 | ❌ Redis ❌ 新表 ❌ 新依赖（JDK `Mac` + `Base64` 足够）|

---

## 六、写入实现路径（冻结）

```text
Agent Tool（薄层）
  └─ AppointmentWriteService.createFromAgent(identity, cid, date, slot)
        ├─ 再次校验（不信任 Tool 层）
        ├─ 组装实体：studentId=identity.userId / status=1
        ├─ appointmentNo = 服务端生成
        ├─ appliedAt / createdAt = 服务端时间
        └─ @Transactional 写入（复用既有 Service，不直接操作 Mapper）
```

| 约束 | 说明 |
| --- | --- |
| 不在 Tool 内直接 `Mapper.insert` | Tool 只做参数与身份适配 |
| 不允许 `Tool` 接受 `studentId` / `status` 参数 | 从结构上消除越权入口 |
| DuplicateKey → 业务错误 | 不得冒泡成 500，也不得谎报成功 |

---

## 七、Schema 变更（本轮唯一）

### 7.1 DDL（**未执行**，仅设计）

```sql
ALTER TABLE counseling_appointment
  ADD COLUMN active_slot_guard TINYINT
      GENERATED ALWAYS AS (CASE WHEN status = 3 THEN NULL ELSE 1 END) STORED,
  ADD UNIQUE KEY uk_counselor_slot
      (counselor_id, appointment_date, time_slot, active_slot_guard);
```

### 7.2 语义与兼容性

| 项 | 结论 |
| --- | --- |
| `status ∈ {1,2}` | 守卫 = `1` ⇒ 同槽第二次插入冲突 ✅ |
| `status = 3`（拒绝）| 守卫 = `NULL` ⇒ MySQL 允许多个 NULL ⇒ **不占槽** ✅ |
| `status = NULL`（历史/脏数据）| 守卫 = `1` ⇒ 视为有效，**故意保守**（宁可占槽，也不放行重复）|
| 现有数据 | 按 `(counselor, date, slot)` **无重复** ⇒ 迁移可执行 |
| MySQL | `8.0.23` 支持生成列 + 唯一索引 |
| 物理删除 | 行消失 ⇒ 自动释放 |

### 7.3 回滚

```sql
ALTER TABLE counseling_appointment
  DROP INDEX uk_counselor_slot,
  DROP COLUMN active_slot_guard;
```

### 7.4 迁移方式（本项目无 Flyway/Liquibase）

| 项 | 做法 |
| --- | --- |
| 脚本 | 新增 `docs/sql/ROUND4-appointment-slot-guard.sql`（含正/回滚两段）|
| 执行 | 手工执行，并在文档记录执行时间与结果 |
| `mental_health_companion_agent.sql` 全量 dump | **本轮不改**（体积大、易冲突）；新装环境按脚本补一次 —— 该不一致**显式登记** |

---

## 八、状态来源与转换最小修复（Q3-c 落地）

### 8.1 新增策略类（名称待定：`AppointmentStatusPolicy`）

| 方法 | 行为 |
| --- | --- |
| `forceCreateStatus(entity)` | 无条件置 `1`，忽略传入值（`/add`、`/save` 创建路径）|
| `resolveUpdateStatus(oldStatus, newStatus, role)` | `newStatus == null` → 保留旧值；`new == old` → 允许；`1→2`、`1→3` 且角色 ∈ {心理老师, 管理员} → 允许；否则 **拒绝** |

### 8.2 应用点

| 端点 | 改动 |
| --- | --- |
| `/add` | `forceCreateStatus`（学生创建路径，**不改变咨询师审核能力**）|
| `/save`（无 id）| `forceCreateStatus`（前端未使用，但**学生仍可调用** ⇒ 必须一起堵）|
| `/update` | `resolveUpdateStatus`；学生提交的 `status` **被忽略**（保留原值）|
| `/delete` | 不涉及（物理删除）|

### 8.3 明确保留的合法路径

```text
心理老师：1 → 2（同意）
心理老师：1 → 3（拒绝）
管理员  ：1 → 2 / 1 → 3
学生    ：不能改 status（其它字段可正常更新）
```

### 8.4 已知不一致（显式登记，不在本轮修）

```text
/add 仍无「今天 ~ +30 天」窗口限制，也无 slot 字典校验
⇒ 旧接口比 Agent 路径宽松
⇒ Round 4 只保证【Agent 创建路径】严格；未把旧接口统一
```

---

## 九、测试矩阵（S1–S18）

| 组 | # | 场景 | 期望 |
| --- | :-: | --- | --- |
| 预览 | S1 | 正常预览 | 返回文本含老师/日期/时段 + token；**DB 记录数不变** |
| 预览 | S2 | 已占用槽位 | 拒绝，且**不签发有效 token** |
| 确认 | S3 | 正常确认 | 写 1 条；`status=1`、`student_id=本人`、`appointment_no` 非空 |
| 确认 | S4 | **同一 token 再确认** | 拒绝（R1 命中），记录数仍为 1 |
| Token | S5 | 篡改 payload（改 cid/slot）| 拒绝（签名失败）|
| Token | S6 | 过期 | 拒绝，提示重新预览 |
| Token | S7 | **换成另一个学生提交** | 拒绝（uidHash 不匹配）|
| Token | S8 | 用于其它 action | 拒绝 |
| 参数 | S9 | 过去日期 | 拒绝 |
| 参数 | S10 | 超过 +30 天 | 拒绝（边界当天允许）|
| 参数 | S11 | `slot=0 / 9 / null` | 拒绝 |
| 参数 | S12 | 咨询师不存在 | 拒绝 |
| 越权 | S13 | 模型/Tool 传入 `studentId` 或 `status` | **参数不被接受**（结构上不存在该入参）|
| 域 | S14 | counselor / admin 调该 Tool | 拒绝（`AgentIdentity` 白名单）|
| 状态 | S15 | `/add` 传 `status=2` | 落库仍为 `1` |
| 状态 | S16 | 心理老师 `1→2` / `1→3` | 允许；`2→3`、`3→2`、`2→1` 拒绝 |
| 状态 | S17 | 学生 `/update` 传 `status=2` | 被忽略，保持原值 |
| 并发 | S18 | 两个并发确认同槽 | **恰好 1 条成功**，另一条得到业务错误（DB 唯一键）|

> S4 vs S18 的区别：S4 是**串行**重复（业务查重），S18 是**并发**（DB 唯一键兜底）——
> 这正对应 Q3-b 的表述：**应用层负责业务语义，数据库负责并发兜底**。

---

## 十、变异验证（M1–M7）

| # | 变异 | 期望转红 |
| :-: | --- | --- |
| M1 | 删 token 签名校验 | S5 |
| M2 | 删 uidHash 校验 | S7 |
| M3 | 删过期校验 | S6 |
| M4 | 删应用层查重 | S2、S4 |
| M5 | 移除 DB 唯一键 | **S18** |
| M6 | `forceCreateStatus` 改为信任客户端 | S15 |
| M7 | `resolveUpdateStatus` 放开任意转换 | S16 |

> M5 需要「先卸索引 → 跑 S18 → 还原索引」，属受控 DDL 操作，需单独记录。

---

## 十一、参数默认值（若你不反对，即按此冻结）

| 项 | 默认值 |
| --- | --- |
| Token TTL | **300 秒** |
| 预约窗口 | **今天 ~ +30 天（含边界）** |
| `agent.confirm.secret` 缺省 | 启动随机生成 + **WARN** |
| 管理员是否受 `1→2/1→3` 限制 | **受限**（保持审计一致性；不设越权通道）|
| token 是否可由模型展示给用户 | **允许**（绑定用户 + 5 分钟）；Prompt 要求不展示 |
| 失败文案 | 统一为「预约信息已失效/该时段已被预约」，不含内部细节 |

---

## 十二、实施顺序（与前三轮一致）

```text
① 写测试（S1–S18 先红）
② DDL 手工执行（记录时间）+ 验证兼容
③ 实现 Token / Service / Tool / 状态策略
④ 跑绿 → 全量回归
⑤ 变异 M1–M7（含 M5 的临时卸索引）
⑥ 真实 LLM 走一遍「预览 → 确认」
⑦ 零污染检查（fixture 回滚 + 真实数据基线）
⑧ git diff --check / status / cached --check → commit → push
```

---

## 十三、实施记录（2026-11-03 · 按用户修正的顺序）

### 13.1 执行顺序

```text
① 先写 status 测试（6 例）→ 红：6 tests / 4 failures（合法审核 1→2、1→3 已绿）
② status 最小修复 → 6/6 绿
③ DDL（唯一索引 + 生成列）→ 校验 4 列索引与 3 条种子数据兼容
④ 写预约测试（15 + 1 过期）→ 红（Tool 为「未实现」骨架）
⑤ 实现 Token / WriteService / Tools / Prompt → 22/22 绿
⑥ 全量 286/286
⑦ 变异 M1–M7
```

### 13.2 变更文件（16 个：9 新增 + 7 修改）

| 类别 | 文件 |
| --- | --- |
| 新增（主） | `AppointmentBookingTools` · `AppointmentConfirmToken` · `AppointmentWriteService` · `AppointmentPreviewResult` · `AppointmentConfirmResult` · `AppointmentStatusPolicy` |
| 新增（测试） | `Phase4AppointmentStatusTest`（6）· `Phase4AppointmentBookingTest`（15）· `Phase4AppointmentTokenExpiryTest`（1）|
| 修改（主） | `AgentConfig`（注册 3 Bean）· `CounselingAppointmentController`（`/add` `/save` `/update` 接入状态策略）· `AgentPrompts`（第 12–14 条）|
| 修改（测试） | `AgentControllerRegressionTest` / `Phase4SafetyFallbackTest`（Tool **类**数 7→8）· `Phase4ToolDataBoundaryTest`（fixture 按日错开）· `CounselorAssessmentScopeTest`（fixture 按时段错开）|

### 13.3 测试

```text
新增         : 22 例（status 6 + booking 15 + expiry 1）
全量（提交前）: 286 / 286 · BUILD SUCCESS
```

### 13.4 DDL（**已执行**）

```sql
ALTER TABLE counseling_appointment
  ADD COLUMN active_slot_guard TINYINT
      GENERATED ALWAYS AS (CASE WHEN status = 3 THEN NULL ELSE 1 END) STORED,
  ADD UNIQUE KEY uk_counselor_slot
      (counselor_id, appointment_date, time_slot, active_slot_guard);
```

| 项 | 结果 |
| --- | --- |
| 执行结果 | ✅ 成功；索引 4 列均在位 |
| 现有数据 | ✅ 3 条种子，无冲突 |
| 生成列取值 | status 1/2 → `1`；status 3 → `NULL` |
| 脚本 | `docs/sql/ROUND4-appointment-slot-guard.sql`（含回滚）|

### 13.5 变异验证（M1–M7 · 全部精确命中）

| # | 变异 | 转红 | 说明 |
| :-: | --- | :-: | --- |
| M1 | 删签名校验 | **S5**（1）| 伪造 payload 被接受 |
| M2 | 删 uidHash 校验 | **S7**（1）| 他人凭证可用 |
| M3 | 删过期校验 | **过期用例**（1）| TTL=0 仍能写入 |
| M4 | 删应用层查重（preview + confirm）| **S2**（1）| ✅ **S4 仍绿**：DB 唯一键接住了 → 正是双层设计的预期 |
| M5 | 临时卸掉唯一索引 | **S18**（1）| DB 兜底失效 |
| M6 | 不强制 `status=1` | **S15a + S15b**（2）| 学生可自审批 |
| M7 | 放开任意状态转换 | **S16c + S17**（2）| 非法跳转、学生改状态 |

### 13.6 🔴 一次测试数据污染（已修复，如实记录）

```text
M3 变异期间：过期用例【非事务】+ 过期校验被删
  ⇒ 确认真的写入 1 行（id=9202 / 2026-11-08 / counselor 2 / slot 1）
  ⇒ 导致 DataScopeC1CoverageTest 的「前置条数」断言失败（3→4、1→2）
处置：DELETE id=9202（精确单行）→ 数据回到 3 条种子
加固：Phase4AppointmentTokenExpiryTest 加 @Transactional
```

> 这不是「顺手修 bug」，而是变异实验暴露的**测试卫生缺陷**：正常情况下确认必失败，
> 所以该用例原来无需回滚；一旦校验被移除，它就会真的写库。

---

## 十四、🔴 R4-Q5（阻断提交）：两阶段在当前「无状态 Agent」上跨请求不可达

### 14.1 现象（真实 LLM，非 Mock）

```text
第 1 轮（给全参数）: 「帮我预约咨询师2，日期 2026-11-04，时段3」
  → ✅ 正确生成预览：咨询师 / 日期 / 时段 3 = 10:00-11:00 / 待审核
  → ✅ 未泄露 token（Prompt 第 14 条生效）
  → ✅ 未写库

第 2 轮: 「确认」
  → ❌ 「我这边还没有正在进行的预约流程，暂时没有可以确认的内容」
  → DB 仍为 3 条（未误写）
```

### 14.2 根因

```text
Agent 是【单轮无状态】：请求体只有 message，无 conversationId / history
⇒ 第 1 轮 tool 结果里的 token 只存在于【那一次调用】的上下文
⇒ 第 2 轮模型看不到 token，无法调用 confirmAppointment
⇒ 而 Prompt 又要求「不要向用户展示 token」⇒ 用户也无法复制回传
```

**结论**：**Tool 层是正确的**（22 例 + 7 项变异证明），
但**真实 UX 路径不可达** —— 预览能生成，确认永远无法发生。
这是**编排层的设计缺口**，不是实现 bug。

### 14.3 四种补法（需你裁定）

| 方案 | 做法 | 前端改动 | 新存储 | 评价 |
| --- | --- | :-: | :-: | --- |
| **A 同轮完成** | 模型在同一个请求里 preview → confirm | ❌ | ❌ | ❌ 用户没机会核对预览，违背两阶段初衷 |
| **B 客户端携带 token** | `/agent/chat` 增加可选 `confirmationToken`，由前端在用户点「确认」时回传；Controller 放入 `ToolContext` | ✅ **需要** | ❌ | 语义最清晰；但当前前端无预约确认 UI，需一并做 |
| **C 会话草稿（推荐）** | 预览时把 token 存入**既有 HttpSession**（绑定 userId + 5 分钟）；`confirmAppointment` **无参数**，从 `ToolContext` 读草稿 | ❌ | ❌（复用既有 Session）| 最贴合「用户回复确认」的 UX；不引入 Redis/新表；多标签/重启会丢草稿（可接受）|
| **D 展示 token 让用户粘贴** | 放开 Prompt 第 14 条 | ❌ | ❌ | 体验差，且长 token 易抄错 |

**我的建议**：**C**。
- 复用既有 Session（项目已有 `auth_token` 会话），不引入新存储；
- `confirmAppointment()` 改成**无业务参数**，从 `ToolContext` 取草稿 ⇒ 模型依旧无法指定老师/日期/时段；
- 草稿仍用签名 token 表示（服务端校验签名 + 过期 + 绑定 userId），Session 只是「上一次预览的暂存位置」。

⚠️ 若选 C，**冻结文档 §四/§五 的「confirm 以 token 为唯一参数」需要同步改写**；
Token 本身的规格（签名/uidHash/TTL/非一次性）**不变**。

### 14.4 当前代码的安全性（不受该缺口影响）

| 项 | 状态 |
| --- | --- |
| 无 token ⇒ 不能写入 | ✅ |
| 伪造/过期/他人 token ⇒ 拒绝 | ✅ |
| 未确认 ⇒ 不写库 | ✅（真实探测 DB 仍 3 条）|
| 副作用不可撤销部分 | 仅「创建」，无删除/修改 |

---

### 14.5 R4-Q6：C 的落地复测（三次真实探测）

| 尝试 | 改动 | 结果 |
| :-: | --- | --- |
| 1 | 原设计（confirm 以 token 为参数）| 第 2 轮模型拿不到 token ⇒ **不调用**（无工具日志）|
| 2 | confirm 改无参；工具描述强化「确认时直接调用」 | **仍未调用**（日志仍无工具记录）⇒ 单纯改描述不够 |
| 3 | **Controller 在存在草稿时向 system prompt 末尾追加状态提示** | ✅ **调用成功**：`appointmentId=9252`，DB 变 4 条，日志显示「创建成功 + 已清理草稿」|
| 收尾 | 删除探针行 `9252` | ✅ 基线回到 3 条（探针数据不留在库里）|

**结论（必须写准）**：

```text
C 成立，但需要「服务端状态提示」这一步：
  无状态模型不知道上一轮留下过草稿，仅靠工具描述不足以让它调用无参工具。

仍然【是模型中介】：模型要愿意调用 confirmAppointment。
确定性保证在服务端：无草稿 / 凭证无效 / 重复 / 冲突 ⇒ 一律拒绝，与模型是否配合无关。
```

**自动化覆盖**：
- `Phase4AppointmentSessionFlowTest` C1–C5（跨请求、覆盖、清理、无草稿、跨用户凭证）
- C1 额外断言：第二次请求的 **systemPrompt 必须包含「待确认的预约草稿」**（证明提示注入，而不是碰巧成功）
- **M8 变异**（不清理草稿）⇒ C1 / C3 / C5 转红

---

## 十五、状态

| 项 | 值 |
| --- | --- |
| 性质 | 🏁 **CLOSED · 正式基线 `16cd8bd`（用户 2026-11-03 验收通过）** |
| 基线 | `HEAD = origin = 9e4d824` · LEAD 0 |
| 改动 | **21 文件**（11 新增 + 10 修改）|
| DDL | ✅ **已执行**（`active_slot_guard` + `uk_counselor_slot`；回滚脚本 `docs/sql/ROUND4-appointment-slot-guard.sql`）|
| 数据 | ✅ **3 条种子**（M3 时期污染行、R4-Q6 探针行均已清理）|
| 前端 / pom | **0 改动** |
| 编排 | Session 草稿（单条、覆盖、确认即清）+ system 提示；**Session 不是授权依据** |
| 非 Memory | ✅ Agent 仍无会话记忆（表述纪律见 §4.4）|
| 已知限制 | 确认仍是**模型中介**；`/counselingAppointment/add` 的 30 天窗口未统一（登记）|
| 下一步 | ✅ 已验收并 CLOSED |

---

## 十六、🧾 技术债务登记（本轮不修）

### `DEBT-AGENT-APPOINTMENT-CONFIRM-RETRY`

| 项 | 值 |
| --- | --- |
| 编号 | `DEBT-AGENT-APPOINTMENT-CONFIRM-RETRY`（Future Round）|
| 现状 | **任何确认尝试**（`markConsumed`）都会清理草稿 —— 包括「token 合法、业务校验通过、但 DB 写入失败」的情形 |
| 影响 | 失败后用户必须重新 preview（UX / 重试语义），**不是安全问题**（当前已证明失败不会误写）|
| 建议方向 | 只在**进入终态**后清理：成功 / 凭证无效 / 过期 / 重复冲突 / 参数非法；对**系统异常**保留草稿以支持重试 |
| 未来需补测试 | ① 模拟写入异常 → 草稿仍在 → 重试成功 ② 过期 → 清理 ③ 重复 → 清理 ④ 成功 → 清理 |
| 为什么现在不改 | 本轮已证明「失败不误写」这条更重要的边界；改清理时机属独立设计，需自己的异常路径测试 |

> 用户 2026-11-03 明确：**登记，不阻塞 CLOSED，不在本轮修改**。

### 其它已登记、本轮未处理

| 项 | 说明 |
| --- | --- |
| `DEBT-AGENT-UNVERIFIED-PHONE` | 模型自由文本仍可能输出未核实号码（Prompt 软约束）|
| `/counselingAppointment/add` 窗口 | 未统一「今天~+30 天」（Q3-d 只约束 Agent 路径）|
| 学生同槽跨咨询师 | 现有数据已存在该形态，未加约束 |
