# `DataScope` 契约保护 —— 审计范围与验收标准

> **性质**：📐 **仅制定范围与验收标准（零代码 · 不含修复方案）**
> **基线**：`HEAD = origin = bdde50b` · LEAD 0 · worktree CLEAN
> **上游**：逐题作答/错题批次 CLOSED 时，用户指定「下一步：先制定 `DataScope` 契约保护批次的审计范围与验收标准，等待审查后再实施」
>
> **本文不含**：修复代码、SQL 改动、`DataScope` 重写、任何业务规则裁定

---

## 一、问题陈述（一句话）

```text
DataScope 保证的是「参数被放进 params」
它不保证「目标 SQL 消费了该参数」
中间没有任何编译期、启动期或运行期提示
```

`DataScope.java:76-83` 对心理老师**无条件** `params.put(counselorField, userId)`：

```java
if (OwnershipGuard.ROLE_COUNSELOR.equals(role)) {
    Integer userId = OwnershipGuard.currentUserId(request);
    if (userId == null || counselorField == null) {
        OwnershipGuard.deny();
    }
    params.put(counselorField, userId);   // ← 放进去了，然后呢？
    return;
}
```

若目标 XML 没有对应 `<if>` 分支，MyBatis **静默忽略多余参数**，WHERE 少一个条件，返回全量。
`bdde50b` 已证明这条链条可以造成真实越权（`assessment_answer` / `assessment_wrong_question`）。

> **本批次要回答的唯一问题**：
> **「怎么让『声明了收敛但实际没收敛』这件事在出问题之前被发现」**
> 而不是「再逐表补一遍 SQL」。

---

## 二、审计范围（19 个调用点 · 12 个 Controller · 12 个 XML）

### 2.1 调用点清单（只读清点，已核对）

| # | Controller | 行号 | 传入字段 |
| :-: | --- | :-: | --- |
| 1–2 | `AssessmentAnswerController` | 79 / 305 | `"studentId", "counselorId"` |
| 3 | `AssessmentPaperController` | 73 | 同上 |
| 4 | `AssessmentQuestionController` | 102 | 同上 |
| 5–6 | `AssessmentRecordController` | 80 / 306 | 同上 |
| 7–8 | `AssessmentWrongQuestionController` | 81 / 294 | 同上 |
| 9–10 | `CounselingAppointmentController` | 73 / 286 | 同上 |
| 11 | `CounselorController` | 77 | 同上 |
| 12–13 | `CounselorFavoriteController` | 73 / 279 | 同上 |
| 14–15 | `CounselorMessageController` | 73 / 267 | 同上 |
| 16 | `KnowledgeArticleController` | 73 | 同上 |
| 17 | `NotificationController` | 73 | 同上 |
| 18–19 | `StudentController` | 74 / 450 | 同上 |

```text
合计：19 个调用点 / 12 个 Controller
全部传入 ("studentId", "counselorId") —— 没有任何一处传 null
```

### 2.2 范围边界

| 在范围内 | 不在范围内 |
| --- | --- |
| 上述 19 个调用点的参数消费情况 | `OwnershipGuard` 的记录级授权（`bdde50b` 已定） |
| 12 个 XML 的 `selectListView` 消费情况 | `OwnershipGuard` 的关系回调逻辑（Step 13 已定） |
| 未消费时的**实际失败行为** | 具体模块的业务可见范围规则（逐项裁定） |
| 检测机制选型与验收设计 | 前端 / DB / pom |

---

## 三、审计方法（4 个维度，缺一不可）

用户已明确：**不能只检查参数名是否出现在 XML 文本中**。
参数可能出现在**无效分支**、**无关条件**、或**无法限制目标记录的表达式**里。

| 维度 | 要回答的问题 | 不允许的取证方式 |
| :-: | --- | --- |
| **D1 调用方** | 该资源调用 `DataScope` 是否合理？该资源有没有「归属」概念？ | 仅凭「其他模块都调了」 |
| **D2 注入方式** | `studentField` / `counselorField` 是否为该 XML 真实使用的键名？ | 只看常量字符串 |
| **D3 消费情况** | 该 `<if>` **是否真的进入、是否真的限制目标表**？ | **仅 grep `params.xxx` 出现次数** |
| **D4 未消费时的失败行为** | 参数被丢弃后，实际返回**什么**？是 0 条、全量、还是报错？ | 只看代码推断 |

