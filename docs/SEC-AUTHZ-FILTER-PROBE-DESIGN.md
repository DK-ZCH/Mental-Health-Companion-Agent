# `AUTHZ-FILTER-PROBE` 风险核实与回归测试设计

> **性质**：只读核实 + 测试设计（📐 **零代码改动 · 待裁定**）
> **基线**：`HEAD = origin = d6db0b6` · LEAD 0 · worktree CLEAN
> **上游**：Step 15 CLOSED 时用户指定「第 1 条边界：单独处理 `AUTHZ-FILTER-PROBE`」
> **本阶段纪律**：**不修改 SQL、不修改授权逻辑、不修改任何业务代码**；只产出证据与测试设计

---

## 一、结论摘要

| # | 结论 | 证据强度 |
| :-: | --- | :-: |
| 1 | **原议题「筛选条件可做存在性探测」在正确收敛的表上不成立** | ✅ 实测（跨范围真实值 = 不存在值 = 0）|
| 2 | 🔴 **新发现（比原议题严重）：`assessment_answer` / `assessment_wrong_question` 咨询师侧无任何数据范围约束** | ✅ 实测对照（同一老师对同批数据，record 表 0 条 / answer 表 5 条）|
| 3 | 🔴 **Step 13 的咨询师规则只覆盖 6 张表中的 1 张** | ✅ 代码 + 实测双证 |
| 4 | **学生侧行级范围正确，无越权、无探针信号** | ✅ 实测（含 `studentId` 覆盖尝试）|
| 5 | **六个端点为强制鉴权（无 `@IgnoreAuth`）** | ✅ 实测（均 `200 + code=401`）|

> **定性纪律**：结论 2 证明的是「**过滤条件在代码中不存在**」这一技术事实；
> 它是否构成业务缺陷，取决于「咨询师能否看这些学生的答卷」这一业务规则 ——
> 但**同一批学生的测评记录已被限制**，答卷却完全放开，**两个口径自相矛盾**，
> 这本身即构成需要裁定的不一致，而非「可能合规」的设计选择。

---

## 二、攻击面盘点（六个 `/page` + 六个 `/list`）

### 2.1 代码层事实

| 表 | Controller 传 `counselorField` | XML 有 `params.counselorId` 分支 | 实际效果 |
| --- | :-: | :-: | --- |
| `assessment_record` | ✅ `"counselorId"` | ✅ **有**（Step 13 · L55–L63 EXISTS `counseling_appointment`）| 正确收敛 |
| `assessment_answer` | ✅ `"counselorId"` | ❌ **无**（`counselor` 出现次数 = **0**）| 🔴 **条件被静默丢弃** |
| `assessment_wrong_question` | ✅ `"counselorId"` | ❌ **无**（`counselor` 出现次数 = **0**）| 🔴 **条件被静默丢弃** |
| `counseling_appointment` | ✅ | ✅ | 正确收敛 |
| `counselor_favorite` | ✅ | ✅ | 正确收敛 |
| `counselor_message` | ✅ | ✅ | 正确收敛 |

**失效机制**（与 `DataScope` 的设计假设直接相关）：

```java
// DataScope.java:76-83 —— 对 counselor 无条件 put，不关心目标 XML 是否消费该参数
params.put(counselorField, userId);        // counselorId = 1
        ↓
// AssessmentAnswerDao.xml 中不存在 <if test="params.counselorId ...">
        ↓
参数被静默忽略 ⇒ WHERE 子句少一个条件 ⇒ 返回全量
```

> `DataScope` 的注释自己写了「本批刻意未扩张：教师侧可见范围业务规则在代码与数据中都没有依据」，
> 因此它只保证「**参数被放进去了**」，**不保证**「**目标 SQL 消费了它**」。
> 这是一个**契约缺口**：`DataScope` 无法知道调用方是否真的实现了收敛。

### 2.2 鉴权层事实

| 项 | 结果 |
| --- | :-: |
| 六个 Controller 的 `@IgnoreAuth` 数量 | **全部 0** |
| 未认证访问六个 `/page` | 全部 `200 + code=401`「请先登录」|
| 结论 | **不存在匿名枚举面**（与 DataScope 注释中提到的「免鉴权 `/list`」不是同一批端点）|

