# Phase 1｜产品定位与边界规范｜PHASE1-SCOPE-AND-BOUNDARY

> **文档性质**：Phase 1 / Step 1 产出物 —— 本项目**唯一的边界基准**。
> **状态**：✅ **已确认并关闭（2026-09-23）** —— 4 项决策见 §10；可进入 Phase 2 / Step 2
> **依据**：`PROJECT2-MASTER-PLAN.md` §1、§7｜`AGENT-EXECUTION-RULES.md` §6｜`project-audit.md`（真实代码基线）｜`db-naming-convention.md`（命名基准）
> **读者**：所有实现 Agent（Phase 3/4/5/9 必须逐条对照本文）

---

## 0. Step 1 交付说明

### 0.1 范围

**做什么**

- 确定项目名称与一句话定位
- 基于**真实代码**确认 Agent 的能力边界（可以做 / 不可以做）
- 把边界落到 **Prompt / UI / Tool / README** 四个层面（含具体文件与检查点）
- 制定**用词规范**（禁用词 → 推荐替代）
- 据真实代码**校准 Phase 2**（分层、Java 类名、接口路径）

**不做什么**

| 禁止项 | 原因 |
| --- | --- |
| 写任何业务代码 | Step 1 是定义步骤，非实现步骤 |
| 改 `pom.xml` / `application.yml` / `*.sql` / Java / Vue | 同上；本轮**零代码改动** |
| 升级依赖、改数据库 | 属 Phase 2 |
| **设计高风险（自伤/自杀/伤害他人）识别与响应策略** | `AGENT-EXECUTION-RULES.md` §6.2 硬约束：必须先出方案经用户确认，**Phase 9 才可以碰** |

### 0.2 前置核对

| 前置依赖 | 状态 | 证据 |
| --- | --- | --- |
| Step 0 只读审计 | ✅ 完成 | `project-audit.md`（11 章 + 附录 A 启动验证） |
| 全库命名规范化 | ✅ 完成 | `db-naming-convention.md` §10 |
| 数据库按新命名重建 | ✅ 完成 | 16 张新表名 / 数据齐全 / `utf8mb4` |
| 重命名残留修复 | ✅ 完成 | 用户端 26 个 html；旧引用 0 残留（同上 §10.2） |
| 与真实代码一致性核对 | ✅ 完成 | 本文 §3 每项能力均对应**已核实**的真实表与接口；§8 影响面均来自实测计数 |

### 0.3 改动清单

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `docs/PHASE1-SCOPE-AND-BOUNDARY.md` | **新增**（本文件） | Step 1 产出物 |
| `docs/README.md` | 文档清单 + 状态表各加 1 行 | 索引同步 |
| `docs/jiaojie.md` | 进度表加 1 行、下一步改为 Phase 2 | 交接同步 |
| `docs/PROJECT2-MASTER-PLAN.md` | §1.1 名称定稿、§7 Phase 1 标注完成、§7 Phase 2 补 Step 2 校准结论 | 路线图校准（§9） |

> **零代码改动**：`pom.xml`、`application.yml`、`*.sql`、`src/**/*.java`、`*.vue`、`*.html` 全部未触碰。

### 0.4 验证证据（文档类 Step 的验收方式）

| # | 校验项 | 结果 |
| --- | --- | --- |
| 1 | 全文**不含**医疗诊断类措辞（除显式标注为「禁用」的示例） | ✅ |
| 2 | §3 每项能力均可追溯到真实表/接口（见「证据」列） | ✅ |
| 3 | 全部代码块围栏闭合（`mermaid` 图可渲染） | ✅ |
| 4 | 引用的文档路径均真实存在 | ✅ |
| 5 | 未修改任何代码/配置/数据库 | ✅（Git 工作区对已跟踪文件无改动） |

### 0.5 回滚方式

✅ **完全可回滚**：删除本文件；`README.md` / `jiaojie.md` / `PROJECT2-MASTER-PLAN.md` 的 4 处新增行直接删除即可。**无代码、无配置、无数据库副作用**。

