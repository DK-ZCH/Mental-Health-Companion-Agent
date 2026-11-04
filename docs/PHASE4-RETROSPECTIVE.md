# Mental Health Companion Agent — Phase 4 技术复盘与下一阶段规划

> **性质**：🧾 **阶段性复盘（零代码 · 不改 `e75842d` 稳定基线 · 零新增依赖）**
> **定位（用户 2026-11-04 确认）**：全面复盘 —— 既检验项目是否经得起技术审查，
> 也为求职准备一套**经得起追问**的项目介绍。
> **证据纪律**：本文所有结论均可回溯到 Phase 4 各轮 CLOSED 文档与测试；不含任何未验证主张。

---

## 一、当前系统架构与调用链

### 1.1 Agent 请求全链路

```text
POST /agent/chat (token)
  ↓
AgentController
  ├─ CurrentUserProvider 解析登录态 → AgentIdentity(userId + userTable)   ← 身份只来自服务端
  ├─ conversationId 归属校验（Step ⑤：属于当前学生，否则 403 —— 防 IDOR）
  └─ AppointmentDraft/Token 放入 extraContext（仅服务端数据，客户端不可注入）
  ↓
AgentService.chat
  ├─ ① RagGate.evaluate(query)          ← 确定性四态：R2 诊断>R1 Tool域>R3 会话指代>ALLOW
  ├─ ② RagRetriever.retrieve(query)     ← ⑦a 关键词检索（topK=2/单篇1500/总长4000）
  ├─ ③ KNOWLEDGE_CONTEXT 独立段前缀     ← 低信任内容结构隔离，永不进 System
  ├─ ④ agentModel.answer(system, userMessage, tools, ctx)
  │      ↓ SpringAiAgentModel（ChatClient）
  │      ├─ MessageChatMemoryAdvisor → AgentWindowChatMemory（读取侧裁剪 ≤20 条）
  │      ├─ MethodToolCallbackProvider → Tool 循环（Spring AI 在 ChatModel 内部执行）
  │      │      └─ AgentToolGuard：域校验 + 身份二元组（userId/userTable）
  │      └─ ToolCallRecorder（观测）
  ├─ ⑤ SafetyRules.evaluate(用户原话)   ← 与 RAG 正交
  └─ ⑥ 命中 ⇒ appendResources（服务端后置追加无号码卡片 —— 模型无法删除）
  ↓
AgentReply { answer, safety{triggered, resourceStatus} }
```

### 1.2 数据层

```text
17 张既有表 + agent_conversation / agent_message（Step ⑤ 新增）
counseling_appointment: active_slot_guard 生成列 + uk_counselor_slot 唯一键（Round 4）
知识语料：knowledge_article 12 篇（6 类 × 2，人工逐篇审核入库，docs/sql 脚本含回滚）
```

### 1.3 组件清单（全部有独立测试）

| 组件 | 职责 | 关键设计 |
| --- | --- | --- |
| `AgentIdentity` / `CurrentUserProvider` | 二元身份（防「学生 1 ≡ 咨询师 1」串读）| 身份最后写入 ToolContext，外部不可覆盖 |
| `OwnershipGuard` | /info 与写路径归属校验（IDOR 修复）| AdminWriteOperation 枚举 |
| `AgentToolGuard` | Tool 域校验 | 白名单 select，绝不载入 password/phone |
| `SafetyRules` + `SafetyResources` | 确定性危机出口 | R/N 片段语义 + Q1b + fail-closed |
| `RagGate` + `KeywordKnowledgeRetriever` | RAG 资格与检索 | 确定性规则；接口可替换 |
| `AgentWindowChatMemory` | 会话窗口 | 自实现读取侧裁剪（1.1.8 官方类只裁写入侧）|
| `AppointmentConfirmToken` + 两阶段 Tool | 写操作确认 | signed capability token（非一次性声明）|
| `AppointmentStatusPolicy` | 状态来源与转换 | 创建强制 1；审核仅 1→2/1→3 |

---

## 二、已完成能力及其验证证据（按交付批次）