### 2.3 状态常量（核实 `assessment_record` 规则确实生效）

```java
// CounselorAssessmentScope.java
public static final int    STATUS_APPROVED        = 2;
public static final String PARAM_APPROVED_STATUS  = "approvedStatus";
```

由 `AssessmentRecordController#page` 在 `DataScope.apply` 之后注入：

```java
params.put(CounselorAssessmentScope.PARAM_APPROVED_STATUS,
           CounselorAssessmentScope.STATUS_APPROVED);
```

> ⚠️ **本文件作者此前的错误结论已更正**：曾据 `grep approvedStatus` 判断「Java 中无赋值点、
> 条件恒不匹配、fail-closed」—— 该判断**错误**，实际通过常量 `PARAM_APPROVED_STATUS` 注入，
> 字面量搜索无法命中。实测已推翻该判断（见 §三）。

---

## 三、实测对照实验（既有数据，无需造数据）

### 3.1 种子数据（决定实验能否区分）

```text
student     : 1(…901) · 2(…902) · 3(…903)
预约关系     : student1 ↔ counselor1  status=2（已同意）
              student1 ↔ counselor2  status=1（待审核）
              student1 ↔ counselor3  status=1（待审核）
数据归属     : assessment_record 只有 student1（1 条）
              assessment_answer 只有 student1（5 条）
              assessment_wrong_question 只有 student1（3 条）
```

**这组数据恰好构成天然对照**：只有 `counselor1` 有「已同意」预约，
而 `counselor2 / counselor3` 没有 —— **同一批数据，三个老师，两种关系**。

### 3.2 决定性结果

| 端点 | counselor1（已同意）| counselor2（待审核）| counselor3（待审核）| 判定 |
| --- | :-: | :-: | :-: | :-: |
| `assessmentRecord/page` | **1** ✅ | **0** ✅ | **0** ✅ | 规则生效 |
| `assessmentAnswer/page` | **5** 🔴 | **5** 🔴 | **5** 🔴 | **无约束** |
| `assessmentWrongQuestion/page` | **3** 🔴 | **3** 🔴 | **3** 🔴 | **无约束** |

```text
同一批学生数据、同一个「已同意/待审核」维度：
   record 表 → 0 / 0（正确区分）
   answer 表 → 5 / 5 / 5（完全不区分）
```

⇒ **这不是「规则宽松」，而是「规则根本不存在」**。
若只是策略差异，`counselor2/3` 至少应表现出某种差异；实测是**恒等于全表**。

### 3.3 学生侧（证明范围正确，且无法探针）

| 场景 | 结果 | 判定 |
| --- | :-: | :-: |
| student1 无筛选 | 5 | 本人数据 |
| student2 无筛选 | 0 | 本人无数据 |
| **student2 + `studentPhone=17703786901`（学生1真实）** | **0** | ✅ 未泄露 |
| **student2 + `studentPhone=19900000000`（不存在）** | **0** | ✅ **与上一行无差异** |
| **student2 + `studentId=1`（显式覆盖尝试）** | **0** | ✅ `DataScope` 覆盖生效 |
| student2 + `counselorMessage?studentId=1` | 2 | ✅ 是**学生2自己的 2 条**（DB 核对），参数被覆盖 |

⇒ **学生侧：既无越权，也无探针信号**（真实值与不存在值返回完全相同）。

### 3.4 已正确收敛的表：探针同样不成立

| 场景 | 结果 |
| --- | :-: |
| counselor1 + message + `studentPhone=17703786902`（**非其学生**，真实）| **0** |
| counselor1 + message + `studentPhone=17703786901`（其学生，真实）| 2 |
| counselor1 + message + `studentPhone=19900000000`（不存在）| **0** |

⇒ 「非其学生」与「不存在」**返回完全相同（0）** ⇒ **无法据此推断他人是否存在**。

### 3.5 咨询师在有分支表上的收敛正确性

| 表 | counselor1 | counselor2 | DB 期望 | 判定 |
| --- | :-: | :-: | --- | :-: |
| `counselor_message` | 3 | 2 | 3（学生1×2 + 学生3×1）/ 2 | ✅ |
| `counselor_favorite` | 2 | 3 | 2 / 3 | ✅ |

---

## 四、泄露信号分级（回到用户的四项要求）