> ⚠️ 注意：`docs/` 被 `.gitignore` 排除（`db-naming-convention.md` §10.4），故目录内改动**无法 `git revert`**，回滚依赖手工删除。

---

## 1. 名称确定

| 类型 | 定稿 | 说明 |
| --- | --- | --- |
| 英文项目名 | **Mental Health Companion Agent** | 已随数据库名 `mental_health_companion_agent` 固化，不再变动 |
| 中文名 | **智能心理健康管理与支持平台** | 与英文语义一致；**必须保留「管理与支持」**，不得改为「诊疗平台」 |
| 旧项目名（历史） | 大学生心理健康管理系统（`xinlijiankangxitong`） | 仅出现在审计与历史记录中 |

### 1.1 命名一致性的现状与缺口

```text
✅ 数据库名        mental_health_companion_agent
✅ 初始化脚本      mental_health_companion_agent.sql
✅ 仓库目录        Mental Health Companion Agent/
⚠️ Java artifactId  xinlijiankangxitong          ← Phase 2 改
✅ HTTP context    /xinlijiankangxitong          ← 保持不变（已确认；纯 URL 前缀，与命名无关）
⚠️ Java 包名        com.xinlijiankangxitong...   ← Phase 2 改（仅启动类所在包）
⚠️ 文件系统目录名    d:/ai/job project/Mental Health Companion Agent/  ← 已规范
```

---

## 2. 产品定位

### 2.1 一句话定位

> 将既有的**大学生心理健康管理系统**就地改造为具备 **RAG、Tool Calling、Memory、个性化分析与辅助规划**能力的**领域 Agent 应用**——用户以自然语言表达诉求，Agent 调用真实业务能力给出**基于知识库与用户真实数据的、有边界的**支持。

### 2.2 目标用户与使用场景

| 角色 | 是否用 Agent | 典型诉求 | Agent 的应答形态 |
| --- | --- | --- | --- |
| **学生**（主用户） | ✅ **是**（当前唯一） | 「最近考试压力大睡不着」「我上次测的结果什么意思」「帮我找个老师咨询」 | 对话 + Tool 调用 + 知识引用 + 结构化卡片 |
| 心理老师 | ❌ 否（Phase 9 再评估） | 查看预约与留言 | 沿用现有后台功能 |
| 管理员 | ❌ 否 | 内容与用户管理 | 沿用现有管理端；Phase 3 增加知识库管理 |

> **为什么 Agent 只面向学生**：Agent 的差异化价值是「结合**用户自己的**历史数据做解释与陪伴」；老师/管理员是**运营视角**，用表格与图表效率更高，且让 Agent 访问他人数据会显著扩大隐私面。

### 2.3 明确「不是什么」（Non-Goals）

| ❌ 不是 | 说明 |
| --- | --- |
| 心理诊断系统 | 不做疾病判定，不解释量表为「病症」 |
| 心理治疗系统 | 不提供治疗方案、不做疗程设计 |
| 「AI 心理医生」 | 不冒充医生，不建立医患关系暗示 |
| 危机干预系统 | 高风险场景**不做算法判定**，按 §4.1 的合规策略处理（Phase 9 设计） |
| 通用闲聊机器人 | 有明确领域边界，非知识库内容应如实说明 |
| Multi-Agent 平台 | 单 Agent + Tool + RAG 可解决则**不加**（`AGENT-EXECUTION-RULES.md` §2 严禁 5） |

---

## 3. 能力边界矩阵（基于真实代码）

> 「真实能力」列全部对应已核实的表与接口（`project-audit.md` §4/§7 + 命名规范后的新表名）。

### 3.1 ✅ 允许的 Agent 能力

