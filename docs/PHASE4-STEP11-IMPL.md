# Phase 4 / Step 11 · Spring AI 最小 Agent 闭环 —— 实现报告

> **性质**：实现（✅ **已实现 · ✅ A 修正已落实 · 🚧 已 staged · 未 commit**）
>
> **v2 修正（用户 2026-10-23 裁定 A）**：身份上下文非法不再返回结构化 error，
> 改为 **抛 `IllegalStateException` + `spring.ai.tools.throw-exception-on-error=true`**
> ⇒ 安全失败**终止** Agent 调用（`code=500`），不被模型包装成正常回答。详见 §十二。
> **基线**：`HEAD = 9ae23ed`（Preflight 已推送）· 改动 **12 文件 +915 行 · 全部新增**
> **上游**：`PHASE4-STEP11-DESIGN.md`（用户 2026-10-23 批准含 7 条修订）
> **目标**：证明「LLM 能通过受控 Tool 访问既有业务数据，且安全边界成立」

---

## 一、结论：**PASS**

| 项 | 结果 |
| --- | :-: |
| 编译 | ✅ |
| 全量回归 | ✅ **77/77**（原 63 + 新增 14）|
| 部署 | ✅ `6680 → 9208`（jar `14:15:04`）|
| 真实 DeepSeek 端到端 | ✅ a1 答「1 条 / 试卷1 / 15 分」，**2.47s** |
| 无数据用户不编造 | ✅ a2 答「暂无记录（共 0 条）」|
| 安全边界 | ✅ Tool 无 userId 参数 · ToolContext 不泄露 · 未认证 401 · 空/Ctrl 400 |
| 既有代码改动 | **0**（12 文件全为新增）|

---

## 二、文件清单（12 文件 · +915 行 · 0 删除）

### 2.1 主代码（10 个，全部 `A`）

| # | 文件 | 说明 |
| :-: | --- | --- |
| 1 | `com/agent/AgentModel.java` | 模型调用边界（接口，~35 行含注释）|
| 2 | `com/agent/SpringAiAgentModel.java` | Spring AI 实现（厂商细节只在此处）|
| 3 | `com/agent/AgentService.java` | 编排：userId → ToolContext → AgentModel |
| 4 | `com/agent/AssessmentTools.java` | 唯一 `@Tool`（ToolContext fail-closed）|
| 5 | `com/agent/AgentPrompts.java` | System Prompt 常量（服务端固定）|
| 6 | `com/agent/AgentChatRequest.java` | 请求 DTO |
| 7 | `com/agent/AssessmentRecordView.java` | LLM-safe 视图（record）|
| 8 | `com/agent/AssessmentRecordToolResult.java` | 结果 DTO（`records/total/truncated/error`）|
| 9 | `com/agent/AgentController.java` | `POST /agent/chat`（**唯一接触 request**）|
| 10 | `com/config/AgentConfig.java` | Bean 装配（POJO 不标 `@Component`）|

### 2.2 测试（2 个）

| # | 文件 | 用例 |
| :-: | --- | :-: |
| 11 | `com/agent/AssessmentToolsTest.java` | **7**（fail-closed 3 + 正常 2 + 结构护栏 2）|
| 12 | `com/regression/AgentControllerRegressionTest.java` | **7**（认证 1 + 入参 2 + 正常 2 + 安全 1 + 上游失败 1）|

---

## 三、7 条修订逐项落实

