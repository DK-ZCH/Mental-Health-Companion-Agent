# ③ Counselor Assessment Access Rule v1 —— 授权逻辑与测试矩阵设计

> **性质**：设计（📐 **待批准 · 不写代码**）
> **基线**：`HEAD = origin = bc9bd3b` · LEAD 0 · worktree CLEAN · DB 未改
> **上游**：`PHASE4-ROADMAP-REVIEW.md` 发现 2（P1 待定）· 用户 2026-10-25 裁定 **② = C1**
> **本批定位**：把 C1 规则转成**精确判定逻辑 + 测试矩阵**，再据实判断需要改哪里

---

## 一、规则正式文本（本批唯一授权来源）

### Counselor Assessment Access Rule v1

> Counselor **不因** counselor 身份自动获得全体学生心理测评记录的访问权限。
>
> Counselor **仅可**访问与其存在**已同意咨询预约**（`counseling_appointment.status = 2`）
> 的学生的心理测评记录。
>
> | 预约状态 | 语义 | 是否可访问 |
> | --- | --- | :-: |
> | `status = 1` | 待审核 | ❌ 不可 |
> | `status = 2` | **同意** | ✅ **可** |
> | `status = 3` | 拒绝 | ❌ 不可 |
> | 不存在预约 | — | ❌ 不可 |
>
> 本规则基于现有 `counseling_appointment` 关系实现，**不新增** counselor-student 归属表。
>
> **属当前项目 MVP 授权模型**；未来若引入正式分配关系，可重新评估。

### 边界锁死（用户 2026-10-25 明确）

```text
✅ 本规则【只】解决：counseling_appointment(status=2) → assessment_record
❌ 不得自动继承到：心理档案 / 咨询记录 / 私密日记 / AI 对话历史 / 危机记录
```

> 每一种新增敏感数据，都必须**单独**判断访问必要性 ——
> 禁止以「既然能看 assessment，那 message/appointment 也一起给」的方式扩张。

---

## 二、授权判定逻辑（精确形式）

### 2.1 判定函数

```text
canAccessAssessment(identity, targetStudentId) -> boolean

if identity.role == 管理员:
    return true                                   # 管理端全量（沿用 DataScope 既有语义）

if identity.role == 学生:
    return identity.userId == targetStudentId     # 只能看本人

if identity.role == 心理咨询老师:
    return EXISTS(
        SELECT 1 FROM counseling_appointment
        WHERE counselor_id = identity.userId
          AND student_id   = targetStudentId
          AND status       = 2                    # 仅「同意」
    )

else:                                             # null / 未知角色
    return false                                  # fail-closed
```

### 2.2 必须保持不变的既有语义

| 角色 | 现有行为 | 本批后 |
| --- | --- | :-: |
| 管理员 | 全量 | ✅ 不变 |
| 学生 | 本人 | ✅ 不变 |
| 咨询师 | **全量（缺陷）** | 🔴 **改为 C1 范围** |
| null / 未知 | 拒绝（`DataScope` 已 fail-closed）| ✅ 不变 |

---

## 三、🔴 关键发现：泄露口是 **3 个读路径**，不是 1 个

### 3.1 路径清点

| # | 端点 | 过滤机制 | counselor 现状 |
| :-: | --- | --- | :-: |
| 1 | `/assessmentRecord/page` | `DataScope` → `params.counselorId` | 🔴 XML 无该条件 ⇒ **全量** |
| 2 | `/assessmentRecord/list` | 同上（**共用同一条 `selectListView`**）| 🔴 **全量** |
| 3 | `/assessmentRecord/info/{id}` | `OwnershipGuard.assertOwnership(..., studentId, **null**)` | 🔴 **放行** |

### 3.2 第 3 条是独立泄露（只修 XML 会漏）

```java
// OwnershipGuard.java:100-105
if (ROLE_COUNSELOR.equals(role)) {
    if (ownerCounselorId == null) {
        // 实体无 counselor_id 字段 → 无依据判定 → 维持现状
        return;              // ← 🔴 直接放行
    }
    ...
}
```

```java
// AssessmentRecordController.java:99-100
// 注：assessment_record 无 counselor_id 字段，教师侧归属规则无 schema 依据，暂维持现状
OwnershipGuard.assertOwnership(request, examrecord,
        examrecord == null ? null : examrecord.getStudentId(), null);   // ← 传 null
```

⇒ **counselor 可用 `/info/{id}` 读取任意测评详情**，即便列表已过滤。

> **结论：本批必须同时覆盖 `/page`、`/list`、`/info/{id}` 三个入口。**
> 只修列表 = 修了一半，且会造成「列表看不到但详情能打开」的假象。

