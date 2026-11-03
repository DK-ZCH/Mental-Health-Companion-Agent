# Phase 4 · Observability 小批设计（只解决「看得见 Tool 执行」）

> **性质**：🏁 **CLOSED · 用户 2026-11-03 验收通过 · 正式基线 `057c774`**
>
> **验收结论**：Spike 4 条 API 事实通过 ✅ · 旁路原则（Recorder fail-silent / Tool 异常原样抛出）✅ ·
> 隐私边界有可证伪测试（M3）✅ · 299/299（提交后复跑）✅ · M1–M4 均产生预期失败 ✅ ·
> 真实 LLM 端到端 Tool trace ✅
> **基线**：`HEAD = origin = 16cd8bd` · LEAD 0 · worktree CLEAN
> **上游**：`PHASE4-CAPABILITY-INVENTORY.md` §七-A（用户 2026-11-03 裁定：三项全批准）

---

## 一、目标与非目标

### 1.1 只解决一件事

```text
用户请求 → Agent → Tool 调用 → Agent 回答
                      │
                      ├─ toolName
                      ├─ durationMs
                      └─ success / failure
```

回答面试/排障问题：**「模型到底调了哪个工具、耗时多少、有没有失败」**。

### 1.2 明确不做（用户裁定）

| 不做 | 原因 |
| --- | --- |
| ❌ Memory / RAG / Counselor Agent | 前置未解（见盘点 §四）|
| ❌ 第二个写 Tool | 用户明确暂停 |
| ❌ 压测 / 性能基准 | 本批不做吞吐与延迟压测 |
| ❌ 大规模日志系统（ELK/Loki/APM）| 引入依赖与运维面 |
| ❌ 前端改造 | 零前端 |
| ❌ 新依赖 / 新表 / 新 HTTP 端点 | 不新增对外表面 |
| ❌ 记录工具入参、返回内容、用户消息 | **隐私硬约束** |

---

## 二、现状与缺口（事实）

| 项 | 现状 |
| --- | --- |
| 已有日志（`AgentController`）| `userId / msgLen / answerLen / safetyTriggered / elapsed` |
| 已有日志（`AgentService`）| 仅安全兜底：`rule / resourceStatus` |
| **缺失** | **tool 名、单工具耗时、单工具成功/失败** |
| 工具循环位置 | Spring AI **1.1.8**，工具循环在 `ChatModel` 内部（`SpringAiAgentModel` 只传对象）|
| 依赖 | 无 actuator / micrometer（pom 实测）|

> 现状能回答「这次对话多久」，**不能**回答「模型调了哪个工具、调对没有」。

---

## 三、拦截点选型

| 方案 | 做法 | 评价 |
| --- | --- | --- |
| **A 包装 `ToolCallback`（选定）** | 用 `MethodToolCallbackProvider` 把工具对象转成 `ToolCallback[]`，逐个包一层计时/计数，再交给 `ChatClient` | ✅ 单一拦截点、Tool 保持纯 POJO、新增 Tool 自动被覆盖 |
| B 每个 `@Tool` 方法内埋点 | 9 处 try/finally | ❌ 重复、易漏；新增工具忘记埋点 |
| C AOP `@Around` | 切 `@Tool` 注解 | ❌ 工具是 `@Bean` 返回的 POJO，代理可能让 Spring AI 扫不到注解 |
| D 只记录整轮耗时 | 现状 | ❌ 答不了「哪个工具」|

### 3.1 已核实的 API（`spring-ai-model:1.1.8`）

```java
public interface ToolCallback {
    ToolDefinition getToolDefinition();
    default ToolMetadata getToolMetadata();
    String call(String toolInput);
    default String call(String toolInput, ToolContext toolContext);
}

public final class MethodToolCallbackProvider {
    ToolCallback[] getToolCallbacks();
    static Builder builder();   // builder().toolObjects(...)
}
```

### 3.2 ✅ Spike 已完成（2/2 通过）—— 4 条 API 事实

| # | 事实 | 对设计的影响 |
| :-: | --- | --- |
| 1 | **`ChatClient.tools(Object...)` 不接受 `ToolCallback`** —— 会尝试扫描 `@Tool` 并抛 `No @Tool annotated methods found`，异常消息明确提示改用 `.toolCallbacks()` | 🔴 **必须把 `SpringAiAgentModel` 改成 `.toolCallbacks(ToolCallback...)`**；这是本方案唯一的 API 修正 |
| 2 | 带参数的 `@Tool` 入参是 **JSON 字符串**（如 `{"input":"hi"}`），不是裸文本 | 测试构造入参时必须传 JSON；生产由模型生成，无影响 |
| 3 | 工具返回的 `String` 会被 **JSON 序列化**（`OK:42` → `"OK:42"`）| 包装器**不得**改写返回值；测试断言需带引号 |
| 4 | 工具异常被 Spring AI 包装为 **`ToolExecutionException`**（message 保留）| 「原样抛出」= 捕获后 **rethrow 同一实例**，不改类型、不改消息 |

