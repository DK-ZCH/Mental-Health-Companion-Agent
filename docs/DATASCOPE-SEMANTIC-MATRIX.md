# `DataScope` 语义矩阵收口（Phase A）

> **性质**：📐 **语义归类（零业务修改）**
> **状态**：**`AUDIT / M2 PROVEN FOR 4 C1 CASES + 1 C4 CASE (Student)`**（**非 CLOSED**）
>
> ⚠️ **措辞纪律**：**不得**写成「M2 已在 5 个案例中验证」。
> Student 属于 **C4**（授权实现缺失、后修复），与 C1（授权已生效）是**不同分类**：
>
> ```text
> M2 变异证据（4 C1 + 1 C4）：
>   · C1：assessment_record
>   · C1：counseling_appointment
>   · C1：counselor_favorite
>   · C1：counselor_message        ← 2026-10-27 补做（1 vs 3）
>   · C4：student                  ← 修复后的补证，不是 C1
> ```
> **基线**：`HEAD = origin = e15e0a2` · LEAD 0 · worktree CLEAN
>
> ### ⚠️ 状态措辞边界（用户 2026-10-25 要求，不得省略）
>
> | 模块 | 授权实现 | 行为测试 | **变异验证** |
> | --- | :-: | :-: | :-: |
> | `assessment_record` | ✅ | ✅ | ✅ **已完成** |
> | `counseling_appointment` | ✅ | ✅（本批 A1/A2）| ✅ **已完成**（`1 vs 3`）|
> | `counselor_favorite` | ✅ | ✅（本批 B1/B2）| ✅ **已完成**（`2 vs 6`）|
> | `counselor_message` | ✅ | ✅（T17/T18）| ✅ **已完成**（`1 vs 3`，2026-10-27）|
>
> ```text
> ✅ 可以写：C1 四个模块的【授权实现】已确认
> ✅ 可以写：C1 四个模块的【变异验证】均已完成（4/4）
> ✅ 可以写：M2 已在 4 个 C1 用例上证明有效
> ❌ 不可以写：DataScope 契约已闭环
>              —— C2 / C3 / C4 未被这四次实验证明
> ❌ 不可以写：M2 已在 5 个案例中验证（Student 是 C4，不能并入 C1 计数）
> ```

### 全局状态分离（用户 2026-10-27 要求，逐项不得合并）

| 事项 | 状态 |
| --- | :-: |
| **`AUTHZ-COUNSELOR-STUDENT-SCOPE`** | ✅ **CLOSED**（`36bb760`）|
| **`DataScope` Contract Audit** | 🟡 **`AUDIT / M2 PROVEN FOR 4 C1 CASES + 1 C4 CASE (Student)`** |
| **Student M2 mutation T13（列表侧 XML）** | ✅ **PASS（2026-10-27）**：删 `StudentDao` EXISTS 条件 → **12/4 失败**（`1 vs 3` / `0 vs 3`，`Errors=0`）→ 恢复 → 12/12 + 169/169；**零提交** |
| **Student 详情侧回调变异（T13b）** | 🚫 **决定不做**（用户 2026-10-27）：它验证的是 `OwnershipGuard` Java 回调的纵深防御，**非 DataScope 契约核心证据**；且修复前 T5/T6 红→修复后绿**已证明回调参与修复**。继续做等于把已 CLOSED 的 Student 批次拉回来无限追加证据，违反「业务修复 CLOSED ≠ 审计证据无限追加」 |
| **C3 Business Visibility** | 🔴 **当前主线**（决定「资源本应公开」还是「存在未实现的范围」；未解决则 DataScope 审计难以关闭）|
| **C3 Business Visibility** | ⏳ **PENDING**（含 `BIZ-NOTIFICATION-VISIBILITY`）|
| **`UX-COUNSELOR-DETAIL-403`** | ⏳ **PENDING**（不得为修 UI 放开后端权限）|
| **Old Admin Export Manual Check** | ⏳ **PENDING**（需人工在浏览器导出一次）|
| **Multi-Tool** | ⏸ **PAUSED** |

> 🔴 **不得因 Student 修复完成而关闭 `DataScope` 整体审计。**
> Student 原来就是 **C4** —— 它恰恰证明了「`DataScope` 参数存在」**不能**作为授权正确的证据。
> ⇒ Student 的关闭是**因为修复已交付**，**不是**因为 `DataScope` 契约已闭环。
> **上游**：AC6 变异实验已通过（`DATASCOPE-AC6-MUTATION-EXPERIMENT.md`）；用户裁定「先按模块语义收口，不机械铺开 19 个调用点」