### 3.3 好消息：`/page` 与 `/list` 共用 SQL

```text
/page  → examrecordService.queryPage(params)  → selectListView
/list  → examrecordService.queryPage(params)  → selectListView   ← 同一条
```

⇒ 改 XML **一处**即同时覆盖两个端点。

---

## 四、实现方案

### 4.1 列表侧（覆盖 `/page` + `/list`）

在 `AssessmentRecordDao.xml` 的 `selectListView` 增加：

```xml
<!-- ③ Counselor Assessment Access Rule v1：仅「已同意预约」的学生可见 -->
<!-- status=2 对应字典 appointment_status: 1待审核 / 2同意 / 3拒绝 -->
<if test="params.counselorId != null and params.counselorId != ''">
    and exists (
        select 1 from counseling_appointment ca
        where ca.student_id  = a.student_id
          and ca.counselor_id = #{params.counselorId}
          and ca.status      = 2
    )
</if>
```

| 影响面 | 说明 |
| --- | --- |
| 学生 | `params.counselorId` 不存在 ⇒ `<if>` 不进入 ⇒ **不受影响** ✓ |
| 管理员 | `DataScope` 直接 return，不 put ⇒ **不受影响** ✓ |
| 咨询师 | 仅此角色进入该分支 ⇒ **行为按 C1 收敛** |
| 其他表 | **不改**（各自 XML 独立）|

### 4.2 详情侧（`/info/{id}`）

新增一个小判定组件，**不改 `OwnershipGuard`**（它是 19 处共用的通用原语，扩张会影响面）：

```java
// com/security/CounselorAssessmentScope.java（新增）
/** Counselor Assessment Access Rule v1 的唯一 Java 判定入口 */
public boolean canAccess(HttpServletRequest request, Integer targetStudentId) {
    // 非 counselor 角色 → 本方法不表态（由既有 DataScope / OwnershipGuard 负责）
    if (!ROLE_COUNSELOR.equals(role)) return true;
    if (targetStudentId == null) return false;              // fail-closed
    return appointmentService.lambdaQuery()
            .eq(counselorId, currentUserId)
            .eq(studentId, targetStudentId)
            .eq(status, STATUS_APPROVED)                    // 2
            .count() > 0;
}
```

Controller 在既有 `assertOwnership` **之后**追加：

```java
OwnershipGuard.assertOwnership(request, examrecord, studentId, null);
counselorAssessmentScope.assertCanAccess(request, studentId);   // ← 新增
```

> **顺序**：先既有归属（学生/管理员），再 C1（咨询师）。两层互不替代。

### 4.3 规则一致性保障（重要）

规则在**两处**表达（SQL 集合过滤 + Java 单点判定），这是无法避免的：
列表必须由数据库按行过滤，不能逐行调 Java。

**保障手段**：

| 手段 | 作用 |
| --- | --- |
| ① 统一常量 `STATUS_APPROVED = 2` | Java 侧只此一处；XML 无法引用，用注释标明来源 |
| ② **同一测试矩阵分别测列表与详情** | 若两处语义漂移，矩阵必然有一半失败 |
| ③ 文档 §一 作为唯一规则来源 | 任何改动先改规则文本 |

---

## 五、测试矩阵

### 5.1 现有真实数据即可覆盖（无需造数据）

```text
student 1 有 1 条测评记录
预约：counselor 1 → status=2（同意）
      counselor 2 → status=1（待审核）
      counselor 3 → status=1（待审核）
```

| # | 身份 | 目标 | 期望（列表）| 期望（详情）| 判定依据 |
| :-: | --- | --- | :-: | :-: | --- |
| T1 | student 1 | 自己的记录 | **1 条** | **200** | 本人 |
| T2 | student 2 | student 1 的记录 | **0 条** | **403** | 非本人 |
| **T3** | **counselor 1** | student 1 | **1 条** ✅ | **200** ✅ | status=2 |
| **T4** | **counselor 2** | student 1 | **0 条** 🔴 | **403** 🔴 | status=1 |
| **T5** | **counselor 3** | student 1 | **0 条** 🔴 | **403** 🔴 | status=1 |
| T6 | admin | student 1 | **1 条** | **200** | 管理员全量 |
| T7 | 无 token | — | 200 + code=401 | 200 + code=401 | 既有契约 |

> **T3 / T4 / T5 是本批的核心证据**，且**全部可用现有数据验证**，不需要改数据库。
> 修复前：counselor 1/2/3 **都能看到**（全量 / 500+ 直接放行）。

### 5.2 需造数据（或用单测覆盖）