**已验证成立**：
- 包装后 `getToolDefinition().name()` 仍为方法名（`demo`）
- `call(String, ToolContext)` 的上下文仍透传（工具读到 `userId=42`）
- 包装器捕获异常后 rethrow，调用方仍收到异常

> 结论：方案可行，但**必须用 `.toolCallbacks(...)`**；不需要 fallback（逐方法埋点）。
> Spike 已保留为 `Phase4ToolObservabilitySpikeTest`（2 个契约用例），防止未来升级 Spring AI 后行为漂移。

---

## 四、数据模型（严格三项）

### 4.1 记录器 `ToolCallRecorder`

| 方法 | 说明 |
| --- | --- |
| `record(String toolName, long durationNanos, boolean success)` | 唯一写入口；**没有**入参/出参字段 |
| `Snapshot snapshot()` | 测试与排障用：按工具聚合 `calls / failures / totalMs / maxMs` |

- 并发：`ConcurrentHashMap` + `LongAdder`；无锁竞争
- 内存有界：**每个工具一条聚合记录**（当前 9 个），**不保存每次调用的历史**
- 失败也要计数（`success=false`）

### 4.2 日志格式（唯一允许的字段）

```text
[AGENT][TOOL] name=getMyAppointments elapsedMs=123 ok=true
[AGENT][TOOL] name=previewAppointment elapsedMs=8 ok=false
```

**硬约束**：

```text
✅ 只允许 name / elapsedMs / ok 三个字段
❌ 不得出现 toolInput、返回值、用户消息、咨询师姓名、分数、留言内容
❌ 不得出现异常 message（只允许 ok=false）
```

> 失败时日志级别 `WARN`，成功 `INFO`；两者字段完全相同。

---

## 五、失败语义（不可更改）

```text
工具抛异常
  → 记录 ok=false（先记录）
  → 异常【原样抛出】
  → 既有 fail-closed 链路不受影响（域拒绝仍终止本次 Agent 调用）
```

**绝不吞异常**：观测层不允许改变业务/安全语义。

---

## 六、验收矩阵（O1–O9）

| # | 场景 | 期望 |
| :-: | --- | --- |
| **O1** | 正常调用一个工具 | 记录 1 条；`name` = 方法名；`ok=true`；`elapsedMs >= 0` |
| **O2** | 工具抛异常 | 记录 `ok=false`；**异常仍向上抛出**（断言调用方收到异常）|
| O3 | 一次对话未调用任何工具 | **不产生**工具记录 |
| O4 | 一次对话调用两个工具 | 记录 2 条（计数正确）|
| **O5** | 隐私：工具入参与返回都含哨兵串（如 `SENSITIVE-IN` / `SECRET-OUT`）| 日志**均不含**这两个串；记录器也不持有它们 |
| **O6** | 日志格式 | 行匹配 `^\[AGENT\]\[TOOL\] name=\S+ elapsedMs=\d+ ok=(true\|false)$`，**无其它字段** |
| O7 | `ToolContext` 透传 | 工具内仍能读到 `userId`（包装不破坏上下文）|
| O8 | 回归 | 全量 291 不回归；前端/DB/pom **0 改动** |
| O9 | 真实运行 | 真实 LLM 一次工具调用 ⇒ 日志出现对应 `name` |

---

## 七、变异验证（M1–M4）

| # | 变异 | 期望转红 |
| :-: | --- | :-: |
| M1 | 不调用 `record` | O1 / O4 |
| M2 | 永远写 `ok=true` | O2 |
| M3 | 日志里带上 `toolInput` | O5 / O6 |
| M4 | 吞掉异常（记录失败但不抛） | 既有 **fail-closed** 用例（域拒绝）|

> M3 是隐私边界的证明：没有它，「不记录参数」只是口头承诺。

---

## 八、实施顺序

```text
① 设计冻结（本文）
② spike：确认 ToolCallback 包装 + ToolContext 透传 + name 不变（10 分钟）
③ 先写测试 O1–O7（红）
④ 实现 ToolCallRecorder + TimedToolCallback + AgentConfig 装配
⑤ 跑绿 → 全量 291+
⑥ 变异 M1–M4
⑦ 真实 LLM 抽查一次日志（O9）
⑧ 静态检查 → commit → push
```

---

## 九、代码改动预估（不写代码，仅边界）

