# Phase 4 · Step ⑤ 设计：多轮会话（Memory + 会话体系）

> **性质**：🏁 **CLOSED · 用户 2026-11-03 验收通过 · 正式基线 `83cfe5d`**
>
> **验收项（用户逐条确认）**：Memory 架构成立 ✅ · **读取侧窗口问题被真正发现并修正** ✅ ·
> 会话 IDOR 防护（M1 使 T3/T6 红）✅ · 删除完整性（M3 使 T6 红 + E2E DELETE）✅ ·
> **失败请求的 Memory 语义合理**（user 先落库、assistant 仅成功后落库 ——「用户确实说过这句话」）✅ ·
> 隐私链路（T12 未抓到 → 加强断言 → 暴露 MyBatis 参数日志 → 局部修复 + 全局债务登记）✅ ·
> 真实两轮 E2E ✅ · conv=0/msg=0 ✅ · 零新增依赖 ✅
>
> **✅ 术语表述纪律（后续文档必须保持，用户 2026-11-03 冻结）**：
> ```text
> Conversation History   = agent_message 持久化聊天历史（完整，用户可见）
> ChatMemory             = 模型上下文窗口（≤20 条，读取侧裁剪）
> Session Orchestration  = Round 4 的 HttpSession 待确认草稿（短生命周期编排状态）
> 三者不得混称为 "Memory"；
> 当前实现是【会话级上下文记忆 conversation-scoped chat memory】，
> 不得宣称「长期用户记忆 / 用户画像 / 偏好记忆」。
> ```
> **基线**：`HEAD = origin = 057c774` · LEAD 0 · worktree CLEAN
> **上游**：路线图 step ⑤「Memory + 前端会话体系」（`PHASE4-ROADMAP-REVIEW.md` §七）
> **为什么现在可以做**：Round 1–4 已建立身份域 / 只读矩阵 / 安全兜底 / 受控写 / 观测，
> 本步只解决「跨请求上下文」这一个问题。

---

## 一、目标与非目标

### 1.1 目标

```text
① 同一会话内，模型能看到本轮之前的问答（多轮追问）
② 会话归属学生本人；他人会话不可读写（防 IDOR）
③ 前端有最小会话体系：会话列表 / 历史消息 / 新会话 / 删除会话
④ 记忆内容不进日志、不进观测；安全兜底与 Tool 观测行为不变
```

### 1.2 非目标

| 不做 | 原因 |
| --- | --- |
| ❌ RAG / 知识库 | 语料 5×7 字符（step ⑥⑦）|
| ❌ 跨会话长期记忆 / 用户画像 | 超出「会话内上下文」；隐私面大，单独立项 |
| ❌ 咨询师域会话 | 仅学生域不变 |
| ❌ 流式输出 / 打字机效果 | 传输体验，与本步正交 |
| ❌ 引入 Redis / 向量库 | 只需关系存储 |

---

## 二、现状事实（全部实测）

| # | 事实 | 证据 |
| :-: | --- | --- |
| F1 | 请求体只有 `message`，无 `conversationId` | `AgentChatRequest` |
| F2 | 全库 17 张表，**无任何会话/记忆表** | `SHOW TABLES` |
| F3 | 前端 `DashboardView` 只有单轮 `aiAnswer`，无历史/会话列表 | `DashboardView.vue` L322–L360 |
| F4 | Spring AI **1.1.8 内置**：`ChatMemory`（conversationId 键控）、`ChatMemoryRepository`、`MessageWindowChatMemory`、`MessageChatMemoryAdvisor` | jar 实测 |
| F5 | 本地存在官方 **`spring-ai-starter-model-chat-memory-repository-jdbc`**（未引入） | m2 实测 |
| F6 | 已有安全链路：身份域 → Tool 观测 → 服务端安全追加（Round 3/4） | `057c774` |
| F7 | 前端已有安全渲染 `renderAgentText`（先转义后白名单标签） | `DashboardView.vue` L294–L319 |

---

## 三、核心设计