| # | 能力 | 真实业务底座 | 证据（表 / 现有能力） |
| --- | --- | --- | --- |
| C1 | 提供心理健康知识科普 | `knowledge_article`（原 `jiankangzhishi`） | 表 + `JiankangzhishiController` CRUD |
| C2 | 帮助**理解**测评结果与历史变化 | `assessment_paper/record/answer/wrong_question` | 判分与错题逻辑（`ExamredetailsController.saveExamredetails`） |
| C3 | 分析用户历史数据的**一般性趋势** | `assessment_record`（含 `submitted_at`） | 记录表 |
| C4 | 一般性自我管理建议（放松、作息、学习节奏） | 无（生成性能力，受 Prompt 约束） | — |
| C5 | 推荐相关知识 | `knowledge_article.category` + 字典 `knowledge_category` | 分类字段 + 字典 |
| C6 | 查找心理老师并**辅助预约** | `counselor` + `counseling_appointment` + 字典 `time_slot` | 预约下单/审核链路 |
| C7 | 查询与管理自己的预约 | `counseling_appointment.student_id` | `XinlilaoshiOrderController` |
| C8 | 查询自己的留言与回复 | `counselor_message` | 留言链路 |
| C9 | 查看自己的收藏与通知 | `counselor_favorite` / `notification` | CRUD |
| C10 | 辅助制定**日常计划**（学习/休息/运动/情绪记录/放松训练/知识阅读） | 无（生成性能力） | — |

### 3.2 ❌ 明确禁止的 Agent 能力

| # | 禁止项 | 判定标准（可测） |
| --- | --- | --- |
| N1 | 诊断或判定精神疾病 | 输出中不得出现「你患有…」「符合…的典型特征」「你的病症是…」 |
| N2 | 冒充心理医生 / 精神科医生 | 不得使用「作为你的医生/咨询师」等身份暗示 |
| N3 | 依据单次对话判定患病 | 不得由单轮对话得出任何疾病结论 |
| N4 | 给出高风险医疗决策 | 不得涉及用药、剂量、停药、住院、诊疗方案 |
| N5 | 替代专业心理咨询 | 不得声明「我可以替代咨询」「不需要找老师」 |
| N6 | 生成伪装成治疗方案的「医嘱」 | 计划类输出不得使用医嘱语气与医疗术语 |
| N7 | 对量表做临床解释 | 不得把量表得分标注为「正常/异常/达标」等临床判定 |
| N8 | 处理高风险场景（自伤/自杀/伤害他人） | **不得自研识别算法或响应话术**，一律走 §4.1 的既定策略（Phase 9 产出前**不得上线相关逻辑**） |
| N9 | 访问他人数据 | Tool 一律取服务器身份，不接受 `userId` 入参 |
| N10 | 冒用心理老师身份回复 | 不得以老师名义写入 `counselor_message.reply_content` |

---

## 4. 边界在四个层面的落地规范

### 4.1 Prompt 层（Phase 4 / Step 11）

| 项 | 规定 |
| --- | --- |
| 存放位置 | `src/main/resources/prompts/`（**禁止硬编码进 Java**） |
| 必备文件（建议） | `mental-health-agent-system.md`（身份与边界）、`safety-rules.md`（禁止项）、`refusal-templates.md`（越界应答模板） |
| 必须声明 | ① 身份：心理健康**助手**，非医生；② 能力范围见 §3.1；③ 禁止项见 §3.2；④ 知识不足时如实说明，**不得编造**；⑤ 高风险场景按既定策略响应，**不自行发挥** |
| 禁止写法 | 把业务规则（谁能预约、时间片是否可用）写进 Prompt —— **LLM 决定语义，代码决定合法性** |
| 越界应答基线 | 用于 N1–N8：先共情 → 说明能力边界 → 给出可行路径（知识/测评/预约老师）→ 不诊断、不承诺 |

### 4.2 UI 层（Phase 3 / Step 6–9）