| 文件 | 性质 |
| --- | --- |
| `agent/ToolCallRecorder.java` | **新增**：记录器 + 快照 |
| `agent/TimedToolCallback.java` | **新增**：`ToolCallback` 包装（计时/计数/失败标记）|
| `agent/SpringAiAgentModel.java` | 改为构建并包装回调；缓存按 `tools` 数组身份 |
| `config/AgentConfig.java` | 注入 recorder |
| `test/.../Phase4ToolObservabilityTest.java` | **新增**：O1–O7 |

**不改**：Controller / Service / 任一 Tool / Prompt / DB / 前端 / pom。

---

## 十、隐私说明（写进代码注释与文档）

```text
本批只记录「工具名 / 耗时 / 成功失败」。
不记录：用户消息、工具入参、工具返回、异常消息、user_id。
原因：本平台处理心理相关数据；tool 入参与返回可能包含分数、留言、咨询师信息。
```

> 若要排障需要更多信息，应另开批次评估「脱敏后的结构化日志」，而不是在本批偷偷扩大字段。

---

## 十一、待裁定（2 项小问题）

| # | 问题 | 建议 |
| :-: | --- | --- |
| **1** | 失败时是否允许额外记录**异常类名**（如 `IllegalStateException`，不含 message）？ | **不允许** —— 本批严格三项；异常类名可能泄露内部结构，且对「成功/失败」判断非必需 |
| **2** | `ToolCallRecorder` 的聚合快照是否需要暴露为 HTTP 端点？ | **不暴露** —— 避免新增对外表面；仅测试与日志使用 |

> 若你不反对，即按「不允许 / 不暴露」冻结。

---

## 十二、实施记录（2026-11-03）

### 12.1 变更文件（6 个：4 新增 + 2 修改）

| 文件 | 性质 |
| --- | --- |
| `agent/ToolCallRecorder.java` | **新增**：三项记录 + 按工具聚合快照（不存历史）|
| `agent/TimedToolCallback.java` | **新增**：计时/计数包装；成功原样返回、失败原样抛出 |
| `agent/SpringAiAgentModel.java` | 构建并包装 `ToolCallback`；改用 **`.toolCallbacks(...)`**；按 tools 数组身份缓存 |
| `config/AgentConfig.java` | 注册 `ToolCallRecorder`、注入 `SpringAiAgentModel` |
| `Phase4ToolObservabilitySpikeTest.java` | **新增**：2 个 API 契约用例（防升级漂移）|
| `Phase4ToolObservabilityTest.java` | **新增**：O1–O7 验收 |

### 12.2 先红 → 后绿

```text
实现前 : 6 tests / 4 failures + 1 error（O1/O2/O4/O5/O6 红；O3 绿属预期）
实现后 : 6 / 0
Spike   : 2 / 2
全量    : 299 / 299 · BUILD SUCCESS
```

### 12.3 变异（M1–M4）

| # | 变异 | 转红 |
| :-: | --- | --- |
| M1 | 不调用 `record` | O1 / O2 / O4 / O5 / O6（5）|
| M2 | 永远 `ok=true` | **仅 O2**（1）|
| M3 | 日志带 `toolInput` | O5 / O6 等（5，含隐私断言）|
| M4 | 吞异常 | **O2 / O5**（2）|

> M2 只红 O2，说明「失败标记」这条断言专一；
> M3 使隐私断言转红，证明「不记录参数」**可被证伪**，不是口头承诺。

### 12.4 真实运行（O9）

```text
请求：/agent/chat「我有哪些预约」（真实 LLM）
日志：2026-11-03T16:02:30 ... [AGENT][TOOL] name=getMyAppointments elapsedMs=92 ok=true
```

✅ 严格三字段；**无入参、无返回内容、无用户标识**。

### 12.5 一处必须说明的实现细节

```text
TimedToolCallback 在 finally 中记录，recorder 自身异常被吞掉（fail-silent）：
  观测是旁路能力，绝不允许因为「记录失败」改变 Agent 行为。
  被吞掉的只有 recorder 内部异常，不涉及工具异常 —— 工具异常仍原样抛出。
```

---

## 十三、状态

| 项 | 值 |
| --- | --- |
| 性质 | 🏁 **CLOSED · 正式基线 `057c774`（用户 2026-11-03 验收通过）** |
| 基线 | `HEAD = origin = 16cd8bd` · LEAD 0 · 改动 **6 文件**（4 新增 + 2 修改）|
| 范围 | **只做 tool 名 / 耗时 / 成功失败** |
| 隐私 | ✅ 无入参 / 无返回 / 无异常消息 / 无 userId |
| 依赖 / DB / 前端 / pom | **0 改动** |
| 已知限制 | 仅聚合计数，**无单次调用历史**（内存有界）；不暴露 HTTP 端点 |
