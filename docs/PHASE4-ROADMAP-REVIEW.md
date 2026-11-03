# Phase 4 路线审查 · Memory / 多 Tool / RAG 的依赖与优先级

> **性质**：路线审查（📐 **零代码 · 零改动 · 待用户裁定**）
> **基线**：`HEAD = origin = 1bc24a5` · LEAD 0 · worktree CLEAN · 后端/DB **0 改动**
> **上游**：`PHASE3-STEP8-IMPL.md`（Step 8 CLOSED）· `STEP11-CLOSED`（Agent MVP）
> **本批定位**：回答「下一步做哪一个」——**先证明该做什么，再设计**

---

## 一、结论先行

**三个候选都不能直接开工**，因为审查中发现了两项**真实隐私缺陷**和两项**硬前置依赖**：

```text
🔴 发现 1（P1 · Step 11 引入 · 已实测）Agent Tool 跨角色串读
🟠 发现 2（P1 待定 · 业务规则缺失）assessment_record 的咨询师数据范围未实现
🟠 发现 3（硬前置）             RAG 语料 = 7 字符占位符 ⇒ 不可做
🟠 发现 4（硬前置）             Memory 缺前端会话概念
```

> **⚠️ 定性纪律（用户裁定 2026-10-25）**：
> 发现 2 的技术事实已确认，但**「未实现过滤」≠「一定不应过滤」** ——
> 缺的是**业务授权规则**（咨询师能看哪些学生的测评），而非已证实的漏洞。
> 因此定性为 **P1 待定**，须先明确规则，再决定是否修复。

**推荐顺序**（用户裁定 2026-10-25，7 步）：

```text
① Agent 身份域安全收敛（Step 12）        ← 当前，修 Step 11 的安全回归
② 明确 counselor 的 assessment 数据范围  ← 业务规则决策点（非技术）
③ 修复/确认 DataScope                    ← 依赖 ② 的结论
④ Phase 4 多 Tool                        ← 在正确身份域下扩展
⑤ Phase 4 Memory + 前端会话体系
⑥ 知识内容建设
⑦ Phase 5 RAG
```

---

## 二、发现 1（P1 · Step 11 引入）：Agent Tool 跨角色串读

### 实测证据

同一句话「我最近的心理测评记录」，不同角色调用 `/agent/chat`：

| 角色 | 实际 id | `code` | 回答含 `15`（学生1 的分数）| 判定 |
| --- | :-: | :-: | :-: | :-: |
| student a1 | 1 | 0 | ✅ True | 正确（本人数据）|
| **counselor a1** | **1** | 0 | 🔴 **True** | 🔴 **串读** |
| counselor a2 | 2 | 0 | False | 无法判定（学生2 无记录）|
| counselor a3 | 3 | 0 | False | 无法判定（学生3 无记录）|
| admin | 6 | 0 | False | 无匹配 id |

> ⚠️ **a2/a3 为 False 不代表安全** —— 只是学生 2/3 恰好没有测评记录。
> **串读条件是「id 碰撞」，与数据无关。**

### 根因

`student` 与 `counselor` 的 id **完全重叠**：

```text
student    : 1, 2, 3
counselor  : 1, 2, 3      ← 完全重叠
admin_user : 6
```

而 `AssessmentTools` 直接假设「`userId` 就是 `studentId`」：

```java
// AssessmentTools.java:97-112
recordService.lambdaQuery()
        .eq(AssessmentRecordEntity::getStudentId, userId)   // ← 无角色校验
        .count();
```

`CurrentUserProvider` **已经能区分角色**（`currentTableName()` 读 `auth_token.user_type`），
但 **Step 11 的 Tool 没有使用它**。

### 影响面

| 项 | 说明 |
| --- | --- |
| 数据敏感度 | **心理测评结果属高度敏感个人信息**（心理咨询场景）|
| 暴露条件 | 咨询师 id 与某学生 id 碰撞，且该学生有记录 |
| 当前实况 | **3 个咨询师 id 全部与学生 id 重合** ⇒ 一旦学生有记录即泄露 |
| 何时引入 | **Step 11**（Agent 是新入口，绕过了既有 `DataScope` 的角色映射）|

---

## 三、发现 2（🟠 P1 待定 · 既有）：`assessment_record` 的咨询师数据范围未实现

