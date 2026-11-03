# ③ Counselor Assessment Access Rule v1 —— 实现报告

> **性质**：实现（🏁 **CLOSED · 用户 2026-10-25 验收通过**）
> **基线**：`HEAD = origin = e15f104` · LEAD 0 · worktree CLEAN · 改动 **7 文件 +778 / −18**
>
> **CLOSED 边界（用户明确，不得扩大解读）**：
> - 未逐点验证全部 19 个 `OwnershipGuard` 调用点的行为等价性
> - 未修复其他数据表的潜在越权问题
> - 不将 C1 自动扩展到其他敏感数据类型
> - **不代表整个系统已完成全面安全审计**
> - **124/124 只证明现有测试覆盖范围内未发现回归**
> **上游**：`STEP13-COUNSELOR-ASSESSMENT-SCOPE-DESIGN.md`（用户 2026-10-25 批准 A + A）
> **核心**：Counselor 仅在存在 **`status = 2`（同意）** 预约关系时才能访问该学生的测评记录

---

## 一、结论

| 项 | 结果 |
| --- | :-: |
| 先红（修复前必须失败）| ✅ **7 run / 2 failures** |
| 后绿（修复后）| ✅ **7/7** |
| 全量回归 | ✅ **124/124** |
| 部署 | ✅ PID `39040` · jar `14:59:36` · 启动 7s 就绪 · ERROR=0 |
| 运行时 T1–T7 | ✅ **全部符合**（与改造前逐项一致）|
| 分页 `total` 收敛 | ✅ `total=0` / `total=1`（无「共 N 条但列表空」）|
| 生产数据 | ✅ **未被污染**（`appointment_total=3` · `TEST-%` = **0**）|
| 既有业务层 | ✅ **0 改动**（service / dao / entity 未动）|
| **授权架构** | ✅ **统一入口**：`OwnershipGuard` 关系回调（§三之二）|

---

## 二、改动清单

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `resources/mapper/AssessmentRecordDao.xml` | 改 | 加 1 个 `<if>` + `EXISTS` 子查询（**覆盖 `/page` 与 `/list`**）|
| 2 | `com/security/OwnershipGuard.java` | 改 | **新增 `RelationChecker` 回调 + 带回调的重载**（旧签名委托传 `null`）|
| 3 | `com/security/CounselorAssessmentScope.java` | **新增** | 关系判定 `hasApprovedAppointment` + `STATUS_APPROVED` 常量 |
| 4 | `com/controller/AssessmentRecordController.java` | 改 | `/page` `/list` 传 `approvedStatus`；`/info/{id}` **一次调用** Guard + 回调 |
| 5 | `test/.../CounselorAssessmentScopeRegressionTest.java` | **新增** | 端到端 T1–T7（7 例）|
| 6 | `test/.../CounselorAssessmentScopeTest.java` | **新增** | 业务规则单测 9 例（status=3 / 多预约 / fail-closed）|
| 7 | `test/.../OwnershipGuardRelationTest.java` | **新增** | **通用回调机制 10 例**（验证「无回调时旧语义未被改写」；**不是** 19 个调用点的逐点等价测试）|

```text
7 files changed, 778 insertions(+), 18 deletions(-)
git diff --cached --check : 无空白错误 ✓
service / dao / entity      : 未改 ✓
DataScope                   : 未改 ✓
OwnershipGuard              : 仅新增重载 + 回调；旧签名委托传 null（旧路径不经过新分支）
DB / 前端 / pom             : 未改 ✓
```

---

## 三、关键实现

### 3.1 列表侧：XML `EXISTS`（不写死数字）

```xml
<if test="params.counselorId != null and params.counselorId != ''">
    and exists (
        select 1
        from counseling_appointment ca
        where ca.student_id   = a.student_id
          and ca.counselor_id = #{params.counselorId}
          and ca.status       = #{params.approvedStatus}
    )
</if>
```

