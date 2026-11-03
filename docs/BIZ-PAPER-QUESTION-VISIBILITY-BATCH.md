# 独立批次：AssessmentPaper / AssessmentQuestion 资源可见性

> **性质**：🏁 **CLOSED · 用户 2026-10-27 验收通过（APPROVED / CLOSED）**
> **基线节点**：`e15e0a2` —— 用户明确「可作为这一批的**正式基线节点**」
> **Git**：`HEAD = origin = e15e0a2` · LEAD 0 · worktree CLEAN · 推送 `36bb760..e15e0a2` 成功
> **改动**：**9 文件 +676/−12** · 目标 **28/28** · 全量 **197/197**（**commit + push 后复跑**）
> **变异**：删 XML 条件 → 10 条转红；改「非 admin 一律拒绝」→ 4 条正向转红
>
> **CLOSED 依据（用户逐项复核的 4 点）**：
> ① Git 完整闭环（非「本地 commit 未推」）② 提交后 197/197 复跑 ⇒ 提交本身无回归
> ③ 范围未漂移（未触及 DataScope / notification / UX-403 / Old Admin / Multi-Tool / DB schema）
> ④ 证据边界措辞得到保留（正向仅测试层 · fixture `id>=9000=0` 只证「测试结束后的持久状态恢复」）
>
> **⚠️ 审查边界声明**：该结论由用户**基于提交方提供的证据**作出；
> 本文档所有数字均由提交方实测产出。
>
> **🔒 明确未被本批关闭**：`SEC-QUESTION-ANSWER-PUBLIC`（🟡 PENDING REVIEW）、
> `exampaperDeleteStart=1` 管理端筛选语义（⏳ 独立问题）、
> **`DataScope` Contract Audit**（🟡 保持**独立审计线**，用户特别强调
> 「**不应因本批关闭而把它与 Paper/Question Visibility 混成一个安全问题**」）
> **上游裁定**（用户 2026-10-27）：
> · Paper/Question 可见性 = **A（按角色视角过滤）**，**独立批次**，**不并入 `DataScope` Audit**
> · `notification` = **全站公告**，现状符合，**不修复、不登记未来需求**
> · `DataScope` Audit 继续独立进行；T13b 不做

---

## 一、🔴 必须先报告的一项重大更正

**`SEC-QUESTION-ANSWER-PUBLIC`（答案/解析匿名可读）实测已不再成立。**

| 身份 | `/assessmentQuestion/list` | `/page` | `/info/1` |
| --- | :-: | :-: | :-: |
| **匿名** | `answer=false · analysis=false` | — | — |
| **学生** | `answer=false · analysis=false` | `false / false` | `false / false` |
| **管理员** | — | **`answer=true · analysis=true`** | — |

⇒ 非管理员路径**已被 `QuestionPublicDTO` 剥离**（S2-B：`toPublic(view, false)` / `toPublic(c, true)`）。

| 项 | 状态 |
| --- | :-: |
| 代码事实 | `AssessmentQuestionController` L82 / L115-120 / L146-149 / L315-319 |
| 实测 | 见上表 |
| **既有登记** | `SEC-QUESTION-ANSWER-PUBLIC` 标为 **P1 / Identified / Unfixed** |
| 🔴 **结论** | **该登记已过时** —— 应改为「**待复核关闭**」，而非「与本批同批处理」 |

**同时必须更正一处前端注释**：

```ts
// frontend/src/api/assessment.ts:113-118（现状）
// ⚠️ 不调用 /assessmentQuestion/list —— 该端点匿名返回 answer / analysis
//    （SEC-QUESTION-ANSWER-PUBLIC 🔴 P1，Identified / Unfixed）
```

该注释描述的风险**已不存在**（`/list` 现返回 `toPublic(codesOnly=true)`）。
但**「前端不调用」这一结论仍然正确**（本题不需要试题内容），故**只需修正注释理由，不改行为**。

> ⚠️ 这属于「文档与事实不一致」，与本项目此前「不为保持纯删除而保留错误说明」的处理原则一致。

---

## 二、可见性缺口证据（实测）

### 2.1 数据事实

