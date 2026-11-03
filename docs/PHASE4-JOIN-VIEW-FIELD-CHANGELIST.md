# 六个 JOIN View 字段移除 —— 变更清单 v2（待批准 · 未实施）

> **性质**：变更清单 v2（📐 **零代码改动 · 待用户批准后再实施**）
> **基线**：`HEAD = origin = d5b75b7` · LEAD 0 · worktree CLEAN
> **上游**：`PHASE4-JOIN-VIEW-FIELD-AUDIT.md` · 用户 2026-10-25 裁定（含 v2 修正要求）
> **授权范围**：`studentPhone` / `studentEmail` / **`studentIdCardNo`** 三字段移出响应 · `studentAvatarUrl` 保留

---

## 一、两处修正（相对 v1 清单）

### 1.1 修正一：**学生邮箱从未被导出**（v1 结论有误）

`json_fields` 中 `'电子邮箱'` **key 出现两次**，`<download-excel :fields="json_fields">` 直接用对象字面量
⇒ 按 JS 语义**后者覆盖前者** ⇒ 最终只有 `'电子邮箱': 'counselorEmail'`。

| 字段 | 导出配置 | 实际导出 |
| --- | --- | --- |
| `studentPhone` | 有（key 唯一）| 🔴 **真实导出** |
| `studentEmail` | 有（**被覆盖**）| ❌ **从未导出** |
| `studentIdCardNo` | 有 | ❌ 空列（`@JsonIgnore`）|

> **表述纪律（用户要求）**：
> 删除「学生邮箱导出列」是清理**死配置**，**不得**描述为「导出功能降级」。

### 1.2 修正二：`studentIdCardNo` 的 SELECT 列 —— **v1 说「保留」，v2 改为「删除」**

**v1 的理由不成立**：
> ❌「已不返回，仅多查一列」—— 但 `@JsonIgnore` 解决的是**序列化**，不是**数据库查询**。
> 继续 SELECT 身份证号 = 继续把 PII 载入内存，无任何收益。

**用户的判断正确**：保留空字段不增加安全性。

---

## 二、只读核查结论（支撑删除 `studentIdCardNo`）

### 2.1 核查一：`studentIdCardNo` 是否被后端逻辑使用？

**结论：没有被任何业务逻辑使用。**

| 引用位置 | 类型 |
| --- | --- |
| 6 个 View 的字段声明 / getter / setter | 仅声明 |
| `AssessmentTools.java:37` | **注释**（解释为何 Tool 不走 `queryPage`）|

✅ **无 Controller / Service / Dao / 判断分支读取或写入该字段**。

### 2.2 核查二：XML 中的身份证号列是否仅用于填充该字段？

| XML 出现位置 | 用途 |
| --- | --- |
| `Base_Column_List` 的 `,student.id_card_no as studentIdCardNo` | **仅填充字段** |
| `<where>` 的 `and student.id_card_no like ...` | **筛选条件**（独立于 SELECT，不依赖别名）|

✅ SELECT 列**唯一用途**是填充那个无人使用的字段。

### 2.3 核查三：删除 SELECT 列是否影响映射 / 排序 / 筛选？

| 维度 | 结论 |
| --- | --- |
| **MyBatis 映射** | `resultType` 自动映射，缺列 → 字段为 `null`，**不报错** |
| **排序** | 6 个 XML 均为 `order by a.${params.orderBy} desc`，使用**主表别名 `a`**，与 `student` 别名无关 ⇒ **不受影响** |
| **筛选** | `<if>` 用 `student.id_card_no`（JOIN 表原列），**不依赖 SELECT 别名** ⇒ **筛选仍可用** |

### 2.4 前端依赖

| 端 | 引用 | 判定 |
| --- | --- | --- |
| **新 Vue** | **0**（非注释） | ✅ 无影响 |
| 旧 Admin `examrecord/add-or-update.vue` | 表单项 `prop="studentIdCardNo"`（`readonly`）| 🟡 值变为 `undefined` ⇒ 显示为空（**原本也是空**，因 `@JsonIgnore` 不返回）|
| 旧 Admin 3 个 `json_fields` | `'学生身份证号': 'studentIdCardNo'` | 🔴 **本批删除**（原为空列）|