| 设计点 | 说明 |
| --- | --- |
| **status 走参数** | `params.approvedStatus` 由 Java 常量注入，**SQL 中不出现数字 2** ⇒ 规则口径只有一处定义（用户要求）|
| **EXISTS 语义** | 「至少存在一条」⇒ 多预约 / 历史预约天然正确 |
| **仅 counselor 进入** | 学生不传 `counselorId`；管理员被 `DataScope` 直接放行 ⇒ **零影响** |
| **漏传 fail-closed** | `approvedStatus` 缺失 ⇒ 条件恒不匹配 ⇒ 查不到数据（方向安全）|

### 3.2 详情侧：`CounselorAssessmentScope`（只补缺口）

```java
public void assertCanAccess(HttpServletRequest request, Integer targetStudentId) {
    if (!OwnershipGuard.ROLE_COUNSELOR.equals(OwnershipGuard.currentRole(request))) {
        return;                       // 学生 / 管理员 / 其他角色：不表态，交既有组件
    }
    Integer counselorId = OwnershipGuard.currentUserId(request);
    if (counselorId == null || targetStudentId == null) {
        OwnershipGuard.deny();        // fail-closed
        return;
    }
    if (!hasApprovedAppointment(counselorId, targetStudentId)) {
        OwnershipGuard.deny();
    }
}
```

**职责划分**（用户 2026-10-25 明确要求）：

```text
OwnershipGuard              = 通用身份 / 所有权保护（③ 起新增可选关系回调，旧签名保留）
CounselorAssessmentScope    = AssessmentRecord 专属访问规则（只补 counselor）
```

### 3.3 Controller：统一入口（一次调用完成归属 + 关系）

```java
Integer targetStudentId = examrecord == null ? null : examrecord.getStudentId();

// 归属判定与关系判定一次完成，Controller 不再串联第二个 Guard
OwnershipGuard.assertOwnership(request, examrecord, targetStudentId, null,
        req -> counselorAssessmentScope.hasApprovedAppointment(req, targetStudentId));
```

> **注释里已写明**：「若只改列表 SQL 而不加这一处，则『列表看不到、详情能打开』= 修了一半」。

---

## 三之二、架构调整：A → B（用户 2026-10-25 复核后要求）

### 3.2.1 变更原因

设计阶段裁定的是 **A（新增组件，不改 `OwnershipGuard`）**；
复核阶段用户要求改为 **B（`OwnershipGuard` 提供通用关系回调）**，理由是
**授权入口应统一**，Controller 不应自己串联两个 Guard。

### 3.2.2 通用回调（不承载任何业务概念）

```java
@FunctionalInterface
public interface RelationChecker {
    boolean hasRelation(HttpServletRequest request);
}
```

> **刻意不做**：接口中**不出现** assessment / appointment / status / 测评 / 预约 等任何业务概念。
> 「关系如何判定」完全由调用方决定 —— 通用原语不被业务污染（用户指定验收点 ②）。

### 3.2.3 回调触发矩阵（只在必要组合调用）

| 角色 | `ownerCounselorId` | 是否调用回调 | 判定依据 |
| --- | --- | :-: | --- |
| 管理员 | 任意 | ❌ | 本就放行 |
| 学生 | 任意 | ❌ | 按 `studentId` 字段 |
| 心理老师 | **非 null**（实体有该字段）| ❌ | 按字段相等 |
| **心理老师** | **null**（实体无该字段）| ✅ | **回调判定** |
| 未知 / null 角色 | 任意 | ❌ | 直接拒绝（**回调不能成为绕过通道**）|

### 3.2.4 旧签名保留与「未观察到回归」（用户指定验收点 ①）

> **⚠️ 证据强度声明（用户 2026-10-25 复核后收紧）**：
> 本批**未**为 19 个调用点逐一建立行为等价测试。
> 因此结论只能是「**在现有测试覆盖范围内未观察到回归**」，
> **不得**表述为「19 处调用点逐点行为完全不变」。

**代码层面（可静态确认）**：旧签名保留，内部委托到新方法并传 `null`：

```java
public static void assertOwnership(HttpServletRequest request,
                                   Integer ownerStudentId,
                                   Integer ownerCounselorId) {
    assertOwnership(request, ownerStudentId, ownerCounselorId, (RelationChecker) null);
}
```

`relationChecker == null` 时，`心理老师 + 无字段` 组合**仍走原来的 `return`（放行）分支** ——
即**旧调用路径不经过任何新代码分支**（这是结构事实，非行为测试结论）。

