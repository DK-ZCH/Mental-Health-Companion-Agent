# AC6 变异实验报告 —— 验证「授权条件对最终查询确实生效」

> **性质**：🔬 **变异实验（已执行并完整还原）**
> **基线**：`HEAD = origin = bdde50b` · LEAD 0 · worktree **CLEAN**
> **授权**：用户 2026-10-25 批准「临时移除一处真实 XML 授权条件 → 跑针对性测试 → 恢复 → 不提交」
> **本轮产出**：实验证据（**零代码改动、零提交、工作区已还原**）

---

## 一、实验结论

**AC6 通过。**

```text
移除条件 → 针对性测试因【越权数据重新出现】而失败（expected 0 but was 1）
恢复条件 → 同一测试重新通过
```

| AC6 层 | 要求 | 结果 |
| :-: | --- | :-: |
| **AC6-1 变红** | 删除一处真实授权条件后，对应测试必须失败 | ✅ **7 tests / 2 failures** |
| **AC6-2 有针对性** | 失败的必须是「**无权目标被排除**」断言，不是总数/SQL 字符串 | ✅ 失败的正是 `counselor2/3 应得 0`，断言消息即「仅有 status=1 ⇒ 不得看到任何测评记录」|
| **AC6-3 可恢复** | 恢复后测试重新通过 | ✅ **7/7**，全量 **149/149** |

> **归因确认（AC11）**：失败类型为 `Failures=2 / Errors=0`，
> 断言值 `expected: 0 but was: 1` —— 即**授权范围扩大、越权记录重新可见**，
> **不是** SQL 语法错误、**不是** 启动失败、**不是** 测试数据缺失。

---

## 二、实验设计（执行前的选择依据）

### 2.1 目标条件的选择理由

| 候选 | 是否选用 | 原因 |
| --- | :-: | --- |
| `assessment_record` 的 `counselorId` `<if>` | ✅ **选用** | 同时满足：① 条件**真实生效**（已由 D3/D4 证明）② **已有**观察它的测试 ③ 测试数据**能区分**「条件生效 / 被移除」|
| `counseling_appointment` 的 `counselorId` `<if>` | ❌ | 无既有针对性测试（若新写测试，违反「不改测试」约束，实验无效）|
| `bdde50b` 的资源级拒绝 | ❌ | 属 **Java 层**，不属本批 XML 变异对象 |

### 2.2 目标对象

```text
文件  ：src/main/resources/mapper/AssessmentRecordDao.xml
条件  ：L55–L63
        <if test="params.counselorId != null and params.counselorId != ''">
            and exists (
                select 1 from counseling_appointment ca
                where ca.student_id   = a.student_id
                  and ca.counselor_id = #{params.counselorId}
                  and ca.status       = #{params.approvedStatus}
            )
        </if>
测试  ：com.regression.CounselorAssessmentScopeRegressionTest（7 例）
关键  ：T4 / T5 —— 「counselor 2/3（status=1 待审核）→ 列表 0 条」
```

### 2.3 测试是否真的能「看到」条件效果（执行前自检）

| 自检项 | 结论 |
| --- | :-: |
| 测试是否只检查固定行数？ | ❌ 不是。T3 断言**有权者得 1**，T4/T5 断言**无权者得 0**，构成对照 |
| 测试数据是否包含「无权目标」？ | ✅ 有。counselor2/3 与 student1 仅有 `status=1` 预约（**非**已批准）|
| 是否会被其他过滤机制掩盖？ | ✅ 不会。`studentId` 条件对学生生效，但 counselor 请求**不带** `studentId`，唯一约束就是本案条件 |
| 是否有反向自检（防止恒 0）？ | ✅ T3（有权者=1）、T6（admin=1）、T1（学生本人=1）|

> 若以上任一不成立，实验即使「变红」也不能证明该条件受保护 —— 本设计已逐项排除。

---

## 三、六步执行记录

### 步骤 1 · 基线

