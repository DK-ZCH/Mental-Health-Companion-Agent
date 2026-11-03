# Step 12 · Agent Identity Domain Security —— 设计

> **性质**：设计（📐 **待批准 · 不写代码**）
> **基线**：`HEAD = origin = 1bc24a5` · LEAD 0 · worktree CLEAN · DB **未改**
> **上游**：`PHASE4-ROADMAP-REVIEW.md`（发现 1 · 用户 2026-10-25 裁定 #1=A / #2=A / #3=A）
> **定位**：修复 **Step 11 引入的安全回归**，建立「Tool 角色域」可信边界
> **非**「又一个安全批次」—— 它是 Multi-Tool 的前置地基

---

## 一、问题重述

### 1.1 现象（已实测）

```text
student    : id 1, 2, 3
counselor  : id 1, 2, 3      ← 与 student 完全重叠
admin_user : id 6
```

`CurrentUserProvider.requireCurrentUserId()` 只返回一个 `Integer`，**不含身份域信息**：

```java
// AgentController.java:72
Integer userId = CurrentUserProvider.requireCurrentUserId(httpRequest);
```

于是 `AssessmentTools` 只能假设「`userId` 就是 `studentId`」：

```java
// AssessmentTools.java:97-99
recordService.lambdaQuery()
        .eq(AssessmentRecordEntity::getStudentId, userId)
        .count();
```

**实测结果**：`counselor a1`（id=1）问「我最近的心理测评记录」⇒ 返回 `student a1` 的 `15/100`。

### 1.2 本质

```text
userId 是一个【命名空间未限定】的整数
        ↓
在 student 表里表示学生 1
在 counselor 表里表示咨询师 1
        ↓
Tool 无法区分 ⇒ 身份域碰撞（ID Domain Collision）
```

**不是**「某个 if 写错了」，而是**缺失了一个维度**。

---

## 二、目标与非目标

### 目标

| # | 目标 |
| :-: | --- |
| G1 | Tool 执行前必须知道**当前用户的身份域**（不只是 id）|
| G2 | 身份域不符 ⇒ **fail-closed**（拒绝，而非猜测）|
| G3 | 管理员 / 咨询师**默认拒绝**（白名单制）|
| G4 | 建立**可复用机制**：后续新增 Tool 自动受约束，不需每次重新想安全问题 |
| G5 | 端到端可验证：counselor / admin 调用 `/agent/chat` 必须拿不到任何学生数据 |

### 非目标（严格不做）

| 不做 | 原因 |
| --- | --- |
| ❌ 修复 `DataScope` | 属 P1 待定，业务规则（②）未定 |
| ❌ 新增任何 Tool | 属 Phase 4 多 Tool |
| ❌ 改 DB / 改表结构 | 无必要 |
| ❌ 改 `AssessmentTools` 查询逻辑 | 查询本身正确，缺的是域校验 |
| ❌ 引入 Memory / RAG | 独立阶段 |
| ❌ 重构 `CurrentUserProvider` | 它已提供 `currentTableName()`，够用 |
| ❌ 前端隐藏 AI 区块 | 属体验优化，见 §九 登记项 |

---

## 三、可行性前提（已实测确认）

### 3.1 身份域的真实来源

```java
// AuthorizationInterceptor.java:72-76
if (tokenEntity != null) {
    request.getSession().setAttribute("userId",    tokenEntity.getUserId());
    request.getSession().setAttribute("role",      tokenEntity.getRole());
    request.getSession().setAttribute("tableName", tokenEntity.getUserType());   // ← 身份域
    request.getSession().setAttribute("username",  tokenEntity.getUsername());
    return true;
}
```

| 项 | 事实 |
| --- | --- |
| 写入时机 | 🔴 **每次请求都从 DB 的 `auth_token` 重新读取并覆盖**（非登录时快照）|
| 数据来源 | `auth_token.user_type` |
| 实测取值 | `student` / `counselor` / `admin_user` |
| 客户端可否篡改 | ❌ **不可**（客户端只能提供 token；token 对应哪个 `user_type` 由 DB 决定）|

⇒ **`CurrentUserProvider.currentTableName(request)` 是可信的服务端身份域来源。**

### 3.2 现有契约可复用