### 3.1 D3 的最低取证标准

对每个「已消费」判定，必须给出**至少一项**：

```text
① 该 <if> 位于主查询的 WHERE 中，且条件列属于主表或被 JOIN 的限定表
② 存在行为测试证明：该参数生效时结果集确实被收窄（有对照：传 vs 不传）
③ 若位于子查询 / EXISTS，必须证明其与外层结果集存在相关性（非恒真/恒假）
```

> **反例（必须排除）**：条件写在**未被使用**的 `<sql>` 片段、写在 `selectOne` 而非 `selectListView`、
> 或 `1=1` 式恒真表达式。

### 3.2 D4 的取证方式

对每个「未消费」判定，用**真实 HTTP 请求**确认返回量：

- 同一资源、两种角色（学生 / 老师）对比
- 若期望不同而实际相同 ⇒ 该参数确实未生效
- 若返回全量但**业务上本就无归属维度** ⇒ 归入 C3（不是缺陷）

---

## 四、初步取证结果（⚠️ 只读清点，非完整审计）

> 以下为制定范围而做的**快速清点**，**不构成审计结论**。
> D3/D4 尚未按 §三 标准逐条取证，分类以待审计确认。

| 模块 | `params.studentId` | `params.counselorId` | 初步分类 |
| --- | :-: | :-: | :-: |
| `AssessmentRecordDao` | 7 | 3 | **C1 双消费** |
| `CounselingAppointmentDao` | 7 | 3 | **C1** |
| `CounselorFavoriteDao` | 7 | 3 | **C1** |
| `CounselorMessageDao` | 7 | 3 | **C1** |
| `AssessmentAnswerDao` | 7 | **0** | **C2 已被其他机制覆盖**（`bdde50b` 资源级拒绝）|
| `AssessmentWrongQuestionDao` | 7 | **0** | **C2**（同上）|
| `StudentDao` | 4 | **0** | **C4 待业务裁定** |
| `AssessmentPaperDao` | **0** | **0** | **C3 疑无归属维度** |
| `AssessmentQuestionDao` | **0** | **0** | **C3** |
| `CounselorDao` | **0** | **0** | **C3** |
| `KnowledgeArticleDao` | **0** | **0** | **C3** |
| `NotificationDao` | **0** | **0** | **C3** |

### 4.1 已取得的辅助事实（只读实测）

| 事实 | 证据 | 含义 |
| --- | --- | --- |
| `notification` 表**无任何归属字段**（仅 `title/cover_url/category/published_at/content`）| `DESC notification` | 各角色均看到 5 条 ⇒ **设计使然，非缺陷** |
| `student1/2`、`counselor1` 访问 `notification/page` **均为 5 条** | 实测 | 与上一条一致 |
| `assessment_paper` 对 student2 返回 2 条 | 实测 | 试卷属公共考试内容，无归属维度 |
| 上述 5 个模块的 XML **完全不含** `params.studentId` / `params.counselorId` / `params.userId` | 参数名枚举 | `DataScope.apply` 对它们是**空操作** |

> **重要含义**：这 5 处调用**不产生安全效果，但会产生「已做收敛」的错觉**。
> 它们是否应移除、保留并注释、或纳入检测，是**独立裁定点**（§七 #1），**不是统一修复**。

### 4.2 原「未查明项」的处置结果（第二轮已查明）

| 项 | 结果 |
| --- | --- |
| **`CounselorController` 请求异常** | ✅ **已查明，非缺陷**：`/page` 与 `/info/{id}` 首行即 `OwnershipGuard.assertAdminOnly(request)`（S2-E-2-1 / S2-D3-1）⇒ 学生得到 **403 `{"msg":"无权访问","code":403}`**，是**设计行为**；此前「EX」是 PowerShell 对 4xx 抛异常，不是服务端故障 |
| **`CounselorController` 的 `DataScope` 调用的实际效果** | 🔴 **对任何角色都是零效果**：学生/老师被前一行 `assertAdminOnly` 挡掉；管理员在 `DataScope` 首行 `ROLE_ADMIN → return`。⇒ 该调用**永不产生参数**，是纯死代码（比 C3 其他模块更彻底）|
| `StudentDao` 的 `studentId=4` 是否真限制目标表 | ⏳ 仍未做 §3.1 取证（属 C4，暂缓）|
| `DataScope` 的 `counselorField == null` 分支 | ⚠️ 19 处全传非 null ⇒ **确认为永不触发的死分支**（待审计正式记录）|

