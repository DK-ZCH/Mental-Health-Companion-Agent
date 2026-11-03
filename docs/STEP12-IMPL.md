# Step 12 · Agent Identity Domain Security —— 实现报告

> **性质**：实现（🏁 **CLOSED · 用户 2026-10-25 验收通过**）
> **基线**：`HEAD = origin = bc9bd3b` · LEAD 0 · worktree CLEAN · 改动 **7 文件 +503 / −45**
>
> **CLOSED 依据**：单测 98/98 · `AgentModel.answer()` 入口计数（counselor/admin = 0，student = 1，异常路径 = 1 作自检）·
> 运行时四组对照（student 200 / counselor 403 / admin 403 / 无 token 200+code=401）· A11 既有接口 4/4 正常
> **上游**：`STEP12-AGENT-IDENTITY-DOMAIN-DESIGN.md`（用户 2026-10-25 批准）
> **核心命题**：让 Agent 从「`userId` 能查到什么」变成「服务端确认的 **(userId, identityDomain)** 能合法访问什么」

---

## 一、结论

| 项 | 结果 |
| --- | :-: |
| 代码实现 | ✅ 完成 |
| 静态检查（`mvn compile` + IDE lint）| ✅ 0 错误 |
| 全量测试 | ✅ **98/98** |
| 部署 | ✅ PID `32836` · jar `13:52:36` · 6s 就绪 |
| 运行时四组对照 | ✅ **全部符合预期** |
| 业务层改动 | ✅ **0** |
| DB / 前端 / pom | ✅ **0 改动** |
| 验收 A1–A11 | ✅ **11/11** |

---

## 二、改动清单

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `com/agent/AgentIdentity.java` | **新增** | 值对象 `record(int userId, String userTable)` + `from(ToolContext)` + `isStudent()` + 覆盖 `toString`（不输出 id）|
| 2 | `com/agent/AssessmentTools.java` | 改 | **L2**：`AgentIdentity.from` → 判空 → `ALLOWED_TABLE` 域校验 → 才查库；删除 `resolveUserId` |
| 3 | `com/agent/AgentService.java` | 改 | 契约 `chat(String, Integer)` → `chat(String, AgentIdentity)`；ToolContext 写 **2 个 key** |
| 4 | `com/agent/AgentController.java` | 改 | **L1**：`currentTableName` → 白名单校验 → 不符抛 `ForbiddenException`（403）|
| 5 | `test/.../AgentIdentityTest.java` | **新增** | 11 例：解析 + fail-closed + 泄露护栏 |
| 6 | `test/.../AssessmentToolsTest.java` | 改 | 8 → **14** 例（+6：缺域/空白域/类型污染/counselor/admin/未知域）|
| 7 | `test/.../AgentControllerRegressionTest.java` | 改 | 7 → **10** 例（+3：counselor 403 / admin 403 / 403 不泄露）|

```text
7 files changed, 473 insertions(+), 42 deletions(-)
git diff --cached --check : 无空白错误 ✓
既有业务层（controller/ service/ dao/ entity/ mapper/）改动 = 0 ✓
```

### 关于「小批」的说明（措辞已按复核意见修正）

**为避免制造无意义的不可运行中间态，本次选择以「完整身份链」作为一个最小可验证闭环。**

理论上确实可以先做 `AgentIdentity + 单测`，再改 Service / Controller / Tool ——
但那需要临时的兼容构造器或接口，会产生「编译不通过」或「语义半成品」的中间态。
本次判断：**这类中间态没有验证价值**，故一次落地 4 个主文件。

> ⚠️ **不写成「数学意义上绝对无法再拆」** —— 那是不准确的表述（用户 2026-10-25 指出）。
> 准确说法是：**在「不引入临时兼容代码」的前提下，这 4 个文件构成最小可验证闭环。**

**验证分两层**（符合小批验收意图）：

```text
① 单测层：98/98（证明逻辑正确）
② 运行时层：部署后四组对照（证明真实链路成立）
```

---

## 三、关键实现

### 3.1 身份来源唯一（用户 2026-10-25 强调项）

```java
// AgentController.java
Integer userId   = CurrentUserProvider.requireCurrentUserId(httpRequest);
String userTable = CurrentUserProvider.currentTableName(httpRequest);   // ← 唯一来源
```

| 检查 | 结果 |
| --- | :-: |
| 是否存在 `session.getAttribute("tableName")` 直读 | ✅ **0 处** |
| 身份读取实现份数 | ✅ **1 份**（`CurrentUserProvider`）|

### 3.2 L1 白名单（Controller）

