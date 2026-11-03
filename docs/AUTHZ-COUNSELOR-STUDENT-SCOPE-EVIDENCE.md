# `AUTHZ-COUNSELOR-STUDENT-SCOPE` —— 三项证据核查 + 修复设计要素

> **性质**：🔍 三项证据核查（已完成）+ 🛠 修复设计（**已实施并推送**）
> **基线**：`HEAD = origin = 36bb760` · LEAD 0 · worktree CLEAN
> **上游**：`AUTHZ-COUNSELOR-STUDENT-SCOPE-AUDIT.md`（审计通过）+ 用户要求补充 3 项证据
> **本批状态**：🏁 **`AUTHZ-COUNSELOR-STUDENT-SCOPE` = CLOSED（`36bb760`）**
>
> **⚠️ 两个结论必须分开写**
>
> ```text
> ✅ 可以写：Student 咨询师范围修复已通过【行为回归测试】
>            （修复前 12/7 失败 → 修复后 12/12 → 全量 169/169）
>
> ❌ 不可以写：Student DataScope M2 已完成【变异验证】
>            —— T13（删除 StudentDao 授权条件 → 测试变红）尚未执行
> ```
>
> T13 属**审计强化证据**，非本次修复正确性的必要条件；已转入 DataScope 审计批次挂账，
> **不重新打开本 CLOSED 批次**。

---

## 一、结论摘要

| # | 用户要求的核查 | 结果 | 对设计的影响 |
| :-: | --- | --- | --- |
| 1 | 审核页是否需要 Student 详情 | ✅ **不需要** —— 列表已含 `studentName` 列 | 🔴 **推翻我此前的「建议 C」** |
| 2 | 空下拉是否影响已有记录 | ✅ **不会误清空** —— 无 `clearable`、非管理员禁用、值来自回填 | 影响仅为**显示降级** |
| 3 | 待审核能否作为访问依据 / 撤销后是否失效 | ✅ **不需要**纳入 `status=1`；撤销天然失效 | 规则可统一为 `status = 2` |

> ### 🔴 必须记录的自我更正
>
> 我在审计报告中建议 **「C：列表=A（status=2）/ 详情=B（任意预约）」**，
> 理由是「审核可能需要查看申请人详情」。
> **该假设未经核实即为建议依据，属方法论错误**。本次核查证明：
> **审核页面完全不调用 Student 详情接口**，因此**没有任何理由**把 `status = 1` 纳入详情访问依据。
>
> ⇒ **修正后的建议：四个入口统一按 `status = 2` 收窄。**
> 用户在上一轮明确指出「不能仅凭『审核可能需要』就认定详情必须放宽」—— 该提醒正确，本文件据此更正。

---

## 二、核查 1：审核页是否需要 Student 详情

### 2.1 审核页的字段来源（`xinlilaoshiOrder/list.vue`）

表格列（`el-table-column` 的 `prop`）：

```text
studentName · counselorName · appointmentNo · appointmentDate
timeSlot · status · reviewComment · insertTime
```

- **`studentName` 已由预约列表自带**（`CounselingAppointmentView.studentName`，Step 15 明确保留）；
- 审核对话框只有两项：`status`（通过/拒绝）+ `reviewComment`；
- 该页面**未出现任何 `student/info` 调用**（全仓检索确认：只有 `add-or-update.vue` 调用）。

### 2.2 结论

```text
审核流程所需信息 = 预约行自身字段（含 studentName）
                  + 审核结论
⇒ 不需要 Student 详情接口
⇒ 「待审核」不构成访问申请人详情的业务依据
```

> 附带：该菜单对咨询师只授予「审核 / 查看」，**无「新增 / 修改」**
> ⇒ 咨询师不会进入 `add-or-update.vue`（唯一会调用 `student/page` 与 `student/info` 的组件）。

---

## 三、核查 2：旧 Admin 表单下拉为空的影响

### 3.1 三个表单的实际绑定与提交语义

| 表单 | 学生下拉 | 非管理员是否禁用 | `studentId` 回填 | 提交 | `clearable` |
| --- | --- | :-: | --- | --- | :-: |
| `xinlilaoshiLiuyan/add-or-update.vue` | `v-model="ruleForm.studentId"` | ✅ **禁用**（`if (role != "管理员") ro.studentId = true`）| `_this.ruleForm = data.data` | `data: this.ruleForm` | ❌ **无** |
| `examrecord/add-or-update.vue` | 同上 | ❌ 未按角色禁用（仅 `type=='cross'` 时禁用）| 同上 | 同上 | ❌ 无 |
| `examrewrongquestion/add-or-update.vue` | 同上 | ❌ 同上 | 同上 | 同上 | ❌ 无 |