| 表 | 记录 | `status`（1=启用/2=禁用）| `is_deleted`（1=已删除）|
| --- | :-: | :-: | :-: |
| `assessment_paper` | 2 条 | **1（启用）** | **1（已删除）** |
| `assessment_question` | 6 条 | 无该字段（随 `paper_id`）| 无该字段 |

字典：`assessment_paper_status` → `1 启用` / `2 禁用`

### 2.2 四类读取入口 × 三种角色（现状）

| 入口 | 鉴权 | 匿名 | 学生 | 管理员 | 问题 |
| --- | --- | :-: | :-: | :-: | --- |
| `/assessmentPaper/list` | `@IgnoreAuth` | ✅ **2 条（含已删除）** | ✅ 2 条 | — | 🔴 无状态过滤 |
| `/assessmentPaper/page` | 需登录 | 401 | ✅ 2 条 | ✅ 2 条 | 🔴 无状态过滤 |
| `/assessmentPaper/info/{id}` | 需登录 | 401 | ✅ **200** | ✅ 200 | 🔴 **无任何守卫** |
| `/assessmentQuestion/list` | `@IgnoreAuth` | ✅ **6 条** | ✅ 6 条 | — | 🔴 不过滤父卷状态 |
| `/assessmentQuestion/page` | 需登录 | 401 | ✅ 6 条 | ✅ 6 条（含 `paperStatus/paperIsDeleted`）| 🔴 同上 |
| `/assessmentQuestion/info/{id}` | 需登录 | 401 | ✅ 200 | ✅ 200 | 🔴 同上 |

### 2.3 试题是否能绕过试卷状态

**能，且证据明确**：

```xml
<!-- AssessmentQuestionDao.xml -->
L26  ,assessment_paper.status      as paperStatus
L27  ,assessment_paper.is_deleted  as paperIsDeleted
L30  left JOIN assessment_paper assessment_paper ON a.paper_id = assessment_paper.id
```

⇒ 试卷状态**已在 SELECT 中**，但**没有任何 WHERE 条件使用它**
⇒ 只修 `/assessmentPaper/*` 会留下 `/assessmentQuestion/*` 这条**等效旁路**

> ✅ **好消息**：修复不需要新增 JOIN，只需在既有 JOIN 上加条件。

---

## 三、实施边界

### ✅ 在范围内

| # | 内容 |
| :-: | --- |
| 1 | `AssessmentPaperController`：`/list`、`/page`、`/info/{id}` 增加**角色视角**过滤 |
| 2 | `AssessmentQuestionController`：`/list`、`/page`、`/info/{id}` 增加**父卷状态**过滤 |
| 3 | `AssessmentPaperDao.xml` / `AssessmentQuestionDao.xml`：新增状态过滤条件（**复用既有 JOIN**）|
| 4 | 状态常量：`PUBLISHED=1` / `NOT_DELETED=0` 收敛到单一常量类，**SQL 不写死数字** |
| 5 | 回归测试（验收矩阵见 §五）+ 变异验证 |
| 6 | 修正 `frontend/src/api/assessment.ts` 中**已失效的风险注释**（仅注释）|

### ❌ 不在范围内

```text
❌ 不并入 DataScope Contract Audit（性质是资源可见性，不是归属维度）
❌ 不改 assessment_record / answer / wrong_question 的任何规则
❌ 不改 notification（已裁定：全站公告，现状正确）
❌ 不改 DB 结构 / 种子数据
❌ 不改旧 Admin 管理端功能（必须保持可管理未发布/已删除）
❌ 不改新 Vue 的页面行为（除注释）
❌ 不重构 DataScope
❌ 不启动 Multi-Tool
```

---

## 四、🔴 实施要点（防止三类常见错误）

### 4.1 不得写成「非 admin 就过滤」

必须按**视角**区分，且**明确列出角色集合**：

| 视角 | 角色 | 过滤 |
| --- | --- | --- |
| 管理视角 | `admin` | **不注入**（全量，含未发布/已删除）|
| 用户视角 | `student` / `counselor` / **匿名** | `status = 1 AND is_deleted = 0` |