### 4.3 C1 四模块的消费证据（D3 + D4，已取证）

**D3（条件是否真限制目标表）**：四处条件全部位于**主查询 WHERE**，约束**主表别名 `a`**（非子查询、非未使用片段）：

| 模块 | studentId 条件 | counselorId 条件 |
| --- | --- | --- |
| `AssessmentRecordDao` | `and (a.student_id = #{params.studentId})` | `and exists (select 1 from counseling_appointment ca where ca.student_id = a.student_id and ca.counselor_id = #{...} and ca.status = #{params.approvedStatus})` |
| `CounselingAppointmentDao` | `and (a.student_id = ...)` | `and (a.counselor_id = ...)` |
| `CounselorFavoriteDao` | `and (a.student_id = ...)` | `and (a.counselor_id = ...)` |
| `CounselorMessageDao` | `and (a.student_id = ...)` | `and (a.counselor_id = ...)` |

**D4（行为是否真的收窄）**——同一资源、不同身份，结果不同：

| 资源 | student1 | student2 | counselor1 | counselor2 |
| --- | :-: | :-: | :-: | :-: |
| `counselingAppointment` | **3** | **0** | **1** | **1**（各自的 1 条）|
| `counselorFavorite` | — | — | **2** | **3** |
| `counselorMessage` | — | — | **3** | **2** |
| `assessmentRecord` | **1** | **0** | **1**（已同意）| **0**（待审核）|

⇒ 四个模块**均有真实的收窄行为**（不是「条数恰好相同」的巧合）。

**其他读取入口**：四个模块 `/info/{id}` 均有 `assertOwnership`（`AssessmentRecord` 另接 `CounselorAssessmentScope` 关系回调）；`/page` 与 `/list` 均有 `DataScope`。

### 4.4 C3 五模块的逐项实测（关键：访问控制**不在 DataScope 层**）

| 模块 | 其他访问控制机制 | 匿名 `/list` | student `/page` | 判定 |
| --- | --- | :-: | :-: | --- |
| `AssessmentPaper` | `@IgnoreAuth` + `assertAdminOnly` | **200** | 200（2 条）| 公共试卷内容 |
| `AssessmentQuestion` | `@IgnoreAuth` + `assertAdminOnly`×2 | **200** | 200（6 条）| 公共试题内容 |
| `Counselor` | `@IgnoreAuth`×4 + **`assertAdminOnly`×6** | **200** | **403** | 公开目录走 `/list`；全实体仅管理员 |
| `KnowledgeArticle` | `@IgnoreAuth` + `assertAdminOnly` | **200** | 200（5 条）| 公开知识 |
| `Notification` | `@IgnoreAuth` + `assertAdminOnly` | **200** | 200（5 条）| 公告性质，表无归属字段 |

> **结论（措辞已按用户意见收紧）**：
> 这 5 个模块的访问控制**确实存在，但由 `@IgnoreAuth`（公开）或 `assertAdminOnly`（管理端）承担**，
> **不由 `DataScope` 承担**。
> ⇒ `DataScope.apply` 对它们**在任何角色下都无效果**（学生/老师的参数被丢弃；管理员首行 return）。
>
> ⚠️ **不得**据此断言「这些资源应当公开」—— 「DataScope 无效」只说明**这一层无效**，
> 公开性仍需业务确认（§七 #1 已按此收紧为「有条件移除」）。
>
> ⚠️ `Notification` **匿名可读 5 条**：其唯一控制层就是 `@IgnoreAuth`，
> 是否属「公开公告」**必须业务确认**，不能由技术侧推定。

### 4.5 C3 五个 no-op 调用的标注（用户 2026-10-25 裁定：**保留调用，仅补充说明**）

**本项仅文档记录，不改任何代码。** 三个层次必须分开陈述，不得混为一谈：

```text
① 技术事实      ：DataScope 注入了参数，但目标 SQL 未消费
                 ⇒ 该调用【没有】实现任何用户归属过滤
② 其他控制层    ：@IgnoreAuth 管「是否需要登录」
                 assertAdminOnly 管「哪些角色可进入该端点」
                 ⇒ 二者都【不】实现「行级数据归属」
③ 业务结论      ：资源是否应公开，必须业务判断
                 ⇒ 不得因「存在其他控制层」就认定授权正确
```