### 3.1 会话与消息的归属模型（防 IDOR 的关键）

```text
agent_conversation                     agent_message
────────────────────                   ────────────────────
id            BIGINT PK                id           BIGINT PK
student_id    INT NOT NULL   ← 归属    conversation_id BIGINT FK
title         VARCHAR(100)             role         VARCHAR(10)  user/assistant
created_at    TIMESTAMP                content      TEXT
last_msg_at   TIMESTAMP                created_at   TIMESTAMP

UNIQUE: agent_message(conversation_id, seq)？—— 不需要：seq 由服务端事务内分配
索引：agent_conversation(student_id, last_msg_at DESC)
      agent_message(conversation_id, id)
```

- `conversationId` 对外暴露的是 **`agent_conversation.id`**（数字）
- **每次携带 `conversationId` 的请求都必须校验 `student_id = 当前用户`**，否则 403（防 IDOR）
- 这是本步**最大的新攻击面**，也是 Round 1–4 建立的「身份只来自服务端」原则的延伸：
  `studentId` 永远取自登录态，**绝不来自请求体**

### 3.1b 🔴 两个冻结原则（用户 2026-11-03 补充）

**① Chat Memory ≠ Chat History（职责分离）**

```text
agent_message          = 持久化聊天历史（完整，用户可见）
ChatMemoryRepository   = 读取/写入该历史的适配层（实现 Spring AI 接口）
MessageWindowChatMemory = 「模型上下文窗口」策略（最多 20 条，淘汰由它负责）
⇒ 窗口从 20 改 30，不需要动数据库历史
⇒ 永久保存 ≠ 永久作为 LLM 上下文
```

**② Memory ≠ Tool execution trace**

```text
Spring AI MessageChatMemoryAdvisor 不保存 Tool calling 的中间消息。
Memory 保存的是：user 消息 + 最终 assistant 回复（用于下一轮上下文）。
Tool 执行情况由 Observability 记录（toolName/duration/success）。
⇒ 不得宣称 Memory 保存了完整 Agent 执行轨迹。
```

**三者的分工（不得互相替代）**：

```text
Memory      → 为下一次 LLM 请求提供上下文
Observability → 记录 Tool 执行情况
agent_message  → 用户可查看的聊天历史
```

### 3.2 Spring AI 集成方式

```text
AgentController
  ├─ 无 conversationId → 服务端生成新会话（title=首条消息前 20 字）
  ├─ 有 conversationId → 校验归属 → 读历史
  ↓
AgentService.chat(message, identity, extraContext)
  ↓
SpringAiAgentModel
  └─ ChatClient.prompt()
        .system(SYSTEM)
        .user(message)
        .toolCallbacks(...)                    ← 不变（含观测包装）
        .advisors(MessageChatMemoryAdvisor)    ← 新增：把历史作为 messages 注入
        .toolContext(...)                      ← 不变（身份/草稿）
```

| 项 | 选择 | 理由 |
| --- | --- | --- |
| 记忆窗口 | `MessageWindowChatMemory`，**最多 20 条消息**（以 Spring AI 窗口语义为准，不宣称「严格 10 轮」）| 防上下文膨胀与成本失控 |
| Advisor | `MessageChatMemoryAdvisor` | 历史以独立 messages 注入，不污染 system |
| 保存时机 | 每次成功返回后，**服务端**写入 user + assistant 两条 | 失败不污染历史 |
| 排除内容 | `safety` 字段、资源卡片？| **卡片保留**（它是回答的一部分）；仅 `safety` 元数据不存 |

### 3.3 存储：自建 vs 官方 JDBC 仓库（**决策点 D1**）

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **A 自建 `ChatMemoryRepository`（推荐）** | 新增两张表 + `JdbcTemplate` 实现 Spring AI 的 `ChatMemoryRepository` 接口 | **零新增依赖**；归属/标题/时间字段可控；与项目 MyBatis-Plus 体系一致 | 自己写 4 个方法（简单）|
| B 官方 `spring-ai-starter-model-chat-memory-repository-jdbc` | 引入 starter，用官方默认表 | 官方维护 | **新增依赖**；默认表**无归属字段**，IDOR 防护要靠「conversationId 前缀编码 userId」；标题/列表仍要自建表 |