| 资产 | 现状 | 复用 |
| --- | --- | --- |
| `ForbiddenException` | → `HTTP 403 + {code:403, msg:"无权访问"}` | ✅ Layer 1 拒绝 |
| `AgentModel.answer(..., Map toolContext)` | 已是 `Map<String,Object>` | ✅ **无需改动** |
| `spring.ai.tools.throw-exception-on-error=true` | Step 11 已配置 | ✅ Layer 2 异常穿透 |

> **重要**：`AgentModel` / `SpringAiAgentModel` **完全不需要修改** ——
> 它们已经接受任意 `Map` 作为 `toolContext`，只需多放一个 key。

---

## 四、方案：双层防御

### 4.1 为什么必须双层

| 层 | 职责 | 能否单独胜任 |
| --- | --- | :-: |
| **L1 Controller** | 「**这个角色能否使用 Agent 功能**」整体准入 | ❌ 无法表达「角色 × 工具」矩阵 |
| **L2 Tool** | 「**这个工具能否在该域下读数据**」 | ❌ 太晚（此时已消耗 LLM token）|

**未来场景**：若给咨询师开放 Agent，但只开放「我的预约」、不开放「学生测评」——

```text
单 L1：要么全放行（学生测评也跟着开放）要么全拒绝（预约也做不了）
双 L1+L2：L1 放行 counselor，L2 按 Tool 各自拒绝 ⇒ 可行
```

⇒ **双层不是冗余，是维度不同。**

### 4.2 数据流

```text
HTTP Request
    ↓
AuthorizationInterceptor（每次请求从 auth_token 重写 Session）
    ↓  Session: userId / tableName / role
AgentController
    ├─ userId    = requireCurrentUserId(request)
    ├─ userTable = currentTableName(request)
    │
    ├─ 🔴 L1 准入：userTable 必须 ∈ {student}
    │      不匹配 → ForbiddenException → HTTP 403（不调用 LLM）
    ↓
AgentService.chat(message, AgentIdentity)
    ↓  ToolContext = { userId, userTable }
AgentModel.answer(...)
    ↓
Tool（Spring AI 内部循环）
    ├─ 🔴 L2 域校验：ALLOWED_TABLE 必须 == ctx.userTable
    │      不匹配/缺失 → IllegalStateException → 终止调用
    ↓
Service → DAO → MySQL
```

### 4.3 新增值对象（核心）

```java
/** 已认证身份：id + 身份域，二者必须同时成立才有意义 */
public record AgentIdentity(int userId, String userTable) {

    public static final String TABLE_STUDENT = "student";

    /** 从 ToolContext 解析；缺失/类型错误 → null（由调用方 fail-closed） */
    public static AgentIdentity from(ToolContext ctx) { ... }

    public boolean isStudent() { return TABLE_STUDENT.equals(userTable); }
}
```

> **为什么用值对象而不是两个参数**：
> `chat(message, userId, userTable)` 分开传，容易出现「传了 userId 忘了 tableName」
> 或「两个字段来自不同请求」的错误组合。值对象让**缺失在编译期就不可能**。

### 4.4 L1：Controller 准入（白名单）

```java
String userTable = CurrentUserProvider.currentTableName(httpRequest);
if (!AgentIdentity.TABLE_STUDENT.equals(userTable)) {
    logger.warn("[AGENT][SEC] 身份域不被允许 userTable={}", userTable);   // 不记 userId
    throw new ForbiddenException();
}
```

| 特性 | 说明 |
| --- | --- |
| 白名单制 | 只允许 `student`；**未知/未来新增的 `user_type` 天然被拒**（不是黑名单）|
| 位置 | 在 `requireCurrentUserId` **之后**（先确认已认证，再判权限）|
| 效果 | counselor / admin **进不到 LLM** ⇒ 零 token 消耗 |

### 4.5 L2：Tool 域自校验