| # | 修订 | 落实 |
| :-: | --- | --- |
| 1 | `AgentModel` 接口批准；实现类**不得**写死厂商 | ✅ `AgentModel` + `SpringAiAgentModel`；`AgentService` 只见接口；`AgentConfig` 注入 `ChatModel`（Spring AI 抽象）而非 `DeepSeekChatModel` |
| 2 | message 上限 **1000**；若已有统一规范则遵循 | ✅ 调研确认**项目无任何请求长度规范**（无 `@Size`/`@Length`/`length()` 校验）⇒ 采用 **1000**；在 `AgentController` 显式校验，**未新造通用 Validator** |
| 3 | Tool **不允许静默截断** | ✅ 返回 `AssessmentRecordToolResult(records, total, truncated, error)`；`truncated = total > records.size()` 自动推导；`MAX_RECORDS=10` 注释明确「工程上限 ≠ 业务总数」 |
| 4 | 包名 `com.agent` | ✅ 全部 Agent 类在 `com.agent`；`AgentConfig` 在 `com.config`（与既有 `InterceptorConfig` 同层）|
| 5 | Tool **不承载业务权限规则** | ✅ Tool 内**无任何角色/归属分支**（无 `if (userId == 1)`）；身份只来自 ToolContext，`eq(student_id, userId)` 是**查询条件**而非权限策略；规则仍由「身份来源不可伪造」保证 |
| 6 | LLM-safe DTO + 最小必要字段 | ✅ `AssessmentRecordView(paperName, score, paperTotalScore)`；**不含** `id/studentId/createdAt/submittedAt/recordNo`（用例 7 反射断言）|
| 7 | 不提前引入 RAG/Memory/Planning/Safety | ✅ 无 ChatMemory、无向量库、无 advisor、无多 Tool、无流式 |

---

## 四、🔴 实现中发现的重要安全事实（Design 阶段未知）

### 4.1 既有 `queryPage` 会带出学生身份证号

`AssessmentRecordDao.xml` 的 `selectListView` **JOIN `student` 表**并返回：

```text
studentName · studentPhone · studentIdCardNo · studentEmail · studentAvatarUrl
```

若 Tool 走 `queryPage`（看似"复用既有入口"更规范），这些字段会**进入应用内存**，
即使最终不返回给模型，也构成不必要的数据暴露面（且可能被日志/异常带出）。

**决策**：Tool 改用 `lambdaQuery().select(paperId, totalScore)` —— **只查两列，不 JOIN**。

> 这是"复用既有 Service"与"最小数据面"之间的一次权衡，结论是**安全优先**。

### 4.2 MyBatis-Plus `lambdaQuery()` 不接受字符串列名

`LambdaQueryChainWrapper.select("paper_id")` / `.eq("student_id", ...)` **编译失败**
（只接受 `SFunction`）。已改为方法引用：
`select(AssessmentRecordEntity::getPaperId, ...)` / `eq(AssessmentRecordEntity::getStudentId, userId)`。
⇒ 类型安全，且列名变更会被编译器捕获。

---

## 五、最终调用链（实测生效）

```text
POST /agent/chat {"message":"我有几条测评记录？"}
  ↓ AuthorizationInterceptor（/** 全拦 → 未认证 200+401）
AgentController
  │ message 校验（非空 / ≤1000）
  │ userId = CurrentUserProvider.requireCurrentUserId(request)   ← 唯一 request 接触点
  ↓
AgentService.chat(message, userId)
  │ ToolContext(Map.of("userId", userId))
  ↓
AgentModel（SpringAiAgentModel）
  │ chatClient.prompt().system(常量).user(message).tools(tools).toolContext(ctx).call().content()
  ↓
DeepSeek ──(tool_call)──→ ToolCallingManager ──→ AssessmentTools.getMyAssessmentRecords(ToolContext)
                                                   │ 无业务参数 ⇒ 模型无从填写 userId
                                                   │ total = count(student_id = userId)
                                                   │ select(paperId, totalScore) LIMIT 10
                                                   │ 批量取 paper.name / paper.total_score
                                                   ↓
                                                 MySQL
  ↓ ToolResponseMessage（仅 records/total/truncated）
LLM → 自然语言
  ↓
R.ok().put("answer", ...)
```

---

## 六、测试：**77/77**