> **🔴 定性纪律（重要）**：
> 以下内容证明的是 **「过滤条件未生效」这一技术事实**，
> **不**证明 **「咨询师不应看到这些记录」这一业务判断**。
>
> ```text
> 技术异常  ≠  安全漏洞
>    ↑              ↑
>    已确认        需要业务规则才能判定
> ```
>
> 因此本项定性为 **P1 待定（业务规则缺失）**，**不**定性为「已证实的越权漏洞」。
> 在明确规则之前，**不得**按漏洞处理，也**不得**据此修改 SQL 或业务代码。

### 事实

`AssessmentRecordController` **确实调用了** DataScope：

```java
// AssessmentRecordController.java:75
DataScope.apply(request, params, "studentId", "counselorId");
```

`DataScope` 对心理老师分支：

```java
if (OwnershipGuard.ROLE_COUNSELOR.equals(role)) {
    params.put(counselorField, userId);      // params.put("counselorId", userId)
}
```

但 `AssessmentRecordDao.xml` **完全不含 `counselor` 字段**：

```text
Search 'counselor' in AssessmentRecordDao.xml  →  ❌ 0 命中
```

⇒ `<if test="params.counselorId ...">` **不存在** ⇒ 条件被 **静默丢弃** ⇒ 心理老师**看到全表**。

### 实测

| 角色 | `/assessmentRecord/list` | 全表实际行数 | 判定 |
| --- | :-: | :-: | :-: |
| student a1 | 1 条 | 1 | 正确（本人）|
| **counselor a1** | **1 条** | 1 | 🔴 **等于全表** —— 数据只有 1 条，恰好掩盖了失效 |

> **这是「样本量太小掩盖了现象」的典型例子。** 若表内有 N 个学生的记录，咨询师会看到 N 条。

### 🔴 但「看到全表」是否合规，取决于业务规则 —— 当前**未定义**

三种可能的业务设计，结论完全不同：

| 业务规则 | 现状行为 | 判定 |
| --- | --- | :-: |
| **A. 心理老师可查看所有学生测评** | ✅ 符合 | 不是缺陷（现状即为正确行为）|
| **B. 只能查看「自己负责的学生」** | 🔴 越权 | 需补 `counselor → student` 授权关系 |
| **C. 只能查看「与自己有预约关系的学生」** | 🔴 越权 | 需按 `counseling_appointment` 关联过滤 |

**当前项目没有任何地方定义过这条规则**：

```text
· 无 counselor_student 关系表
· 无「负责学生」分配字段
· counseling_appointment 只表示「预约」，不等于「授权查看测评」
```

⇒ 因此 **② 必须先做业务决策**，③ 才有明确目标。
在 ② 之前修 SQL 属于**猜测需求**，可能把正确行为改坏。

### 对照：预约表是安全的

`CounselingAppointmentDao.xml` **两个字段都有**：

```xml
L50: <if test="params.counselorId != null ...">  a.counselor_id = #{params.counselorId}
L55: <if test="params.studentId  != null ...">  a.student_id  = #{params.studentId}
```

⇒ 说明 `DataScope` 本身没坏，**是「表缺少对应归属字段」导致条件失效**。

### 归属字段矩阵（决定 Tool 化的安全前提）

| 表 | `student_id` | `counselor_id` | DataScope 双分支 |
| --- | :-: | :-: | :-: |
| `assessment_record` | ✅ | ❌ | 🔴 老师分支失效 |
| `assessment_answer` | ✅ | ❌ | 🔴 同上（需核实其 Controller 是否调用）|
| `assessment_wrong_question` | ✅ | ❌ | 🔴 同上 |
| `counseling_appointment` | ✅ | ✅ | ✅ 正常 |
| `counselor_message` | ✅ | ✅ | ✅ 正常 |
| `counselor_favorite` | ✅ | ✅ | ✅ 正常 |
| `knowledge_article` | ❌ | ❌ | 公共数据（不分角色）|
| `notification` | ❌ | ❌ | 需单独确认归属语义 |

---

## 四、发现 3（硬前置）：RAG 语料为空，当前不可做

### 事实

```text
knowledge_article :  5 条
content 长度      :  全部 7 字符（"健康知识名称1"）⇒ 占位符，无真实内容
```

| 项 | 现状 |
| --- | --- |
| 可检索语料 | **0 字**（7 字符是标题占位，非知识内容）|
| 向量库 | **无**（项目只有 MySQL，无 PGVector / Redis / Milvus）|
| Embedding 模型 | **未接入**（当前只有 DeepSeek chat，未配 embedding）|
| 切分策略 | 无可切分对象 |