```text
HEAD    = bdde50b   origin = bdde50b
worktree= CLEAN
命令    : mvn -B test -Dtest=CounselorAssessmentScopeRegressionTest
结果    : Tests run: 7, Failures: 0, Errors: 0  ·  BUILD SUCCESS
```

### 步骤 2 · 选择有区分能力的案例

见 §2.1 / §2.3（选择 `assessment_record` 的 counselor 条件 + T4/T5 断言）。

### 步骤 3 · 只移除一处条件（改动范围校验）

```text
git diff --stat
  src/main/resources/mapper/AssessmentRecordDao.xml | 9 ---------
  1 file changed, 9 deletions(-)

非本文件改动数 = 0      ← 未改其他 XML / Java / 夹具 / 数据库
```

### 步骤 4 · 运行针对性测试（预期变红）

```text
命令    : mvn -B test -Dtest=CounselorAssessmentScopeRegressionTest
结果    : Tests run: 7, Failures: 2, Errors: 0  ·  BUILD FAILURE

失败用例：
  counselorWithPendingAppointment_cannotAccess   (T4)   expected: 0 but was: 1
  counselorWithPendingAppointment_cannotAccess2  (T5)   expected: 0 but was: 1
```

**归因分析**：

| 观察 | 含义 |
| --- | --- |
| `Failures=2 / Errors=0` | 是**断言失败**，不是异常/SQL/启动问题 |
| `expected: 0 but was: 1` | counselor2/3 原本应得 0 条，现在得到 **1 条** = student1 的测评记录**重新可见** |
| 恰为 T4/T5（无权者） | 失败的正是「无权目标被排除」断言 ⇒ **AC6-2 成立** |
| T3（有权者=1）、T6（admin=1）、T2（学生他人=0）**仍通过** | 证明只影响了 counselor 分支，且**不是**全局崩溃 ⇒ 针对性成立 |

### 步骤 5 · 精确恢复并复验

```text
git checkout -- src/main/resources/mapper/AssessmentRecordDao.xml
git status --porcelain  →  空（CLEAN）
恢复后 L48–L63 逐行核对：条件 9 行与原文完全一致（含 EXISTS 子查询与 status 绑定）
```

```text
命令    : mvn -B test -Dtest=CounselorAssessmentScopeRegressionTest
结果    : Tests run: 7, Failures: 0, Errors: 0  ·  BUILD SUCCESS
```

### 步骤 6 · 最终状态核对

```text
commit FULL = bdde50bb9975a7502bec299020e7730ac8356178   ← 未变化
HEAD        = bdde50b
origin      = bdde50b
LEAD        = 0
worktree    = CLEAN
git log -1  = bdde50b fix(security): 逐题作答/错题咨询师一律不可读   ← 无新提交
全量测试    = Tests run: 149, Failures: 0, Errors: 0 · BUILD SUCCESS
```

> **实验过程的一次中断（如实记录）**：步骤 5 的合并命令因审批提示超时被取消，
> 当时文件仍处于变异状态；随即**单独执行恢复**并核验 `worktree=CLEAN`、
> 条件 9 行完整、`HEAD` 未变。最终状态与预期一致，但该中断已如实登记。

---

## 四、本实验证明了什么 / 没证明什么

### ✅ 已证明

| # | 结论 |
| :-: | --- |
| 1 | `assessment_record` 的 `counselorId` 条件**确实约束了最终查询**，不是死代码 |
| 2 | 该约束**有针对性测试保护**，被移除时会**立刻**失败（不是靠人工发现）|
| 3 | 现有测试**能区分**「条件生效」与「条件被移除」，不存在「假绿」 |
| 4 | 恢复后完全回到基线 ⇒ **测试失败确实由该条件缺失导致** |

### ❌ 未证明（不得据此推断）

| # | 未证明项 |
| :-: | --- |
| 1 | 其他 18 个调用点的条件是否也受同等保护 —— **只验证了 1 处** |
| 2 | `counseling_appointment` / `favorite` / `message` 是否有针对性测试 —— **未验证**（本次因其无既有测试而未选）|
| 3 | 新增 `/page` 时是否会被这套测试拦住 —— **不会**（当前无 M1 声明机制）|
| 4 | C3 五模块的 no-op 调用是否安全 —— **与本实验无关** |

