# 六个 JOIN View 学生敏感字段移除 —— 实现报告

> **性质**：实现（🏁 **CLOSED · 用户 2026-10-25 验收通过**）
> **基线**：`HEAD = origin = d6db0b6` · LEAD 0 · worktree CLEAN · 改动 **16 文件 −342 / +4**
>
> **CLOSED 依据（8 项）**：① 16 文件范围符合批准 ② `AssessmentTools` 仅 javadoc 变动
> ③ 编译 0 错 + 131/131 ④ 已推送且 `HEAD = origin` · LEAD 0 · worktree CLEAN
> ⑤ `counselorPhone`/`counselorEmail` 实际增删行 = 0 ⑥ DB / 权限 / SQL 查询条件未改
> ⑦ 注释表述与当前字段状态一致 ⑧ 统计自洽（−312 / −18 / −9 / +4−3 = 16 文件 +4/−342）
>
> **⚠️ 审查边界声明**：用户明确其结论为「**基于提交方提供的证据通过审查**」，
> **未独立执行 git 命令、未重新运行测试**。本文档中所有数字均由提交方实测产出。
>
> **关闭范围严格限定**：仅 ① 六个 JOIN View 三字段移除 ② 三个旧 Admin 导出配置清理
> ③ 配套 javadoc 修正。**不代表**敏感信息治理完成，**不代表**所有学生数据接口已满足同一授权规则。
> **上游**：`PHASE4-JOIN-VIEW-FIELD-CHANGELIST.md` v2（用户 2026-10-25 批准实施）
> **本批定位**：把 `studentPhone` / `studentEmail` / `studentIdCardNo` 三个学生字段
> 从六个 JOIN View 的**响应**与**查询投影**中移除
>
> **📌 提交前追加 1 项（用户裁定 A）**：`AssessmentTools` 中一条 javadoc 原列举
> `studentName / studentPhone / studentIdCardNo / studentEmail` 为该路径带出的字段，
> 本批删除后该枚举**已与事实不符** ⇒ 裁定「一并修正，不为保持纯删除而保留错误说明」。
> 改动 **仅 javadoc（+4/−3）**，`AssessmentTools` 运行时代码 **零改动**。
> ⇒ 最终 **16 文件 / +4 / −342**（4 行新增全部为注释）

---

## 一、结论

| 项 | 结果 |
| --- | :-: |
| 改动规模 | **15 文件 · +0 / −339**（**纯删除，零新增**）|
| 编译（V3）| ✅ 0 错误 |
| 全量测试（V4）| ✅ **131/131** |
| 部署 | ✅ PID `13656` · jar `20:02:10` · 9s 就绪 |
| 六接口字段（V1/V9）| ✅ 三字段**全部不再返回** |
| 基础字段保留（V2）| ✅ `studentId` / `studentName` / `studentAvatarUrl` 全部仍在 |
| 筛选真实生效（V5）| ✅ **真实值命中 1 条 / 不存在值 0 条** |
| 排序（V6）| ✅ 正常 |
| 无残留引用（V11）| ✅ View / XML 均无 |
| 导出配置（V7/V10）| ✅ 语法正确、无重复 key、咨询师字段完整 |
| **咨询师字段** | ✅ **零改动**（已精确验证：无任何 `+/-` 行）|

---

## 二、改动清单

| 类别 | 文件数 | 行数 |
| --- | :-: | :-: |
| **View**（删 3 字段 + getter + setter）| 6 | −312 |
| **XML**（删 3 行 `Base_Column_List`）| 6 | −18 |
| **旧 Admin `json_fields`**（删 3 列配置 × 3）| 3 | −9 |
| **合计** | **15** | **−339 / +0** |

### 2.1 六个 View

| View | 删除内容 |
| --- | --- |
| `AssessmentRecordView`<br>`AssessmentAnswerView`<br>`AssessmentWrongQuestionView`<br>`CounselingAppointmentView`<br>`CounselorFavoriteView`<br>`CounselorMessageView` | `studentPhone` · `studentEmail` · `studentIdCardNo` 的<br>**字段声明 + javadoc + getter + setter**（含 `studentIdCardNo` 上的 `@JsonIgnore` getter）|

**保留**：`studentId` · `studentName` · `studentAvatarUrl`

### 2.2 六个 XML

每个 `Base_Column_List` 删除 3 行，保留 `student.name` 与 `student.avatar_url`：

```diff
-        ,student.phone as studentPhone
-        ,student.id_card_no as studentIdCardNo
         ,student.avatar_url as studentAvatarUrl
-        ,student.email as studentEmail
```

**保留未动**（用户裁定 A）：

```xml
and student.phone like CONCAT('%',#{params.studentPhone},'%')
and student.id_card_no like CONCAT('%',#{params.studentIdCardNo},'%')
and student.email like CONCAT('%',#{params.studentEmail},'%')
```

### 2.3 三个旧 Admin `list.vue`