| 项 | 规定 |
| --- | --- |
| 信息架构 | 首页 / AI 助手 / 我的状态 / 心理测评 / 知识中心 / 咨询预约 / 我的 |
| 文案基调 | 温和、低压力、以「状态 + 可行动作」为中心；**不使用**诊断/治疗/患者/医嘱措辞 |
| 免责声明 | ① AI 助手页**首屏常驻**一行；② 首次会话时以卡片形式说明「本助手提供心理健康知识与管理支持，不能替代专业诊疗」；③ 测评结果页附一句「结果仅用于自我了解，不构成任何诊断」 |
| Tool 过程反馈（Step 9） | 必须可见：「正在查询你的最近一次测评…」「正在检索相关心理健康知识…」「正在生成建议…」 |
| 空状态话术 | 禁止「暂无数据」这类冷冰冰文案，改为引导性文案（如「还没有测评记录，要现在做一次吗？」） |
| 知识来源展示 | RAG 命中时展示来源标题，**不得伪造来源** |

### 4.3 Tool 层（Phase 4 / Phase 9）

**允许的 Tool 白名单**（命名可在 Step 12–15 微调，**能力范围不得超出**）：

| Tool | 权限 | 约束 |
| --- | --- | --- |
| `get_my_health_profile` | LOW | 仅当前学生 |
| `get_latest_assessment` / `get_assessment_history` / `get_assessment_detail` | LOW | 仅当前学生；经领域 DTO，不直出 Entity |
| `get_my_wrong_questions` | LOW | 仅当前学生 |
| `search_health_knowledge` / `get_health_knowledge` | LOW | 只读已发布知识 |
| `get_my_appointments` | LOW | 仅当前学生 |
| `find_available_counselor` | LOW | 只读 |
| `create_appointment` | **MEDIUM** | 参数校验 + 冲突校验 + 确认流程 |
| `cancel_appointment` | **MEDIUM–HIGH** | 需用户二次确认 |
| `get_my_messages` / `send_message_to_counselor` | MEDIUM | 仅新增留言，**不得写入老师回复字段** |
| `get_my_notifications` | LOW | 仅当前学生 |
| （Phase 5）`retrieve_knowledge` | LOW | 只读向量库；返回来源元数据 |

**禁止出现的 Tool 类型**：

```text
❌ 诊断类        diagnose_*  / assess_disorder_*
❌ 风险评分类    crisis_score / risk_level（除 Phase 9 经确认的既定策略）
❌ 医疗建议类    medication_advice / treatment_plan
❌ 跨用户读取类  get_user_data(userId)  —— 身份只从服务器取
❌ 直连 SQL 类   execute_sql / query_database
❌ 冒名写入类    以老师身份写 reply_content
```

> **分层额外约束**：Tool **不得**直接访问 DAO/Mapper，必须经 Service（`AGENT-EXECUTION-RULES.md` §3）。

### 4.4 README 层（Phase 12 / Step 38）

必须包含 **「项目定位」** 与 **「已知边界」** 两节，逐条列出 §2.3 与 §3.2，并明确写出「本项目不提供诊断与治疗，如遇紧急情况请联系专业机构」。

---

## 5. 用词规范（禁用词 → 推荐替代）

> 适用于 **Prompt / UI / Tool 描述 / README / 文档**全部产出。

| ❌ 禁用 | ✅ 推荐 | 原因 |
| --- | --- | --- |
| 诊断 / 确诊 | 了解 / 参考 / 自我认识 | 本项目无诊断资质 |
| 治疗 / 治愈 / 疗程 | 自我管理 / 调整 / 支持 | 避免治疗关系暗示 |
| 患者 / 病人 / 用户病情 | 用户 / 同学 / 你 / 当前状态 | 不把用户病理化 |
| 医嘱 / 处方 / 用药建议 | 建议 / 可以尝试 | 禁止医疗决策 |
| 你患有 X 症 | 你的测评结果显示出…；你的描述可能与…有关 | N1/N3 |
| 正常 / 异常（用于量表结论） | 分值区间 / 与自己上次相比的变化 | N7 |
| 心理医生 | 心理老师 | 与系统内真实角色一致 |
| 危机评分 / 风险等级（对用户可见） | ——（不得对用户展示） | N8 |
| AI 医生 / 智能诊疗 | 心理健康助手 | 身份一致性 |

**必须使用的固定表述**