**关键事实**：

1. **`clearable` 全模块 0 处** ⇒ 下拉没有清除按钮，**用户无法通过 UI 清空 `studentId`**；
2. Element `el-select` 的 `v-model` 值**不依赖选项列表是否存在** —— 选项为空时值仍保留（仅显示为原始 id 或空白）；
3. 提交体是 `this.ruleForm`，其中 `studentId` 来自回填 ⇒ **不会被清空**；
4. 编辑态下 `yonghuChange(studentId)` 会调用 `student/info/{id}` 填充**辅助显示字段** `yonghuForm.studentName`；
   该调用失败（未来 403）时，仅 `studentName` 显示为空，**不影响 `ruleForm` 与提交**。

### 3.2 结论

| 风险 | 判定 |
| --- | :-: |
| 下拉为空导致**误清空 `studentId`** | ❌ **不成立**（无 `clearable`、值来自回填、提交体与选项无关）|
| 下拉为空导致**无法提交** | ⚠️ `studentId` 有 `required` 校验，但回填值存在 ⇒ 正常通过 |
| 下拉为空导致**显示异常** | ✅ **成立**，但仅限「编辑态的学生姓名辅助字段」与「换学生能力」|

⇒ **允许为空是可接受的最小风险方案**，但需在设计中明确：**这是显示降级，不是数据破坏**。

> ⚠️ 另发现（与本批无关，仅登记）：下拉 `:label="item.studentName"` 而 `student/page` 返回的是 `name` 字段
> ⇒ **选项标签可能本来就是空白**（既有 UI 缺陷）。若成立，则「下拉为空」对现状**几乎没有额外影响**。

---

## 四、核查 3：权限粒度与撤销

### 4.1 预约状态语义

| 状态 | 含义 | 数据形态 |
| :-: | --- | --- |
| `1` | 待审核 | 记录存在 |
| `2` | 已批准 | 记录存在 |
| `3` | 已拒绝 | 记录存在（状态变更）|
| **取消** | —— | 🔴 **物理删除**（`removeAppointment` → `/counselingAppointment/delete`）|

### 4.2 撤销后的访问失效

| 场景 | 数据变化 | 若规则为 `EXISTS(status = 2)` |
| --- | --- | --- |
| 预约被**拒绝**（1 → 3）| 状态变 3 | ✅ **立即失效**（不再满足 `status = 2`）|
| 预约被**取消** | 记录**物理删除** | ✅ **立即失效**（EXISTS 找不到行）|
| 预约从 2 被改回 1（异常操作）| 状态变 1 | ✅ 立即失效 |

⇒ **规则基于「当前存在的 `status = 2` 行」，天然支持即时撤销**，无需额外的失效机制。

### 4.3 结论

```text
待审核（status = 1）不需要作为访问依据 —— 审核页不读 Student 详情
⇒ 四个入口可统一按 status = 2 收窄
```

---

## 五、逐入口授权矩阵（更新后）

> **修正**：原审计报告 §六 建议「列表=A / 详情=B」，现统一为 **A（仅 `status = 2`）**。

| 入口 | 授权语义 | 承担层 | 咨询师期望 | 现状 |
| --- | --- | --- | --- | --- |
| `/student/page` | 仅「已批准关系」学生 | `StudentDao.selectListView` 增加 `EXISTS(status=2)` + Controller 注入 `approvedStatus` | counselor1 → 1；counselor2/3 → **0** | 🔴 全量 3 |
| `/student/list` | 同上（同一条 SQL，不得旁路）| 同上 | 同上 | 🔴 全量 3 |
| `/student/info/{id}` | 仅「已批准关系」学生 | `OwnershipGuard` **关系回调**（新增 `CounselorStudentScope`）| c1 → 200（student1）；c2 → **403** | 🔴 放行 |
| `/student/detail/{id}` | 与 `info` **同一判定** | 同上 | 同上 | 🔴 放行 |
| **学生维度（4 入口）** | 本人 | 现有 `params.studentId` / `assertOwnership` | 不变 ✅ | 已正确 |
| **管理员** | 全量（B 类）| `DataScope` 首行 return | 不变 ✅ | 已正确 |

---

## 六、SQL / 关系检查方案（设计草案 · 未实施）

### 6.1 `StudentDao.xml` 新增条件（**仅选列式，不改既有条件**）

```xml
<!-- 建议位置：紧邻既有 params.studentId 条件之后 -->
<if test="params.counselorId != null and params.counselorId != ''">
    and exists (
        select 1
        from counseling_appointment ca
        where ca.student_id    = a.id
          and ca.counselor_id  = #{params.counselorId}
          and ca.status        = #{params.approvedStatus}
    )
</if>
```

