# 债务清理批次 —— 实施计划（v1 · 零代码 · 停等审查批准后才改代码）

> **性质**：📋 **实施计划 v1**（用户要求：先输出敏感 DAO 全量枚举、债 1 遮蔽位置与 8 项误伤矩阵映射、测试计划，**审查批准后才允许改代码**）
> **基线**：`HEAD = origin = e75842d` · worktree 仅文档修改 · 本计划不含任何实现
> **边界（用户裁定）**：债 1 只对**最终返回给用户的模型自由文本**做遮蔽，不改模型原始输出、语料、SafetyRules；
> 债 2 先枚举确认敏感 DAO 再逐个降级，**不扩大到全局关闭 SQL 日志**；prod profile 暂缓至部署阶段。

---

## §1 敏感 DAO 全量枚举（18 个 · 分级结果）

### 取证方法
全量列出 `com/dao`（18 个）→ 扫描对应实体字段 → 按「参数是否含用户生成内容 / 凭据 / PII / 答案 / 令牌」分级。

### 分级表

| 级别 | DAO | 敏感字段（实体取证）| 建议级别 |
| :-: | --- | --- | :-: |
| **P0 ✅ 已降级** | `AgentConversationDao` · `AgentMessageDao` | title · content（对话内容）| warn（Step ⑤，实测 0 泄露）|
| **P0 🔴 新发现** | `AuthTokenDao` | **`token`（登录令牌）** —— debug 下每次 token 查询把令牌打进日志 ⇒ **会话劫持面** | **warn（最高优先）** |
| **P1 内容型** | `CounselorMessageDao` | content · replyContent（用户留言 + 咨询师回复）| warn |
| P1 内容型 | `AssessmentAnswerDao` | studentAnswer · score（学生作答原文 + 成绩）| warn |
| P1 内容型 | `AssessmentWrongQuestionDao` | studentAnswer | warn |
| P1 答案泄露面 | `AssessmentQuestionDao` | **answer（测评题正确答案）**——Prompt 第 10 条「不输出正确答案」的内部对应面 | warn |
| P1 凭据/PII | `AdminUserDao` · `CounselorDao` · `StudentDao` | password（凭据）· phone · email（PII）| warn |
| P1 行为/评语 | `CounselingAppointmentDao` | appointmentNo · reviewComment（个人预约行为 + 审核评语）| warn |
| P1 成绩 | `AssessmentRecordDao` | totalScore（成绩）| warn |
| **P2 维持 debug** | `KnowledgeArticleDao`（公开语料）· `NotificationDao`（公开公告）· `SysDictItemDao`（字典）· `SysConfigDao`（配置）· `AssessmentPaperDao`（试卷名/总分，公开）| — | debug（保留开发调试能力）|

**汇总**：18 = 2 已降级 + **9 待降级（P0×1 + P1×8）** + 5 维持 debug + 2 已计入 P0。
（实施时以 yml 追加 9 行例外，**不全局关闭** —— 用户裁定边界。）

---

## §2 债 1：服务端遮蔽的位置与边界设计

### 2.1 遮蔽位置

```text
AgentService.chat()
  ├─ modelAnswer = agentModel.answer(...)          ← 模型原始输出（无持久化：日志只记长度）
  ├─ SafetyRules.evaluate(用户原话) → 卡片
  └─ finalAnswer = phoneMasking.mask(modelAnswer)  ← ✅ 遮蔽点：只作用于返回给用户的模型自由文本
        ↓
     AgentReply { answer = finalAnswer + 卡片, safety }   ← 卡片（零数字）不经过遮蔽也天然安全；
                                                            语料在 KNOWLEDGE_CONTEXT 段（不改）
```

**「不修改模型原始输出」的落实说明**：模型原始输出在本系统**无持久化**（日志只记长度与耗时）；
遮蔽仅作用于返回给用户的字符串 —— 与裁定边界一致。

### 2.2 高置信电话模式（只匹配带结构特征的号码，纯短数字/编号不命中）

| 模式 | 示例（命中）| 反例（不命中）|
| --- | --- | --- |
| `400[-\s]?\d{3}[-\s]?\d{3,4}` | 400-161-9995 | 「400 米」(数字不足)|
| `(?<!\d)1[3-9]\d{9}(?!\d)` | 13812345678 | 日期 20261104(1 开头但 8 位后接字符——11 位约束)|
| `0\d{2,3}[-\s]?\d{7,8}` | 010-12345678 | 订单号 ABC1234567（无 0 区号前缀）|
| 全角数字串 ≥10 位 | １３８１２３４５６７８ | 全角「２０」时长 |