> ⇒ **AC6 对「被验证的那一处」成立**；对「整个 `DataScope` 契约」尚不成立。
> 这正是后续 M2 需要覆盖 19 个调用点的原因。

---

## 四之二、T13 · Student 列表侧变异实验（2026-10-27 补做）

> **性质**：`AUTHZ-COUNSELOR-STUDENT-SCOPE` 的 **M2 mutation evidence**。
> ⚠️ **本实验不重开已 CLOSED 的 Student 修复批次**（`36bb760`）；
> 它是 `DataScope` 审计线的**独立证据增强项**。

### 目标

```text
文件  ：src/main/resources/mapper/StudentDao.xml
条件  ：L42–L50
        <if test="params.counselorId != null and params.counselorId != ''">
            and exists (select 1 from counseling_appointment ca
                        where ca.student_id = a.id
                          and ca.counselor_id = #{params.counselorId}
                          and ca.status = #{params.approvedStatus})
        </if>
测试  ：com.regression.CounselorStudentScopeRegressionTest（12 例）
```

### 六步执行结果

| 步骤 | 结果 |
| :-: | --- |
| 1 基线 | `HEAD=36bb760` · CLEAN · **12/12 BUILD SUCCESS** |
| 2 选例 | 该模块已确认「条件真实生效 + 有针对性测试 + 数据可区分（1 / 0 vs 3）」|
| 3 只删一处 | `1 file changed, 9 deletions` · **非目标文件改动 = 0** |
| 4 变红 | **12 tests / 4 failures · `Errors = 0`** |
| 5 恢复 + 复验 | `git checkout` → 无 M 记录 → **12/12** |
| 6 最终 | `HEAD` 未变（`36bb760`）· **零提交** · CLEAN · 全量 **169/169** |

### 失败用例与归因

```text
counselor1SeesOnlyApprovedStudent (T1)   expected: 1   but was: 3
counselor2SeesNothing             (T2)   expected: 0   but was: 3
counselor3SeesNothing             (T3)   expected: 0   but was: 3
listMatchesPage                   (T15)  expected: 1   but was: 3
```

- `Errors = 0` ⇒ **断言失败**，不是 SQL 语法错误 / 启动失败 / 测试环境异常
- 失败的**全部 4 条都是咨询师范围断言** ⇒ **AC6-2 针对性成立**
- 恢复后 12/12 ⇒ **AC6-3 可恢复成立**

### 🔴 必须记录的一个局限

```text
本次变异【只影响列表侧】。
T4 / T5 / T6 / T7（/info、/detail）在变异后仍然通过 ——
因为详情侧由【Java 关系回调】承担，与 XML 条件是两个独立层。
```

⇒ 这证明两层是**纵深防御**（任一层单独被移除都不至于立刻全盘失守），
但也意味着：**「删除详情侧回调」是另一项尚未执行的变异证据**。

| 变异对象 | 状态 |
| :-: | :-: |
| Student XML 列表条件 | ✅ **已验证**（本次）|
| Student Java 详情回调 | ⏳ **未验证**（需改 Java，属另一项可选证据）|

> 详情侧并非无证据：修复前 T5/T6 为红（403 断言失败）、修复后为绿，已证明回调**确实生效**；
> 只是尚未做「移除回调 → 变红」的反向变异。

## 五、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `bdde50b` / `bdde50b` · LEAD 0 |
| worktree | **CLEAN** |
| 本次实验产生的提交 | **0** |
| 代码 / SQL / DB / 前端 / pom 最终状态 | **与实验前完全一致** ✓ |
| 全量测试 | **149/149** |
| 服务 | 后端 `8080` PID `31608` · 前端 `5173` |

**下一步（待批准）**：把该实验方法推广到其余 18 个调用点，并落地 M1 声明机制。