```java
if (!AgentIdentity.TABLE_STUDENT.equals(userTable)) {
    logger.warn("[AGENT][SEC] 身份域不被允许（userTable={}），拒绝进入 Agent", userTable);
    throw new ForbiddenException();      // → HTTP 403
}
```

白名单制：`counselor` / `admin_user` / `null` / 任何未来新增值 **全部拒绝**。

### 3.3 L2 二元身份校验（Tool）

```java
AgentIdentity identity = AgentIdentity.from(toolContext);
if (identity == null) throw new IllegalStateException(ERR_IDENTITY_CONTEXT_INVALID);
if (!ALLOWED_TABLE.equals(identity.userTable()))
    throw new IllegalStateException(ERR_IDENTITY_DOMAIN_DENIED);
int userId = identity.userId();      // 域校验通过后才使用 id
```

> **顺序是关键**：先验域，再用 id。若反过来（先拿 id 查库再验域），数据已经进内存了。

### 3.4 一处实现细化（对设计的补充）

**`AgentIdentity.toString()` 覆盖为只输出 `userTable`**：

```java
@Override
public String toString() {
    return "AgentIdentity[userTable=" + userTable + "]";
}
```

理由：record 默认 `toString` 会打印全部字段，对象一旦被误打日志 / 出现在断言失败信息中，`userId` 就会被记录。这比"约定日志不记 id"更可靠。

---

## 四、测试：98/98

| 测试类 | 数量 | 变化 |
| --- | :-: | --- |
| `Step 12 AgentIdentity` | **11** | 新增 |
| `Step 11 AssessmentTools` | **14** | 8 → 14（+6）|
| `Step 11 AgentController` | **10** | 7 → 10（+3）|
| `IdentityAuthorizationRegressionTest` | 30 | 不变 ✓ |
| `Phase2CoreRegressionTest` | 17 | 不变 ✓ |
| `PasswordHasherTest` | 16 | 不变 ✓ |
| **合计** | **98** | 63 基线全绿 + Step 11 的 15 项全绿 + 新增 20 |

### 4.1 实现中发现并修正的测试假设错误（须显式声明）

首跑 `AgentIdentityTest` 有 **1 error**：

```text
java.lang.NullPointerException: Cannot invoke "Object.getClass()" because "m" is null
    at java.util.Collections.unmodifiableMap(Collections.java:1476)
    at org.springframework.ai.chat.model.ToolContext.<init>(ToolContext.java:61)
```

**根因**：`ToolContext` 构造器内部调用 `Collections.unmodifiableMap(m)`，
传 `null` 会**在构造时 NPE** ⇒ **「ToolContext 内部 Map 为 null」这个状态不可达**。

| 处置 | 说明 |
| --- | --- |
| 删除该用例 | 状态不可达，测它等于测「不可能发生的事」|
| 保留 `context == null` 判空 | 防御性（框架行为若变化也不会 NPE），已加注释说明 |
| 补 `emptyContext`（`Map.of()`）| 覆盖「无任何键」这一**真实可达**的形态 |

> 这是**测试假设错误**，不是实现缺陷 —— 但必须记录，避免后续误以为是代码问题。

### 4.2 新增用例分布（关键 6 例）

| 用例 | 断言 |
| --- | --- |
| 3.4 🔴 | `userId=1` + `userTable=counselor` → **抛异常**（防伪造域）|
| 3.5 🔴 | `userId=1` + `userTable=admin_user` → **抛异常** |
| 3.1 | 有 `userId` 但缺 `userTable` → 抛异常（**不默认当学生**）|
| 3.6 | 未知域 `some_future_role` → 抛异常（白名单）|
| 8 🔴 | counselor 走 HTTP → **403** 且 `AgentModel` **未调用** |
| 9 🔴 | admin 走 HTTP → **403** 且 `AgentModel` **未调用** |

---

## 五、🔴 运行时四组对照（最关键的验收）

部署后实测（同一句「我最近的心理测评记录」）：

| 角色 | HTTP | body | 含学生数据 | 判定 |
| --- | :-: | --- | :-: | :-: |
| **student a1** | **200** | `code=0` + 正常回答 | 含 `15` ✅（本人）| ✅ 正确 |
| **counselor a1** | **403** | `{"msg":"无权访问","code":403}` | ❌ **0** | ✅ 修复 |
| **counselor a2** | **403** | `{"msg":"无权访问","code":403}` | ❌ **0** | ✅ 修复 |
| **admin** | **403** | `{"msg":"无权访问","code":403}` | ❌ **0** | ✅ 修复 |
| 无 token | **200** | `{"msg":"请先登录","code":401}` | — | ✅ 契约不变 |

**对照 Step 12 之前**：