```java
@Tool(description = "查询当前登录用户自己的心理测评记录。...")
public AssessmentRecordToolResult getMyAssessmentRecords(ToolContext toolContext) {

    AgentIdentity identity = AgentIdentity.from(toolContext);
    if (identity == null) {
        logger.error("[AGENT][SEC] 身份上下文非法");
        throw new IllegalStateException("agent identity context invalid");
    }
    if (!ALLOWED_TABLE.equals(identity.userTable())) {
        logger.error("[AGENT][SEC] 身份域不符（该工具仅允许 {}）", ALLOWED_TABLE);
        throw new IllegalStateException("agent identity domain not allowed");
    }
    // ↓ 以下查询逻辑【完全不变】
    ...
}
```

| 特性 | 说明 |
| --- | --- |
| 声明式 | 每个 Tool 用常量 `ALLOWED_TABLE` 显式声明自己的域 |
| 方法签名 | **仍然没有 `userId` / `userTable` 参数** ⇒ 模型无法填 |
| 语义 | `IllegalStateException` → 穿透 → `code=500`（沿用 Step 11 安全失败约定）|

---

## 五、变更清单（文件级）

| # | 文件 | 类型 | 变更 | 预估 |
| :-: | --- | :-: | --- | :-: |
| 1 | `com/agent/AgentIdentity.java` | **新增** | 值对象 + `from(ToolContext)` | ~45 行 |
| 2 | `com/agent/AssessmentTools.java` | 改 | 顶部加 2 段域校验 + `CTX_USER_TABLE` 常量 | ~+18 / −6 |
| 3 | `com/agent/AgentService.java` | 改 | `chat(String, int, String)` → `chat(String, AgentIdentity)`；多放 1 个 key | ~+8 / −6 |
| 4 | `com/agent/AgentController.java` | 改 | 解析 `currentTableName` + L1 白名单校验 | ~+12 / −2 |
| 5 | `com/config/AgentConfig.java` | 改 | 装配签名随 ③ 调整 | ~+2 / −2 |
| 6 | `test/.../AgentIdentityDomainTest.java` | **新增** | 单测：域解析 / 拒绝矩阵 | ~90 行 |
| 7 | `test/.../AssessmentToolsTest.java` | **新增** | 单测：Tool 在错误域 / **伪造域**下抛异常 | ~110 行 |
| 8 | `test/.../AgentIdentityRegressionTest.java` | **新增** | `@SpringBootTest + MockMvc` 端到端 + **`AgentModel` 调用次数 = 0** | ~130 行 |

> `AgentPrompts` **已裁定不改**（§十一 #2 = A），不再列入变更清单。

### 5.1 变更边界（精确表述 · 消除歧义）

| 范围 | 允许 | 说明 |
| --- | :-: | --- |
| `com/agent/**` 中 **`AgentController` / `AgentService` / `AssessmentTools`** | ✅ **允许修改** | L1 与 L2 的落地位置，**必然要改** |
| `com/config/AgentConfig` | ✅ 允许修改 | 仅装配签名 |
| **`AgentModel` / `SpringAiAgentModel`** | ❌ 不改 | 已接受任意 `Map`，零改动 |
| `AgentChatRequest` / `AssessmentRecordToolResult` | ❌ 不改 | 与身份域无关 |
| **既有业务** Controller / Service / Mapper / XML（`AssessmentRecordController` 等）| ❌ **一律不动** | 本批不碰业务层 |
| `AgentPrompts` | ❌ 不改 | 已裁定 |
| DB / 前端 / `pom.xml` | ❌ 不改 | — |

> **本文档此前写作「不改任何 Service / Controller」，与 L1 设计冲突** —— 已按上式修正为
> **「不修改既有业务 Controller/Service，仅修改 Agent 自有类」**。此处以本节为准。

> 净改动预估：**8 文件（3 新增）· ≈ +440 / −16 行**

---

## 六、fail-closed 语义矩阵（本设计的核心契约）

### 6.1 L1 Controller

| `userTable` | 结果 | HTTP | 是否调用 LLM |
| --- | --- | :-: | :-: |
| `student` | 放行 | 200 | ✅ |
| `counselor` | 🚫 拒绝 | **403** | ❌ |
| `admin_user` | 🚫 拒绝 | **403** | ❌ |
| `null`（Session 缺失）| 🚫 拒绝 | **403** | ❌ |
| `""` / 空白 | 🚫 拒绝 | **403** | ❌ |
| 未来新增任意值 | 🚫 拒绝（白名单）| **403** | ❌ |