> ⚠️ 关键：**匿名**也要覆盖（`/list` 是 `@IgnoreAuth`），
> 不能因为「没有角色」就落到「不过滤」的默认分支。

### 4.2 `/info/{id}` 必须单独处理

- 两个 `/info` 目前**没有任何守卫**（仅需登录）
- 不能只改列表：`/info/{id}` 是**等效旁路**（已知 id 即可直读）
- 用户视角读未发布/已删除 ⇒ **403**（与既有 `deny()` 契约一致，不用 404）

### 4.3 试题必须继承父卷状态

```text
/assessmentQuestion/* 的可见性 = 父卷 status=1 且 is_deleted=0
```

- `paper_id` 为空或父卷不存在 ⇒ **用户视角不可见**（fail-closed）
- 管理员视角不受限（需管理题目）

### 4.4 ⚠️ 必须预告的行为后果

```text
当前 2 张试卷 is_deleted = 1（均已删除）
⇒ 修复后：新 Vue 试卷列表 / 匿名 /list 将变为【0 条】

这不是缺陷，是【规则生效】的直接结果。
若产品希望学生能看到试卷，应由管理员把试卷恢复为 is_deleted=0，
而不是放宽过滤条件。
```

> 这一点必须写进报告，否则会被误判为「功能被改坏了」。

---

## 五、验收矩阵

### 5.1 主矩阵（现状 → 期望）

| # | 角色 | 入口 | 现状 | 期望 |
| :-: | --- | --- | :-: | :-: |
| A1 | 匿名 | `/assessmentPaper/list` | 2 条 | **0 条** |
| A2 | 学生 | `/assessmentPaper/list` | 2 条 | **0 条** |
| A3 | 学生 | `/assessmentPaper/page` | 2 条 | **0 条** |
| A4 | 学生 | `/assessmentPaper/info/1` | 200 | **403** |
| A5 | 学生 | `/assessmentQuestion/list` | 6 条 | **0 条** |
| A6 | 学生 | `/assessmentQuestion/page` | 6 条 | **0 条** |
| A7 | 学生 | `/assessmentQuestion/info/1` | 200 | **403** |
| A8 | counselor | 六入口 | 同学生 | **同学生**（与归属无关，一律按视角）|
| A9 | **admin** | `/assessmentPaper/page` | 2 条 | **2 条（不变）** |
| A10 | **admin** | `/assessmentQuestion/page` | 6 条，含 `answer/analysis` | **不变** |
| A11 | 未认证 | `/page`、`/info` | 401 | **401（不变）** |
| A12 | 学生 | `/assessmentPaper/page?isDeleted=1` | 2 条 | **0 条**（客户端参数不得绕过）|
| A13 | 学生 | `/assessmentPaper/page?status=2` | 0 条 | **0 条**（不得借参数扩大）|

### 5.2 反向自检（防「全局空」式假通过）

```text
✅ admin 的 /assessmentPaper/page 必须 2 条
✅ admin 的 /assessmentQuestion/page 必须 6 条
✅ admin 的 /assessmentQuestion/page 必须含 answer/analysis
```

> 缺这三条，「学生 0 条」可能只是**查询坏了**，而不是**被正确过滤**。

### 5.3 独立验收项（原 `SEC-QUESTION-ANSWER-PUBLIC`）

> ⚠️ 按 §一 的实测，该项**已被修复**，本批只做**回归固化**，不与可见性验收混合。

| # | 角色 | 入口 | 期望 |
| :-: | --- | --- | :-: |
| S1 | 匿名 | `/assessmentQuestion/list` | 不含 `answer` / `analysis` |
| S2 | 学生 | `/assessmentQuestion/page` | 不含 `answer` / `analysis` |
| S3 | 学生 | `/assessmentQuestion/info/1` | 不含 `answer` / `analysis` |
| S4 | admin | `/assessmentQuestion/page` | **含** `answer` / `analysis`（管理需要）|
| S5 | 学生 | 各入口 `options` | 已中性化（`codesOnly` 语义不变）|

### 5.4 变异验证（若实施）