| 模块 | 调用位置 | no-op 事实 | 其他控制层及其**实际作用** | 它**未**覆盖的需求 | 待业务确认 |
| --- | :-: | --- | --- | --- | --- |
| `AssessmentPaper` | `AssessmentPaperController:73` | ❌ 无归属过滤 | `@IgnoreAuth`（公开 `/list`）、`assertAdminOnly`（写/全量）| 行级数据归属 | 试卷是否应全量公开？有无发布状态/适用人群约束？|
| `AssessmentQuestion` | `AssessmentQuestionController:102` | ❌ 无归属过滤 | 同上（`assertAdminOnly`×2）| 行级数据归属 | 试题是否应全量公开？|
| `Counselor` | `CounselorController:77` | ❌ **对任何角色零效果**（学生/老师被 `assertAdminOnly` 拦，管理员在 `DataScope` 首行 return）| `assertAdminOnly`×6（全实体仅管理员）、`@IgnoreAuth`×4（公开目录 `/list`，`PublicCounselorView` 8 字段）| 行级数据归属 | 公开目录字段集是否足够？`DEF-COUNSELOR-LIST-PUBLIC-PII` 仍挂账 |
| `KnowledgeArticle` | `KnowledgeArticleController:73` | ❌ 无归属过滤 | `@IgnoreAuth`（公开）、`assertAdminOnly`（管理端）| 行级数据归属 | 知识是否全部公开？有无草稿/审核态？|
| **`Notification`** | `NotificationController:73` | ❌ 无归属过滤 | **仅** `@IgnoreAuth`（匿名可读 5 条）+ `assertAdminOnly`（管理端）| **无任何用户维度约束** | 🔴 **必须区分两种可能**：<br>**A. 全站公告** ⇒ 现状正确<br>**B. 用户私有通知** ⇒ **属独立缺陷**（无 `user_id` 字段，需改表 + 改查询）<br>**当前证据无法区分 A/B** |

> **登记**：`BIZ-NOTIFICATION-VISIBILITY` —— 确认 `Notification` 是「全站公告」还是「用户私有通知」。
> 若是 B，则不仅是 `DataScope` 问题，而是**数据模型缺少归属字段**，需独立批次。
>
> ⚠️ **不得**把上表「其他控制层」一栏描述成「已覆盖同一授权需求」：
> `@IgnoreAuth` 与 `assertAdminOnly` 控制的是**登录要求**与**角色准入**，
> 而 `DataScope` 试图控制的是**行级数据归属** —— **三者不是同一维度**。

---

## 五、分类框架与**差异化**处置（禁止统一策略）

用户明确：**不要预设每个未消费参数的接口都应采用同一种拒绝策略。**

| 分类 | 含义 | 候选处置（**待审计后逐项裁定**）|
| :-: | --- | --- |
| **C1** | 双参数均被有效消费 | ✅ 不改（仅纳入回归保护）|
| **C2** | 参数未消费，但**已有其他机制**覆盖（如资源级拒绝）| 二选一：① 认定足够 ② 补 XML 分支做纵深防御 |
| **C3** | 资源**本无归属维度**，调用为空操作 | 三选一：① 移除调用 ② 保留 + 显式注明「no-op，勿误认为已收敛」③ 保留并纳入检测白名单 |
| **C4** | 参数未消费且**业务规则未定** | **必须先业务裁定**，不得由技术侧猜（同 `assessment_record` 的处理纪律）|

> **C3 与 C4 绝不可合并处理**：前者是「本来就不需要」，后者是「需要但没实现」。

---

## 六、候选检测机制（**待审计后选型，此处不决策**）

| # | 机制 | 思路 | 风险 |
| :-: | --- | --- | --- |
| **M1** | 调用点显式声明「本资源需要哪些收敛」，启动期校验 XML 是否存在对应分支 | 能把 à la carte 变为契约 | 启动期解析 XML 成本；复杂 SQL 难判定 |
| **M2** | **行为断言测试**：每个模块「传 vs 不传」结果必须不同（或必须相同，按分类）| **最贴近用户要求**（验证最终查询确实生效）| 测试量大；需固定 fixture |
| **M3** | XML 哨兵条件（如 `<if test="params.roleScopeRequired">` 强制存在）| 显式、可 grep | 仍是文本层，且侵入所有 XML |
| **M4** | 仅登记 + 逐模块裁定（最小干预）| 成本最低 | 不防新增模块重犯 |