### 6.2 L2 Tool（**二元身份**契约）

> **核心断言**：Tool 必须使用 **`(userId, userTable)` 二元身份**，
> **不得退化为单独 `userId`** —— 否则本设计等于没做。
> 下表中「单维度合法 + 另一维度非法」的全部组合，**必须以拒绝收场**。

| # | `userId` | `userTable` | 结果 | 说明 |
| :-: | --- | --- | --- | --- |
| 1 | ✅ 合法 | ✅ `student` | ✅ **正常查询** | 唯一放行组合 |
| 2 | ✅ 合法 | 🔴 `counselor` | `IllegalStateException` | **伪造/错配域** |
| 3 | ✅ 合法 | 🔴 `admin_user` | `IllegalStateException` | **伪造/错配域** |
| 4 | ✅ 合法 | 🔴 `null` | `IllegalStateException` | 缺域 ⇒ 不得默认当 student |
| 5 | ✅ 合法 | 🔴 `""` / 空白 | `IllegalStateException` | 同上 |
| 6 | ✅ 合法 | 🔴 非 `String`（如 `Integer`）| `IllegalStateException` | 类型污染 |
| 7 | 🔴 `null` | ✅ `student` | `IllegalStateException` | **不得只靠域** |
| 8 | 🔴 非 `Integer`（如 `String "1"`）| ✅ `student` | `IllegalStateException` | 类型污染 |
| 9 | 🔴 `null` | 🔴 `null` | `IllegalStateException` | — |
| 10 | `null` | — | `IllegalStateException` | `toolContext == null` |

> 第 2/3 行是本设计的**关键负向测试**（用户 2026-10-25 要求）：
> 即使 `userId` 完全合法，只要 `userTable` 不属于该 Tool 的允许域，就必须拒绝。
> 这直接证明 Tool 校验的是**二元身份**，而非「拿到 id 就查」。

### 6.3 绝对禁止的写法（负面清单）

```java
// ❌ 禁止：默认值兜底
int userId = ctx.containsKey("userId") ? ... : 1;

// ❌ 禁止：从模型参数取身份
getMyRecords(Integer studentId)          // studentId 会进 JSON Schema

// ❌ 禁止：域缺失时「当作学生处理」
if (userTable == null) userTable = "student";   // 等于关闭本设计

// ❌ 禁止：try-catch 后返回空结果
try { ... } catch (Exception e) { return ok(List.of()); }   // 会把安全失败伪装成「无数据」
```

---

## 七、测试策略

### 7.1 单元测试（不启动容器）

| 用例组 | 覆盖 |
| --- | --- |
| `AgentIdentityTest` | `from()` 正常解析 / `null` ctx / 缺 userId / 缺 userTable / 类型错误（`Integer` 域、`String` id）|
| `AssessmentToolsTest` | 覆盖 §6.2 **全部 10 行组合**，重点 **#2/#3（合法 userId + 伪造域）必须抛异常**；#1 `student` 域正常返回 |

### 7.2 端到端（`@SpringBootTest + MockMvc`）

| # | 场景 | 期望 |
| :-: | --- | --- |
| E1 | `student a1` 调用 | `200` + `code=0` + 含本人数据 |
| E2 | **`counselor a1` 调用** | 🔴 **`403`** + `code=403` |
| E3 | **`admin` 调用** | 🔴 **`403`** |
| E4 | 未登录（无 token）| `200` + `code=401`（既有契约，不得变）|
| E5 | 空 message | `400` |
| E6 | 超长 message | `400` |
| **E7** | **E2/E3 时 `AgentModel.answer()` 调用次数** | 🔴 **`= 0`**（`@SpyBean`/Mock 计数）——**证明未进入模型层** |

> **E7 是本批的强化证据**（用户 2026-10-25 裁定）：
> 不以「耗时 <100ms」作为「未调用 LLM」的主要证据（网络抖动 / 快速失败 / 缓存都可能造成短耗时），
> 而是**直接计数** —— 调用次数必须是 **0**。

### 7.3 回归

```text
既有 63 项 → 必须 63/63（Agent 相关用例若受影响需说明，不得静默改断言）
新增     → 约 12–15 项
```

> ⚠️ **若既有用例因签名变更需调整，必须逐条说明理由** —— 不允许「改断言让它过」。