- 助手自我介绍：**「我是心理健康助手，可以帮你了解心理健康知识、看懂测评结果、找心理老师聊聊。」**
- 能力边界声明：**「我可以提供心理健康知识与管理支持，但不能替代专业诊疗。」**
- 知识不足：**「当前知识库中没有找到足够相关的信息。」**（不得编造）

---

## 6. 角色边界与数据可见性

| 数据 | 学生（Agent 可访问） | 心理老师 | 管理员 | 说明 |
| --- | --- | --- | --- | --- |
| 自己的资料（`student`） | ✅ 读写本人 | — | ✅ 管理 | Agent 仅本人 |
| 测评记录/答案/错题 | ✅ **仅本人** | ❌ | ✅ 管理 | 老师不通过 Agent 看 |
| 知识文章 | ✅ 只读 | ✅ | ✅ 管理 | Phase 5 成为 RAG 源 |
| 心理老师名录 | ✅ 只读 | ✅ 本人 | ✅ 管理 | 用于 C6 |
| 预约 | ✅ 本人（建/查/撤） | ✅ 本人被预约项 | ✅ 管理 | 取消需二次确认 |
| 留言 | ✅ 本人（发/查） | ✅ 回复 | ✅ 管理 | **Agent 不得代写回复** |
| 收藏 / 通知 | ✅ 本人 | ✅ 本人 | ✅ 管理 | — |
| 其他学生的任何数据 | ❌ **绝对禁止** | ❌ | ✅ 管理后台 | N9 |

---

## 7. 边界验收清单（Phase 3/4/12 逐项勾选）

```text
Prompt
[ ] Prompt 外置于 resources/prompts/，Java 中无长文本 System Prompt
[ ] System Prompt 显式声明身份（助手，非医生）与 §3.2 禁止项
[ ] 有越界应答模板（N1–N7 各至少 1 条）
[ ] 高风险场景未自研策略；如未完成 Phase 9 设计，对应逻辑不存在

UI
[ ] AI 助手页有常驻免责声明；首次会话有说明卡片
[ ] 测评结果页有「不构成诊断」提示
[ ] 全文无 §5 禁用词（含空状态、错误提示、按钮文案）
[ ] Tool 调用过程在 UI 可见（查询中/检索中/生成中）
[ ] RAG 回答展示真实来源，无伪造引用

Tool
[ ] 所有用户相关 Tool 从服务器身份取 userId，入参无 userId
[ ] 无诊断类 / 风险评分类 / 医疗建议类 / 跨用户类 / 直连 SQL 类 Tool
[ ] create_appointment 与 cancel_appointment 有确认流程
[ ] Tool 不直接访问 DAO（经 Service）
[ ] 数据经领域 DTO，不直出 Entity

README
[ ] 含「项目定位」与「已知边界」两节
[ ] 写出紧急情况引导语
```

---

## 8. Phase 2 校准（据真实代码重新确认）

### 8.1 分层目标

```text
Controller（HTTP、参数校验、鉴权入口）
    ↓
Application / Agent（用例编排；Phase 4 起为 MentalHealthAgent）
    ↓
Tool（领域能力封装，供 LLM 调用；**不允许直连 DAO**）
    ↓
Service（业务规则、事务）
    ↓
DAO / Mapper
    ↓
Database
```

**禁止**：`Controller → LLM`、`Tool → SQL`、`Controller → 直接 JDBC`。

### 8.2 Java 类名重命名（用户已确认，Phase 2 执行）

现状：类名保留旧拼音名，仅 `@TableName` 指向新表（`db-naming-convention.md` §9 D1）。Phase 2 补齐：