---

## 一、核心结论（先看这个）

```text
19 个调用点 ≠ 19 个需要 M2 的对象
按模块语义归类后：12 个模块 → 只有 2 个模块真正缺 M2 行为测试
```

| 结论 | 数值 |
| --- | :-: |
| 调用点 | **19** |
| 模块 | **12** |
| C1（应产生范围限制且已生效）| **4** |
| C2（未消费但已有更高层拒绝）| **2** |
| C3（当前无该归属维度，待业务确认）| **5** |
| C4（应生效但缺失）| **1**（且**仅咨询师维度**）|
| **真正需要新增 M2 的模块** | 🔴 **2**（`counseling_appointment` · `counselor_favorite`）|

> **为什么不是 18**：C2 不应人为要求 SQL 消费无效参数；C3 若为公共资源，**「传与不传结果相同」才是正确行为**；
> C4 属独立安全缺陷，不混入契约修复。强行铺开会把「公共资源」测成「缺陷」—— 这正是用户指出的风险。

---

## 二、维度级语义矩阵（19 调用点 / 12 模块）

> **以「维度」为最小单位**，因为同一模块的两个维度可能分属不同分类
> （例：`student` 的学生维度已正确收敛，咨询师维度才缺）。

| # | 模块 | 调用点 | 学生维度 | 咨询师维度 | 分类 |
| :-: | --- | :-: | --- | --- | :-: |
| 1 | `assessment_record` | 2（L80/L306） | ✅ 生效（`a.student_id`，7 处）| ✅ 生效（EXISTS `ca.status = approved`）| **C1** |
| 2 | `counseling_appointment` | 2（L73/L286）| ✅ 生效（`a.student_id`）| ✅ 生效（`a.counselor_id`）| **C1** |
| 3 | `counselor_favorite` | 2（L73/L279）| ✅ 生效（`a.student_id`）| ✅ 生效（`a.counselor_id`）| **C1** |
| 4 | `counselor_message` | 2（L73/L267）| ✅ 生效（`a.student_id`）| ✅ 生效（`a.counselor_id`）| **C1** |
| 5 | `assessment_answer` | 2（L79/L305）| ✅ 生效（7 处）| ❌ **未消费** → 资源级拒绝（`bdde50b`）| **C2** |
| 6 | `assessment_wrong_question` | 2（L81/L294）| ✅ 生效（7 处）| ❌ **未消费** → 资源级拒绝（`bdde50b`）| **C2** |
| 7 | `student` | 2（L74/L450）| ✅ 生效（4 处）| 🔴 **未消费（0 处）· 应生效但缺失** | **C4** |
| 8 | `assessment_paper` | 1（L73）| ❌ 无消费 | ❌ 无消费 | **C3** |
| 9 | `assessment_question` | 1（L102）| ❌ 无消费 | ❌ 无消费 | **C3** |
| 10 | `counselor` | 1（L77）| ❌ 无消费 | ❌ 无消费（且**对任何角色零效果**）| **C3** |
| 11 | `knowledge_article` | 1（L73）| ❌ 无消费 | ❌ 无消费 | **C3** |
| 12 | `notification` | 1（L73）| ❌ 无消费 | ❌ 无消费 | **C3** |

### 2.1 逐项回答四个问题（用户要求）