---

## 八、验收标准

| # | 标准 | 判定方式 |
| :-: | --- | :-: |
| A1 | `counselor a1` 调用 `/agent/chat` → **403** | 实测 |
| A2 | `admin` 调用 → **403** | 实测 |
| A3 | `student a1` 调用 → 正常，**只返回本人 1 条** | 实测 |
| A4 | counselor/admin 调用时**日志有 `[AGENT][SEC]` 且不含任何学生数据** | 查日志 |
| A5 | counselor/admin 调用**不产生 LLM 调用** | 🔴 **`AgentModel.answer()` 调用次数 = 0**（单测 E7 直接计数）。**耗时不作为证据，仅作辅助观察** |
| A6 | 未登录仍为 `200 + code=401`（契约不变）| 实测 |
| A7 | 63/63 回归 + 新增用例全绿 | mvn test |
| A8 | 零 DB 改动 / 零新增 Tool / 零前端改动 / **既有业务 Controller·Service·Mapper·XML 零改动** | `git diff --name-only` 逐项核对 |
| A9 | Tool 方法签名**仍无身份参数**（模型无法填）| 代码审查 |
| **A10** | **Tool 校验的是二元身份**（`userId` + `userTable`），非单独 `userId` | §6.2 **#2/#3** 单测：合法 `userId` + 伪造域 → 必须抛异常 |
| **A11** | 既有业务接口行为不变（`/assessmentRecord/list` 等仍按原样工作）| 抽样实测 3 个既有端点 |

### 关键验收实验（必须做）

```text
同一句「我最近的心理测评记录」
    ├─ student  a1 → 200 + "1 条，试卷1，15/100"     ✅ 数据正确
    ├─ counselor a1 → 403 + 无任何数据                ✅ 修复点
    └─ admin       → 403 + 无任何数据                ✅ 修复点
                ↓
        对照 Step 12 之前的实测记录
        （counselor a1 曾返回 15/100）
        ⇒ 证明回归被真实修复，而非仅代码层面
```

---

## 九、不做什么 + 已登记项

| 类型 | 内容 |
| --- | --- |
| ❌ 不做 | `DataScope` 修复（② 规则未定）· 新增 Tool · DB 改动 · 前端改动 · Memory · RAG |
| 📌 登记 | **UX-AGENT-ROLE-UNAVAILABLE**：咨询师/管理员登录后仍可见 Dashboard 的 AI 区块，点击会得到 403 提示。属体验问题，后端已阻断数据，**留待前端阶段处理** |
| 📌 登记 | **SEC/AUTH-LOGIN-ERROR-STATUS**（沿袭）：登录失败返回 `code=500`，应为 401 |
| 📌 登记 | **DATA-SCOPE-COUNSELOR-SCOPE**：`assessment_record` 的咨询师数据范围规则未定义（P1 待定，见路线审查 §三）|

---

## 十、风险与回滚

| 风险 | 概率 | 影响 | 缓解 |
| --- | :-: | --- | --- |
| 既有测试因签名变更失败 | 🟠 中 | 需改测试 | 逐条说明理由，不得静默改断言 |
| 未来新增角色被白名单挡住（若本应放行）| 🟡 低 | 需改白名单 | **这是期望行为**（白名单 = fail-closed）|
| `tableName` 在某些边缘请求为 `null` | 🟡 低 | 误拒 | 已设计为 403；若实测出现，需查根因而非放宽 |
| `ForbiddenException` 在 `@RestControllerAdvice` 之前被吞 | 🟡 低 | 500 而非 403 | 实测 A1/A2 直接验证 HTTP 码 |

**回滚**：本批为**新增校验**，回滚 = `git revert` 单个 commit，无数据迁移、无 schema 变更。

---

## 十一、裁定结果（用户 2026-10-25 · 已定案）