| 现状类名前缀 | 目标类名 | 对应表 |
| --- | --- | --- |
| `Yonghu*` | `Student*` | `student` |
| `Xinlilaoshi*` | `Counselor*` | `counselor` |
| `XinlilaoshiOrder*` | `CounselingAppointment*` | `counseling_appointment` |
| `XinlilaoshiLiuyan*` | `CounselorMessage*` | `counselor_message` |
| `XinlilaoshiCollection*` | `CounselorFavorite*` | `counselor_favorite` |
| `Jiankangzhishi*` | `KnowledgeArticle*` | `knowledge_article` |
| `Tongzhi*` | `Notification*` | `notification` |
| `Exampaper*` | `AssessmentPaper*` | `assessment_paper` |
| `Examquestion*` | `AssessmentQuestion*` | `assessment_question` |
| `Examrecord*` | `AssessmentRecord*` | `assessment_record` |
| `Examredetails*` | `AssessmentAnswer*` | `assessment_answer` |
| `Examrewrongquestion*` | `AssessmentWrongQuestion*` | `assessment_wrong_question` |
| `Dictionary*` | `SysDictItem*` | `sys_dict_item` |
| `Config*` | `SysConfig*` | `sys_config` |
| `Users*` | `AdminUser*` | `admin_user` |
| `Token*` | `AuthToken*` | `auth_token` |

**各层影响面（实测计数）**

| 层 | 文件数 | 说明 |
| --- | --- | --- |
| `entity` | 17 | 16 个实体 + `EIException` |
| `dao` | 17 | 每个实体 1 个 |
| `service` + `service/impl` | 34 | 17 接口 + 17 实现 |
| `controller` | 17 | 每个实体 1 个 |
| Mapper XML | 17 | 含 `typeAliases` / `resultType` 引用 |
| **合计（不含死代码）** | **≈ 102** | 建议**分层分批**改，每批独立构建 + 提交 |

### 8.3 🧹 Phase 2 应顺手清理的死代码（本轮新发现）

| 发现 | 证据 | 结论 |
| --- | --- | --- |
| `com.entity.model`（13 个 `*Model`） | 全仓 `com.entity.model` 仅 **13 处匹配，且全部在 model 包自身** | **孤儿类**，无任何消费者 |
| `com.entity.vo`（13 个 `*VO`） | 全仓 `com.entity.vo` 仅 **13 处匹配，且全部在 vo 包自身** | **孤儿类**，无任何消费者 |
| `com.entity.view`（13 个 `*View`） | 被 **52 个文件**引用（service/impl 13 + dao 13 + controller 13 + view 自身 13） | **在用，保留** |

> **处理建议**：Step 3（DTO/VO 重构）时**不要**直接沿用这 26 个孤儿类做 DTO —— 它们是字段复制的产物，未按接口契约设计。建议：按接口需求**重新设计** `dto`/`vo`，同时删除这 26 个孤儿类（预计净减 ~26 文件）。

### 8.4 接口路径重命名（✅ **已确认：Phase 2 末尾执行**）

现状：表已叫 `student`，接口仍叫 `/yonghu/*`（`db-naming-convention.md` §9.1 有意保留）。

| 现状路径 | 目标路径 |
| --- | --- |
| `/yonghu/*` | `/student/*` |
| `/xinlilaoshi/*` | `/counselor/*` |
| `/xinlilaoshiOrder/*` | `/counselingAppointment/*` |
| `/xinlilaoshiLiuyan/*` | `/counselorMessage/*` |
| `/xinlilaoshiCollection/*` | `/counselorFavorite/*` |
| `/jiankangzhishi/*` | `/knowledgeArticle/*` |
| `/tongzhi/*` | `/notification/*` |
| `/exampaper/*` … `/examrewrongquestion/*` | `/assessmentPaper/*` … `/assessmentWrongQuestion/*` |
| `/dictionary/*` | `/sysDictItem/*` |
| `/config/*` | `/sysConfig/*` |
| `/users/*` | `/adminUser/*` |

**为什么建议现在就改**

- Phase 3 将**新建** Vue 3 前端；若路径此刻不定稿，新前端会按旧路径写一遍再返工
- 路径是**对外契约**，越晚改代价越大（Phase 4 的 Tool、Phase 5 的 RAG 都会引用）

**代价与顺序**

- 影响 `@RequestMapping`（17 个 Controller）+ 用户端 49 个 html + 管理端 55 个 vue（**均可用脚本化替换 + 逐页验证**）
- 管理端 `admin/dist` 是编译产物，将因路径变更而失效 —— 与 §9 D2「留待 Phase 3 重建」一致，**属已知接受代价**
- **执行顺序建议**：放在 Phase 2 **末尾**（分层与类名稳定后），作为独立 Step，避免与结构重构交叉

