# `AUTHZ-COUNSELOR-STUDENT-SCOPE` —— Student 全入口审计

> **性质**：🔍 **只读审计（零代码改动 · 不修复）**
> **基线**：`HEAD = origin = 36bb760` · LEAD 0 · worktree CLEAN
> **后续**：三项证据核查 + 修复实现见 `AUTHZ-COUNSELOR-STUDENT-SCOPE-EVIDENCE.md`
>          —— **本工单已 CLOSED（`36bb760`）**；T13 变异验证**未执行**，转入 DataScope 审计挂账
> **业务规则（已裁定）**：咨询师只能读取**至少存在一条 `counseling_appointment.status = 2`** 的学生
> **本批边界**：**不改 SQL / Controller / 权限 / 前端**；只回答「规则如何落到各入口而不破坏业务」

---

## 一、结论摘要

| # | 结论 | 证据 |
| :-: | --- | --- |
| 1 | **四条入口对咨询师全部放行**，且 `/page`、`/list`、`/info`、`/detail` 均返回 `phone` + `email` | 实测（§二）|
| 2 | 学生维度**已正确收敛并有测试**（`a.id = #{params.studentId}`）| 实测 student1=1 / student2=1 |
| 3 | 🔴 **咨询师维度完全缺失**：`counselorId` 被 `DataScope` 注入但 XML **0 处消费** | `StudentDao.xml` grep |
| 4 | 🔴 `/info` 与 `/detail` 对咨询师**放行**：`assertOwnership(..., null)` 在「老师 + 无 `counselor_id` + 无回调」组合下 `return` | 代码 + 实测 |
| 5 | **这不是遗漏，是已登记的挂账**：`StudentDao.xml:35` 注释明写「教师侧不映射…挂 G3」 | 代码注释 |
| 6 | ✅ **建约流程不需要咨询师选学生** —— 预约由**学生发起**，`studentId` 服务端强制为本人 | `api/appointment.ts:43-56` |
| 7 | ⚠️ **但存在 3 处真实依赖**：学生管理列表、考试记录/留言管理的「查看·修改」表单会加载 `student/page` | 旧 Admin 调用点 |
| 8 | 🔴 **规则与「审核」流程存在语义冲突**：审核针对 `status = 1`（待审核），而规则只允许 `status = 2` | §五 |

---

## 二、入口矩阵（四条读取入口 · 实测）

### 2.1 授权机制

| 入口 | 代码位置 | 授权机制 | 咨询师结果 |
| --- | --- | --- | :-: |
| `/student/page` | `StudentController:70-74` | `DataScope.apply(request, params, "studentId", "counselorId")` | 🔴 **全量** |
| `/student/list` | `StudentController:444-450` | 同上 | 🔴 **全量** |
| `/student/info/{id}` | `StudentController:92-97` | `assertOwnership(request, yonghu, id, null)` | 🔴 **放行** |
| `/student/detail/{id}` | `StudentController:468-480` | 同上 | 🔴 **放行** |

**放行的代码原因**（`OwnershipGuard:185-190`）：

```java
// 实体无 counselor_id 字段（如 assessment_record）：
if (relationChecker == null) {
    return;                    // ← 未传回调 ⇒ 维持现状放行
}
```

`StudentController` 传入的 `ownerCounselorId = null` **且**`relationChecker = null` ⇒ **必然放行**。
（对照：`AssessmentRecordController` 已接 `CounselorAssessmentScope` 回调，故能拒绝。）

### 2.2 实测（真实 HTTP，2026-10-25）

| 入口 | student1 | student2 | counselor1（有 status=2）| counselor2（无 status=2）| admin |
| --- | :-: | :-: | :-: | :-: | :-: |
| `page` | **1**（id=1）✅ | **1**（id=2）✅ | 🔴 **3**（1,2,3）| 🔴 **3**（1,2,3）| 3 ✅ |
| `list` | — | — | 🔴 **3** | 🔴 **3** | — |
| `info/1` | 200 | **403** ✅ | **200** | 🔴 **200** | — |
| `detail/1` | — | — | — | 🔴 **200** | — |

**依规则应为**：counselor1 → 仅 `[1]`（1 条）；counselor2 → **0 条**。

### 2.3 返回字段（PII）

| 入口 | 返回字段 |
| --- | --- |
| `page` / `list` | `id, username, name, phone, email, gender, avatarUrl, ...` |
| `info` / `detail` | 同上（单条）|
| **不返回** | `password`、`idCardNo`（`@JsonIgnore`，Step 3 已收敛）|

⇒ 咨询师当前可读**全部学生的姓名 + 手机号 + 邮箱**。

### 2.4 XML 层事实