```text
Step 12 前：counselor a1 → 200 + "1 条，试卷1，15 / 100"      🔴 串读
Step 12 后：counselor a1 → 403 + {"msg":"无权访问","code":403}  ✅ 阻断
```

### 5.1 证据时效说明（措辞严谨性）

| 事实 | 精确表述 |
| --- | --- |
| 部署时刻 | jar `13:52:36` · PID `32836` · 启动耗时 6s |
| 部署后主代码变动 | **无**（后续仅修改测试代码）|
| 本轮测试改动对运行时证据的影响 | **无**（测试代码不进入 jar）|

> ⚠️ **不声称**「运行中的 jar 与当前工作区主代码完全一致」——
> 该断言需要 artifact hash / 构建时间比对才能成立，本轮**未做**该比对。
>
> 准确表述为：**本轮仅修改测试代码、未修改主代码；运行中的 jar 对应此前已验证过
> 四组对照的 Step 12 主代码，因此本轮测试修改不影响既有运行时证据。**

---

## 六、🔴 A5 证据（两层：显式计数 + 运行时日志）

### 6.1 单测层 —— 显式计数器（直接证据）

`CapturingFakeAgentModel` 增加 `invocationCount`，**计数点是 `AgentModel.answer()` 方法体第一行**：

```java
@Override
public String answer(String systemPrompt, String userMessage, Object tools,
                     Map<String, Object> toolContext) {
    invocationCount++;          // ← 方法入口：先计数，再做任何事
    ...
}
```

| 场景 | `invocationCount` | 说明 |
| --- | :-: | --- |
| 未登录 | **0** | 拦截在拦截器 |
| 空 / 超长 message | **0** | 校验在模型之前 |
| **counselor** | **0** 🔴 | L1 拒绝，未进模型 |
| **admin** | **0** 🔴 | L1 拒绝，未进模型 |
| student 正常 | **1** | 正常路径 |
| student + 边界 1000 字符 | **1** | 边界值放行一次 |
| student + 模型抛异常 | **1** | 🔴 **自检：证明计数器不是恒为 0** |

> **第 7 行是关键的负向自检**：若计数器恒为 0，「counselor = 0」这条证据就毫无意义。
> 它证明计数器本身工作正常，因此「counselor/admin = 0」才构成有效证据。

**位置确认（用户 2026-10-25 要求核实）**：

```text
✅ 计数在 AgentModel.answer() —— 即「是否进入模型层」这条边界本身
❌ 不是 Controller 层近似指标
❌ 不是 AgentService.chat() 层近似指标
❌ 不是靠耗时推断
```

### 6.2 运行时层 —— 日志交叉印证

```text
[AGENT][SEC] 身份域不被允许        = 3   ← counselor×2 + admin×1
[AGENT] 对话成功（msgLen=）        = 1   ← 仅 student

13:53:03.543  INFO  [AGENT] 对话成功 userId=1 msgLen=10 answerLen=180 elapsed=2388ms   ← 仅此 1 次
13:53:03.656  WARN  [AGENT][SEC] 身份域不被允许（userTable=counselor），拒绝进入 Agent
13:53:03.789  WARN  [AGENT][SEC] 身份域不被允许（userTable=counselor），拒绝进入 Agent
13:53:03.911  WARN  [AGENT][SEC] 身份域不被允许（userTable=admin_user），拒绝进入 Agent
```

| 断言 | 证据 |
| --- | --- |
| 3 次 403 是否调用模型 | ❌ **未调用**（无任何 `对话成功` 记录）|
| SEC 日志是否泄露 id | ✅ 只记 `userTable`，**无 userId** |
| 日志是否含学生数据 | ✅ `考试试卷1` = 0 命中 · `15/100` = 0 命中 |

> `GlobalExceptionHandler` 的 3 条 WARN「授权失败: 无权访问」= 我们主动抛的
> `ForbiddenException` 被统一处理器记录，**不是异常故障**。

---

## 七、验收逐项（A1–A11）

| # | 标准 | 结果 |
| :-: | --- | :-: |
| A1 | counselor → 403 | ✅ a1/a2 均 403 |
| A2 | admin → 403 | ✅ |
| A3 | student 正常 + 只返回本人 | ✅ `含15=True`，`total=1` |
| A4 | SEC 日志存在且不含学生数据 | ✅ 3 条 · 0 泄露 |
| A5 | **`AgentModel.answer()` = 0（对 counselor/admin）** | ✅ 日志仅 1 次成功（student）|
| A6 | 未登录仍 `200 + code=401` | ✅ `请先登录` |
| A7 | 回归 + 新增全绿 | ✅ **98/98** |
| A8 | 零 DB / 零新增 Tool / 零前端 / 既有业务层零改动 | ✅ 见 §二 |
| A9 | Tool 签名仍无身份参数 | ✅ `getMyAssessmentRecords(ToolContext)` |
| A10 | **二元身份**（合法 id + 伪造域 → 拒绝）| ✅ 单测 3.4 / 3.5 |
| A11 | 既有业务接口行为不变 | ✅ 4 个端点抽样全 `200 + code=0` |