**测试层面（覆盖到什么程度，就说到什么程度）**：

| 证据 | 强度 | 能支持什么 |
| --- | :-: | --- |
| `OwnershipGuardRelationTest` 用例 1–3 | 中 | 「无回调」时该原语三种组合的语义未被改写 |
| 全量 **124/124** | 中 | 现有测试覆盖的既有行为未见回归 |
| 19 个 Controller 的逐点等价测试 | ❌ **未做** | — |

| 用例 | 场景 | 期望 | 说明 |
| :-: | --- | --- | --- |
| 1 | 无回调 + 老师 + 无字段 | **放行** ✅ | 旧语义未被改写 |
| 2 | 无回调 + 老师 + 有字段且匹配 | 放行 | 既有语义 |
| 3 | 无回调 + 老师 + 有字段不匹配 | 403 | 既有语义 |
| 9 | 有回调 + 未知角色 + 回调返回 `true` | **403** | 回调不得扩大授权面 |
| 10 | 目标对象为 null + 回调返回 `true` | **403** | 存在性预言机防护不被绕过 |

### 3.2.5 职责重新划分

| 组件 | 职责 | 是否含业务 |
| --- | --- | :-: |
| `OwnershipGuard` | 角色判断 + 既有归属语义 + **何时调用回调** | ❌ 无 |
| `OwnershipGuard.RelationChecker` | 「关系是否存在」的抽象 | ❌ 无 |
| `CounselorAssessmentScope` | 具体关系：`counseling_appointment.status = 2` | ✅ 有（应在此处）|
| `AssessmentRecordController` | 组合：把 Scope 作为回调传给 Guard | ❌ 无规则 |

> `CounselorAssessmentScope` **不再有 `assertCanAccess`**（不再自己拒绝），
> 只提供 `hasApprovedAppointment(...)` 回答「关系是否成立」—— **拒绝动作统一由 Guard 执行**。

---

## 四、🔴 先红后绿证据（本批最重要的验证方式）

### 4.1 修复前：测试必须先失败

```text
mvn test -Dtest=CounselorAssessmentScopeRegressionTest

[ERROR] Tests run: 7, Failures: 2
        expected: 0        ← T4 counselor 2（status=1）列表本应为 0，实际 1
        expected: 0        ← T5 counselor 3（status=1）列表本应为 0，实际 1
BUILD FAILURE
```

⇒ **证明测试确实捕获了修复前存在的越权**，而不是「改完再补一个刚好通过的测试」。

### 4.2 修复后：转绿

```text
mvn test -Dtest=CounselorAssessmentScopeRegressionTest

[INFO] Tests run: 7, Failures: 0, Errors: 0
BUILD SUCCESS
```

### 4.3 全量回归：115/115

| 测试类 | 数量 | 变化 |
| --- | :-: | --- |
| `③ Counselor Assessment Scope`（端到端）| **7** | 新增 |
| `③ CounselorAssessmentScope`（业务规则）| **9** | 新增 |
| `③ OwnershipGuard 关系回调`（通用机制）| **10** | 新增 |
| Step 12 AgentIdentity | 11 | 不变 ✓ |
| Step 11 AssessmentTools | 14 | 不变 ✓ |
| Step 11 AgentController | 10 | 不变 ✓ |
| IdentityAuthorization | 30 | 不变 ✓ |
| Phase2Core | 17 | 不变 ✓ |
| PasswordHasher | 16 | 不变 ✓ |
| **合计** | **124** | 98 既有全绿 + 26 新增 |

---

## 五、运行时矩阵（修复前 vs 修复后）

### 5.1 修复前基线（Step 12 之后、本批之前实测）

| 身份 | `/page` 列表 | `/info/1` |
| --- | :-: | :-: |
| student a1 | 1 | 200 |
| **counselor a1（status=2）** | 1 | 200 |
| **counselor a2（status=1）** | **1** 🔴 | **200** 🔴 |
| **counselor a3（status=1）** | **1** 🔴 | **200** 🔴 |
| admin | 1 | 200 |

### 5.2 修复后（实测）