| 信号 | 是否可被未授权者利用 | 证据 |
| --- | :-: | :-: |
| **记录条数 / `total`** | 🔴 **在 `assessment_answer` / `wrong_question` 上可以**（真实 5 / 不存在 0）| §3.2 / §3.6 |
| 记录条数 / `total`（其余四表）| ✅ 不可以（跨范围恒 0）| §3.4 |
| **分页行为（`pages` / `total`）** | 同上 —— 依赖 `total`，只要 `total` 不区分即不泄露 | 同上 |
| **错误差异（`code` / `msg`）** | ✅ 无差异（全部 `code=0`，未触发异常分支）| §3.2 全程 `code=0` |
| **响应时间** | ⚠️ **未测**，且**在已证实直接暴露的前提下无判定价值** | — |

> **结论**：把「filter probe」当成独立风险是**方向偏移**。
> 真正的问题是「**两张表没有行级约束**」；「可探测存在性」只是它的**附带后果**，
> 而且在有约束的表上**并不存在**。

### 3.6 附带后果的实测（仅用于说明，不作为独立结论）

| counselor1 + `assessmentAnswer` | 结果 |
| --- | :-: |
| `studentPhone=17703786901`（真实）| **5** |
| `studentPhone=19900000000`（不存在）| **0** |

---

## 五、结论与定性

| 项 | 定性 |
| --- | --- |
| **`assessment_answer` 咨询师侧无任何行级约束** | 🔴 **P1（技术事实已确认，口径矛盾）** |
| **`assessment_wrong_question` 同上** | 🔴 **P1（同上）** |
| `AUTHZ-FILTER-PROBE`（原议题：筛选探测） | 🟢 **在正确收敛的表上不成立**；🟠 **在未收敛的表上成立，但属上一条的后果** |
| 学生侧 | 🟢 正确，无需处理 |
| 匿名访问 | 🟢 已阻断 |
| `DataScope` 的**契约缺口** | 🟠 「put 参数」≠「SQL 消费参数」，**无编译期/运行期保证** |

### 为什么这不只是「又一个筛选问题」

```text
Step 13 补了 assessment_record 的咨询师规则（EXISTS + approved 状态）
        ↓
同一份「学生答题明细」在 assessment_answer / wrong_question 完全无约束
        ↓
结果：老师看不到该学生的【成绩单】，却能看到他的【每一道题答了什么、错在哪】
        ↓
心理测评的逐题作答与解析，敏感度不低于总分
```

---

## 六、回归测试设计（**先定预期，再写测试**）

### 6.1 预期安全行为（**待裁定后才可固化为断言**）

| 表 | 学生 | 咨询师 | 管理员 | 未认证 |
| --- | --- | --- | --- | --- |
| `assessment_record` | 仅本人 | **仅「已同意预约」学生**（现状）| 全量 | 401 |
| `assessment_answer` | 仅本人 | ❓ **A：同 record 规则 / B：一律不可见** | 全量 | 401 |
| `assessment_wrong_question` | 仅本人 | ❓ 同上 | 全量 | 401 |
| `counseling_appointment` / `favorite` / `message` | 仅本人 | 仅本人关联 | 全量 | 401 |

> **必须先裁定 A/B，否则测试无法写断言** —— 这正是本阶段「只设计、不实施」的原因。

### 6.2 用例矩阵（`@SpringBootTest + MockMvc`）

| # | 角色 | 端点 | 入参 | 预期（裁定 A）| 预期（裁定 B）|
| :-: | --- | --- | --- | --- | --- |
| T1 | student1 | answer/page | 无 | 200 · 5 | 同左 |
| T2 | student2 | answer/page | 无 | 200 · 0 | 同左 |
| T3 | student2 | answer/page | `studentPhone=学生1真实` | **0** | **0** |
| T4 | student2 | answer/page | `studentPhone=不存在` | **0** | **0** |
| T5 | student2 | answer/page | `studentId=1` | **0**（覆盖生效）| 同左 |
| T6 | **counselor1**（已同意）| answer/page | 无 | **5** | **0** |
| T7 | **counselor2**（待审核）| answer/page | 无 | 🔴 **0** | **0** |
| T8 | counselor2 | answer/page | `studentPhone=学生1真实` | **0** | **0** |
| T9 | counselor2 | answer/page | `studentPhone=不存在` | **0** | **0** |
| T10 | admin | answer/page | 无 | 5 | 5 |
| T11 | 无 token | answer/page | 无 | 200 + code=401 | 同左 |
| T12 | counselor2 | wrongQuestion/page | 无 | 🔴 **0** | **0** |
| T13 | counselor1 | wrongQuestion/page | 无 | 3 | 0 |
| T14 | counselor2 | message/page | `studentPhone=非其学生真实` | **0** | 同左 |
| T15 | counselor2 | message/page | `studentPhone=不存在` | **0** | 同左 |
| T16 | counselor2 | record/page | 无 | **0**（现状已正确）| 同左 |