- `a` 为 `student` 主表别名（与既有 `a.id = #{params.studentId}` 一致，**已验证**）
- `approvedStatus` 复用现有常量 `CounselorAssessmentScope.PARAM_APPROVED_STATUS`
  （**不新造第二处口径**，避免规则漂移）
- `page` 与 `list` 共用 `selectListView` ⇒ **一处改动覆盖两入口**，天然无旁路

### 6.2 `info` / `detail` 的关系回调

```java
// 复用 Step 13 已建立的模式（OwnershipGuard.assertOwnership 5 参重载）
OwnershipGuard.assertOwnership(request, yonghu, id.intValue(), null,
        new CounselorStudentScope(tokenService));   // 新增：判定「是否存在 status=2 预约」
```

- **两处必须使用同一回调实例语义** ⇒ 满足 Step 14 的「`info` 与 `detail` 行为一致」要求
- 回调内部**只查 `counseling_appointment`**，不读学生敏感字段

### 6.3 明确不做

```text
❌ 不新增端点
❌ 不改 counseling_appointment 的任何规则
❌ 不改前端（下拉为空属已知降级）
❌ 不改 DataScope 实现（契约缺口仍独立挂账）
❌ 不对 status = 1 / 3 放开
```

---

## 七、测试矩阵（设计草案）

| # | 角色 | 入口 | 期望 | 作用 |
| :-: | --- | --- | --- | --- |
| T1 | counselor1（status=2）| `page` | 1 条且 id=1 | 正面 |
| T2 | **counselor2（无 status=2）** | `page` | **0 条** | **核心：无权排除** |
| T3 | counselor3（仅 status=1）| `page` | **0 条** | **证明不含 status=1** |
| T4 | counselor1 | `info/1` | 200 | 正面 |
| T5 | **counselor2** | `info/1` | **403** | **核心** |
| T6 | counselor2 | `detail/1` | **403** | 与 T5 一致 |
| T7 | counselor1 | `detail/1` | 200，且与 `info` 码一致 | 一致性（Step 14 T7）|
| T8 | student1 | `page` | 1 条（本人）| 学生维度不回归 |
| T9 | student2 | `page` | 1 条（本人）| 同上 |
| T10 | student2 | `info/1` | 403 | 同上 |
| T11 | admin | `page` / `info/1` | 3 条 / 200 | 反向自检（防全局空）|
| T12 | 未认证 | 四入口 | 200 + code=401 | 契约不变 |
| **T13** | **变异实验** | 删除 6.1 的 `<if>` | T2/T3 **变红**（`expected 0 but was 3`）| AC6 三层 |
| T14 | 拒绝/取消后失效 | 将 appointment 置 3 / 删除 | counselor1 → **0 条 / 403** | 撤销语义 |

**防假通过**：T1/T4/T11 提供「有权侧 > 0」，确保 T2/T3/T5/T6 的 0/403 不是链路故障。

---

## 八、回滚方案

| 项 | 内容 |
| --- | --- |
| 改动面 | `StudentDao.xml`（+8 行左右）· `StudentController`（2 处传参）· 新增 1 个小类（关系回调）· 1 个测试类 |
| 回滚方式 | `git revert <commit>`（单提交，无 DB 迁移、无前端、无依赖变更）|
| 回滚后行为 | 回到「咨询师可见全部学生」= 当前基线 |
| 数据影响 | **无**（纯读路径收窄，不写任何表）|
| 变异实验残留 | 实验中临时改动的 XML **必须 `git checkout` 还原**，不进入提交 |

---

## 九、待你审批（4 项）

| # | 问题 | 我的建议 |
| :-: | --- | --- |
| **1** | 四入口是否统一按 `status = 2`（**不再区分列表/详情**）| **是** —— 依据核查 1，`status=1` 无业务依据 |
| **2** | 表单下拉允许为空 | **是** —— 无 `clearable`、值来自回填、仅显示降级 |
| **3** | `info` / `detail` 是否共用同一关系回调 | **是** —— 满足 Step 14 一致性要求 |
| **4** | 是否批准进入实现（含 T13 变异实验）| 等你决定 |

---

## 十、状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `9a1cb70` / `9a1cb70` · LEAD 0 |
| worktree | **CLEAN**（零代码改动）|
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** ✓ |
| 实测 | 仅代码阅读 + 只读检索，**未改任何文件** |
| 服务 | 后端 `8080` PID `31608` · 前端 `5173` |
| 上游挂账 | `DataScope` 整体契约未闭环 · C3 待业务确认 · Multi-Tool 暂停 |

**本文档不构成修复授权。等你审批 §九 后，再进入实现。**