### 判定

**RAG 现在做不了 —— 不是技术问题，是内容问题。**

若强行做：

```text
5 条 7 字符串 → 向量化 → 检索 → 返回 "健康知识名称1"
        ↓
无法评估检索质量（没有正确答案）
无法产生产品价值（回答里没有任何知识）
⇒ 典型的技术名词堆砌
```

**前置依赖**：知识内容建设（内容来源 / 录入 / 审核 / 规模），属**产品与内容工作**，
应作为独立工单，不应由 Agent 技术批次承担。

---

## 五、发现 4（硬前置）：Memory 缺前端会话概念

### 现状

```text
DashboardView.vue
  aiInput  : ref('')        ← 输入框
  aiAnswer : ref('')        ← 单条回答，每次 sendToAgent 直接覆盖
  ⇒ 无消息列表、无 conversationId、无多轮上下文
```

后端 `AgentService.chat(message, userId)` **明确注释「单轮对话（无历史，Step 11 不做 ChatMemory）」**。

### 做 Memory 需要的改动面

| 层 | 改动 | 成本 |
| --- | --- | :-: |
| 前端 | 消息列表 UI + 会话状态 + `conversationId` 管理 | 中 |
| 后端 | `ChatMemory` 装配（`MessageChatMemoryAdvisor`）| 低 |
| 存储 | 内存版（重启即丢）/ JDBC 版（**需建表 = DB 改动**）| 中 |
| 🔴 安全 | `conversationId` **必须服务端绑定 `userId`**，否则可读他人会话 | **高** |

> **安全提示**：`conversationId` 是新的**外部可控标识**，
> 其越权风险与本轮发现 1 同类（「客户端提供 id → 服务端直接使用」）。
> 若做 Memory，必须一次性把「会话归属校验」纳入设计，不能后补。

---

## 六、三个候选的对比评估

| 维度 | ① 多 Tool | ② Memory | ③ RAG |
| --- | --- | --- | --- |
| 前置依赖 | 🔴 **身份域必须先正确** | 🟠 前端会话 UI + 会话归属安全 | 🔴 **语料 + 向量库 + Embedding** |
| 新增基础设施 | **0** | 1（记忆存储）| **2–3**（向量库 / Embedding / 切分）|
| 新增外部可控输入 | 无（Tool 无参）| 🔴 `conversationId` | 无 |
| 可验证性 | ✅ 高（Tool 选择 / 参数 / 数据正确性）| 🟡 中（追问是否记住）| 🔴 **低（语料为空，无 ground truth）**|
| 真实产品价值 | ✅ 高（覆盖学生日常场景）| 🟡 中（多轮追问 / 陪伴）| 🔴 **当前为 0** |
| 改动面 | 小（新增 Tool 类 + 注册）| 中大（前后端 + 存储）| 大 |
| 前置安全修正 | 🔴 **必须先做** | 🟠 需内置 | 无额外 |

### 关键判断

**为什么「多 Tool」也不能直接开工？**

因为每新增一个 Tool，就新增一处「`userId` 当 `studentId` 用」的串读面：

```text
加「我的预约」  → counseling_appointment 有 student_id + counselor_id 双字段
                → 若照抄现有写法，咨询师会读到「学生 1 的预约」而非「我作为咨询师的预约」
加「我的留言」  → 同上，且留言含正文（更敏感）
加「我的错题」  → assessment_wrong_question 只有 student_id
                → 咨询师读到学生错题
```

⇒ **在身份域错误的前提下扩展 Tool，是把一个 P1 复制成 N 个 P1。**

---

## 七、推荐路线（用户裁定 2026-10-25 · 7 步）