| 模块 | ① 是否应产生范围限制 | ② 限制对象 | ③ 承担机制 | ④ 若无限制，为什么 |
| --- | :-: | --- | --- | --- |
| `assessment_record` | ✅ 是 | 学生=本人；老师=有已批准预约的学生 | `DataScope` + XML EXISTS | — |
| `counseling_appointment` | ✅ 是 | 学生=本人；老师=本人接的预约 | `DataScope` + XML `a.student_id`/`a.counselor_id` | — |
| `counselor_favorite` | ✅ 是 | 同上 | 同上 | — |
| `counselor_message` | ✅ 是 | 同上 | 同上 | — |
| `assessment_answer` | ✅ 是（学生维度）| 学生=本人 | `DataScope` + XML | **老师维度**：由**资源级拒绝**承担（一律 403），比行级更严 |
| `assessment_wrong_question` | 同上 | 同上 | 同上 | 同上 |
| **`student`** | ✅ 是（**两个维度都应**）| 学生=本人（已实现）；**老师=有已批准预约的学生（缺失）** | 学生维度：XML `params.studentId` | 🔴 **老师维度缺失** → C4 |
| `assessment_paper` | ❓ 待业务确认 | 若有：发布态/适用人群，**不是**「用户归属」 | 当前仅 `@IgnoreAuth` + `assertAdminOnly` | 试卷可能是公共考试内容 |
| `assessment_question` | ❓ 待业务确认 | 同上 | 同上 | 同上 |
| `counselor` | ❌ 无用户归属语义 | — | `assertAdminOnly`（全实体）/ `@IgnoreAuth`（公开目录）| 咨询师名录不是「某用户的数据」 |
| `knowledge_article` | ❓ 待业务确认 | 若有：草稿/审核态 | 当前仅 `@IgnoreAuth` + `assertAdminOnly` | 知识可能全站公开 |
| `notification` | 🔴 **待明确 A/B** | A=无（全站公告）；B=用户本人 | 当前**仅** `@IgnoreAuth` | 🔴 **无法用现有证据区分** |

---

## 三、C1 的现有行为测试覆盖（用户要求第 3 点）

> **区分「有请求」与「有范围收窄断言」** —— 前者不等于后者。

> **措辞纪律（用户 2026-10-25 指出）**：不得写「C1 覆盖完整」。
> 准确表述是：**C1 授权实现 4/4 已确认；测试覆盖此前仅 2/4 完整，2/4 待补 M2。**
> 本批（`DataScopeC1CoverageTest`）已补齐后 2 个。

| 模块 | 学生侧范围断言 | 咨询师侧范围断言 | 覆盖判定 |
| --- | --- | --- | :-: |
| `assessment_record` | ✅ T1/T2（`CounselorAssessmentScopeRegressionTest`）| ✅ T3/T4/T5 + **AC6 变异实验** | ✅ 完整 |
| `counselor_message` | ✅ T17（伪造 `studentId` 仍只得自己 2 条）| ✅ T18（所有行 `counselorId == 我`，且 `total > 0` 防空跑）+ ✅ **变异**（删 counselor 条件 → `expected: 1 but was: 3`）| ✅ 完整 |
| `counseling_appointment` | ✅ **本批补齐**（A1：student1=3 全本人 / student2=0）| ✅ **本批补齐**（A2：各 1 条 · 全本人 · admin 对照 3）| ✅ **完整（新）** |
| `counselor_favorite` | ✅ **本批补齐**（B1：三人各 2 条 · admin 对照 6）| ✅ **本批补齐**（B2：c1=2 / c2=3 · 全本人）| ✅ **完整（新）** |

> 本批新增 `DataScopeC1CoverageTest`（7 例）：
> A1/A2（appointment 双维度）· B1/B2（favorite 双维度）· C1/C2（提交他人 id 不得放大）· D1（未认证 401）。
> **红/绿验证**：移除 `AppointmentDao` 的 counselor 条件 → `expected: 1 but was: 3`；
> 移除 `FavoriteDao` 的 counselor 条件 → `expected: 2 but was: 6`（连带 C2 也捕获）；恢复后 7/7、全量 **156/156**。

**说明**：`Phase2CoreRegressionTest` 中确实请求了 `counselingAppointment/page`（5 次）与 `counselorFavorite/page`（4 次），
但那是「响应不含 `password`/身份证」的字段检查，**不验证行级范围** ⇒ 不能算作范围收窄的测试。

> ⇒ **M2 待补 = 2 个模块**（`counseling_appointment`、`counselor_favorite`），各需 2 个维度用例。

---

## 四、C2 的机制与边界（用户要求第 4 点）

| 项 | 内容 |
| --- | --- |
| 授权机制 | **资源级拒绝**：`OwnershipGuard.assertCounselorNotAllowed()` 在 `/page`、`/list`、`/info` 三路径**查询前**执行（`bdde50b`）|
| 覆盖对象 | **咨询师维度**（一律 403，与预约状态无关）|
| 学生维度 | 由 `DataScope` + XML `params.studentId` 承担（已生效，实测 student1=5 / student2=0）|
| **不要求的** | ❌ **不要求** XML 消费 `counselorId` —— 在「咨询师一律不可读」的裁定下，行级条件已无意义 |
| **边界（必须保留）** | ⚠️ 仅覆盖**经过这三个控制器方法**的读取；若出现绕过控制器的路径（新端点 / 直接 Service 调用），需重新评估 |
| **契约缺口仍在** | `counselorId` 未被消费这件事**本身**仍是 `DataScope` 契约缺口，只是当前由更高层兜住 |