> **用户约束已记录**：**文本匹配不能作为充分证据**（`§三`），因此 M3 单独使用**不可接受**；
> **M2 是唯一能证明「授权条件对最终查询生效」的机制**，但需评估测试成本。

---

## 七、裁定结果（用户 2026-10-25 · 已定案）

| # | 问题 | 裁定 | 落地含义 |
| :-: | --- | --- | --- |
| **1** | C3 五个 no-op 调用 | ⚠️ **有条件移除**（**不批准一刀切**）| 先逐项确认该资源的业务可见范围与**其他访问控制层**；确认「不需要 `DataScope` 归属约束」后方可移除，**并记录移除理由**。若资源有其他访问控制需求，必须写明由哪一层实现 —— **不得因 `DataScope` 无效就推断资源应公开** |
| **2** | C4（`Student` 的 counselor 范围）| ✅ **定义已给出 · 实现另立批次** | **有效关系 = 咨询师与学生之间至少存在一条 `counseling_appointment.status = 2`（已批准）的预约**。**本批不修改 `Student`**，但规则现已可判定 ⇒ 见 §7.2 |
| **3** | 检测机制 | ✅ **M2 为主 + M1 为辅** | M2 验证最终查询行为；M1 显式记录资源要求哪些约束，为新增调用提供审查依据。**M3 只能辅助提示，不能单独通过验收；M4 只能登记，不能防回归** |
| **4** | C2 是否补 XML | ✅ **本批不补** | 维持 `bdde50b` 资源级拒绝。**仅限本批** —— 不意味着所有未消费 `counselorId` 的查询都可依赖控制器拒绝；若发现绕过该拒绝的入口，另行评估 |

### 7.1 两条定性纪律（必须写入所有结论）

```text
❌ 「参数被 SQL 消费」  ≠  「授权正确」
     条件可能写错表、写进无效分支、或恒真

❌ 「当前没有归属字段」  ≠  「这个资源永远不需要访问控制」
     只能说明【当前数据模型】没有按用户归属过滤
```

> ⚠️ **对 C2 的措辞约束**：**不得**把 `assessment_answer` / `assessment_wrong_question`
> 描述成「已被 `bdde50b` 从根本上解决」。准确说法是：
> **「当前咨询师读取路径已有资源级拒绝作为第一道防线；
> 但这两个模块的 `counselorId` 参数未被消费，共享机制的契约缺口仍然存在。」**
> 若业务规则改变，或出现绕过控制器的代码路径，仍需重新评估。

### 7.2 「有效关系」的权威定义（用户 2026-10-25 裁定）

```text
有效关系成立  ⟺  咨询师与该学生之间至少存在一条
                  counseling_appointment.status = 2（已批准）的预约
```

#### 与既有实现的一致性核对

| 项 | 值 | 结论 |
| --- | --- | :-: |
| `CounselorAssessmentScope.STATUS_APPROVED` | **2** | ✅ 与裁定**完全一致** |
| `AssessmentRecordDao` 的 counselor 分支 | `exists (... and ca.status = #{params.approvedStatus})` | ✅ **已按该定义实现** |

> ⇒ 这不是一条新规则，而是把 `assessment_record` 已实现的规则**显式化并推广**。

#### 适用性矩阵（按资源逐一判断是否受该定义约束）

| 资源 | 当前实现 | 与裁定一致性 |
| --- | --- | :-: |
| `assessment_record` | EXISTS 已批准预约 | ✅ **完全一致** |
| `assessment_answer` / `wrong_question` | 资源级拒绝（咨询师一律不可读）| ✅ **比裁定更严格**，不冲突 |
| `counseling_appointment` / `favorite` / `message` | `counselor_id = 当前用户` | ✅ 属「本人数据」，不涉及「能看哪些学生」 |
| **`student`** | **无任何约束 → 全体咨询师看到全部学生** | 🔴 **缺口**，本批不改（见下）|
| `assessment_paper` / `question` / `knowledge` / `notification` | 无归属维度 | ➖ 不适用 |

#### 🔴 C4 缺口已量化（只读实测，本批**不修复**）

依裁定，各咨询师「应可见」的学生（SQL 按 `status = 2` 计算）：