**T3–T9 是「探针」专项**：每一对「真实值 / 不存在值」必须返回**相同结果**，
否则即为可探测。**这是把原议题落成可执行断言的方式。**

### 6.3 数据前提

现有种子数据**足以支撑** T1–T8（因为 counselor1 与 counselor2/3 的关系天然不同），
但**不足以**验证「多个学生各有数据时是否串读」。
⇒ 建议测试内使用**独立测试 fixture**（事务回滚 / 测试专用数据），
**不得修改生产库种子数据**。

### 6.4 反向自检（防止断言恒真）

| 检查 | 目的 |
| --- | --- |
| admin 必须看到 5 条 | 证明「0 条」不是查询本身坏了 |
| student1 必须看到 5 条 | 证明数据存在、SQL 可用 |
| `counselor1`（已同意）在 record 上必须看到 1 条 | 证明收敛逻辑**不是恒 0** |

> 缺少这三条，「counselor2 → 0」可能只是**什么都没查到**，而非**被正确拒绝**。

---

## 七、待裁定（3 项）

| # | 问题 | 选项 | 我的建议 |
| :-: | --- | --- | --- |
| **1** | `assessment_answer` / `wrong_question` 的咨询师可见范围 | **A. 与 `assessment_record` 一致**（仅「已同意预约」学生）<br>**B. 一律不可见**（老师只看成绩单，不看逐题作答）| **A**：同一份测评数据不应出现「总分可见、逐题不可见」的割裂；且 `record` 已确立规则，复用成本最低、口径一致。但 **B 也合理**（逐题作答敏感度更高），属产品判断 |
| **2** | 是否同时修补 `DataScope` 的契约缺口 | A. 本批不改（只补 XML 分支）<br>**B. 增加「未消费即拒绝」的保护** | **B（后续独立批次）**：本次根因是「参数被静默丢弃」，只补两张表不解决同类问题再次发生 |
| **3** | 修复批次边界 | A. 只补两张 XML 分支 + 测试<br>B. 一并重构 `DataScope` | **A**：先止血，B 单独立项 |

---

## 八、本文件中的自我更正（求真务实记录）

| # | 此前结论 | 实际 | 原因 |
| :-: | --- | --- | --- |
| 1 | 「`AssessmentRecordDao.xml` 完全不含 `counselor` 字段」（`PHASE4-ROADMAP-REVIEW.md` §三）| **已过时**：Step 13（`e15f104`）已补 EXISTS 分支，实测生效 | 该结论产生于 Step 13 之前，未回写 |
| 2 | 「`params.approvedStatus` 无任何赋值点 ⇒ 条件恒不匹配 ⇒ 恒 0」 | **错误**：由 `CounselorAssessmentScope.PARAM_APPROVED_STATUS` 常量注入 | 用了字面量 `grep`，常量引用无法命中 |
| 3 | 「`AUTHZ-FILTER-PROBE` 是主要风险」 | **方向偏移**：主要风险是两张表**无行级约束**；探针只是后果 | 原议题基于「筛选可用」，未先验证「行是否已被收敛」|

---

## 九、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `d6db0b6` / `d6db0b6` · LEAD 0 |
| worktree | **CLEAN**（本阶段零代码改动）|
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** ✓ |
| 实测方式 | 仅 HTTP 只读调用 + 只读 SQL 查询，**无写入、无残留** |
| 服务 | 后端 `8080` PID `13656` · 前端 `5173` |
| 旧 Admin 导出人工验证 | ⏳ **仍为独立待办，本阶段未处理、未关闭** |

**未进入任何修复，等你裁定 §七。**