**推荐 A**：归属是本步的安全核心，字段必须可控；4 个接口方法约 60 行。

### 3.4 conversationId 的引入与校验（**决策点 D2**）

| 项 | 设计 |
| --- | --- |
| 请求体 | `AgentChatRequest` 增加**可选** `conversationId`（Long）|
| 缺省 | 服务端创建新会话，响应返回 `conversationId` |
| 归属校验 | 有值 ⇒ `SELECT student_id` 必须等于当前用户，否则 **403**（与域拒绝同语义）|
| 伪造 | 传不存在的 id ⇒ 403（不 404，避免会话存在性预言机）|
| 日志 | **不记录 conversationId**（可关联到具体会话）；如需排障用 hash |

### 3.5 隐私与保留（**决策点 D3**）

| 项 | 设计 |
| --- | --- |
| 内容入日志 | ❌ 永不（含对话内容、标题；标题由用户首条消息生成，也算内容）|
| 删除 | 前端提供「删除会话」⇒ 物理删除该会话及其消息（归属校验后）|
| 保留策略 | 第一版**永久保留**，用户可自行删除；自动过期（如 90 天）留作后续裁定 |
| 观测 | 观测字段不变（toolName/duration/success），**不新增** conversationId 字段 |

---

## 四、前端最小会话体系（首次前端改动，**决策点 D4**）

| 项 | 设计 |
| --- | --- |
| 位置 | `DashboardView` AI 区块内增加「历史会话」折叠列表（**不新增路由/页面**）|
| 能力 | ① 列出我的会话（标题 + 时间，按 `last_msg_at` 倒序）② 点开会话 → 加载历史消息 ③「新会话」按钮 ④ 删除会话（带确认）|
| 渲染 | 历史消息沿用 `renderAgentText`（先转义后白名单标签）；**用户消息与助手消息分开渲染** |
| API | 新增 `GET /agent/conversations`、`GET /agent/conversations/{id}/messages`、`DELETE /agent/conversations/{id}`（**均为登录态 + 归属校验**）|
| 不做 | 流式、markdown 完整渲染、会话重命名、导出 |

> 这是项目**首次前端改动**：仍坚持「先转义后白名单」的渲染纪律，且所有新端点都要归属校验。

---

## 五、测试矩阵（T1–T14，先红后绿）

| # | 场景 | 期望 |
| :-: | --- | --- |
| T1 | 首次提问（无 conversationId）| 返回新 `conversationId`；落库 1 条 user + 1 条 assistant |
| T2 | 带 conversationId 追问「我刚才说了什么」 | 模型能引用上一轮内容（真实 LLM 验证）|
| **T3** | **student1 的 conversationId 被 student2 使用** | **403**，且不读不写 |
| **T4** | 不存在的 conversationId | 403（非 404，防存在性预言机）|
| T5 | 会话列表只返回本人会话 | student1 看不到 student2 的 |
| T6 | 删除会话 → 消息一并删除；他人删除 ⇒ 403 | |
| T7 | 模型调用失败 | **不写** assistant 消息（历史不被污染）|
| T8 | 工具调用失败 | 同上（既有 fail-closed 不回归）|
| T9 | 窗口裁剪 | 第 11 轮后，注入历史条数 ≤ 20 |
| T10 | 安全兜底在多轮下仍生效（含历史含危机文本）| 卡片仍追加 |
| T11 | 观测字段不变（无 conversationId）| |
| T12 | 内容零日志（标题/消息内容不入日志）| 哨兵串断言 |
| T13 | ToolContext 身份键不被 extraContext 覆盖 | 既有断言不回归 |
| T14 | 未登录 → 既有 401 契约不变 | |