```xml
<!-- StudentDao.xml:33-37 -->
<!-- D2-2b-G2：读范围收敛 —— 学生表以【自身主键 id】作为归属字段 -->
<!-- 注意：教师侧不映射（counselor 与 student 是两个实体，无 student.id = counselor.id 的证据）—— 挂 G3 -->
<if test="params.studentId != null and params.studentId != ''">
    and a.id = #{params.studentId}
</if>
```

- `params.studentId` 出现 **4 次**（生效）
- `params.counselorId` 出现 **0 次**（被静默丢弃）
- ⇒ 与 `AUTHZ-FILTER-PROBE` 审计的结论**完全一致**（同一根因）

---

## 三、调用方全景（回答「谁在用」）

### 3.1 新 Vue：**0 调用**

`frontend/src` 中只使用 `/student/login`、`/student/session`、`/student/update`。
**不使用** `page` / `list` / `info` / `detail` ⇒ 收窄对**新前端零影响**。

### 3.2 旧 Admin：8 处调用

| 调用点 | 端点 | 用途 | 咨询师是否有该菜单 |
| --- | --- | --- | :-: |
| `yonghu/list.vue:656` | `student/page` | **学生管理列表** | ✅ **有（查看）** |
| `yonghu/add-or-update.vue:253` | `student/info/{id}` | 学生详情/编辑 | ✅ **有（查看）** |
| `xinlilaoshiOrder/add-or-update.vue:251` | `student/page?limit=100` | 预约表单**选学生下拉** | ⚠️ 无「新增/修改」按钮 |
| `xinlilaoshiLiuyan/add-or-update.vue:177` | 同上 | 留言表单选学生 | ✅ **有（查看/修改）** |
| `xinlilaoshiCollection/add-or-update.vue:175` | 同上 | 收藏表单选学生 | ❌ 无该菜单 |
| `examrecord/add-or-update.vue:304` | 同上 | 测评记录选学生 | ✅ **有（查看/批改/删除）** |
| `examredetails/add-or-update.vue:313` | 同上 | 答题明细选学生 | ⚠️ 随记录页进入 |
| `examrewrongquestion/add-or-update.vue:317` | 同上 | 错题选学生 | ✅ **有（查看/删除）** |

**咨询师后台菜单**（`utils/menu.js:222-347`）：
健康知识（查看）· 通知（查看）· **学生管理（查看）** · 留言管理（查看/修改）· **预约申请管理（审核/查看）** · 试卷管理 · 考试记录（查看/批改/删除）· 错题本（查看/删除）。

### 3.3 `/student/detail/{id}`：**仅测试调用**

全仓检索：生产前端/Admin **0 调用**，仅 `StudentDetailIdorRegressionTest` 使用。
⇒ 属 API-only 入口（或外部客户端），**无法从仓库内判断其真实消费者**。

---

## 四、建约流程（回答「是否需要选未建立关系的学生」）

**结论：不需要。预约由学生发起。**

```ts
// frontend/src/api/appointment.ts:43-56
export async function addAppointment(payload: {
  counselorId: number | string
  appointmentDate: string
  timeSlot: number | string
}): Promise<void> {
  await unwrap(http.post('/counselingAppointment/add', {
    counselorId: Number(payload.counselorId),
    appointmentDate: payload.appointmentDate,
    timeSlot: Number(payload.timeSlot),
    status: STATUS_PENDING
  }))
}
```

- 学生从**咨询师目录**（`/counselor/list`，公开）选择老师
- `studentId` **不提交**，由服务端强制为当前用户
- ⇒ **咨询师侧不存在「建约时选学生」的流程**

**旧 Admin 的 `xinlilaoshiOrder/add-or-update.vue` 确实有学生下拉，但**：

1. 咨询师在该菜单只有「审核 / 查看」，**无「新增 / 修改」**按钮（`isAuth` 门控）
2. 该表单还会请求 `counselor/page?limit=100`，而该端点是 **admin-only**（`assertAdminOnly`）⇒ 咨询师打开必然失败
3. 「审核」使用 `list.vue` 内联对话框（`status` + `reviewComment`），**不经过 add-or-update**

⇒ **建约/审核都不依赖咨询师读学生列表**。

---

## 五、🔴 规则与现有流程的三处冲突（本审计最重要的发现）

> 规则「只能读 `status = 2` 的学生」在多处与现有 UI 用途不一致。
> **这不是实现问题，是规则粒度问题。**

| # | 冲突 | 现状 | 若严格按 `status = 2` 收窄 |
| :-: | --- | --- | --- |
| **1** | **预约审核面向 `status = 1`** | 咨询师审核待审核申请 | 若审核页要展示学生详情，**审核前看不到该学生** ⇒ 审核流程受阻。当前审核对话框只用预约行数据，**暂不触发**，但 UI 一旦增强就会撞上 |
| **2** | **留言管理（查看/修改）** | 表单加载 `student/page?limit=100` 填充下拉 | 下拉变为「仅已服务学生」或空；**查看**不受影响，**修改**若需换学生则会受限 |
| **3** | **考试记录 / 错题本（查看）** | 详情表单加载 `student/page` | 同上：只影响下拉，不影响已有记录展示 |