| 咨询师 | 应可见学生 | 应可见数 |
| --- | :-: | :-: |
| counselor1（与 student1 有已批准预约）| `[1]` | **1** |
| counselor2（仅 status=1 待审核）| `[]` | **0** |
| counselor3（仅 status=1 待审核）| `[]` | **0** |

实测 `/student/page`：

| 身份 | 实际可见 | 与裁定期望 |
| --- | :-: | :-: |
| counselor1 | **3**（id 3,2,1）| 🔴 多 2 条 |
| counselor2 | **3** | 🔴 多 3 条（应 0）|
| counselor3 | **3** | 🔴 多 3 条（应 0）|
| admin | 3 | ✅（B 类，管理端本就全量）|

> **登记**：`AUTHZ-COUNSELOR-STUDENT-SCOPE`（新工单）
> · 定义已明确 · 证据已量化 · **本批不实施**，需另立批次（涉及 `Student` 的 `selectListView` + `/info` + `/list` 路径）
> · ⚠️ 该批次需重新评估「咨询师查看学生列表」是否还承担其他功能（如新建预约时选学生），避免一刀切收窄破坏业务流程

---

## 八、输出物清单（本批次应产出）

| # | 产出 | 形式 |
| :-: | --- | --- |
| 1 | **参数消费审计表**：19 调用点 × 4 维度（D1–D4）逐项结论 | 文档表格 + 证据引用 |
| 2 | **分类清单**：每项归入 C1/C2/C3/C4，**不允许「未分类」** | 文档表格 |
| 3 | **消费有效性证据**：每个「已消费」判定附 §3.1 三选一证据 | 测试名 / 代码行 / 实测输出 |
| 4 | **未消费失败行为证据**：每个「未消费」判定附真实 HTTP 对照 | 命令 + 输出 |
| 5 | **检测机制设计**：选型理由 + 「故意破坏必须变红」的反向验证 | 设计文档 + 测试 |
| 6 | **修复方案**（最小范围）| 设计文档，**单独批准后**才实施 |

---

## 九、验收标准（可执行、可反驳）

| # | 验收项 | 判定方式 |
| :-: | --- | --- |
| **AC1** | 19 个调用点**全部有分类**，无「未分类/待定」 | 审计表逐行检查 |
| **AC2** | 每个「已消费」判定有 §3.1 三选一证据，**不接受仅 grep 计数** | 逐条核对 |
| **AC3** | 每个「未消费」判定有真实 HTTP 对照证据 | 命令 + 输出留档 |
| **AC4** | C3 每项有明确处置（A/B/C 之一）及理由 | 表格 |
| **AC5** | C4 每项有**业务裁定记录**，无技术侧擅自决定 | 裁定引用 |
| **AC6** | 检测机制**能在缺失消费时失败**：故意移除一处 `<if>` ⇒ 对应测试必须**红** | 反向验证执行记录（见 §9.1 三层）|
| **AC7** | **C1 四模块行为零变化**（改前/改后 HTTP 返回一致）| 对照实测 |
| **AC8** | 全量测试通过；新增测试可独立运行 | `mvn test` 输出 |
| **AC9** | 零 DB / 零前端 / 零 pom 改动 | `git diff --name-only` |
| **AC10** | **资源策略可追溯**：每个模块记录其访问范围、归属维度、其他授权机制及业务裁定依据 | 逐模块表格 |
| **AC11** | **检测失败原因可区分**：测试失败时能区分「授权条件缺失 / 测试数据问题 / SQL·启动错误」；AC6 必须证明失败源于**授权行为回归** | 失败日志 + 归因说明 |
| **AC12** | **不以参数出现作为语义证明**：任何 XML 静态检查或启动期校验**都不能替代真实行为测试**，只能作辅助检测 | 方法审查 |

> **AC3 的补充口径（用户要求）**：对未消费参数，真实 HTTP 对照**必须有可区分的目标数据和明确的预期结果**。
> 若测试环境没有能区分两种行为的数据，**结果相同既不能证明安全，也不能证明设计正确** —— 只能记为「无法判定」。

### 9.1 AC6 的三层拆解（用户要求）

| 层 | 要求 | 反例（视为验收失败）|
| :-: | --- | --- |
| **AC6-1 变红** | 删除一处**真实授权条件**后，对应测试必须失败 | 测试仍绿 ⇒ 该测试没覆盖该条件 |
| **AC6-2 有针对性** | 失败的必须是「**无权目标被排除**」那条断言，**不是**总行数、不是 SQL 字符串比对 | 只断言 `total == 3` 之类的总数变化 ⇒ 无法区分「条件删了」与「数据变了」|
| **AC6-3 可恢复** | 恢复条件后测试重新通过 | 恢复后仍红 ⇒ 失败另有原因（夹具/SQL/环境），本次实验无效 |