**变异（M1–M6，证明测试非恒真）**：去掉归属校验（T3 红）/ 去掉窗口裁剪（T9 红）/
失败仍写 assistant（T7 红）/ 列表不做归属过滤（T5 红）/ 内容写日志（T12 红）/ 删除不做归属校验（T6 红）。

---

## 六、变更边界（预估，待裁定后冻结）

| 文件 | 性质 |
| --- | --- |
| `agent/AgentChatRequest.java` | 增加可选 `conversationId` |
| `agent/AgentConversationService.java` | **新增**：会话 CRUD + 归属校验 |
| `agent/AgentChatMemoryRepository.java` | **新增**：实现 Spring AI `ChatMemoryRepository`（方案 A）|
| `entity/AgentConversationEntity.java` + `AgentMessageEntity.java` | **新增** |
| `dao/AgentConversationDao.java` + `AgentMessageDao.java` + mapper XML | **新增** |
| `agent/AgentService.java` / `SpringAiAgentModel.java` | 接入 advisor；保存消息 |
| `controller/AgentController.java` | 会话端点 + 归属校验 + 返回 conversationId |
| `frontend/.../DashboardView.vue` + `api/agent.ts` | 会话列表/历史/新会话/删除 |
| SQL 脚本 | `docs/sql/ROUND5-memory-tables.sql`（含回滚）|

**两处「第一次」需要你明确授权**：
1. **首次前端改动**（本步必要，无法绕过）
2. **第 2 处 schema 变更**（两张新表，含回滚脚本）

---

## 七、实施顺序（用户修正版，前端最后）

```text
① 冻结测试矩阵（T1–T14，先红）
② DDL + 回滚脚本
③ Repository / ownership（自建 ChatMemoryRepository + 归属校验）
④ Memory + Advisor（MessageWindowChatMemory + MessageChatMemoryAdvisor）
⑤ Agent API 会话生命周期（chat 带 conversationId / 列表 / 历史 / 删除）
⑥ 前端（DashboardView 会话 UI，最后做）
⑦ 全量回归
⑧ 变异 M1–M6
⑨ 真实 LLM 两轮追问验证
⑩ 零污染 / 静态检查 / commit
```

> **前端必须最后**：先证明 conversation 创建/查询/归属/消息持久化/删除/窗口/Advisor
> 这些后端链路全部成立，再让 Dashboard 接入。

---

## 八、待你裁定（6 项）

| # | 问题 | 我的建议 |
| :-: | --- | --- |
| **D1** | 存储方案：A 自建 Repository / B 官方 JDBC starter | **A**（零依赖 + 归属字段可控）|
| **D2** | conversationId 校验：归属不符 / 不存在 ⇒ 403？ | **是**（防 IDOR + 防存在性预言机）|
| **D3** | 保留策略：第一版永久 + 用户可删？ | **是**（自动过期另议）|
| **D4** | 前端范围：DashboardView 内折叠列表（不加路由/页面）？ | **是** |
| **D5** | 记忆窗口：最近 **20 条**？ | **是**（成本与效果平衡，可后调）|
| **D6** | 授权两处「第一次」：**前端改动** + **第 2 处 schema**？ | **批准**（本步无法绕过）|

---

## 九、状态

| 项 | 值 |
| --- | --- |
| 性质 | 🏁 **CLOSED · 正式基线 `83cfe5d`（用户 2026-11-03 验收通过）** |
| 基线 | `HEAD = origin = 83cfe5d` · LEAD 0 · worktree CLEAN |
| 改动 | **21 文件**（后端 15 + 前端 2 + 测试 1 + 配置 1 + 既有测试 2）· **+1303/−30** |
| DDL | ✅ 已执行（回滚脚本就绪）|
| 数据 | conv=0 / msg=0（真实探针会话 9893 已通过 DELETE 端点清理）|
| 待验收 | 用户复核 `83cfe5d` |

---

## 十、实施记录（2026-11-03）

### 10.1 变更（21 文件 +1303/−30）