| # | 场景 | 建议方式 |
| :-: | --- | --- |
| T8 | `status=3`（拒绝）的预约 | **单测**（mock/内存构造），不入库 |
| T9 | 完全无预约的 counselor | 单测 + 可考虑临时插入后清理 |
| T10 | 同一 counselor 对同一学生有多条预约（含 1 条 status=2）| **单测**（`exists` 语义应放行）|

### 5.3 回归（不得破坏）

| # | 项 |
| :-: | --- |
| R1 | 既有 98 项测试全绿 |
| R2 | `counselor_message` / `counseling_appointment` / `counselor_favorite` 三个双字段表的列表行为**不变** |
| R3 | 写路径（`/save` `/update` `/delete` `/add`）**不受影响** |
| R4 | Step 12 的 Agent 403 行为**不变** |

---

## 六、影响面与不做什么

### 6.1 改动清单（预估）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `resources/mapper/AssessmentRecordDao.xml` | 改 | 加 1 个 `<if>` + EXISTS 子查询 |
| 2 | `com/security/CounselorAssessmentScope.java` | **新增** | C1 判定组件（Java 唯一入口）|
| 3 | `com/controller/AssessmentRecordController.java` | 改 | `/info/{id}` 追加 1 行判定；注入组件 |
| 4 | `com/config/SpringConfig.java` 或等价 | 改 | 组件装配（或加 `@Component`）|
| 5 | 测试 | **新增** | 列表矩阵 + 详情矩阵 + 单测 3 例 |

> 净改动预估：**5–6 文件 · ≈ +200 / −5 行**

### 6.2 明确不做

| 不做 | 原因 |
| --- | --- |
| ❌ 改 `OwnershipGuard` | 19 处共用原语，扩张影响面不可控 |
| ❌ 改 `DataScope` | 语义保持「只 put params」，不引入关系解析 |
| ❌ 修其他 13 个 Controller 的同类问题 | 用户已锁边界；登记为独立工单 |
| ❌ 新增 counselor 权限表 / RBAC | 本批只用现有预约关系 |
| ❌ 让 C1 自动继承到 message / appointment / 其他敏感数据 | **用户明确锁死** |
| ❌ Agent Tool 开放给 counselor | Step 12 已定：counselor 一律 403 |

### 6.3 需登记的新工单

| 工单 | 说明 |
| --- | --- |
| `DATA-SCOPE-COUNSELOR-OTHER-TABLES` | `assessment_answer` / `assessment_wrong_question` / `student` / `notification` 等表的 `DataScope.apply(..., "counselorId")` **同样无 XML 条件**，属同类问题，本批不修 |
| `AUTHZ-RULE-PER-DATATYPE` | 后续每类敏感数据（消息/预约/档案/危机记录）都必须单独定义访问规则，**不得继承 C1** |

---

## 七、待裁定（2 项）

| # | 问题 | 选项 | 建议 |
| :-: | --- | --- | --- |
| **1** | `/info/{id}` 修法 | **A. 新增 `CounselorAssessmentScope` 组件**（不改通用原语）<br>B. 改 `OwnershipGuard` 支持关系回调 | **A**：B 会让 19 处共用原语承担业务规则，影响面不可控 |
| **2** | 列表侧实现 | **A. XML `EXISTS` 子查询**（一处覆盖 `/page`+`/list`）<br>B. Java 先查可见 `studentId` 集合再传 `IN` | **A**：避免额外查询、避免空集合 `IN ()` 边界；B 需处理「无可见学生」的特例 |

---

## 八、执行顺序（批准后）

```text
① 写测试（先红）：T3/T4/T5 三个核心用例，对当前实现应【失败】
      —— 证明缺陷真实存在，而不是「改完再补测试」
        ↓
② 改 XML（列表侧）→ T3/T4/T5 的列表断言转绿
        ↓
③ 加 CounselorAssessmentScope + `/info/{id}` 判定 → 详情断言转绿
        ↓
④ 单测补 T8/T9/T10（拒绝 / 无预约 / 多预约含同意）
        ↓
⑤ mvn test → 98/98 + 新增全绿
        ↓
⑥ 部署 + 运行时矩阵验证（T1–T7 全部实测）
        ↓
⑦ staged review → 用户复核 → commit → push
```

> **① 是关键**：先把测试写成失败状态，用实测证明确实存在越权，
> 再改代码让它变绿 —— 而不是「先改完，再写一个正好通过的测试」。

---

## 九、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `bc9bd3b` / `bc9bd3b` · LEAD 0 |
| worktree | **CLEAN** |
| 代码 / DB / 前端 | **0 改动** |
| Step 12 | 🏁 CLOSED |
| 本批 | 📐 **仅设计，未实现** |

**未写任何代码，等你批准 §七 的 2 项。**