| 变异 | 期望 |
| --- | --- |
| 删除 paper 的 `is_deleted` 条件 | A1–A4 变红 |
| 删除 paper 的 `status` 条件 | （需构造一条 `status=2` 数据，或标记为未覆盖）|
| 删除 question 的父卷条件 | A5–A7 变红 |

---

## 六、待你确认（3 项）

| # | 问题 | 选项 | 说明 |
| :-: | --- | --- | :- |
| **1** | `SEC-QUESTION-ANSWER-PUBLIC` 是否改为「**待复核关闭**」（实测已修复）| A. 是<br>B. 仍按「待修复」处理 | **A**：实测非管理员已剥离 answer/analysis；继续按 Unfixed 管理会导致重复修复 |
| **2** | 试卷全部 `is_deleted=1` ⇒ 修复后学生/匿名看到 **0 条**，是否接受 | A. 接受（规则优先）<br>B. 先由管理端恢复试卷再修 | **A**：规则优先；同时提示「学生列表变空」是预期结果 |
| **3** | 前端 `assessment.ts` 的失效注释是否本批一并修正 | A. 一并（仅注释）<br>B. 挂账 | **A**：与本项目「文档不得与事实不符」一致；改动仅注释 |

---

## 七、✅ 实施记录（2026-10-27）

### 7.1 变更文件（7 个）

| # | 文件 | 性质 |
| :-: | --- | --- |
| 1 | `src/main/java/com/security/ResourceVisibility.java` | **新增**：状态常量 + 视角二分 + 详情判定 |
| 2 | `src/main/java/com/controller/AssessmentPaperController.java` | `/page` `/list` 注入公共视角；`/info` 加可见性判定 |
| 3 | `src/main/java/com/controller/AssessmentQuestionController.java` | `/page` `/list` 注入；`/info` **继承父卷**判定（复用已加载父卷，避免重复查询）|
| 4 | `src/main/resources/mapper/AssessmentPaperDao.xml` | 新增 `scopePublic` 条件块（**复用既有列，无新增 JOIN**）|
| 5 | `src/main/resources/mapper/AssessmentQuestionDao.xml` | 同上，作用于**父卷** |
| 6 | `src/test/java/com/regression/PaperQuestionVisibilityTest.java` | **新增** 28 用例（含 6 条事务 fixture 正向用例）|
| 7 | `frontend/src/api/assessment.ts` | **仅注释**（修正已失效的风险描述）|
| 8 | `src/test/java/com/regression/Phase2CoreRegressionTest.java` | 既有断言 2 → 0（基线数据全为已删除）|
| 9 | `src/test/java/com/regression/IdentityAuthorizationRegressionTest.java` | 把两个 `info` 从「内容端点白名单」移出并断言 403 |

### 7.2 先红 → 后绿

```text
修复前基线 : Tests run: 22, Failures: 14, Errors: 0     ← 全部为可见性断言
             （admin 反向自检 4 条、答案剥离 2 条、契约不变 1 条 已通过 ⇒ 断言不是恒假）
修复后     : Tests run: 22, Failures: 0,  Errors: 0
正向补齐后 : Tests run: 28, Failures: 0,  Errors: 0     ← 新增 6 条 fixture 用例（§7.4b）
全量回归   : Tests run: 197, Failures: 0, Errors: 0  · BUILD SUCCESS
```

### 7.3 变异验证（证明测试不是恒真）

同时移除两处 XML 条件块（paper 的 `is_deleted`、question 的整块）后重跑：

```text
Tests run: 22, Failures: 10
转红用例：anonymousSeesNoPaperOnList / studentSeesNoPaperOnList / studentSeesNoPaperOnPage /
          counselorSeesNoPaperOnPage / anonymousSeesNoQuestionOnList / studentSeesNoQuestionOnList /
          studentSeesNoQuestionOnPage / counselorSeesNoQuestionOnPage /
          clientCannotWidenByIsDeleted / clientCannotWidenQuestionByPaperIsDeleted
```

> `/info` 的 4 条用例**仍绿** —— 因为详情判定由控制器侧 `ResourceVisibility` 承担，不依赖 XML。
> 这正是**双层防御**的预期表现：删掉任一层的条件，另一层仍能独立拦截。