```diff
  '学生姓名': 'studentName',
- '学生手机号': 'studentPhone',
- '学生身份证号': 'studentIdCardNo',
  '学生头像': 'studentAvatarUrl',
- '电子邮箱': 'studentEmail',
  '心理老师姓名': 'counselorName',
  '心理老师手机号': 'counselorPhone',
  '心理老师头像': 'counselorAvatarUrl',
  '电子邮箱': 'counselorEmail',      ← 保留（本批不动咨询师字段）
```

---

## 三、验收逐项（V1–V11）

### 3.1 V1 / V9：六接口不再返回三字段（实测）

| 接口 | `studentPhone` | `studentEmail` | `studentIdCardNo` | 返回的 `student*` |
| --- | :-: | :-: | :-: | --- |
| `assessmentRecord` | ✅ no | ✅ no | ✅ no | `studentId, studentName, studentAvatarUrl` |
| `assessmentAnswer` | ✅ no | ✅ no | ✅ no | 同上 + `studentAnswer` |
| `assessmentWrongQuestion` | ✅ no | ✅ no | ✅ no | 同上 + `studentAnswer` |
| `counselingAppointment` | ✅ no | ✅ no | ✅ no | `studentId, studentName, studentAvatarUrl` |
| `counselorFavorite` | ✅ no | ✅ no | ✅ no | 同上 |
| `counselorMessage` | ✅ no | ✅ no | ✅ no | 同上 |

### 3.2 V2：基础字段未被误删

六个接口均仍返回 `studentId` / `studentName` / `studentAvatarUrl` ✅

### 3.3 V5：筛选真实生效（**含不存在值对照**）

| 筛选条件 | 返回 | 命中 `studentId` |
| --- | :-: | :-: |
| `studentPhone=17703786901`（**真实**）| **1 条** / total=1 | **1** |
| `studentPhone=19900000000`（**不存在**）| **0 条** / total=0 | — |
| `studentIdCardNo=410224199610232001`（**真实**）| **1 条** | **1** |
| `studentIdCardNo=999999999999999999`（**不存在**）| **0 条** | — |
| `studentEmail=1@qq.com`（**真实**）| **1 条** | **1** |
| `studentEmail=notexist@x.com`（**不存在**）| **0 条** | — |

⇒ **筛选功能未被删除 SELECT 列破坏**，且**真实命中正确**（不是只看 HTTP 200）。

### 3.4 V6：排序正常

`orderBy=id` → 1 条 / total=1 ✅

### 3.5 V11：无残留引用

| 检查 | 结果 |
| --- | :-: |
| 六个 View 中三字段 | ✅ **0 残留** |
| 六个 XML 的 `as studentPhone/studentEmail/studentIdCardNo` | ✅ **0 残留** |
| 其他 Java（除 View）| ✅ 仅 `AssessmentTools.java:37` 的**注释**（说明性，内容仍准确）|
| 反射 / 序列化配置 | ✅ 无（无 `@JsonInclude` 等引用这些字段）|

### 3.6 V7 / V10：导出配置

| 文件 | 学生字段残留 | 重复 key | 咨询师字段 |
| --- | :-: | :-: | :-: |
| `xinlilaoshiCollection` | 0 | **无** | ✅ 完整 |
| `xinlilaoshiLiuyan` | 0 | **无** | ✅ 完整 |
| `xinlilaoshiOrder` | 0 | **无** | ✅ 完整 |

> **重要**：删除 `'电子邮箱': 'studentEmail'` 后，`'电子邮箱': 'counselorEmail'` 成为**唯一 key**
> ⇒ 消除了原有的**重复 key 覆盖**问题。

#### V10 的诚实边界

| 项 | 状态 |
| --- | :-: |
| `json_fields` 语法正确、无重复 key | ✅ **静态验证通过** |
| 其余导出列完整（学生姓名/头像、咨询师全套、业务列）| ✅ **静态核对通过** |
| **实际运行浏览器导出一次** | ❌ **未执行**（无自动化浏览器环境）|

> **建议人工抽查**：登录旧 Admin → 收藏/留言/预约列表 → 点「导出」→ 确认
> ① 文件能正常生成 ② 不含「学生手机号」「学生身份证号」「学生邮箱」列 ③ 其余列正常。

---

## 四、兼容性变化（已获批准）

| # | 变化 | 严重度 |
| :-: | --- | :-: |
| 1 | 旧 Admin 3 模块导出**少「学生手机号」列** | 🟠 **真实降级**（原本有值）|
| 2 | 导出**少「学生身份证号」列** | 🟡 无感（原本为空）|
| 3 | 导出「电子邮箱」列 | ✅ **实际无变化**（原本导出的就是咨询师邮箱）|
| 4 | 新 Vue | ✅ 无影响（0 引用）|
| 5 | 六接口响应体 | ✅ 目标达成 |
| 6 | 筛选 / 排序 | ✅ **未受影响**（实测）|
| 7 | 内存中不再持有学生身份证号 | ✅ **新增安全收益** |

---

## 五、📌 `AUTHZ-FILTER-PROBE` 仍挂账（本批不解决）

### 5.1 V5 的实测恰好**证实了该风险真实存在**