### 5.1 由此得出：规则必须区分**三种访问意图**

| 意图 | 场景 | 应允许的范围 |
| --- | --- | --- |
| **服务管理** | 学生管理列表、查看本人服务的学生 | `status = 2` |
| **流程处理** | 审核预约（`status = 1`）、处理留言 | 至少「**与该老师存在任意预约记录**」的学生 |
| **选择/枚举** | 表单下拉（若仍需） | 若业务不需要咨询师换学生，**应移除该下拉**而非放宽范围 |

> ⚠️ **不得**用「一个 `status = 2` 条件」覆盖全部三种意图 —— 这正是本审计要阻止的错误简化。

---

## 六、逐入口的规则适用建议（回答第 4 点）

| 入口 | 建议 | 理由 |
| --- | --- | --- |
| `/student/page`（学生管理列表）| **按 `status = 2` 收窄**（`EXISTS` 子查询）| 该菜单语义就是「我服务的学生」 |
| `/student/list` | 与 `page` 同源同条件 | 两入口共用 `selectListView`，必须一致 |
| `/student/info/{id}` | **接 `OwnershipGuard` 关系回调**（复用 Step 13 模式）| 单条详情，与 `assessment_record` 同构 |
| `/student/detail/{id}` | 同上（同一回调）| 与 `info` 行为必须一致（Step 14 的 T7 一致性要求）|

### 6.1 但「关系」的定义需要按意图扩展

```text
若只实现 status = 2：
    审核场景（status = 1）将看不到申请人详情

若实现「任意预约记录（status ∈ {1,2,3}）」：
    覆盖面更广，但偏离用户裁定的字面

⇒ 必须二选一并明确记录（§八 #1）
```

---

## 七、分角色分入口实测汇总（回答第 5 点）

| 角色 | page | list | info/1 | detail/1 | 依规则应为 |
| --- | :-: | :-: | :-: | :-: | --- |
| student1 | 1（本人）✅ | — | 200（本人）✅ | — | 本人 ✅ |
| student2 | 1（本人）✅ | — | **403** ✅ | 403 ✅ | 他人不可见 ✅ |
| counselor1 | 🔴 **3** | 🔴 3 | 🔴 200 | — | **1**（仅 student1）|
| counselor2 | 🔴 **3** | 🔴 3 | 🔴 200 | 🔴 200 | **0** |
| counselor3 | — | — | — | — | **0** |
| admin | 3 ✅ | — | — | — | 全量（B 类）✅ |
| 未认证 | 401 ✅ | 401 ✅ | 401 ✅ | 401 ✅ | 拒绝 ✅ |

> **注意「实测而非推断」**：本表全部来自真实 HTTP，
> 不是依据「`DataScope` 被调用了」或「XML 里没有 `counselorId`」推断。

---

## 八、待裁定（3 项 · 修复前必须明确）

| # | 问题 | 选项 | 说明 |
| :-: | --- | --- | --- |
| **1** | 「有效关系」是否包含**待审核**（`status = 1`）| A. 仅 `status = 2`（严格按裁定）<br>B. `status ∈ {1,2,3}`（任意预约）<br>C. 分入口：列表用 A / 详情用 B | **建议 C**：列表=服务范围（A），详情=流程需要（B）。但这需要业务确认 |
| **2** | `yonghu/add-or-update.vue` 等表单的**学生下拉**如何处理 | A. 允许为空（只影响换学生操作）<br>B. 从下拉移除（改由服务端固定）<br>C. 单独提供「可选学生」端点 | **建议 A（先）**：改动最小、不破坏查看；若后续确认咨询师不应换学生，再做 B |
| **3** | `/student/detail/{id}` 无生产调用方，是否保留 | A. 保留并按同一规则改造<br>B. 下线该端点 | **建议 A**：下线属接口契约变更，需外部消费者确认 |

---

## 九、本审计明确**没有**做的事

```text
❌ 未改任何 SQL（StudentDao.xml 未动）
❌ 未改任何 Controller
❌ 未改任何权限判断
❌ 未改前端
❌ 未新增/修改测试
❌ 未把「DataScope 被调用」当作安全证据（全部结论来自实测 + 代码路径）
```

---

## 十、状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `9a1cb70` / `9a1cb70` · LEAD 0 |
| worktree | **CLEAN**（本文档为唯一产出，`docs/` 被 gitignore）|
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** ✓ |
| 实测方式 | 仅 HTTP 只读 + 只读 SQL，**无写入、无残留** |
| 服务 | 后端 `8080` PID `31608` · 前端 `5173` |
| 上游 | `DataScope` 审计状态 `AUDIT / M2 PROVEN FOR 3 C1 CASES` |

**本审计不构成修复授权。等你对 §八 三项裁定后再进入设计。**