| 变异 | 结果 |
| --- | --- |
| 删 paper `is_deleted` | ✅ 转红 |
| 删 question 父卷条件 | ✅ 转红 |
| 删 paper `status` 条件 | ✅ **已覆盖**（补 fixture 后）：用例 27 用事务内 `status=2` 数据验证，删除该条件会使它转红 |
| 🔴 **过度限制变异**：把 `assertDetailAccessible` 改成「非 admin 一律 `deny()`」 | ✅ **4 条转红**：`studentCanReadVisiblePaperInfo` / `counselorCanReadVisiblePaperInfo` / `studentCanReadVisibleQuestionInfoWithoutAnswer` / `visibleAllowedWhileDeletedDeniedForSameRole` ⇒ 正向用例**不是恒真**，可捕获「非管理员一律拒绝」这类错误实现 |

### 7.4 运行时实测（真实 HTTP，非 MockMvc）

| 身份 | paper/list | paper/page | paper/info/1 | paper/info/999999 | question/list | question/page | question/info/1 |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| 匿名 | 200 · **0** | — | — | — | 200 · **0** | — | — |
| 学生 | 200 · **0** | 200 · **0** | **403** | **403** | 200 · **0** | 200 · **0** | **403** |
| 咨询师 | — | 200 · **0** | **403** | — | — | 200 · **0** | — |
| **管理员** | — | **200 · 2** | **200** | — | — | **200 · 6** | **200** |

✅ 「不存在」与「存在但不可见」**同为 403** ⇒ 无存在性预言机。
✅ 管理员纸面数/题数**未变**（2 / 6）⇒ 反向自检成立。

### 7.4b 🔴 正向可访问证据（事务 fixture，不修改正式 DB）

> **为什么必须补**（用户 2026-10-27 指出的验收缺口）：正式库两张试卷**全部 `is_deleted=1`**，
> 仅凭 403 只能证明「已删除不可读」，**无法排除「非管理员一律 403」这种过度限制**。
> 该实现错误会让「禁止未发布资源」被误写成「禁止所有非管理员访问详情」。

**做法**：在测试方法上加 `@Transactional`，用 `JdbcTemplate` 插入 fixture，**方法结束自动回滚**。
正式 DB 复核：`paper_total=2 / fixture_paper=0`、`question_total=6 / fixture_q=0` ⇒ **零污染**。

| # | 场景 | 期望 | 实测 |
| :-: | --- | --- | :-: |
| 23 | 学生 → `paper/info/{status=1,is_deleted=0}` | 200 | ✅ 200，`data.id` 匹配 |
| 24 | 咨询师 → 同上 | 200 | ✅ |
| 25 | 学生 → `question/info/{父卷可见}` | 200 且**不含** `answer/analysis` | ✅ 且 `content` 匹配 |
| 26 | 匿名/学生 → `paper/list`、`question/list` | **包含**可见 fixture | ✅ `id` 命中 |
| 27 | 学生 → `paper/info/{status=2,is_deleted=0}` | 403；列表不含；**admin 仍 200** | ✅ |
| 28 | 同一学生：可见 200 / 已删除 403 | 成对成立 | ✅ |

> **用例 25 顺带关闭了 §7.6 的弱点 1**：这里有**真实数据**，
> 「非管理员响应不含 `answer`」不再是空集恒真，成为 `SEC-QUESTION-ANSWER-PUBLIC` 的**有效正向对照**。

**变异验证（过度限制）**：把实现改为「非 admin 一律 `deny()`」后重跑 ⇒ **4 条正向用例转红**，
其余 24 条仍绿。⇒ 这组正向证据能**区分「按状态授权」与「非管理员一律禁止」**，不是形式主义。

⚠️ **证据层级说明**：正向路径的证明**在测试层（fixture）**，不在运行时（正式库无可见数据）。
两条负向结论（删除/禁用不可见）同时有**运行时实测**与测试证据；正向结论**只有测试证据**。

### 7.5 两处既有测试更新（不是绕过，是语义变更）