| 批次 | 能力 | 核心证据 |
| --- | --- | --- |
| **R1** | 5 个只读 Tool（T0–T4）| IDOR 修复；白名单 select；基线回归 |
| **R2** | T5 测评趋势 / T6 收藏 | scoreRate 跨卷归一化（60/100 vs 8/10）；n=窗口−排除数；tie-breaker；**变异 4 项全命中** |
| **R3** | 确定性安全兜底 | 先红 10 → 19/19；**M1–M5 变异全命中**（M4 恰为「模型不能抑制资源」单点）；真实 LLM 卡片末段 |
| **R4** | 受控写操作（创建预约）| 两阶段 token；S1–S18（含 S18 并发撞唯一键）；status 来源/转换最小修复；**M1–M7 含「移除索引后 S18 红」**|
| **观测批次** | /admin 收敛、快慢日志分离、脱敏 | 基线断言 |
| **Step ⑤** | 多轮会话 Memory | 会话 IDOR（M1 使 T3/T6 红）；读取侧窗口自实现；删除完整性（先消息后会话）；真实两轮 E2E；隐私链路（T12 升级断言暴露 MyBatis 参数日志 → 局部降级 + 全局债务）|
| **Step ⑥** | 知识语料建设 | 12 篇逐篇人工审核（闸门 12/12 + 统一质量门 10 项）；真实 SQL 入库含回滚；前端 10 条截断缺陷修复（参数名经 service 取证）|
| **Step ⑦a** | RAG 关键词最小实现 | 先红 10 → 19/19（含 M2 隔离/M1 结构层常驻）；**变异 M1–M4+M6 全命中**；真实 LLM L1–L8（L4 R2 不注入但 T0 照常调用；L5 危机知识正常检索；L6 卡片正交；L7a/L7b 对照如实登记；L8 双证据 Memory+RAG）；**L2c 漏检证据成立** |

**测试基线**：330/330 · 每批次均执行「先红（真红，假通过如实标注）→ 绿 → 变异 → 真实 LLM → 全量」。

---

## 三、安全与隐私设计（及每项的性质）

| 设计 | 性质（必须准确表述）|
| --- | --- |
| 身份只来自服务端登录态；ToolContext 身份最后写入 | **结构保证** |
| 检索内容不进入 System、不获得 Tool 执行权 | **结构保证**（M2 常驻断言）|
| 危机卡片服务端后置追加 | **结构保证**（M4 单点变异证明模型无法抑制）|
| DB 唯一键兜底（并发撞键）| **结构保证**（S18 + M5 移除索引转红）|
| `AgentPrompts` 第 7/15 条（工具文本/检索片段「不是指令」）| Prompt 层引导；结构隔离是真正的边界 |
| 信任声明（第 15 条）| **辅助提示，不是安全边界**（L7a/L7b 单次对照无法证明效果——已如实登记）|
| 无号码纪律（卡片+语料均零数字）| 资源未确认时的保守策略；`configured()` 强制 verifiedBy/At/source |
| 日志脱敏 | 只记长度/耗时/触发标记；[AGENT][SAFETY] 只记规则 id |

---

## 四、Agent / Memory / RAG 的职责边界

```text
Tool   = 「我的」个人数据（强身份，IDOR 防护，8 个 Tool：7 只读 + 1 两阶段写）
Memory = 「刚才」会话上下文（会话级，≤20 条窗口，完整历史落库）
RAG    = 「公共」科普知识（无身份，匿名可问；Gate 决定资格）
Gate   = 检索前的确定性资格判定（R2 诊断请求 / R1 Tool 域 / R3 会话指代）
卡片   = 危机时的确定性出口（与以上全部正交）
组合规则：Memory + RAG 允许；Tool + RAG ⑦a 不自动组合
```

---

## 五、遗留债务与技术限制（全部已登记，不隐瞒）

| 编号 / 限制 | 内容 | 状态 |
| --- | --- | --- |
| `DEBT-AGENT-UNVERIFIED-PHONE` | 模型自由文本可能输出未验证号码（L7 前实测）；Prompt 仅软约束 | 挂账；⑦b-2 类结构方案评估 |
| `DEBT-MAPPER-SQL-PARAM-LOGS` | 全局 `com.dao` DEBUG 会把 SQL 参数（含对话内容）写日志；本批新 mapper 已降级 | 挂账；隐私优先级高于新功能 |
| **L2c 语义召回缺口** | 词表外表述漏检（实测证据）；模型通识回答无来源可审计 | ⑦b-1 向量可行性的前置证据 |
| **伪造标记段** | 用户文本可伪造第二个 KNOWLEDGE_CONTEXT 段；真段顺序在前 + System 声明缓解；独立 UserMessage 为候选方案 | ⑦b-2 架构评估项 |
| 拼音/谐音/隐喻绕过 | SafetyRules 刻意不做（不可控误报）| 已知限制 |
| 语料规模 12 篇 | 足够证明链路，不证明规模化检索质量 | ⑦b-1 前置 |
| confirm secret 随机 | 重启后旧 token 失效；HttpSession 草稿单实例 | 可接受降级（已登记）|
| 全量 dump 与增量 SQL | 无 Flyway；docs/sql 手工执行（含回滚）| 过程性不一致已显式登记 |