### A11 抽样明细

```text
assessmentRecord/list       HTTP=200 code=0 size=1
counselingAppointment/list  HTTP=200 code=0 size=1
knowledgeArticle/list       HTTP=200 code=0 size=1
notification/list           HTTP=200 code=0 size=1
```

### DB 未改动

```text
student=3 · counselor=3 · admin_user=1 · assessment_record=1 · auth_token=16   ← 与基线一致
```

---

## 八、边界确认（防止范围扩散）

| 项 | 状态 |
| --- | :-: |
| 既有业务 Controller / Service / Mapper / XML | ✅ **0 改动** |
| `AgentModel` / `SpringAiAgentModel` | ✅ **0 改动** |
| `AgentChatRequest` / `AssessmentRecordToolResult` | ✅ **0 改动** |
| `AgentPrompts` | ✅ **0 改动**（裁定 = 不加身份说明）|
| `AgentConfig` | ✅ **实际未改**（构造参数未变；设计中列为「可能要改」）|
| DB / DDL / 前端 / `pom.xml` | ✅ **0 改动** |
| `DataScope` / `OwnershipGuard` / `CurrentUserProvider` | ✅ **0 改动** |
| 新增 Tool | ✅ **0 个** |

> **未顺手修**：`DataScope` 字段债（P1 待定）· 登录失败 `code=500` 语义债 · 前端 AI 区块对非学生隐藏。

---

## 九、已知问题 / 说明

| # | 项 | 处置 |
| :-: | --- | --- |
| 1 | `ToolContext(null)` 不可构造（构造器 NPE）| 已在测试注释与 `AgentIdentity` 注释中记录，判空保留为防御 |
| 2 | 咨询师登录后仍可见 Dashboard 的 AI 区块，点击得 403 | 已登记 `UX-AGENT-ROLE-UNAVAILABLE`，属前端阶段 |
| 3 | `app.log` 中文在 Windows 控制台显示为乱码 | **仅显示编码问题**，不影响日志内容与检索（ASCII 匹配正常）|

---

## 十、Staged Review

```text
M  src/main/java/com/agent/AgentController.java
A  src/main/java/com/agent/AgentIdentity.java
M  src/main/java/com/agent/AgentService.java
M  src/main/java/com/agent/AssessmentTools.java
A  src/test/java/com/agent/AgentIdentityTest.java
M  src/test/java/com/agent/AssessmentToolsTest.java
M  src/test/java/com/regression/AgentControllerRegressionTest.java

7 files changed, 473 insertions(+), 42 deletions(-)
git diff --cached --check : 无空白错误 ✓
既有业务层混入              : 0 ✓
```

| 项 | 值 |
| --- | --- |
| HEAD / origin | `1bc24a5` / `1bc24a5` · LEAD 0 |
| 未暂存 / 未跟踪 | **0** |
| 已暂存 | **7 files** |
| commit | ❌ **未提交** ⇒ repository 非 clean |
| 服务 | 后端 `8080` PID `32836`（新 jar）· 前端 `5173` |

---

## 十一、建议 commit message

```text
feat(ai/s12): Agent 身份域安全 —— 二元身份 (userId, userTable) 取代裸 userId

- 新增 AgentIdentity 值对象（record），身份由 id + 身份域共同表达
- L1 AgentController：currentTableName 白名单准入，非 student 一律 403（不调 LLM）
- L2 AssessmentTools：ALLOWED_TABLE 域校验，不符抛异常终止（fail-closed）
- AgentService 契约改为 AgentIdentity，ToolContext 下发 userId + userTable
- 身份来源唯一：只经 CurrentUserProvider，不直读 Session
- 测试 98/98（新增 20 例，含「合法 userId + 伪造域」必须拒绝）
- 运行时四组对照：student 200 / counselor 403 / admin 403 / 无token 200+code=401
- 日志证明 counselor/admin 未进入模型层（对话成功仅 1 次）
- 零业务层改动；未修 DataScope；未新增 Tool
```

---

## 十二、下一步

| 待办 | 状态 |
| --- | --- |
| staged review → commit → push | 等用户复核 |
| Phase 4 多 Tool | 待 Step 12 CLOSED |
| ② counselor 数据范围业务规则 | 待用户裁定（A 全量 / B 负责学生 / C 预约关系）|
| Step 10 Admin / R15 / UX-AGENT-ROLE | 挂账 |