> **结论**：删除 `studentIdCardNo` 的 SELECT 列 + View 字段 + 导出配置，
> **无业务功能损失**（原本就不返回、不显示、不导出有效值）。

---

## 三、变更清单（v2）

### A. 六个 View（删 **3** 个字段 × 6 = 18 处）

每个字段需删 **3 块**：字段声明、getter（含 `@JsonIgnore`）、setter。

| # | View | `studentPhone` | `studentEmail` | `studentIdCardNo` |
| :-: | --- | --- | --- | --- |
| 1 | `AssessmentRecordView` | L59 / L218-225 | L71 / L258-265 | L63 / L232-239 |
| 2 | `AssessmentAnswerView` | L67 / L253-260 | L79 / L293-300 | L71 / L267-274 |
| 3 | `AssessmentWrongQuestionView` | L93 / L370-377 | L105 / L410-417 | L97 / L384-391 |
| 4 | `CounselingAppointmentView` | L41 / L175-182 | L53 / L215-222 | L45 / L189-196 |
| 5 | `CounselorFavoriteView` | L37 / L159-166 | L49 / L199-206 | L41 / L173-180 |
| 6 | `CounselorMessageView` | L33 / L143-150 | L45 / L183-190 | L37 / L157-164 |

> ⚠️ **行号仅用于定位**：同一文件内多处删除会改变行号，
> 实施时按**内容匹配**删除（`private String studentPhone;` / `getStudentPhone()` 等），**不按行号顺序**。
>
> **保留**：`studentId` · `studentName` · `studentAvatarUrl`

### B. 六个 XML（删 **3** 行 `Base_Column_List`，**保留 WHERE 筛选**）

| # | XML | 删除行 |
| :-: | --- | --- |
| 1 | `AssessmentRecordDao` | L26 `,student.phone as studentPhone`<br>L27 `,student.id_card_no as studentIdCardNo`<br>L29 `,student.email as studentEmail` |
| 2 | `AssessmentAnswerDao` | L28 / L29 / L31 |
| 3 | `AssessmentWrongQuestionDao` | L33 / L34 / L36 |
| 4 | `CounselingAppointmentDao` | L24 / L25 / L27 |
| 5 | `CounselorFavoriteDao` | L20 / L21 / L23 |
| 6 | `CounselorMessageDao` | L22 / L23 / L25 |

#### B.1 明确**不动**的（筛选条件）

```xml
and student.phone like CONCAT('%',#{params.studentPhone},'%')
and student.id_card_no like CONCAT('%',#{params.studentIdCardNo},'%')
and student.email like CONCAT('%',#{params.studentEmail},'%')
```

**保留理由（用户裁定 A）**：本批只减少响应字段，不改变现有搜索行为。
**但必须登记**：见 §七「筛选权限待核实」。

### C. 旧 Admin 三个 `list.vue`（删 **3** 列 × 3 = 9 行）

| # | 文件 | 删除 | 保留 |
| :-: | --- | --- | --- |
| 1 | `xinlilaoshiCollection/list.vue` | L224 学生手机号 · L225 学生身份证号 · L227 学生邮箱 | **L231 `'电子邮箱': 'counselorEmail'`** |
| 2 | `xinlilaoshiLiuyan/list.vue` | L253 · L254 · L256 | **L260** |
| 3 | `xinlilaoshiOrder/list.vue` | L334 · L335 · L337 | **L341** |

> 删除 `'电子邮箱': 'studentEmail'` 后，`'电子邮箱': 'counselorEmail'` 成为唯一 key
> ⇒ **导出结果不变**（原本生效的就是它）。

---

## 四、真实兼容性影响

| # | 影响 | 严重度 | 说明 |
| :-: | --- | :-: | --- |
| 1 | 旧 Admin 3 模块导出**少「学生手机号」列** | 🟠 **真实降级** | 原本有真实值 |
| 2 | 导出**少「学生身份证号」列** | 🟡 无感 | 原本为空列 |
| 3 | 导出「电子邮箱」列 | ✅ **零变化** | 原本导出的是咨询师邮箱 |
| 4 | 新 Vue | ✅ **无影响** | 0 引用 |
| 5 | 六接口响应体 | ✅ **目标达成** | 三字段均不再返回 |
| 6 | 六接口**筛选能力** | 🟡 **保留** | `<if>` 未动 |
| 7 | 六接口**排序能力** | ✅ **不受影响** | `order by a.${orderBy}` 用主表别名 |
| 8 | 内存中不再持有身份证号 | ✅ **安全收益** | 这是 v2 相对 v1 的新增收益 |
| 9 | 现有测试 | ✅ **无断言依赖** | 已核查 |

