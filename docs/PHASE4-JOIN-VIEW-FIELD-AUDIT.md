# 六个 JOIN View 字段必要性与暴露审计

> **性质**：只读审计（📐 **零代码改动 · 待用户裁定**）
> **基线**：`HEAD = origin = d5b75b7` · LEAD 0 · worktree CLEAN
> **上游**：`STEP14-STUDENT-DETAIL-IDOR-IMPL.md`（已 CLOSED）· 用户 2026-10-25 指定第 3 项
> **本批定位**：**逐字段**确认「是否查询 / 是否序列化 / 谁在用 / 移除影响什么」

---

## 一、结论摘要

| # | 发现 | 判定 |
| :-: | --- | --- |
| 1 | 6 个 View **结构完全一致**（同一代码模板生成）| 事实 |
| 2 | 6 个 XML **全部** `JOIN student` 且 SELECT `phone` / `id_card_no` / `email` / `name` | 🔴 已确认 |
| 3 | 6 个接口实测**全部返回 `studentPhone` / `studentEmail`** | 🔴 已确认 |
| 4 | `studentIdCardNo` 在 **6/6 个 View** 都有 `@JsonIgnore`，实测**均不返回** | ✅ 保护有效 |
| 5 | **新 Vue 对 5 个 `student*` 字段：非注释引用 = 0** | ✅ 完全不用 |
| 6 | **旧 Admin 有 4 个模块使用**，其中 3 处是**导出 Excel 的列配置** | 🔴 **不能用「前端不显示」推断无用途** |
| 7 | 🔴 **导出是纯前端实现**（`<download-excel>` + `json_fields`），数据来自 `/page` | 移除即影响导出列 |
| 8 | 🔴 **导出配置里含 `studentIdCardNo`，但因 `@JsonIgnore` 该列实际为空** | 建议清理配置 |

> **最重要的结论**：
> 「新 Vue 不渲染」**不能**推出「可以安全移除」——
> **旧 Admin 的导出功能确实依赖 `studentPhone` / `studentEmail`**。
> 且同一个 `/page` 接口**无法按调用方区分字段**（新 Vue 与旧 Admin 共用）。

---

## 二、六个 View 的结构一致性

### 2.1 SQL 层（`Base_Column_List`）

| View | 对应 XML | `JOIN student` | `student.phone` | `student.id_card_no` | `student.email` | `student.name` |
| --- | --- | :-: | :-: | :-: | :-: | :-: |
| `AssessmentRecordView` | `AssessmentRecordDao` | ✅ 1 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 |
| `AssessmentAnswerView` | `AssessmentAnswerDao` | ✅ 1 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 |
| `AssessmentWrongQuestionView` | `AssessmentWrongQuestionDao` | ✅ 1 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 |
| `CounselingAppointmentView` | `CounselingAppointmentDao` | ✅ 1 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 |
| `CounselorFavoriteView` | `CounselorFavoriteDao` | ✅ 1 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 |
| `CounselorMessageView` | `CounselorMessageDao` | ✅ 1 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 |

> 「2 次」= `Base_Column_List` 中 1 次 + 查询条件 `<if>` 中 1 次。
> ⇒ **六张表全部在 SQL 层取出了学生敏感字段**，与是否展示无关。

### 2.2 序列化层（`@JsonIgnore`）

| View | `@JsonIgnore` 保护的 getter |
| --- | --- |
| `AssessmentRecordView` | **`getStudentIdCardNo()`** |
| `AssessmentAnswerView` | **`getStudentIdCardNo()`** |
| `AssessmentWrongQuestionView` | **`getStudentIdCardNo()`** |
| `CounselingAppointmentView` | **`getStudentIdCardNo()`** |
| `CounselorFavoriteView` | **`getStudentIdCardNo()`** |
| `CounselorMessageView` | **`getStudentIdCardNo()`** |

⇒ **6/6 一致**：只挡了身份证号，**未挡 phone / email**。

---

## 三、逐接口实测（admin 身份，`/page?limit=1`）

| 接口 | 返回的 `student*` 字段 | phone | email | idCardNo |
| --- | --- | :-: | :-: | :-: |
| `/assessmentRecord/page` | `studentId, studentName, studentPhone, studentAvatarUrl, studentEmail` | 🔴 YES | 🔴 YES | ✅ no |
| `/assessmentAnswer/page` | 同上 + `studentAnswer` | 🔴 YES | 🔴 YES | ✅ no |
| `/assessmentWrongQuestion/page` | 同上 + `studentAnswer` | 🔴 YES | 🔴 YES | ✅ no |
| `/counselingAppointment/page` | `studentId, studentName, studentPhone, studentAvatarUrl, studentEmail` | 🔴 YES | 🔴 YES | ✅ no |
| `/counselorFavorite/page` | 同上 | 🔴 YES | 🔴 YES | ✅ no |
| `/counselorMessage/page` | 同上 | 🔴 YES | 🔴 YES | ✅ no |

