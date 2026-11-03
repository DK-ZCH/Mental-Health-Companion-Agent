# S2-C2-0 —— admin-only 删除授权 + `/detail` 死 API + 404 调用面复核（只读审计）

> 日期：2026-10-20 ｜ 基线：`c167a96`（S2-C1 已推送，LEAD=0）
> 模式：**只读审计 / 设计** —— 不改 Controller / Service / Mapper / DB / 前端 / 测试 ✗，不 commit ✗
> 目标：逐个确认 9 个 `/delete` 真实调用链；`/detail/{id}` 死接口最终确认；两个 404 调用面复核。

---

## 0. 结论速览

1. **9 个资源里 7 个 `/delete` 是 admin 真实在用**，直接加 `assertAdminOnly` 即可；**2 个（题目 / 记录）的裸 `/delete` 是死端点**，admin 实际调用的是**级联删除专用端点**（但前端名字调错 → 404）。
2. **`/detail/{id}` 全仓库调用 = 0**，`/info/{id}` 才是实际消费者（管理端 22 处 `info` 调用）。9 资源里 8 个有 `/detail`（sysDictItem 无）。
3. **两个 404 已静态复核**：真实 mapping 是 `deleteAssessmentQuestion` / `deleteAssessmentRecord`（级联端点，**同样无归属校验**）。

---

## 1. 9 资源 Delete 调用链矩阵（逐资源取证）

> 「旧 front」= 学生端静态页；「frontend/src」= Vue3 学生端；「admin/src」= 管理端。
> 学生 / 教师对这些资源的 delete 调用：**全仓库 0 处**（`front/`、`frontend/src/`、`src/test/` 三目录逐项检索均 0）。

| 资源 | 管理端调用 | 学生 | 教师 | 真实命中端点 | Delete 模型 | 裸 `/delete` 是否死端点 | 结论 |
| --- | --- | :-: | :-: | --- | --- | :-: | --- |
| `assessmentPaper` | `exampaper/list.vue:497` → `assessmentPaper/delete` ✓ | 无 | 无 | `/delete`（**软删** `setIsDeleted(2)`）| Admin | 否（在用）| 加 `assertAdminOnly` |
| `assessmentQuestion` | `examquestion/list.vue:512` → `deleteExamquestion` **404 ✗** | 无 | 无 | **`/deleteAssessmentQuestion`（级联回扣试卷总分）** | Admin | **是（死端点）** | 待裁定（见 §4）|
| `knowledgeArticle` | `jiankangzhishi/list.vue:682` → `knowledgeArticle/delete` ✓ | 无 | 无 | `/delete`（物理删）| Admin | 否 | 加 `assertAdminOnly` |
| `notification` | `tongzhi/list.vue:682` → `notification/delete` ✓ | 无 | 无 | `/delete`（物理删）| Admin | 否 | 加 `assertAdminOnly` |
| `config` | `config/list.vue:428` → `config/delete` ✓ | 无 | 无 | `/delete`（`Long[]` 参数）| Admin | 否 | 加 `assertAdminOnly` |
| `sysDictItem` | `dictionary/list.vue:691` → `sysDictItem/delete` ✓ | 无 | 无 | `/delete`（物理删）| Admin | 否 | 加 `assertAdminOnly` |
| `assessmentRecord` | `examrecord/list.vue:508` → `deleteExamrecord` **404 ✗** | 无 | 无 | **`/deleteAssessmentRecord`（级联删作答明细）** | Admin | **是（死端点）** | 待裁定（见 §4）|
| `assessmentAnswer` | `examredetails/list.vue:493` → `assessmentAnswer/delete` ✓ | 无 | 无 | `/delete`（物理删）| Admin | 否 | 加 `assertAdminOnly` |
| `assessmentWrongQuestion` | `examrewrongquestion/list.vue:564` → `assessmentWrongQuestion/delete` ✓ | 无 | 无 | `/delete`（物理删）| Admin | 否 | 加 `assertAdminOnly` |

### 1.1 「后三个」确为真实业务能力（非仅“只有 admin 页面”）

| 资源 | 管理端删除按钮 | 权限绑定 | 删除语义 | 判定 |
| --- | --- | --- | --- | :-: |
| `assessmentRecord` | `examrecord/list.vue:135` | `isAuth('examrecord','删除')` | 删记录 + **级联删作答明细**（防残留脏数据）| ✅ 真实能力 |
| `assessmentAnswer` | `examredetails/list.vue:171` | `isAuth('examrecord','删除')` ⚠️ 绑定 `examrecord` 权限 | 删单条作答明细 | ✅ 真实能力 |
| `assessmentWrongQuestion` | `examrewrongquestion/list.vue:182` | `isAuth('examrewrongquestion','删除')` | 删错题记录 | ✅ 真实能力 |