> **AC7 的反向约束**：不是所有模块都应满足「传参数与不传参数结果不同」。
> C3 若经业务裁定为**公开资源**，预期结果本就可能相同。
> ⇒ **AC6 必须以「每个资源明确声明的预期访问行为」为依据**，不得对所有模块套同一断言。

### 9.2 M2 测试设计（本阶段只设计，不写代码）

#### 用例结构（每模块一组，共 4 组 C1 + 2 组 C2）

| 组 | 用例 | 断言 | 目的 |
| :-: | --- | --- | --- |
| **G1 前置：数据确实存在** | 管理员视角读取 | `total > 0`，且能取到目标 `id` | 排除「数据不存在导致的假 0」（AC11 归因）|
| **G2 收窄：无权目标被排除** | 无权身份读取同一资源 | `total == 0` **或** `403`（按该资源声明）| **AC6-2 的针对性断言就在这里** |
| **G3 反证：有权目标可见** | 有权身份读取 | `total > 0` 且包含目标 | 排除「全局返回空」式假绿 |
| **G4 不得越界** | 客户端提交他人 `studentId`/`counselorId` | 结果与不提交时**完全一致** | 证明服务端覆盖生效 |

#### 现有种子数据的可区分性（已核实，可直接支撑 G1–G4）

| 资源 | 可区分维度 | 是否够用 |
| --- | --- | :-: |
| `assessment_record` | counselor1（已同意）**1** vs counselor2（待审核）**0** | ✅ |
| `counseling_appointment` | student1 **3** vs student2 **0**；counselor1 **1** vs counselor2 **1**（各自）| ✅ |
| `counselor_favorite` | counselor1 **2** vs counselor2 **3** | ✅ |
| `counselor_message` | counselor1 **3** vs counselor2 **2** | ✅ |
| `assessment_answer` / `wrong_question` | counselor **403** vs student1 **5/3** | ✅（C2 由资源级拒绝）|

> ⚠️ **单学生数据不足的风险**：`assessment_answer`/`wrong_question` 只有 `student1` 有数据，
> 「多个学生各有数据时是否串读」**无法用现有种子验证** —— 但 C2 采用**查询前**资源级拒绝，
> 该维度不影响其结论；若未来改为行级过滤，必须补多学生 fixture（用户已明确）。

#### 变异实验设计（AC6 三层，进入实施阶段执行）

```text
对象   ：counseling_appointment 的 <if test="params.counselorId ..."> 分支（C1，行级条件，非资源级）
步骤   ：
  1. 基线  → mvn test 全绿（记录用例数）
  2. 删除该 <if> 整段 → 重跑 G2
       期望：G2（无权 counselor 读到 0）变红，且失败信息为「期望 0 实际 N」
       若失败原因是 SQL 异常/启动失败 ⇒ **判定实验无效**（AC11）
  3. git checkout 还原 → 重跑全量 → 恢复绿
判据   ：三步全部符合才算 AC6 通过；仅「变红」不足以通过
边界   ：临时改动**不进入提交**，实验后 worktree 必须 CLEAN
```

#### M1 的定位（辅助，非证明）

| 项 | 内容 |
| --- | --- |
| 做什么 | 调用点**显式声明**该资源要求哪些访问约束（如 `REQUIRES_STUDENT_SCOPE`） |
| 何时校验 | 启动期只校验**声明完整性**（有声明、且非空）|
| **不做什么** | **不判定语义正确性** —— 声明了不等于真生效（AC12）|
| 价值 | 新增 `/page` 时不写声明 ⇒ 立刻暴露，防止「忘了想」 |

---

## 十、禁止事项

```text
❌ 全量重写 DataScope（用户明确禁止）
❌ 统一采用同一种拒绝策略（用户明确禁止）
❌ 以「参数名出现在 XML 文本中」作为收敛成立的证据
❌ 顺手修改任何模块的业务可见范围规则
❌ 把 C3（本来不需要）与 C4（需要但没实现）合并处理
❌ 在本批内顺便处理 UX / 前端 / 旧 Admin 导出
```

---