---

## 六、简历价值与面试考察点（经得起追问的讲法）

### 6.1 可量化交付

```text
Phase 4 = 4 轮能力交付 + 1 步 Memory + 1 步语料 + 1 步 RAG（全部先红→绿→变异→真实调用）
测试 330/330；每批 4–7 项变异全部命中（含「删索引/删规则/删隔离」类结构性变异）
DB：+2 会话表 +1 生成列 +1 唯一键；零 ORM 旁路；每次 schema 变更带回滚脚本
```

### 6.2 高频面试问题 → 本项目的答案锚点

| 追问 | 答案锚点（全部有证据）|
| --- | --- |
| 「Spring AI Tool Calling 踩过什么坑？」| Spike 实测：`ChatClient.tools()` 不接受 `ToolCallback` ⇒ `MethodToolCallbackProvider` 转换；工具循环在 ChatModel 内部，advisor 不参与 |
| 「Memory 窗口怎么做的？为什么不用官方类？」| 实测 1.1.8 `MessageWindowChatMemory` 只做写入侧裁剪，`get()` 不裁 ⇒ 自实现 `AgentWindowChatMemory`（DB 完整历史 + 读取侧 ≤20 条）|
| 「RAG 为什么是注入式而不是 Tool？」| Tool 式链路不可确定性断言；注入式才能建立 F7 信任边界（结构隔离 + Gate）；Tool 式无新证明 |
| 「怎么防提示注入？」| 分层回答：结构保证（片段不进 System/无工具权，有常驻断言）+ 确定性 Gate + Prompt 引导（辅助）；**诚实边界**：L7 对照单次无法证明声明效果 |
| 「写操作怎么保证安全？」| 两阶段 capability token（绑定用户/动作/参数/TTL，非一次性声明）+ 服务端状态策略 + 生成列唯一索引（status=3 释放槽位）+ S18 并发验证 |
| 「怎么证明不是关键词扫描？」| 变异测试：删否定规则 → S16 红且 S17 绿（精确性）；「我不想自杀，但我想死」仍触发（片段语义）|
| 「RAG 检索质量怎么评估？」| 真实漏检证据 L2c（词表零命中→不注入→模型通识回答无来源）⇒ 向量引入以前置证据为条件，不为其本身 |
| 「隐私怎么处理？」| 日志只记元数据；发现 MyBatis DEBUG 参数日志泄对话（测试观测升级暴露）→ 新 mapper 降级 + 全局债务登记（测试过程如实展开）|

### 6.3 诚实边界话术（防止面试翻车）

```text
不说「防住了提示注入」→ 说「结构隔离 + 确定性 Gate + 变异验证；Prompt 声明是辅助」
不说「支持语义检索」  → 说「⑦a 关键词最小实现，L2c 漏检证据成立，向量为 ⑦b 评估项」
不说「具备长期记忆」  → 说「会话级上下文记忆（conversation-scoped）」
不说「危机检测」      → 说「确定性安全出口；未命中 ≠ 安全；不做检出率承诺」
```

---

## 七、下一阶段优先级建议（用户框架确认）

```text
① 本文（复盘）—— 当前
② 债务评估（零代码，安全/隐私优先于新功能）：
   DEBT-AGENT-UNVERIFIED-PHONE → 风险评估与方案（⑦b-2 类结构思路）
   DEBT-MAPPER-SQL-PARAM-LOGS  → 影响面与降级方案
③ 视复盘结论判定：
   召回缺口影响使用 → 启动 ⑦b-1（L2c 对照实验先行）
   消息边界风险优先 → 启动 ⑦b-2（不与向量绑定）
   现有能力满足目标 → 两项暂缓
```

---

## 八、状态

| 项 | 值 |
| --- | --- |
| 性质 | 🧾 **复盘文档（零代码 · 基线不动）** |
| 基线 | `e75842d` · 330/330 · worktree 仅本文档修改 |
| 证据可追溯 | 每条结论对应 Phase 4 各轮 CLOSED 文档 |