| # | 身份 | 列表 | `total` / `totalPage` | `/info/1` | 判定 |
| :-: | --- | :-: | :-: | :-: | :-: |
| T1 | student a1 | **1** | 1 / 1 | **200** | ✅ |
| T2 | student a2 | **0** | — | **403** | ✅ |
| **T3** | **counselor a1（status=2）** | **1** | **1 / 1** | **200** | ✅ 允许 |
| **T4** | **counselor a2（status=1）** | **0** | **0 / 0** | **403** | ✅ 拒绝 |
| **T5** | **counselor a3（status=1）** | **0** | — | **403** | ✅ 拒绝 |
| T6 | admin | **1** | 1 / 1 | **200** | ✅ 不变 |
| T7 | 无 token | — | — | — | ✅ `200 + code=401` |

> **唯一变量是预约状态**：三位咨询师对同一学生，仅 `status=2` 的那位可通过。
> 这直接证明规则作用于「关系」而非「角色身份」。

### 5.3 分页 count 一致性（额外验证）

```text
counselor a2（无权限）：total=0  totalPage=0  list=[]     ← 一致
counselor a1（有权限）：total=1  totalPage=1  list=[1条]  ← 一致
```

⇒ MyBatis-Plus 的 auto-count 复用了同一 `WHERE`（含 `EXISTS`）⇒
**不存在「共 N 条但列表为空」的错位**。

### 5.4 日志

```text
Started ... in 10.445 seconds        ← 启动正常
ERROR = 0
GlobalExceptionHandler 记录 3 次      ← T2(student a2) + T4(counselor a2) + T5(counselor a3) 的详情 403
```

> `/page` 对 counselor 是**过滤**（返回 0 条，HTTP 200），**不是拒绝** ——
> 所以只有 `/info` 产生 403 日志，共 3 次，与预期完全一致。

---

## 六、生产数据未被污染（单测回滚验证）

`status=3` 与「多预约」两类组合在库中不存在，单测用
`@SpringBootTest + @Transactional + @Rollback` **插入临时行后自动回滚**：

```text
执行 CounselorAssessmentScopeTest（10 例，含 4 次 insert）之后：

appointment_total = 3          ← 与基线一致
TEST-% 行数       = 0          ← 临时数据已全部回滚
明细：id 1/2/3 · student 1 · counselor 2/1/1 · status 1/1/2   ← 完全未变
```

> 这是用户明确要求（**不要为测试改生产数据**）的执行结果。

---

## 七、验收对照（用户批准的 A + A）

| # | 要求 | 落实 |
| :-: | --- | :-: |
| 1 | `/info` 用 `CounselorAssessmentScope`，**不改 `OwnershipGuard`** | ✅ `OwnershipGuard` 零改动 |
| 2 | Scope **只补 counselor 缺口**，不重写其他角色 | ✅ 非 counselor 直接 `return`；单测 #7 覆盖学生/管理员/未知角色 |
| 3 | 列表用 **XML `EXISTS`** | ✅ 一处覆盖 `/page` + `/list` |
| 4 | **不在 SQL / Java / 测试散落数字 2** | ✅ 常量 `STATUS_APPROVED`，XML 取 `params.approvedStatus` |
| 5 | 多预约语义 = **至少一条 status=2** | ✅ 单测 #5（1+3+2 → true）· #6（1+3 → false）|
| 6 | 先红后绿 | ✅ 7/2 failures → 7/0 |
| 7 | 详情加「不同学生」边界，**不造生产数据** | ✅ 单测 #4/#5/#6 用事务回滚 |
| 8 | `DATA-SCOPE-COUNSELOR-OTHER-TABLES` 只登记不修 | ✅ 未动其他表 |
| **9** | 🔴 **旧签名保留，既有行为未观察到回归** | 🟡 旧签名委托传 `(RelationChecker) null`（**静态可见：旧路径不经过新分支**）+ 用例 1–3 覆盖旧语义 + 全量 124/124。**⚠️ 未做 19 个调用点的逐点等价测试，故不声称「逐点行为完全不变」** |
| **10** | 🔴 **未把 Assessment / Appointment 业务硬编码进 `OwnershipGuard`** | ✅ 接口与实现中**无**任何业务概念；用例 6–9 用**任意回调**验证机制本身（不引用任何业务表）|
| **11** | 授权入口统一（Controller 不再串联两个 Guard）| ✅ `/info/{id}` 只有**一次** `OwnershipGuard.assertOwnership(...)` 调用 |