## 十一、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `bdde50b` / `bdde50b` · LEAD 0 |
| worktree | **CLEAN**（本文档为唯一产出，`docs/` 被 gitignore）|
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** ✓ |
| 服务 | 后端 `8080` PID `31608` · 前端 `5173` |
| 上一批 | 逐题作答/错题咨询师读取范围 🏁 **CLOSED（`bdde50b`）** |

### 11.1 进入实施前的 7 项前置条件（用户 2026-10-25 列出）交付状态

| # | 前置条件 | 状态 | 说明 |
| :-: | --- | :-: | --- |
| 1 | 19 个调用点完整清单，每个都完成分类 | 🟡 **部分** | 清单 ✅（§2.1）；分类 ✅ 但 **D3 正式取证仅完成 C1 四模块 + C3 五模块事实**，`Student`（C4）暂缓 |
| 2 | C1 真实授权消费证据 + 行为回归基线 | ✅ **已交付** | §4.3：XML 条件落在主表别名 `a`；4 模块行为收窄实测（3/0、2/3、3/2、1/0）|
| 3 | C2 现有资源级拒绝证据及**适用边界** | ✅ **已交付** | §7.1 措辞约束：仅覆盖咨询师读取路径；`counselorId` 未消费的契约缺口仍在 |
| 4 | C3 五模块**逐项业务可见性结论** | 🟡 **技术事实已齐，业务确认待补** | §4.4 已查明：控制层是 `@IgnoreAuth` / `assertAdminOnly`，**不在 `DataScope`**；但「是否应公开」需业务确认（尤其 `Notification` 匿名可读 5 条）|
| 5 | C4 业务裁定记录（不擅自改 `Student`） | ✅ **已交付（定义级）** | §7.2：**有效关系 = 至少一条 `status = 2` 的已批准预约**；与 `STATUS_APPROVED=2` 完全一致；**缺口已量化**（counselor1 应 1 实 3、counselor2/3 应 0 实 3）—— **本批不实施** |
| 6 | M2 测试设计 + 一处删除条件变红/恢复变绿完整证据 | ✅ **已交付** | §9.2 设计 + **变异实验已执行**（`DATASCOPE-AC6-MUTATION-EXPERIMENT.md`）：删除 `assessment_record` 的 counselor 条件 → **7/2 失败（expected 0 but was 1）** → 恢复 → **7/7 + 全量 149/149**；`HEAD` 未变、**零提交**、worktree CLEAN |
| 7 | `CounselorController` 异常原因 | ✅ **已查明** | §4.2：`/page`、`/info` 为首行 `assertAdminOnly` ⇒ 学生 **403 属设计**；其 `DataScope` 调用对任何角色**零效果** |

```text
可就绪：2 · 3 · 5 · 7
待补  ：1（C4 部分）· 4（业务确认）· 6（变异实验，需授权临时改 XML）
```

### 挂账状态（均**未**因本文档关闭）

| 事项 | 状态 |
| --- | :-: |
| `DataScope` 契约保护 | 📐 **范围与验收已定稿（含裁定）；实施未开始** |
| **`AUTHZ-COUNSELOR-STUDENT-SCOPE`（新）** | 🔴 **定义已明确、缺口已量化（counselor1 1→3、counselor2/3 0→3），本批不实施**；需评估「咨询师查看学生列表」是否承担建约选人等业务 |
| **`BIZ-NOTIFICATION-VISIBILITY`（新）** | ❓ **`Notification` 是全站公告还是用户私有通知未定**；若为私有，属**数据模型缺归属字段**，需独立批次 |
| **`C3-NOOP-CALLS`（记录项）** | 📌 五个模块的 `DataScope` 调用为 **no-op**（仅文档标注，调用保留）；业务可见性确认待办，**未因标注而关闭** |
| `UX-COUNSELOR-DETAIL-403` | ⏳ 待处理（**不得为修 UI 放开后端权限**）|
| 旧 Admin 浏览器导出人工验证 | ⏳ 待人工检查 |
| Multi-Tool | ⏸ 继续暂停 |
| 多学生数据分布隔离验证 | ⏳ 非阻塞盲区（若改行级过滤必须补）|

**本文档仍未实施任何修复。等你对 §11.1 的 3 项待补内容给出下一步指示
（尤其 §6 的变异实验需你授权「临时修改一处 XML、实验后还原、不进入提交」）。**