| 类别 | 文件 |
| --- | --- |
| 新增（后端 11） | `AgentConversationEntity` / `AgentMessageEntity` / `AgentConversationDao` / `AgentMessageDao` / `AgentConversationService(+Impl)` / `AgentMessageService(+Impl)` / `AgentChatMemoryRepository` / **`AgentWindowChatMemory`** |
| 修改（后端 6） | `AgentChatRequest`（+conversationId）· `AgentController`（归属校验 + 3 个会话端点 + 响应返回会话 id）· `AgentIdentity`（+CTX_CONVERSATION_ID）· `AgentService`（上下文透传）· `SpringAiAgentModel`（Memory Advisor + `.toolCallbacks`）· `AgentConfig`（ChatMemory/Repository 装配）|
| 前端（2，**首次**） | `api/agent.ts`（会话 API）· `DashboardView.vue`（会话列表/历史/新会话/删除 + 消息流渲染）|
| 配置 | `application.yml`（新 mapper SQL 日志降级 warn）|
| 测试 | `Phase4MemorySessionTest`（12 例）+ 既有 2 类补 `@Transactional`（修 27 行会话泄漏）|

### 10.2 Spike/实测发现（写进文档与代码注释）

| # | 发现 | 处置 |
| :-: | --- | --- |
| 1 | 1.1.8 `MessageWindowChatMemory` **只在写入侧裁剪，`get()` 读取不裁**（T9 实测 25 条历史整段进入上下文）| 自实现 **`AgentWindowChatMemory`**：`add` 不裁剪（完整历史）、`get` 取最近 20 条 |
| 2 | `MessageChatMemoryAdvisor` 在 **before 阶段**即持久化 user 消息；assistant 仅成功后落库 | T7 语义改为「user 已落库（用户确实说过），assistant 不落库」|
| 3 | Advisor 自动保存走我们的 Repository ⇒ `saveAll` 采用**整窗替换**语义 | 删除+重插；T9 证明窗口裁剪在存储层生效 |

### 10.3 变异（M1–M4）

| # | 变异 | 转红 |
| :-: | --- | --- |
| M1 | 去归属校验 | T3 + T6 |
| M2 | 读取侧窗口失效 | T9 |
| M3 | 删除不清理消息 | T6 |
| M4 | 会话标题进日志（**根 Logger**）| T12 |

> M4 首次未命中：T12 原本只挂 Controller logger —— 已改为**根 Logger 捕获**（更强的隐私断言）。

### 10.4 隐私事件与修复

| 事件 | 处置 |
| --- | --- |
| 非事务测试（SafetyFallback/ControllerRegression）每轮 `/agent/chat` 泄漏会话行（累计 27 行，消息 0）| 精确删除 + 两类补 `@Transactional` |
| `logging.level.com.dao: debug` 把**所有 mapper 参数**（含会话内容）打进日志 | 新 mapper 降级 warn；**全局问题登记 `DEBT-MAPPER-SQL-PARAM-LOGS`** |

### 10.5 真实 LLM 两轮（E2E）

```text
第 1 轮「我叫小明，最近总是失眠，帮我看看我的测评情况」
  → 创建会话 9893；模型真实调用 T0；user+assistant 各 1 条
第 2 轮「我刚才说我叫什么名字？现在还有什么困扰？」
  → ✅ 「你刚才说自己叫小明，困扰是最近总是失眠」（Memory 生效）
  → 4 条消息落库
DELETE /agent/conversations/9893 → 200；重复删除 → 403；基线 conv=0 / msg=0
```

> 第 2 轮首测 400 是**探针脚本 bug**（把函数多段输出当 id 传），与应用无关，已修正复测。

---

## 十一、路线图位置

```text
① ② ③ ④ ✅   ⑤ Memory ✅（本步）   ⑥ 知识内容（下一步）   ⑦ RAG（依赖 ⑥）
债务：DEBT-MAPPER-SQL-PARAM-LOGS（新）· CONFIRM-RETRY · UNVERIFIED-PHONE · /add 窗口
```