| 测试 | 原断言 | 现断言 | 理由 |
| --- | --- | :-: | --- |
| `Phase2CoreRegressionTest#assessmentPaperList_returnsBaselineCount` | 匿名 `/list` = 2 | = **0** | 2 条基线数据**全部 `is_deleted=1`** ⇒ 公共视角本应 0；管理员仍 2（由本批用例 16 兜底）|
| `IdentityAuthorizationRegressionTest#contentEndpointsAreUnaffected` | 含 `paper/info/1` 与 `question/info/1` 应 200 | 二者**移出白名单**并显式断言 **403** | 该用例验的是「**身份授权**未改变」；这两条现由「**资源可见性**」拒绝，原因不同，不能混为一谈 |

### 7.6 🔴 必须承认的三个弱点（不掩盖）

| # | 弱点 | 说明 |
| :-: | --- | --- |
| 1 | ~~S1–S3 断言在空集上恒真~~ → ✅ **已关闭** | 用例 25 用 fixture 构造可见题目，在**有真实数据**的前提下断言不含 `answer/analysis`；用例 20/21 仍保留但已标注为弱断言，不再单独作为证据 |
| 2 | ~~`status=2` 分支无数据可测~~ → ✅ **已关闭** | 用例 27 用 fixture 插入 `status=2 + is_deleted=0`，覆盖「学生 403 / 列表不含 / **admin 仍 200**」；删除 `status` 条件的变异也会使它转红 |
| 2b | **正向证据仅在测试层** | 正式库无可发布数据，运行时只能验证负向；正向结论由 fixture 用例承担（§7.4b）。若后续有真实已发布试卷，建议补一次运行时抽查 |
| 3 | **`/assessmentPaper/page` 存在既有硬编码** | `params.put("exampaperDeleteStart",1)` ⇒ 管理端 `/page` **只显示已删除**试卷（生成器遗留）。**本批未改动**（超出授权边界），但会让管理员看到「全部是已删除项」，属独立议题 |

### 7.7 `SEC-QUESTION-ANSWER-PUBLIC` 证据链（供你判「是否可关闭」）

| 项 | 结论 |
| --- | --- |
| **实现提交** | `bb29b50 fix(security/s2-b): 题目数据边界（DTO 投影，剥离公开/学生侧的 answer·analysis）` |
| **实现位置** | `/list`（`toPublic(c, true)`）、`/page`（`toPublic(c, false)`）、`/info`（`toPublic(view, false)`）—— **三入口全覆盖** |
| **修复时是否有回归测试** | ❌ **无**（`src/test` 中当时无任何 `assessmentQuestion` 用例）|
| **本批新增覆盖** | 用例 19（管理员**必须**看到 `answer/analysis`，正向对照）+ 20/21（非管理员响应体不含，**弱断言**）|
| **状态** | 🟡 **PENDING REVIEW / 待复核关闭** —— 不加 CLOSED，因缺陷原始提交无测试、空集下无法正向复验 |

---

## 八、状态

| 项 | 值 |
| --- | --- |
| 性质 | 🏁 **CLOSED — Paper/Question Resource Visibility Batch · 正式基线节点 `e15e0a2`** |
| 代码改动 | **9 文件**（1 新增类 + 2 Controller + 2 XML + 2 测试 + 2 处既有测试断言更新 + 1 前端注释）|
| DB / 种子数据 | **0 改动** |
| 其他模块 | notification / DataScope / UX-403 / Old Admin / Multi-Tool **全部未动** |
| 测试 | 目标 **28/28**（含 6 条正向 fixture）· 全量 **197/197** |
| 突变验证 | 删 XML 条件 → 10 条转红；改「非 admin 一律拒绝」→ **4 条正向转红** |
| 正式 DB / 种子数据 | **零污染**（`fixture_paper=0` / `fixture_q=0`，事务已回滚）|
| `SEC-QUESTION-ANSWER-PUBLIC` | 🟡 **PENDING REVIEW**（不关闭）|
| `DataScope` Audit | 🟡 `AUDIT / M2 PROVEN …` —— **独立，不因本批改变** |
| C3 Business Visibility | ✅ 已结束 |
| T13b | 🚫 不做 |

**等你复核 staged diff。通过后我再 commit + push。**