⇒ **`studentIdCardNo` 在 6 个接口全部被成功阻断**（`@JsonIgnore` 真实生效，非仅代码存在）。

---

## 四、使用方矩阵（关键：区分「页面」与「导出」）

### 4.1 新 Vue（`frontend/src`）

| 字段 | 非注释引用 |
| --- | :-: |
| `studentPhone` | **0** |
| `studentEmail` | **0** |
| `studentIdCardNo` | **0** |
| `studentName` | **0** |
| `studentAvatarUrl` | **0** |

⇒ 新前端**完全不使用**这些字段（`assessment.ts` / `message.ts` 中仅有「不渲染」的注释）。

### 4.2 旧 Admin（`admin/src`）—— 🔴 有真实用途

| 字段 | 引用数 | 使用位置 |
| --- | :-: | --- |
| `studentName` | 61 | 多个模块（列表/表单/导出）|
| `studentPhone` | **8** | **`examrecord/add-or-update.vue`（5）· `xinlilaoshiCollection/list.vue`（1）· `xinlilaoshiLiuyan/list.vue`（1）· `xinlilaoshiOrder/list.vue`（1）** |
| `studentEmail` | **8** | 同上 4 个文件 |
| `studentIdCardNo` | 8 | 同上（**导出列配置**）|
| `studentAvatarUrl` | 7 | 同上 |

#### 具体用法（逐处核实）

**① 三个 `list.vue` —— 导出 Excel 的列映射**

```javascript
// xinlilaoshiOrder/list.vue:330-337
//导出excel
json_fields: {
  //级联表字段
  '学生姓名': 'studentName',
  '学生手机号': 'studentPhone',        // ← 导出列
  '学生身份证号': 'studentIdCardNo',   // ← 导出列（但实际为空，见 §五）
  '学生头像': 'studentAvatarUrl',
  '电子邮箱': 'studentEmail',          // ← 导出列
  ...
}
```

同样的 `json_fields` 出现在 `xinlilaoshiCollection/list.vue:224-227`、`xinlilaoshiLiuyan/list.vue:253-256`。

**② `examrecord/add-or-update.vue` —— 表单字段（只读展示）**

```html
<el-form-item label="手机号" prop="studentPhone">
  <el-input v-model="ruleForm.studentPhone" :readonly="ro.studentPhone" readonly>
```

⚠️ **但 `AssessmentRecordEntity` 并**没有** `studentPhone` 字段**
⇒ 该表单既不写入、也无实际绑定来源，属**模板残留**（详见 §六 判定）。

---

## 五、🔴 新发现：导出是独立外泄面

| 项 | 事实 |
| --- | --- |
| 导出实现 | **纯前端** `<download-excel>` + `json_fields`（后端无导出端点）|
| 数据来源 | 即 `/page` 返回的 `data.list` |
| 导出内容 | **含学生手机号、电子邮箱**（真实有值）|
| 身份证号列 | **配置存在，但因 `@JsonIgnore` 值为 `undefined` ⇒ 导出为空** |
| 受影响模块 | `xinlilaoshiCollection` · `xinlilaoshiLiuyan` · `xinlilaoshiOrder` |

### 5.1 两个必须区分的结论

```text
✅ 身份证号：已由 @JsonIgnore 阻断，导出列实际为空  → 无外泄（但配置误导）
🔴 手机/邮箱：导出真实包含，Excel 可传播        → 比页面显示更严重
```

### 5.2 补充：导出面同时包含**咨询师侧**联系方式

同样 3 个 `list.vue` 的 `json_fields` 里还有：

```javascript
'心理老师手机号': 'counselorPhone',
'电子邮箱':       'counselorEmail',
```

| 文件 | 学生侧 | 咨询师侧 |
| --- | :-: | :-: |
| `xinlilaoshiCollection/list.vue` | L224/227 | **L229/231** |
| `xinlilaoshiLiuyan/list.vue` | L253/256 | **L258/260** |
| `xinlilaoshiOrder/list.vue` | L334/337 | **L339/341** |

⇒ **导出的是「学生 + 咨询师」双方联系方式**。本批只审计了学生侧，
但**咨询师侧同类字段的暴露面同样存在**（已登记为后续项）。

### 5.3 由此产生的关键约束

```text
同一个 /page 接口同时服务「新 Vue（不用这些字段）」与「旧 Admin（导出用这些字段）」
                ↓
接口级字段无法按调用方区分
                ↓
移除 = 旧 Admin 导出缺列；保留 = 新 Vue 仍收得到（可被抓包）
```

---

## 六、逐字段决策表