```text
30 IdentityAuthorizationRegressionTest       ← 既有，未改动
17 Phase2CoreRegressionTest                  ← 既有，未改动
16 PasswordHasherTest                        ← 既有，未改动
 7 AssessmentToolsTest                       ← 新增
 7 AgentControllerRegressionTest             ← 新增
────────────────────────────────────────────
77 BUILD SUCCESS
```

### 6.1 新增用例覆盖

| 类 | 用例 | 关键断言 |
| --- | --- | --- |
| Tools | 1 | `toolContext=null` → denied，records 空 |
| Tools | 2 | 缺 `userId` → denied |
| Tools | 3 | `userId` 为 `String` / `Long` → denied（**不做类型强转兜底**）|
| Tools | 4 | a1 → 1 条，试卷1 / 15 / 100，`truncated=false` |
| Tools | 5 | a2 → 空 + `total=0`（不报错）|
| Tools | **6** | **【护栏】所有 `@Tool` 方法参数只允许 `ToolContext`** |
| Tools | **7** | **【护栏】视图不含 `id/studentId/createdAt/submittedAt/recordNo`** |
| Ctrl | 1 | 未登录 → 200 + code=401，**未触达模型层** |
| Ctrl | 2 | 空 / 空白 / 缺字段 → 400，未触达模型层 |
| Ctrl | 3 | 1001 字 → 400；**1000 字边界 → 200** |
| Ctrl | 4 | 正常 → code=0 + answer 透传 |
| Ctrl | **5** | **`toolContext.userId` 等于登录用户 id（Integer）** |
| Ctrl | **6** | **tools 为 `AssessmentTools`；system prompt 为常量且不含用户输入** |
| Ctrl | 7 | 上游异常 → code=500 + 统一文案 + **不含异常细节** |

### 6.2 回归隔离（零真实 API）

```java
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
```

`AgentModel` 被 `CapturingFakeAgentModel`（`@Primary`）替换 ⇒
`mvn test` **不产生任何 DeepSeek 请求**（77 项 25s 内完成，无网络等待）。

---

## 七、部署与真实端到端

| 项 | 旧 | 新 |
| --- | --- | --- |
| PID | `6680`（12:54:41）| **`9208`**（14:15:04）|
| jar | — | **`14:15:04` / 154,642,666 B** |
| 8080 | — | 就绪 7s |
| ERROR | — | **0** |

### 7.1 真实 DeepSeek（a1 有数据）

```json
{"code":0,"answer":"你目前共有 **1 条**心理测评记录：\n- 试卷名称：考试试卷1\n- 得分：**15** 分（满分 100 分）\n这也是你最新的一次记录。..."}
```

日志：`[AGENT] 对话成功 userId=1 msgLen=19 answerLen=104 elapsed=2470ms`

### 7.2 无数据用户（a2）

```json
{"code":0,"answer":"查询结果显示：你目前**暂无心理测评记录**（共 0 条）。..."}
```

日志：`[AGENT] 对话成功 userId=2 msgLen=11 answerLen=82 elapsed=2040ms`

> **关键**：两个不同用户得到**不同结果**，且 a2 **不编造** ——
> 证明工具真的查了 DB、数据范围按身份生效、模型未凭空生成。

### 7.3 失败路径

| 场景 | 实测 |
| --- | --- |
| 未登录 | `HTTP 200 + code=401`（既有契约）|
| `message=""` | `HTTP 400` |

---

## 八、验收对照（Design §12 · 15 项）