> 学生侧**无任何删除入口**：测评历史只读展示（前端只读，后端 `/delete` 无学生业务路径）。教师侧写授权仍属批 4 挂账，本轮不涉及。

---

## 2. `/delete` 端点守卫现状 + 实现关键点

### 2.1 现状：全部无守卫

9 个 `/delete` + 2 个级联端点（`deleteAssessmentQuestion` / `deleteAssessmentRecord`）**均无 `OwnershipGuard` / `assertAdminOnly`**（S1 静态表文件级命中 = 0，本次逐文件复核确认）。

### 2.2 实现关键点（S2-C2 落地前必须知道）

| # | 事实 | 影响 |
| :-: | --- | --- |
| 1 | **所有 delete 方法签名都无 `HttpServletRequest request` 参数**（`delete(@RequestBody Integer[] ids)`）| 加 `assertAdminOnly(request)` 前需**补 `request` 参数**（Spring 自动注入，无需改调用方）|
| 2 | `config/delete` 参数是 **`Long[]`**，其余 8 个是 `Integer[]` | 改动时勿混淆签名 |
| 3 | `assertAdminOnly` 原语**已存在**（`OwnershipGuard` L117，S2-C-0 之后由 Step 5 批 2 引入）| 无需新增原语，直接复用 |
| 4 | `assessmentPaper/delete` 是**软删**（`setIsDeleted(2)`），非物理删 | 语义不变，仅加授权 |

---

## 3. `/detail/{id}` 死接口最终确认（9 资源逐一列出）

> 全仓库检索（`src/main/resources/**` + `src/main/java/**` + `frontend/**`）`/detail` 调用 = **0**；`/info/{id}` 是实际消费者（管理端 **22 处** `info` 调用）。

| 资源 | `/detail/{id}` 存在 | 定位 | 备注 | 建议 |
| --- | :-: | --- | --- | :-: |
| `assessmentPaper` | 有 | `AssessmentPaperController` L273 | 无调用；`/info` 在用 | 删除 |
| `assessmentQuestion` | 有 | `AssessmentQuestionController` L325 | 无调用；S2-B 已投影公开 DTO；`/info` 在用 | 删除 |
| `knowledgeArticle` | 有 | `KnowledgeArticleController` L260 | 无调用；`/info` 在用 | 删除 |
| `notification` | 有 | `NotificationController` L260 | 无调用；`/info` 在用 | 删除 |
| `config` | 有 | `SysConfigController` L68 | **`@IgnoreAuth` 匿名**；无调用；内容仅轮播图 | 删除（与 §6 裁定项 #3 联动）|
| `sysDictItem` | **无** | — | 只有 `/info/{id}` | 无需处理 |
| `assessmentRecord` | 有 | `AssessmentRecordController` L309 | 无调用；`/info` 在用 | 删除 |
| `assessmentAnswer` | 有 | `AssessmentAnswerController` L310 | 无调用；`/info` 在用 | 删除 |
| `assessmentWrongQuestion` | 有 | `AssessmentWrongQuestionController` L299 | 无调用；`/info` 在用 | 删除 |

> 关键区分：**`/detail` 是「前端详情」死端点；`/info/{id}` 是「后端详情」活端点**。二者是**独立方法**，删除 `/detail` **不影响** `/info` 及其 IDOR 归属（`/info` 归属已有 `assertOwnership` / S2-B 投影，见既有登记，本轮**不混为一谈**）。

---

## 4. 两个 404 —— 调用面复核（本批不修）

| ID | 管理端调用方 | 调用的 URL | 真实 mapping | 真实端点守卫 |
| --- | --- | --- | --- | :-: |
| `DEF-QUESTION-ADMIN-ENDPOINT-MISSING` | `examquestion/list.vue:512` | `assessmentQuestion/deleteExamquestion` | `assessmentQuestion/deleteAssessmentQuestion`（`AssessmentQuestionController` L481）| **无** ✗ |
| `DEF-RECORD-ADMIN-ENDPOINT-MISSING` | `examrecord/list.vue:508` | `assessmentRecord/deleteExamrecord` | `assessmentRecord/deleteAssessmentRecord`（`AssessmentRecordController` L395）| **无** ✗ |

**静态证明 404**：两个 Controller 的 `@RequestMapping` 均无 `/deleteExamquestion`、`/deleteExamrecord`，只有 `/deleteAssessmentQuestion`、`/deleteAssessmentRecord` ⇒ Spring 无法路由 → 404（S2-C-0 已实测复现）。

**其余消费者**：`deleteAssessmentQuestion` / `deleteAssessmentRecord` 全仓库**无任何前端/测试调用**（仅 Controller 定义）——前端调错了名字，故当前**无人真正到达**级联端点。