| # | 问题 | 裁定 | 落地含义 |
| :-: | --- | :-: | --- |
| **1** | Controller 拒绝形态 | ✅ **A. `403 ForbiddenException`** | `401 = 未认证` / `403 = 已认证但无 Agent 权限` / `200 = 正常 Agent 请求`；**不**把授权拒绝包装成「Agent 正常回答」|
| **2** | `AgentPrompts` 身份说明 | ✅ **A. 不加** | **Prompt 只负责行为表达，不负责授权**：安全边界 = 服务端身份 → Controller → Tool，而不是 → Prompt → 希望 LLM 自觉 |
| **3** | 新增 ToolContext 伪造测试 | ✅ **采纳（复核新增）** | 见 §6.2 全 10 行矩阵 + §7.1/#2-#3 + §八 A10 |
| **4** | A5 证据方式 | ✅ **改为「调用次数 = 0」** | 耗时**不作**主要证据（网络抖动/快速失败/缓存都可能短耗时）|
| **5** | 「不改 Controller」表述矛盾 | ✅ **已修正** | 见 §5.1：**不修改既有业务 Controller/Service/Mapper/XML；允许修改 `AgentController`/`AgentService`** |

---

## 十一之二、复核意见落实（用户 2026-10-25 代码/文档复核）

| # | 复核意见 | 落实 |
| :-: | --- | --- |
| ① | 「L1 改 Controller」与「不改任何 Controller」自相矛盾 | ✅ §5.1 新增**变更边界表**，逐类声明允许/禁止，并注明「此处以本节为准」|
| ② | `<100ms` 不能证明未调用 LLM | ✅ A5 改为 **`AgentModel.answer()` 调用次数 = 0**（E7 计数），耗时降为辅助观察 |
| ③ | 缺少 ToolContext 身份伪造测试 | ✅ §6.2 扩为 **10 行组合矩阵**（含 #2/#3「合法 userId + 伪造域」），§7.1 要求全覆盖，新增验收 **A10** |
| ④ | L2 不要一开始就做权限矩阵 / RBAC / ToolPermission 表 | ✅ 保持最简：**仅 `ALLOWED_TABLE` 常量 + 一个 if**，不做任何权限框架 |
| ⑤ | 边界：只解决身份域，不顺手修 DataScope / 不扩展 Counselor Tool / 不碰 Memory·RAG | ✅ §二 非目标 + §九 已逐条锁定 |

---

## 十二、执行顺序（批准后）

```text
① 新增 AgentIdentity（值对象 + from）
        ↓
② AgentService 契约改为 AgentIdentity
        ↓
③ AgentController 加 L1 白名单（403）
        ↓
④ AssessmentTools 加 L2 域校验（ALLOWED_TABLE）
        ↓
⑤ 单测：
     · AgentIdentityTest（解析 / 类型污染）
     · AssessmentToolsTest —— §6.2 全 10 行，
       🔴 重点 #2/#3「合法 userId + 伪造域」必须抛异常
        ↓
⑥ 端到端测试（含 E7：AgentModel.answer() 调用次数 = 0）
        ↓
⑦ mvn test → 63/63 + 新增全绿
        ↓
⑧ 部署新 jar
        ↓
⑨ 🔴 运行时对照实验（4 组）：
     · student  a1 → 200 + "1 条，试卷1，15/100"   ← 数据正确
     · counselor a1 → 403 + 无任何学生数据
     · admin       → 403 + 无任何学生数据
     · 无 token    → 200 + code=401（契约不变）
   并核对日志：无 `[AGENT] 对话成功`（证明未进模型层）
        ↓
⑩ staged review → 用户复核 → commit → push
```

---

## 十三、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `1bc24a5` / `1bc24a5` · LEAD 0 |
| worktree | **CLEAN** |
| 代码改动 | **0**（本批为设计）|
| DB / 前端 / pom | **0 改动** |
| 设计状态 | ✅ **已批准（含 3 项复核修正已落实）** |
| 文档 | 新增本文件 |
| 上一批 | Step 8 ✅ CLOSED · 路线审查 ✅ 已裁定 |

> **📌 批准记录（用户 2026-10-25）**：
> §十一 #1 = **A（403）** · #2 = **A（Prompts 不加）**；
> 并要求实现前先修正：**① 变更边界表述 ② A5 证据方式 ③ 新增 ToolContext 伪造测试** ——
> 三项已全部落实（见 §5.1 / §八 A5 / §6.2 + §7.1 + A10）。

**本文件已定稿，未写任何实现代码 —— 下一步进入实现阶段（§十二）。**