| # | 项 | 结果 |
| :-: | --- | :-: |
| ① | 已登录 → 200 + code=0 + answer | ✅ |
| ② | 内容与 DB 一致（1 条 / 试卷1 / 15 分）| ✅ |
| ③ | 日志可见调用记录 | ✅ `[AGENT] 对话成功 userId=... elapsed=...` |
| ④ | 无数据用户 → 「暂无记录」不编造 | ✅ a2 |
| ⑤ | 未登录 → 200 + code=401 | ✅ |
| ⑥ | Session 无 userId → 403 | ✅ 复用 `requireCurrentUserId`（既有契约，未改）|
| ⑦ | Tool 签名无 userId 形参 | ✅ 用例 6 反射护栏 |
| ⑧ | ToolContext 值不进模型视野 | ✅ Preflight smoke（`userId=917` 未出现）+ 本批无参数入口 |
| ⑨ | ctx 缺失/类型错 → error 且不查库 | ✅ 用例 1–3 |
| ⑩ | 查询恒带 `student_id = 当前用户` | ✅ `eq(getStudentId, userId)`，无全表路径 |
| ⑪ | 既有 63/63 | ✅ |
| ⑫ | 新增测试全绿 | ✅ 14/14 |
| ⑬ | `mvn test` 零真实 DeepSeek 请求 | ✅ `FakeAgentModel` 隔离 |
| ⑭ | 既有功能不受影响 | ✅ 63 项全绿 + 实登 3 角色 |
| ⑮ | 部署验证（PID/时间戳/实登）| ✅ `9208` / `14:15:04` |

---

## 九、边界声明

| 未做 | 状态 |
|---|:-:|
| RAG / Embedding / 向量库 | ❌ |
| ChatMemory / 多轮历史 | ❌ |
| 多 Tool / 编排 / 并行 | ❌ |
| 写操作 Tool（预约/留言/保存/删除）| ❌ |
| Streaming SSE | ❌ |
| 前端页面 | ❌ |
| 限流 / 会话管理 | ❌（登记为增强项）|
| 既有 Controller/Service/Entity/Mapper/XML 改动 | ❌ **0** |
| DB / DDL / 种子数据 | ❌ **0** |
| 安全体系（拦截器/守卫/DataScope/契约）| ❌ **0** |
| 升级 Boot 4 | ❌ |

---

## 十、状态

| 项 | 值 |
|---|---|
| 文件 | **12 新增**（10 主 + 2 测试）· **+915 行** · **0 删除** |
| 回归 | **77/77** |
| Git | `HEAD=9ae23ed` · **已 staged 12 文件** · **未 commit** |
| 服务 | PID `9208` · jar `14:15:04` · ERROR=0 |
| DB | 未改动（7/7 BCrypt，测评记录仍 1 条）|
| 下一步 | 等你 reviewed → commit → push |

---

## 十一、建议 commit message

```text
feat(ai/s11): Spring AI 最小 Agent 闭环（ChatClient + Tool + ToolContext）

- 新增 com.agent 分层：AgentModel / SpringAiAgentModel / AgentService /
  AssessmentTools / AgentController / AgentPrompts
- 身份：Controller 经 CurrentUserProvider 取得 userId → ToolContext 注入 Tool，
  Tool 方法签名不含 userId 形参（模型无填写入口）
- Tool：getMyAssessmentRecords 用 lambdaQuery 只查两列（避开 selectListView 的
  student JOIN），返回 records/total/truncated，不做静默截断
- 回归隔离：AgentModel 由 Fake 替身注入，mvn test 零真实 DeepSeek 请求
- 测试 77/77（新增 14：含「Tool 无业务参数」「视图无敏感字段」两条护栏）
- 真实链路：a1 → 1 条/试卷1/15 分（2.47s）；a2 → 暂无记录（不编造）
```

**边界**：`SEC-PLAINTEXT-PASSWORD-STORAGE` 已 CLOSED；本批未触碰任何密码/权限代码。

---

## 十二、v2 修正（裁定 A）：安全失败必须终止，不得由模型代言

### 12.1 问题（用户指出的真实缺口）

v1 实现中身份非法时返回 `AssessmentRecordToolResult.denied(...)`：

```text
Tool 返回 {records:[], total:0, error:"无法确认当前用户身份"}
        ↓ 回喂模型
模型输出「抱歉，我目前无法获取你的测评记录」
        ↓
HTTP 200 + code=0
        ↓
🔴 HTTP 层与监控层都看不到安全事件
```