```text
① Step 12  Agent Identity Domain Security        ← 当前，修 Step 11 的安全回归
      · Tool 必须声明「允许的角色域」，非本域调用 → fail-closed
      · 当前仅开放 学生域（student）；管理员/咨询师 默认拒绝
      · 复用 CurrentUserProvider.currentTableName()，不新造身份机制
      · ✅ 可行性已实测：Interceptor:73-76 每次请求从 auth_token 重写 Session
        ⇒ tableName = auth_token.user_type ∈ {student, counselor, admin_user}
        ⇒ 服务端来源、客户端不可篡改
      ↓
② 明确 counselor 的 assessment 数据范围           ← 业务规则决策点（非技术）
      · A 全量 / B 负责学生 / C 预约关系 —— 三选一
      · 决定 ③ 的目标；② 未定则 ③ 无法开工
      ↓
③ 修复/确认 DataScope                            ← 依赖 ② 的结论
      · 若 ②=A ⇒ 现状即为正确，仅需显式登记（不修）
      · 若 ②=B/C ⇒ 需新增授权关系 + SQL 过滤
      ↓
④ Phase 4 多 Tool                                ← 在 ① 的身份域下扩展
      · 候选：我的预约 / 我的留言 / 我的错题 / 可预约咨询师
      · 全部纯读、无副作用
      ↓
⑤ Phase 4 Memory + 前端会话体系                   ← 会话 UI + conversationId 归属校验
      ↓
⑥ 知识内容建设                                    ← RAG 的前置（产品/内容工作）
      ↓
⑦ Phase 5 RAG
```

### 为什么 ① 是最有价值的下一步（而非「又一个安全批次」）

它同时满足三件事：

1. **修真实 P1** —— 心理测评数据在心理咨询场景下是最高敏感级别；
2. **建立可复用范式** —— 「Tool 声明角色域 + fail-closed」一旦成立，
   后续每个 Tool 都自动受约束，**不需要每次重新想安全问题**；
3. **回答项目核心命题** —— Agent 不是绕过既有安全体系，而是**受其约束地访问真实数据**。

> 这正是「从能调用一个 Tool 进化成有实际价值、可解释、可验证的 Agent」的实质：
> **不是加功能，而是建立可信的能力边界。**

---

## 八、本批「不做什么」

| 不做 | 原因 |
| --- | --- |
| ❌ 写任何实现代码 | 本批为路线审查 |
| ❌ 做多 Tool | 前置（身份域）不成立 |
| ❌ 做 Memory | 需前端会话 UI，且引入 `conversationId` 越权面 |
| ❌ 做 RAG | 语料 7 字符占位符，无 ground truth |
| ❌ 顺手修 DataScope | 属既有表结构/映射问题，需独立设计（避免与本批耦合）|
| ❌ 改 DB / 改前端 / 改 pom | 本批零改动 |

---

## 九、裁定结果（用户 2026-10-25 · 已定案）

| # | 问题 | 裁定 | 落地含义 |
| :-: | --- | :-: | --- |
| **1** | 下一步做什么 | ✅ **A. Agent 身份域安全收敛** | 定名 **Step 12 / Agent Identity Domain Security**；性质是**修 Step 11 的安全回归**，不是「又一个安全批次」|
| **2** | 管理员在 Agent 中的策略 | ✅ **A. 默认拒绝** | `admin_user` 不得因系统权限高而自动获得「我的测评」；未来若需 Agent 能力，应是**独立的管理型 Tool**（统计/异常/运营），而非把 admin id 映射成 studentId |
| **3** | 咨询师在 Agent 中的角色 | ✅ **A. 本批关闭相关 Tool** | 在业务规则（②）未定前，**宁可不提供**，也不让 Agent 猜「我的测评」属于谁 |
| **4** | 发现 2 定性 | ✅ **改为「P1 待定 / 数据范围规则未明确」** | 不得按漏洞处理；不得据此改 SQL；② 先做业务决策 |

### 项目叙事（本轮自然形成）

```text
Step 11  最小 Agent 闭环
    ↓
Step 8   真实用户 Dashboard
    ↓
路线审查  发现真实身份域问题
    ↓
Step 12  Agent Identity Domain Security
    ↓
建立可信 Tool 边界
    ↓
Multi-Tool → Memory → 知识内容 → RAG
```

> 这条路线比「先把 Spring AI / Memory / RAG 全部接上」扎实得多：
> **每一步都建立在上一层的真实发现之上，而不是技术清单之上。**

---

## 十、审查状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `1bc24a5` / `1bc24a5` · LEAD 0 |
| worktree | **CLEAN** |
| 代码改动 | **0** · pom **未改** · DB **未改** · 前端 **未改** |
| 实测探针 | 仅 HTTP 只读调用，**无数据写入**，无残留 |
| 新增文档 | 本文档 |
| 服务 | 后端 `8080` 运行中 · 前端 `5173` 运行中 |

**未进入任何实现，等你裁定 §九。**