---

## 八、边界确认（未扩散）

| 项 | 状态 |
| --- | :-: |
| `OwnershipGuard` | 🟡 **仅新增 `RelationChecker` + 带回调重载**；旧签名委托传 `(RelationChecker) null` ⇒ 旧路径不经过新分支；**未做 19 点逐点等价测试** |
| `OwnershipGuard` 内是否出现业务概念 | ✅ **无** —— 不含 assessment / appointment / status 等任何字样（用例 6–9 用任意回调验证机制本身）|
| `DataScope` | ✅ 0 改动 |
| 其他 13 个 Controller 的同类问题 | ✅ 未动（已登记）|
| `assessment_answer` / `wrong_question` / `student` / `notification` | ✅ 未动 |
| `counseling_appointment` / `counselor_message` / `counselor_favorite` | ✅ 未动（本就双字段正常）|
| Service / Dao / Entity | ✅ 0 改动 |
| DB / DDL / 前端 / pom | ✅ 0 改动 |
| Agent（Step 12 行为）| ✅ 未动，`counselor → /agent/chat` 仍 403 |
| C1 是否继承到其他敏感数据 | ✅ **未继承**（明确不做）|

---

## 九、Staged Review

```text
M  src/main/java/com/controller/AssessmentRecordController.java
A  src/main/java/com/security/CounselorAssessmentScope.java
M  src/main/java/com/security/OwnershipGuard.java
M  src/main/resources/mapper/AssessmentRecordDao.xml
A  src/test/java/com/regression/CounselorAssessmentScopeRegressionTest.java
A  src/test/java/com/security/CounselorAssessmentScopeTest.java
A  src/test/java/com/security/OwnershipGuardRelationTest.java

7 files changed, 778 insertions(+), 18 deletions(-)
check: OK · 未暂存/未跟踪: 0
```

| 项 | 值 |
| --- | --- |
| HEAD / origin | `bc9bd3b` / `bc9bd3b` · LEAD 0 |
| commit | ❌ **未提交** ⇒ repository 非 clean |
| 服务 | 后端 `8080` PID `39040`（jar `14:59:36`）· 前端 `5173` |
| DB | `counseling_appointment=3`（未变）· `TEST-%` = 0 |

---

## 十、建议 commit message

```text
feat(security/step13): Counselor 测评访问规则 v1 —— OwnershipGuard 关系回调 + 已同意预约

- OwnershipGuard 新增 RelationChecker 通用关系回调（不含任何业务概念）；
  旧签名委托传 null ⇒ 旧调用路径不经过新分支（全量 124/124 未见回归；
  未做 19 个调用点的逐点等价测试）
- 新增 CounselorAssessmentScope：仅提供「已同意预约」关系判定（status 常量单点定义）
- /info/{id} 改为统一入口：一次 assertOwnership 调用完成归属 + 关系判定
- XML: assessmentRecord 列表加 EXISTS 子查询；status 经 params.approvedStatus 传入（SQL 无魔法数字）
- 测试：先红后绿（7 run / 2 failures → 7/0）；全量 124/124（新增 26 例）
- OwnershipGuardRelationTest 直接证明：无回调时放行语义不变；回调仅在「老师+无字段」触发；
  未知角色即使回调返回 true 也拒绝
- 运行时：student 200 / counselor(status=2) 200 / counselor(status=1) 403 / admin 200
- 分页 total 同步收敛（total=0），无「共 N 条但列表空」
- 单测用事务回滚覆盖 status=3 与多预约，生产库未被污染
- 未修其他表的同类问题（已登记 DATA-SCOPE-COUNSELOR-OTHER-TABLES）
```

---

## 十一、下一步

| 待办 | 状态 |
| --- | --- |
| staged review → 用户复核 → commit → push | 等用户 |
| `DATA-SCOPE-COUNSELOR-OTHER-TABLES` | 登记挂账（需逐表业务规则）|
| Phase 4 多 Tool | 待本批 CLOSED |
| Step 10 / R15 / UX-AGENT-ROLE | 挂账 |