**替换文案草案（待批）**：
```text
[此号码未经平台核实，已隐藏。紧急援助请通过当地官方渠道查询，或前往最近的医院急诊。]
```
—— 落实你的产品语义要求：**隐藏数字的同时附加指引，不让用户误以为已提供可用热线**。

### 2.3 八项误伤矩阵映射（Round 3 冻结版 → 本批测试）

| # | 误伤矩阵项 | 预期 | 测试 |
| :-: | --- | :-: | :-: |
| ① | 正常数字（15/100 分）| 不命中 | S4 |
| ② | 日期（2026-11-04）| 不命中 | S4 |
| ③ | 分数（totalScore 88）| 不命中 | S4 |
| ④ | 订单/编号（ABC1234567 · 纯 7 位）| 不命中（无结构特征）| S5 |
| ⑤ | 电话号码不同格式（400/手机/座机）| **命中并替换** | S1–S3 |
| ⑥ | 夹带在正常文本中 | **命中替换，前后文保留** | S6 |
| ⑦ | 已验证资源中的号码保留 | 当前卡片/语料零数字 ⇒ N/A；架构上 mask **只作用于模型自由文本**，未来 `configured()` 的卡片不经过遮蔽路径 | S8 + 架构断言 |
| ⑧ | 中英文/全角数字 | 全角 ≥10 位命中；普通中文相邻不误伤 | S7 |

---

## §3 债 2：降级实施与探针回归计划

### 3.1 实施（yml 追加 9 行，逐个对应 §1 分级）

```yaml
  com.dao.AuthTokenDao: warn
  com.dao.CounselorMessageDao: warn
  com.dao.AssessmentAnswerDao: warn
  com.dao.AssessmentWrongQuestionDao: warn
  com.dao.AssessmentQuestionDao: warn
  com.dao.AdminUserDao: warn
  com.dao.CounselorDao: warn
  com.dao.StudentDao: warn
  com.dao.AssessmentRecordDao: warn
  com.dao.CounselingAppointmentDao: warn
```

### 3.2 探针回归（自动化，OutputCapture）

| 探针 | 步骤 | 断言 |
| --- | --- | :-: |
| T-a 留言内容 | 测试事务内 insert 一条 `counselor_message`（content=探针词）| OutputCapture 中探针词出现 = **0** |
| T-b token | 登录后查 auth_token（token=探针值场景）| token 值出现 = **0** |
| T-c 低敏对照 | 查 knowledge_article（公开语料）| **SQL 语句仍可见**（证明降级是精准的，不是全局关闭）|
| 变异 | 还原 yml（移除 CounselorMessageDao 例外）| T-a 转红（探针词出现）⇒ 降级真实生效 |

---

## §4 测试计划汇总

| 新增 | 用例数 | 内容 |
| --- | :-: | --- |
| `Phase4PhoneMaskingTest`（或并入既有）| S1–S8（8 例）+ M1/M2（2 变异）| 债 1：遮蔽 + 误伤矩阵 |
| 债 2 探针测试（`OutputCapture`）| T-a/T-b/T-c（3 例）+ 变异 | 债 2：降级精准性 |
| 既有回归 | 330 → 预计 **341**（+11）| 全量 |

## §5 批次流程（冻结顺序）

```text
① 本计划审查（你批准词表/模式/替换文案/DAO 清单）   ← 当前
② 实现：PhoneMasking + yml 9 行（先写测试 → 先红 → 实现 → 绿）
③ 变异验证（债 1 M1/M2 · 债 2 探针变异）
④ 真实 LLM 抽查（危机场景含号码的模型输出 → 遮蔽生效）
⑤ 全量回归 + git diff/DB/依赖检查
⑥ commit + push（本批次独立提交，不与 ⑦b 合并）
```

---

## §6 待审查项（本轮请逐项确认）

| # | 项 | 我的建议 |
| :-: | --- | :-: |
| 1 | §1 分级表（9 个 DAO 降 warn · 5 个维持 debug）| ✅ |
| 2 | §2.2 高置信模式（4 组正则）与「纯短数字/编号不命中」策略 | ✅ |
| 3 | §2.2 替换文案措辞 | ✅（可改）|
| 4 | §3 降级清单（9 行 yml）| ✅ |
| 5 | 测试计划（S1–S8 + 探针 T-a/b/c + 变异）| ✅ |