---

## 五、C3 的处置（用户要求第 5 点）

```text
保留调用 · 不改代码 · 等待业务确认 · 不因标注而关闭
```

| 模块 | 待确认问题 | 若结论为「公共资源」 | 若结论为「应受约束」 |
| --- | --- | --- | --- |
| `assessment_paper` | 是否全量公开？有无发布态？ | 现状正确，可移除 no-op 调用 | 需补**非用户维度**约束（发布态），非 `DataScope` |
| `assessment_question` | 同上 | 同上 | 同上 |
| `counselor` | 公开目录 8 字段是否足够？ | 现状正确 | 见 `DEF-COUNSELOR-LIST-PUBLIC-PII` |
| `knowledge_article` | 是否全部公开？有无草稿态？ | 现状正确 | 需补发布态约束 |
| **`notification`** | 🔴 **A 全站公告 / B 用户私有通知** | 现状正确 | 🔴 **数据模型缺归属字段**，需改表 + 改查询，独立批次 |

> **登记**：`BIZ-NOTIFICATION-VISIBILITY` · `C3-NOOP-CALLS`

### 5.1 🔴 定性修正（用户 2026-10-27 裁定）

```text
C3 ≠ DataScope 契约缺陷
C3 = 当前业务模型是否存在「归属 / 发布态」需求的问题
```

⇒ **禁止**为了让审计「看起来完整」而给 C3 强行添加 `DataScope` 条件。
`DataScope` 只能约束**已存在的列**；模型里没有的维度，加参数也只会变成新的 no-op。

### 5.2 逐模块技术结论（表结构实测，2026-10-27）

| 模块 | 实测事实 | 技术结论 | 后续归属 |
| --- | --- | --- | --- |
| `knowledge_article` | 全表字段仅 `id / title / cover_url / category / published_at / content / created_at` —— **无 `status`、无 `is_deleted`、无任何归属字段** | **无 ownership / 发布态维度可约束**；现状符合模型 | 若未来要求「草稿→审核→发布」，开**独立业务/DB 设计议题**，不塞进本审计 |
| `notification` | 同样**无任何归属字段** | 全站公告（用户 2026-10-27 裁定）⇒ 现状正确 | **不修复、不登记未来需求** |
| `counselor` | `@IgnoreAuth`×4 · `assertAdminOnly`×6 | **公开目录 + 管理端管理**双重身份，**不是 ownership 资源** | PII 议题走 `DEF-COUNSELOR-LIST-PUBLIC-PII`；**不在此加归属过滤** |
| `assessment_paper` / `assessment_question` | 有 `status` / `is_deleted`（发布态），**无归属字段** | 属**发布态维度**，非归属维度 | ✅ 已由资源可见性批次 `e15e0a2` 处理 |

> **C3 技术层结论**：5 个模块中
> · 2 个（paper / question）已由**资源可见性**解决，且**本就不属归属维度**
> · 3 个（notification / knowledge_article / counselor）**当前模型无该维度**，现状符合模型
> ⇒ **C3 不再是 `DataScope` 审计的阻塞项**；「将来是否需要该维度」是业务决策，不是契约缺陷。

---

## 六、C4 独立立项（用户要求第 6 点）

| 项 | 内容 |
| --- | --- |
| 工单 | `AUTHZ-COUNSELOR-STUDENT-SCOPE` |
| 业务规则 | 咨询师只能读取**至少存在一条 `status = 2` 已批准预约**的学生 |
| 现状 | counselor1 应 1 实 **3**；counselor2/3 应 0 实 **3** |
| 学生维度 | ✅ **已实现且有测试**（`Phase2CoreRegressionTest` T4：学生只得 1 条）|
| **不混入本批** | 属**实际授权缺陷**，不是契约检测问题 |
| **修复方向未定** | 不能简单加 `WHERE counselor_id = me` —— `/student/page` 可能承担「新建预约时选学生」，需先审计：<br>· `/student/page` · `/student/list` · `/student/detail` · `/student/info` · 建约选人流程 · 其他入口<br>· 是否存在「管理端/咨询师共用同一列表」 |
| 两种可能方向 | ① 同一接口按业务场景分权限 ② 拆成「可管理学生列表」与「可建约学生列表」 |