---

## 五、验证计划

| # | 项 | 方法 |
| :-: | --- | --- |
| V1 | 六接口**不再返回** `studentPhone` / `studentEmail` / `studentIdCardNo` | HTTP 实测逐接口 |
| V2 | 六接口**仍返回** `studentId` / `studentName` / `studentAvatarUrl` | 同上（防误删）|
| V3 | `mvn compile` 0 错误（View 字段删除后无残留引用）| 编译 |
| V4 | `mvn test` → **131/131** | 全量 |
| V5 | **筛选仍可用**：`/page?studentPhone=17703786901` 仍能命中 | 实测 |
| V6 | 排序仍可用：`/page?orderBy=id` | 实测 |
| V7 | `json_fields` 语法正确、无重复 key、无残留逗号 | 静态检查 |
| V8 | 其余导出列仍在（`studentName` / `studentAvatarUrl` / `counselor*`）| 静态核对 |

---

## 六、不做（严格）

| 不做 | 原因 |
| --- | --- |
| ❌ 删除 DB 字段 | 未授权 |
| ❌ 修改 `student` 个人资料接口（`MeView` 需 phone/email）| 未授权 |
| ❌ 修改 `counselorPhone` / `counselorEmail` | **用户明确：本批只处理学生侧** |
| ❌ 修改 XML `<if>` 筛选条件 | 用户裁定 A：保留搜索行为 |
| ❌ 修改 `order by a.${params.orderBy}` 的拼接方式 | 属既有的 SQL 注入风险，独立议题 |
| ❌ 清理旧 Admin `add-or-update.vue` 的表单项 | 属前端残留，独立议题 |
| ❌ 扩大到其他 View / 其他表 | 未授权 |

---

## 七、📌 新登记项：筛选权限待核实（本批不解决）

> 用户明确指出：**保留筛选行为 ≠ 确认筛选权限安全**。

| 项 | 现状 | 风险 |
| --- | --- | --- |
| 谁能调用这 6 个 `/page` | 任何已登录用户（拦截器只校验登录）| 未知 |
| 筛选条件是否受行级授权约束 | `DataScope` 只约束**返回行**，`<if>` 条件可自由传 | 🔴 **存在性探测**：`?studentPhone=17700000000` 可判断该手机号是否有学生记录 |
| `studentIdCardNo` 筛选 | 同样可用 | 🔴 **更敏感**：可验证身份证号是否在库 |
| 行级授权与筛选的组合 | 例如 counselor 只能看自己的预约，但可传 `studentPhone` 探测其他学生 | 🔴 **需核实** |

**建议独立工单**：`AUTHZ-FILTER-PROBE` —— 核查筛选条件的权限约束与探测面。
**本批不做**，仅在报告中登记。

---

## 八、规模预估

| 类别 | 文件数 | 预计行数 |
| --- | :-: | :-: |
| View（删 3 字段 × 6）| 6 | ≈ **−234** |
| XML（删 3 行 × 6）| 6 | −18 |
| 旧 Admin `json_fields`（删 3 行 × 3）| 3 | −9 |
| **合计** | **15** | **≈ −261** |

> 全部为删除，无新增代码。**测试文件不改**（现有测试无这些字段断言）。

---

## 九、待你确认（2 项）

| # | 问题 | 状态 |
| :-: | --- | --- |
| **1** | `studentIdCardNo` 是否一并从 View + XML SELECT 删除 | ✅ **核查已完成，建议删除**（无业务依赖、无映射/排序/筛选影响）|
| **2** | 是否接受「导出少一列学生手机号」 | 你已裁定 **A：接受**，本清单已记录 |

> 若你确认 §二 的核查结论，本清单即为**可执行版本**。

---

## 十、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `d5b75b7` / `d5b75b7` · LEAD 0 |
| worktree | **CLEAN** |
| 代码 / DB / 前端 / pom | **0 改动** |
| 本批 | 📐 **清单 v2，未实施** |

**未写任何代码，等你确认 §九 后我再实施。**