```text
?studentPhone=17703786901        → 1 条   （存在）
?studentPhone=19900000000        → 0 条   （不存在）
?studentIdCardNo=410224...001    → 1 条
?studentIdCardNo=999999...999    → 0 条
```

⇒ **任何已登录用户都能通过返回条数/`total` 精确判断「某手机号 / 身份证号 / 邮箱是否有学生记录」**。

### 5.2 事实分级（按用户要求）

| 层级 | 内容 | 状态 |
| --- | --- | :-: |
| **已确认** | 筛选条件可由请求任意传入（`<if>` 直接取 `params`）| ✅ 实测 |
| **已确认** | 通过「返回条数 / `total`」可判断值是否存在 | ✅ **本轮 V5 实测** |
| **待验证** | 不同角色是否受行级授权约束（如 counselor 能否用筛选探测无权学生）| ❌ **未验证** |
| **待验证** | 分页 `total`、错误差异是否泄露更多信息 | ❌ **未验证** |
| **待验证** | 是否应限制筛选字段本身（而非只限制返回行）| ❌ **未设计** |

> **不得**把「`DataScope` 约束返回行」当作「筛选不存在信息泄露」——
> 本轮实测已证明筛选本身可做存在性探测。

---

## 六、不做（严格）

| 不做 | 确认 |
| --- | :-: |
| 删除数据库字段 | ✅ 未动 |
| 修改 `student` 个人资料接口（`MeView` 需 phone/email）| ✅ 未动 |
| **修改 `counselorPhone` / `counselorEmail`** | ✅ **零改动**（无任何 `+/-` 行）|
| 修改 XML `<if>` 筛选条件 | ✅ 未动 |
| 修改 `order by a.${params.orderBy}` | ✅ 未动 |
| 清理旧 Admin `add-or-update.vue` 表单项残留 | ✅ 未动（独立议题）|
| 修改 `StudentEntity` / `StudentController` / `OwnershipGuard` / Service / Dao | ✅ 未动 |
| 扩大其他 View / 其他表 | ✅ 未动 |

---

## 七、Staged Review

```text
M  src/main/java/com/entity/view/AssessmentAnswerView.java
M  src/main/java/com/entity/view/AssessmentRecordView.java
M  src/main/java/com/entity/view/AssessmentWrongQuestionView.java
M  src/main/java/com/entity/view/CounselingAppointmentView.java
M  src/main/java/com/entity/view/CounselorFavoriteView.java
M  src/main/java/com/entity/view/CounselorMessageView.java
M  src/main/resources/admin/admin/src/views/modules/xinlilaoshiCollection/list.vue
M  src/main/resources/admin/admin/src/views/modules/xinlilaoshiLiuyan/list.vue
M  src/main/resources/admin/admin/src/views/modules/xinlilaoshiOrder/list.vue
M  src/main/resources/mapper/AssessmentAnswerDao.xml
M  src/main/resources/mapper/AssessmentRecordDao.xml
M  src/main/resources/mapper/AssessmentWrongQuestionDao.xml
M  src/main/resources/mapper/CounselingAppointmentDao.xml
M  src/main/resources/mapper/CounselorFavoriteDao.xml
M  src/main/resources/mapper/CounselorMessageDao.xml

15 files changed, 339 deletions(-)
check: OK · 未暂存/未跟踪: 0
零新增（+0）
```

| 项 | 值 |
| --- | --- |
| HEAD / origin | `d5b75b7` / `d5b75b7` · LEAD 0 |
| commit | ❌ **未提交** |
| 服务 | 后端 `8080` PID `13656`（jar `20:02:10`）· 前端 `5173` |
| DB | 未改动 |

---

## 八、建议 commit message

```text
refactor(security/step15): 六个 JOIN View 移除学生手机号/邮箱/身份证号字段

- 6 个 View 删除 studentPhone / studentEmail / studentIdCardNo 及 getter/setter
- 6 个 XML 的 Base_Column_List 删除对应 3 个 SELECT 列（WHERE 筛选保留）
- 旧 Admin 3 个 list.vue 删除对应导出列；消除 '电子邮箱' 重复 key
- 咨询师侧字段零改动；未动 DB / 学生资料接口 / 权限逻辑
- 测试 131/131；编译 0 错误；六接口实测三字段均不再返回
- 筛选仍真实生效（真实值命中 1 条 / 不存在值 0 条），排序正常
- 兼容性变化：旧 Admin 3 模块导出少「学生手机号」列（已批准）
- AUTHZ-FILTER-PROBE 挂账：筛选仍可做存在性探测（本轮实测证实）
```

---

## 九、下一步

| 待办 | 状态 |
| --- | --- |
| staged review → 用户复核 → commit → push | 等用户 |
| **V10 人工抽查旧 Admin 导出** | 建议执行（无自动化环境）|
| `AUTHZ-FILTER-PROBE` 筛选权限核实 | 🟡 挂账 |
| `student` 字段分类 + 行级授权矩阵 | 🟡 待裁定（第 2 项 D）|
| 六个 View 之外的其他表同类问题 | 🟡 挂账 |
| Phase 4 多 Tool | ⏸ **未启动** |