---

## 5. 新发现的问题（本轮登记，不修）

| ID | 级别 | 事实 |
| --- | :-: | --- |
| **`SEC-QUESTION-CASCADE-DELETE-NO-GUARD`** | 🔴 P1 | `deleteAssessmentQuestion` 级联删除（删题 + 回扣试卷总分）**无归属校验** ✗ —— 修 404 后 admin 真实命中此端点，须一并加 `assertAdminOnly` |
| **`SEC-RECORD-CASCADE-DELETE-NO-GUARD`** | 🔴 P1 | `deleteAssessmentRecord` 级联删除（删记录 + 删作答明细）**无归属校验** ✗ —— 同上 |
| `DEF-PAPER-DELETE-SOFT` | 🟡 说明 | `assessmentPaper/delete` 是**软删**（非物理删），授权后语义不变，仅记录口径 |
| `DEF-ANSWER-DELETE-AUTH-BINDING` | 🟡 说明 | `examredetails/list.vue:171` 删除按钮绑 `isAuth('examrecord','删除')`（非 `examredetails`）——前端权限绑定口径，非本轮安全项 |

> 已存在、不重复登记：`SEC-DETAIL-IDOR`（本轮确认 `/detail` 死端点，收口风险极低）· 教师侧写授权未收敛（批 4 / G3）。

---

## 6. 已裁定事项（用户裁定，2026-10-20）

| # | 问题 | 裁定 |
| :-: | --- | --- |
| 1 | **题目 / 记录的裸 `/delete` 死端点** | **保留 + `assertAdminOnly`（选项 A），不删除**。理由：已有 API 兼容；加守卫封死潜在越权；死端点清理仅针对 `/detail` |
| 2 | **两个 404** | **本批不修**（维持原裁定）。但对应**真实级联端点必须加 admin-only**（`deleteAssessmentQuestion` / `deleteAssessmentRecord`），否则学生可绕过 `/delete` 直达真实 mapping |
| 3 | **`config/detail/{id}`** | **随 `/detail` 批量删除**。全仓库调用 0 · `/info` 是实际接口 · `detail` 匿名 · 删除不改变实际消费者；`config/info` 是否匿名本批不改变 |
| 4 | **实现范围** | **11 个删除（9 `/delete` + 2 级联）加 admin-only + 8 个 `/detail` 删除**。两个新 P1（`SEC-QUESTION-CASCADE-DELETE-NO-GUARD` / `SEC-RECORD-CASCADE-DELETE-NO-GUARD`）在 S2-C2 完成后一并关闭 |

---

## 7. 已裁定实现范围（S2-C2 · 待实现批准 ✗）

```text
A. 11 个删除能力加 assertAdminOnly(request)：
   9 × /delete：
     assessmentPaper · assessmentQuestion · knowledgeArticle · notification ·
     config · sysDictItem · assessmentRecord · assessmentAnswer · assessmentWrongQuestion
   2 × 级联（本次新发现 P1）：
     assessmentQuestion/deleteAssessmentQuestion · assessmentRecord/deleteAssessmentRecord
   （每处补 HttpServletRequest request 参数）

B. 删除 8 个无调用 /detail/{id}：
   assessmentPaper · assessmentQuestion · knowledgeArticle · notification ·
   config · assessmentRecord · assessmentAnswer · assessmentWrongQuestion
   （sysDictItem 无 /detail，不处理）

C. 不做（本批）：
   ✗ 修两个 404 · ✗ 前端改动 · ✗ /info 改动 · ✗ /info IDOR · ✗ DB · ✗ 测评答题/评分

D. 实现要求（每个删除接口至少验证 4 角色）：
   anonymous → 既有认证契约拒绝（401）· student → 403 · counselor → 403 · admin → 原业务行为保持
   尤其 deleteAssessmentQuestion / deleteAssessmentRecord 须专门做非 Admin 直接调用真实 mapping 的负向测试，
   证明 SEC-QUESTION-CASCADE-DELETE-NO-GUARD / SEC-RECORD-CASCADE-DELETE-NO-GUARD 两个 P1 确实被堵住
```

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2 | 2026-10-20 | 按用户 4 项裁定修订：①裸 `/delete` 保留+admin-only（不删除）；②两 404 不修但**级联端点纳入本批**（加守卫）；③`config/detail` 随批删除；④最终范围 = **11 删除 + 8 detail**；补 4 角色负向测试要求 |
| v1 | 2026-10-20 | S2-C2-0 只读审计：9 资源 delete 调用链逐一取证（7 在用 + 2 死端点）；`/detail` 8 处死端点确认（全仓库调用 0）；两 404 静态复核（真实 mapping 为级联端点）；新登记 2 个级联 P1；4 项待裁定；**零改动** ✓ |