| 字段 | 6 接口返回 | 新 Vue | 旧 Admin | 导出用 | 建议 | 理由 |
| --- | :-: | :-: | :-: | :-: | --- | --- |
| `studentId` | ✅ | 0 | 关联键 | — | **保留** | 业务主键，列表关联必需 |
| `studentName` | ✅ | 0 | **61 处** | ✅ | **保留** | 无法在不展示姓名的情况下提供业务列表 |
| `studentAvatarUrl` | ✅ | 0 | 7 处 | ✅ | 🟡 **暂缓** | 敏感度低，删除收益小；需确认头像是否有业务必要 |
| **`studentPhone`** | ✅ | **0** | 8 处 | **✅** | 🟡 **待裁定** | 需先定「导出是否需要手机号」；不是技术问题 |
| **`studentEmail`** | ✅ | **0** | 8 处 | **✅** | 🟡 **待裁定** | 同上 |
| `studentIdCardNo` | ❌ **不返回** | 0 | 8 处 | 列为空 | ✅ **已阻断** + 🔴 **建议清理导出配置** | 配置里保留该列会误导，且一旦 `@JsonIgnore` 被去掉就泄露 |

### 6.1 三类结论的划分（按你的要求）

| 类别 | 字段 | 说明 |
| --- | --- | --- |
| **保留并说明必要性** | `studentId` · `studentName` | 列表关联与识别所需 |
| **移除并列出影响面** | （暂无可直接判定）| 需先裁定 phone/email 业务必要性 |
| **证据不足暂缓** | `studentPhone` · `studentEmail` · `studentAvatarUrl` | 页面层不用，但**导出层在用** ⇒ 不能仅凭页面判定 |

---

## 七、若移除 `studentPhone` / `studentEmail`，影响面清单

| # | 影响 | 严重度 |
| :-: | --- | :-: |
| 1 | 旧 Admin **3 个模块导出**丢失「学生手机号」「电子邮箱」列 | 🟠 功能降级 |
| 2 | 旧 Admin `examrecord/add-or-update.vue` 手机号/邮箱表单项变空 | 🟡 该表单本就是残留（Entity 无此字段）|
| 3 | 6 个 XML 的 `Base_Column_List` 需同步移除（否则仍查询但不映射）| 🟡 性能微降 |
| 4 | `@JsonIgnore` 之外新增「字段不存在」的保护 | ✅ 收益 |
| 5 | 新 Vue **无影响**（本来不用）| ✅ |
| 6 | 现有测试 **无直接影响**（无测试断言这些字段）| ✅ 已核查 |

> ⚠️ 第 3 项需注意：若只删 View 字段而保留 SQL 列，MyBatis 会忽略未映射的列（不报错），
> 但会**继续查询**这些列 ⇒ 应同时清理 SQL，否则属于「不彻底的移除」。

---

## 八、待裁定（3 项）

| # | 问题 | 选项 | 备注 |
| :-: | --- | --- | --- |
| **1** | `studentPhone` / `studentEmail` 在 6 个接口中的去留 | A. **全部保留**（含导出）<br>B. **全部移除**（同步改 3 个 `json_fields`）<br>C. 保留但**导出列移除**（页面字段仍返回）<br>D. 保留但**脱敏**（如 `177****6901`）| 需业务判断「导出是否需要联系方式」|
| **2** | `studentIdCardNo` 的导出配置 | A. 从 3 个 `json_fields` 中**删除该列**<br>B. 保留（反正为空）| 建议 **A**（避免误导 + 防未来回归）|
| **3** | `studentAvatarUrl` | A. 保留<br>B. 移除 | 敏感度低，可最后处理 |

> **不建议**批量一次改 6 个 View —— 你的要求是「逐接口确认后分批」。

---

## 九、审计边界与未确认项

| 项 | 状态 |
| --- | :-: |
| 6 个 XML 的 SQL 字段 | ✅ 已核对 |
| 6 个 View 的 `@JsonIgnore` | ✅ 已核对（均为 `getStudentIdCardNo`）|
| 6 个接口的实际序列化输出 | ✅ 实测（admin）|
| 新 Vue 使用情况 | ✅ 已核对（0 引用）|
| 旧 Admin 源码使用情况 | ✅ 已核对（4 模块）|
| **旧 Admin `dist`（编译产物）** | 🟡 **未逐个核对**（与 src 可能不同步，见 STEp14 审计 §2.2）|
| **外部客户端 / 移动端 / 脚本** | ❌ **未覆盖** |
| **导出功能是否实际被使用** | ❌ **无法确认**（需产品/运营侧信息）|
| `counselorPhone` / `counselorEmail` 是否在导出配置 | ✅ **已确认存在**（同样 3 个 `list.vue`，见 §5.2）|
| `counselorPhone` / `counselorEmail` 的接口返回与角色暴露面 | 🟡 **本批未审计**（仅确认导出配置存在）|

---

## 十、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `d5b75b7` / `d5b75b7` · LEAD 0 |
| worktree | **CLEAN** |
| 代码 / DB / 前端 / pom | **0 改动** |
| 实测方式 | HTTP 只读 + 文件检索 + 代码阅读，**无写入、无残留** |
| 文档 | 新增本文件 |

**未写任何代码，等你裁定 §八 的 3 项。**