---

## 七、M2 的最终范围（Phase B 的前置结论）

| 分类 | 是否需要新增 M2 | 数量 | 状态 |
| --- | :-: | :-: | :-: |
| C1 · `assessment_record` | ❌ 已有（含变异实验）| 0 | — |
| C1 · `counselor_message` | ❌ 已有 T17/T18 | 0 | — |
| **C1 · `counseling_appointment`** | ✅ 需要（学生 + 咨询师两维度）| 1 模块 | ✅ **本批已完成** |
| **C1 · `counselor_favorite`** | ✅ 需要（学生 + 咨询师两维度）| 1 模块 | ✅ **本批已完成** |
| C2（answer / wrongQuestion）| ❌ 不需要（资源级拒绝已有 18 例回归）| 0 | — |
| C3（五个）| ❌ 不需要（公共资源预期结果本就相同）| 0 | — |
| C4（student 咨询师维度）| ❌ 不在本批（独立缺陷）| 0 | ⏳ 待审计 |
| **合计** | | **2 个模块 / 4 组用例** | ✅ **已全部补齐 ⇒ M2 待补 = 0** |

> **C1 状态（2026-10-27 更新）**：授权实现 4/4 确认 · 测试覆盖 **4/4 完整** · **变异验证 4/4 完成**
> （`assessment_record` · `counseling_appointment` · `counselor_favorite` · `counselor_message`）。
>
> ⚠️ **覆盖注记（不是缺口）**：`counselor_message` 的变异只在 `/page` 上有断言；
> `/list` 与 `/page` **共用同一条 `selectListView`**，变异同样使它失效，
> 但**当前没有针对 `/list` 的咨询师范围断言** ⇒ 若将来拆分两条 SQL，需补 `/list` 断言。

### AC6 的正确表述（按用户意见升级）

```text
❌ 错误定义：所有 DataScope 调用都必须「传/不传结果不同」
✅ 正确定义：授权策略声明了某项数据范围约束 → 对应行为测试必须能捕获该约束被移除
```

⇒ C3 若确认为公共资源，**「传与不传结果相同」是正确预期**，不构成缺陷。

---

## 八、待裁定（3 项）

| # | 问题 | 选项 | 建议 |
| :-: | --- | --- | :- |
| **1** | 是否为 `counseling_appointment` / `counselor_favorite` 补 M2（4 组用例）| A. 本批补<br>B. 留到 C4 批次一起<br>C. 不补 | **A**：这是唯一真正缺的 C1 覆盖，且不改业务代码（仅加测试）|
| **2** | C3 五模块的 no-op 调用是否移除 | A. 继续保留<br>B. 业务确认后移除 | **A（暂）**：等 `BIZ-NOTIFICATION-VISIBILITY` 等结论 |
| **3** | C4 是否现在启动（先审计 `/student` 全部入口）| A. 现在审计<br>B. 排在其他挂账之后 | **A（仅审计，不改代码）**：它是已量化的实际缺陷，且审计不产生风险 |

---

## 九、状态

| 项 | 值 |
| --- | --- |
| 审计状态 | 🟡 **`AUDIT / M2 PROVEN FOR 4 C1 CASES + 1 C4 CASE (Student)`**（**非 CLOSED**）|
| AC6 | ✅ **PASS**（4 个 C1 模块 + 1 个 C4 模块均以「删条件转红」证明）|
| `DataScope` 整体契约 | ❌ **仍未证明**（缺 **D4 检测机制**选型 —— 这是能否 CLOSED 的真正门槛）|
| C4 | ✅ 修复已交付 `36bb760` · 变异 T13 PASS |
| C3 | ✅ **技术层已收口**（§5.1/§5.2）：3 个模块模型内**无该维度**，2 个已由资源可见性处理 ⇒ **不再是阻塞项** |
| HEAD / origin | `e15e0a2` / `e15e0a2` · LEAD 0 |
| worktree | **CLEAN**（零代码改动）|
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** ✓ |