**可达性**：真实链路**不可达**（`auth_token.user_id NOT NULL` → `requireCurrentUserId` → `AgentService` 已保证 `userId` 非空且为 `Integer`），
但**失败语义本身不合理** —— 安全边界不应由模型做最终解释。

### 12.2 改动（3 处，严格限定范围）

| # | 文件 | 改动 |
| :-: | --- | --- |
| 1 | `AssessmentTools.java` | `return denied(...)` → **`throw new IllegalStateException("agent identity context invalid")`**；`logger.warn` → **`logger.error`**（安全事件级）；日志只记上下文**形态**不记内容 |
| 2 | `application.yml` | 新增 `spring.ai.tools.throw-exception-on-error: true`（注释标明「Step 11 单 Tool 最小实现，非全局异常治理契约」）|
| 3 | `AssessmentToolsTest.java` | 用例 1–3 由「断言 denied」改为 **`assertThatThrownBy`**；新增用例 8 **配置护栏**（断言该属性为 `true`，防止被误删导致静默退化）|

**保留**：`AssessmentRecordToolResult.error` 字段（未来「业务层面可解释失败」仍可用）；
本次只修「**身份安全失败 ≠ 普通业务结果**」，不是废除 error 字段。

### 12.3 🔴 对照实验（一次性探针，已删除）

用真实 DeepSeek + 强制调用会抛异常的 Tool，对比两种 `ToolExecutionExceptionProcessor` 配置：

| 配置 | 实测输出 |
| --- | --- |
| **`throw=true`**（本批） | `THROWN (1820ms)` · `top=ToolExecutionException` · `root=IllegalStateException` · `msg=agent identity context invalid` |
| `throw=false`（v1 行为） | `NO-THROW (2102ms)` · 模型输出：*"I called `readSecret`, and it returned an error... So no secret data was returned... You may need to re-authenticate"* |

> **这就是本次修正的决定性证据**：`throw=false` 时模型把安全失败**包装成正常回答**（用户看到 200）；
> `throw=true` 时异常**穿透**到调用方，可被 Controller 转成 `code=500`。

### 12.4 修正后效果

```text
身份上下文非法
  ↓ Tool: logger.error("[AGENT][SEC] ...") + throw IllegalStateException
  ↓ spring.ai.tools.throw-exception-on-error=true
  ↓ ToolExecutionException 穿透 ChatClient.call()
  ↓ AgentController catch → logger.error("[AGENT] 对话失败 ...")
  ↓ HTTP 200 + code=500 + 统一文案（不泄露内部细节）
```

### 12.5 ⚠️ 适用边界（**不得过度外推**）

> 本批只能确认：**Step 11 的 `AssessmentTools` 身份上下文异常，会通过 Spring AI 异常链终止当前 Agent 调用**，
> 而不是作为正常业务数据回馈给 LLM。

**不能**表述为「开启该配置后，所有 Tool 安全问题都会自动正确处理」。
未来引入 RAG / Memory / 多 Tool / 业务异常后，仍需分别设计：

```text
安全异常 · 业务异常 · 参数错误 · 外部服务异常 · 模型异常
—— 各自应在哪一层终止 / 转换 / 记录
```

### 12.6 修正后验证

| 项 | 结果 |
| --- | :-: |
| 全量回归 | **78/78**（原 63 + Tools 8 + Ctrl 7）|
| 配置护栏用例 | ✅ 断言 `throw-exception-on-error=true` |
| 部署 | `9208 → 32200`（jar `14:31:23`）· 启动 `8.918s` · ERROR=0 |
| 真实链路 a1 | ✅ 「1 条 / 试卷1 / 15 分」|
| 真实链路 a2 | ✅ 「暂无记录（共 0 条）」不编造 |
| 正常路径是否受影响 | ✅ 不受影响（`throw=true` 仅改变**异常**路径）|