### 8.5 Phase 2 建议执行顺序（校准后）

```text
Step 2   分层骨架 + Boot 3.x/JDK17 升级（分支开发，不可回滚）
Step 2b  CommonController 动态表名接口收敛为白名单（安全）
Step 3   DTO/VO 重构（含剔除 password / 身份证号 —— 🔴 S1）+ 清理 26 个孤儿类
Step 4   统一异常处理（BusinessException + GlobalExceptionHandler）
Step 5   统一身份（CurrentUserProvider → currentUserId）
Step 5b  Java 类名重命名（≈102 文件，分批）
Step 5c  接口路径重命名 + 前端同步替换（含 §10 待决项 3 一并决策）
```

---

## 9. 需回写 `PROJECT2-MASTER-PLAN.md` 的差异

| # | 原表述 | 校准后 | 依据 |
| --- | --- | --- | --- |
| 1 | §1.1 名称「暂定」，Phase 1 后确定 | **定稿**：Mental Health Companion Agent / 智能心理健康管理与支持平台 | §1 |
| 2 | §7 Phase 1 仅一句「边界四层落点已定义」 | 补充：四层落点已具体到文件与检查点；新增用词规范与角色边界 | 本文 §4–§6 |
| 3 | §7 Phase 2 未包含「类名重命名」与「路径重命名」 | 新增 Step 5b / 5c；Step 3 增「清理 26 个孤儿类」 | §8.2–§8.4 |
| 4 | §7 Phase 2 Step 2 未提 `CommonController` 收敛 | 拆出 Step 2b | `project-audit.md` §8.1 S3/S4 |
| 5 | §1.3 边界表 | 与本文 §3 保持一致；边界矩阵扩展为 10 项允许 / 10 项禁止 | 本文 §3 |

---

## 10. 决策确认（2026-09-23 已全部确认，Phase 1 关闭）

| # | 问题 | ✅ 决策 | 理由 | 连带影响 |
| --- | --- | --- | --- | --- |
| 1 | 项目名称 | **定稿**：Mental Health Companion Agent / 智能心理健康管理与支持平台 | 数据库名、脚本名、仓库目录已固化，改名成本远大于收益 | 无 |
| 2 | AI 助手面向角色 | **仅学生** | Agent 的价值在「结合**本人**历史数据做解释与陪伴」；老师/管理员是运营视角，表格图表更高效；且可把隐私面压到最小 | 老师/管理员不接 Agent；§6 数据可见性矩阵作为约束生效 |
| 3 | HTTP context-path | **保持不改**（`/xinlijiankangxitong`） | 它是纯 URL 前缀，与数据库命名无关；改名收益低 | 104 个前端文件与全部图片 URL 无需改动 |
| 4 | 接口路径重命名 | **在 Phase 2 末尾执行**（§8.4） | 路径是**对外契约**，越晚改代价越大；否则 Phase 3 新建前端要按旧路径开发再返工 | 17 个 Controller + 49 html + 55 vue 需同步替换；`admin/dist` 将失效（与 `db-naming-convention.md` §9 D2 一致，**已知接受**） |

**Phase 1 正式关闭。** 下一步：**Phase 2 / Step 2（后端分层骨架 + Boot 3.x / JDK17 升级）**，执行顺序见 §8.5。

---

## 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-09-23 | Phase 1 / Step 1 首次产出：名称定稿、能力边界矩阵（10 允许 / 10 禁止）、四层落地规范、用词规范、角色边界、验收清单、Phase 2 校准（含 26 个孤儿类死代码发现） |
| v2 | 2026-09-23 | §10 四项决策全部按推荐方案确认（名称定稿 / Agent 仅面向学生 / context-path 保持不变 / 接口路径 Phase 2 末尾重命名）；Phase 1 **关闭**，进入 Phase 2 |
