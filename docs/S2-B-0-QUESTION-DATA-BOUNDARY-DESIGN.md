# S2-B-0 —— 题目数据边界只读设计

> 日期：2026-10-20 ｜ 基线：`09140cd`（S2-A 已推送，LEAD=0）
> 模式：**只读审计 + 设计** —— 不改 Java / Vue / DB / 测试 / API 路径 ✗，不 commit ✗
> 结论先行：**不需要三个 DTO；2 个 DTO + 按角色选择即可覆盖现有全部场景** ✓

---

## 1. 四个端点的真实调用面

> 判据：**搜索到 ≠ 仍是有效业务路径**。下表区分「活调用 / 管理端源码 / dist 残留 / 测试」。

| 端点 | 旧 `front/`（活页面）| 管理端 `admin/src` | `admin/dist` | 新 `frontend/src` | 测试 |
| --- | --- | --- | --- | :-: | --- |
| `/assessmentQuestion/list` | **`examquestion/list.html:461,474`**（学生端题目列表页）| 0 | **0** | 0 | **刻意不断言**（Phase2 测试注释：C 组业务证据不足）|
| `/assessmentQuestion/page` | **`exampaper/exam.html:236`**（答题页，取题）| `examquestion/list.vue:440`（列表，**含答案列**）· `examrewrongquestion/add-or-update.vue:308`（选题下拉）· `examredetails/add-or-update.vue:304`（选题下拉）· `exampaper/exam.vue:211`（管理端答题）| **0** | 0 | 0 |
| `/assessmentQuestion/info/{id}` | 0 | `examquestion/add-or-update.vue:443`（编辑预填）· `examrewrongquestion/add-or-update.vue:398` · `examredetails/add-or-update.vue:383` | **0** | 0 | **1 条**：`/assessmentQuestion/info/1` 断言学生 **200**（只断言状态，**不断言字段**）|
| `/assessmentQuestion/detail/{id}` | **0** | 0 | 0 | 0 | 0 |

**结论**：
- `/list` 是**匿名可达的活页面依赖**（回答不含分页参数也可用）⇒ 公开字段必须保留、答案必须移除 ✓
- `/page`、`/info` 同时服务**管理端（需 answer/analysis）**与**旧学生答题页（不该有 answer）** ⇒ 必须**按角色分流** ✓
- `/detail` **无任何调用方**；`/list` 的 `@IgnoreAuth` 是唯一匿名入口 ✓
- ⚠️ **测量更正（M 类）**：初次用「HTTP 状态」探测 `/add`、`/detail`、`/saveAssessmentQuestion` 得到 200，**误判为匿名可达** ✗；实际响应体是 `{"code":401,"msg":"请先登录"}` —— 本项目 401 契约是 **HTTP 200 + code=401**。三者均需登录 ✓

---

## 2. 当前实际返回字段（`/list` 实测）

```text
id, paperId, content, options, score, answer, analysis,
questionType, sortOrder, createdAt, questionTypeLabel,
paperName, paperDurationMinutes, paperTotalScore, paperStatus, paperStatusLabel, paperIsDeleted
```

| 字段 | 性质 | 是否敏感 |
| --- | --- | :-: |
| `answer` | **标准答案** | 🔴 是 |
| `analysis` | **解析** | 🔴 是 |
| `options` | 选项 JSON，**文案可能含“正确答案X”** | 🔴 **是（隐藏泄露）** |
| `content` / `score` / `questionType` / `sortOrder` | 题面与结构 | 否 |
| `paper*` / `questionTypeLabel` | 联表冗余 | 否 |

---

## 3. 敏感字段暴露矩阵

| 字段 | 公开浏览（匿名 `/list`）| 学生答题（旧 `/page`）| 提交后复习（`assessmentAnswer`/`wrongQuestion`）| 管理端 |
| --- | :-: | :-: | :-: | :-: |
| `content` | ✓ 需要 | ✓ 需要 | ✓ | ✓ |
| `options`（**清洗后**）| ✓ 需要 | ✓ 需要 | ✓ | ✓ |
| `score` | ✓（展示分值，可选）| ✓（本地判分需要，未来改服务端）| ✓ | ✓ |
| `questionType` / `sortOrder` | ✓ | ✓ | ✓ | ✓ |
| **`answer`** | ❌ 不允许 | ❌ **不允许** | ✅ 允许（**仅本人已提交记录**）| ✅ 允许 |
| **`analysis`** | ❌ 不允许 | ❌（提交前不允许）| ✅ 允许（仅本人已提交）| ✅ 允许 |
| `paper*` | ✓ | ✓ | ✓ | ✓ |

**关键待决**：学生答题阶段是否需要 `answer`？按安全设计应**不需要**（判分放服务端）；现有旧页面**确实依赖它**（见 §5），但这属 R15 要重构的部分 ⇒ 记录为**兼容性破坏**，不作为“必须保留”理由 ✓。

---

## 4. `options` 答案文案影响面（逐行实测）

| 题目 | paper | options 原文 | 是否泄露 |
| :-: | :-: | --- | :-: |
| 1 | 1 | `答案A / 答案B / **正确答案C** / 答案D` | 🔴 是 |
| 2 | 1 | `答案A / **正确答案B** / **正确答案C** / 答案D` | 🔴 是（两个）|
| 3 | 1 | `[]`（填空题）| ✅ 否 |
| 4 | 1 | `A.对 / B.错` | ✅ 否 |
| 5 | 1 | `答案A / **正确答案B** / 答案C / 答案D` | 🔴 是 |
| 6 | 2 | `A / B / **正确答案C** / **正确答案D**` | 🔴 是（两个）|

| 问题 | 结论 |
| --- | --- |
| 出现在哪些题 | **4 / 6 题**（id 1,2,5,6）|
| 来源 | **数据库原始 `options` 字段**（管理员在题目表单里填写），**不是后端拼接** |
| “答案A/B/D”是否算泄露 | ❌ 不算（只是选项文案）；**只有含“正确”的才是泄露** |
| 旧管理端是否依赖该文案 | 管理端原样展示/编辑，**不解析文案**⇒ 清洗不破坏功能 |
| 学生端是否消费该格式 | 旧答题页 `JSON.parse(options)` 后**直接渲染 text** ⇒ 清洗后学生看到的是干净文案 ✓ |
| 可选的修复位置 | ① **DB 数据修正**（4 题 5 处文案）② **DTO 层文本规范化** ③ **题目维护校验**（新增/编辑禁止“正确/答案”字样）|
| **建议** | **①+③ 为主，② 兜底**（只做②属掩盖数据问题；只做①会被再次录错）→ **本轮不清洗** ✗ |

---

### 4bis. `options` 真实结构与安全投影（实现前确认 · 2026-10-20）

**结构实测（全 6 行）**：

| 事实 | 值 |
| --- | --- |
| `json_valid(options)` | **6/6 = 1** ✓ |
| `json_type` | **ARRAY（6/6）** ✓ |
| 元素结构 | `{"code":"A","text":"答案A"}`（**code + text 两键**）|
| 元素个数 | id1/2/5/6 = **4** · id4 = **2** · id3 = **0**（`[]`，填空题）|
| 写入方 | 管理端题目表单 `addOptions` 构造 `{code, text}` 数组后整体序列化 |
| 消费方 | 旧答题页 `JSON.parse(options)` → 渲染 `io.text`、按 `io.code` 提交 |

**关键新证据**：`/list` 的唯一人工页面 `examquestion/list.html` **完全不渲染 options / answer / analysis**
（对 `options|item.text|item.code` 的检索命中 **0**）⇒ 该页只展示题面，**对 options 做中性化不会造成 UI 影响** ✓

**投影策略（按端点分档）**：

| 端点 | 角色 | `answer` / `analysis` | `options` |
| --- | --- | --- | --- |
| `/list`（匿名）| — | 剥离 ✓ | **仅 `code` + `选项{code}`（不含原始 text）** —— 唯一调用页不渲染 ⇒ 零影响、零泄露 |
| `/detail/{id}` | — | 剥离 ✓ | 同上（**无调用方**）|
| `/page` | 管理员 | 保留 ✓ | 原始 |
| `/page` | 学生/其他 | 剥离 ✓ | **检测 + 整体中性化**（见下）|
| `/info/{id}` | 管理员 | 保留 ✓ | 原始 |
| `/info/{id}` | 学生/其他 | 剥离 ✓ | 同上 |

**检测与中性化规则（不是简单字符串删除）**：

```text
1) 解析失败 / 非数组 → Public 侧返回 []（fail-closed）
2) 元素只保留白名单键 code / text；code 不合规 → 丢弃该元素
3) 对每个 text 做【泄露检测】：命中 → 整条 text 替换为 "选项{code}"（不做子串删除）
   检测词（示例）：正确 | 标准答 | 正解 | 答案 | 应选 | 本题 | 此题 | 选[A-D] | √ | ✔
4) 命中时置 optionsMasked=true（供 QA/前端提示，不影响判分）
```

**为什么不用 `replace("正确答案","")`**：子串删除会留下 `C`、`：C` 等残片，且变体（"正确答案：C"/"答案为C"/"本题选C"）会绕过。
**残留风险（必须承认）**：自由文本无法穷举语义 ⇒ 检测只能覆盖已知模式；**根治依赖数据治理**（DB 修正 5 处 + 管理端维护校验，独立任务）。
**为什么仍可接受**：当前 4 题 5 处全部命中 `正确`，可被完全覆盖；且 `/list`、`/detail` 采用 **codes-only** 档位，**不依赖检测器**即有零泄露保证 ✓

---

## 5. `assessmentAnswer` / `assessmentWrongQuestion` 的业务语义

| 证据 | 内容 |
| --- | --- |
| `front/pages/examrecord/detail.html:71-77` | 明确渲染「**正确答案**」+「**解析**」`questionAnswer` / `questionAnalysis` |
| `front/pages/examrewrongquestion/list.html:141` | 有解析滤镜（注释状态）|
| `admin/.../examrewrongquestion/add-or-update.vue:103` | 管理端编辑「答案解析」|
| `/assessmentAnswer/list`、`/assessmentWrongQuestion/list` | **DataScope 强制本人**（实测 A=5 / B=空）|

⇒ **业务语义：提交后查看自己的答案与解析，是既有功能** ✓
⇒ 因此这两个端点返回 `questionAnswer/questionAnalysis` **不判定为漏洞**，只需保证「**只能看自己的**」✓（`/list` 已 DataScope；`/detail` 属 `SEC-DETAIL-IDOR` 既有问题）
⇒ **待裁定**：是否应限制「仅已完成/已提交」才可看解析（当前无状态门槛）⚠️

---

## 6. 候选 DTO 边界

| 方案 | 内容 | 评估 |
| --- | --- | --- |
| **① 2 个 DTO（推荐）** | `QuestionPublicDTO{id,paperId,content,options,score,questionType,sortOrder}` + `QuestionAdminDTO{+answer,+analysis}`；`/page`、`/info` 按角色选择 | ✅ 覆盖全部场景；不新增无用类型 |
| ② 3 个 DTO（Public/Student/Admin）| 额外拆 Student | ❌ **无必要**：答题场景同样不应拿到答案，与 Public 完全一致 |
| ③ 单 DTO + 参数过滤 | 同端点按 `?withAnswer=` 返回 | ❌ 危险：客户端可控开关 = 形同虚设 |
| ④ `@JsonView` | 用视图注解序列化 | ⚠️ 可行但侵入性高，与现有 `View`/`BeanUtils` 风格冲突 |

**推荐 ①**：`/list` 与 `/detail` 恒用 Public；`/page`、`/info` 按 `CurrentUserProvider.currentRole` 分流（管理员 → Admin，其余 → Public）。

---

## 7. 兼容性影响（必须提前承认）

| 消费方 | 影响 |
| --- | --- |
| 旧学生答题页 `exampaper/exam.html` | **会失效** ✗ —— 它用 `ruleForm.answer` 在浏览器本地判分（L95/L287）；但该页提交端点 `saveExamredetails` **本就 404**，流程已不可用 ⇒ 属**预期破坏**（R15 将改服务端判分）|
| 管理端 `exampaper/exam.vue` | **不受影响**（管理员走 Admin DTO）|
| 旧 `examquestion/list.html` | 不受影响（页面未展示 answer/analysis）✓ |
| 管理端题目列表/编辑 | 不受影响（Admin DTO 保留全字段）✓ |
| 新前端 R14 | **本来就不调用**题目接口 ⇒ 无影响 ✓ |
| `/assessmentAnswer` / `/wrongQuestion` | 不改动（复习语义合法）✓ |

---

## 8. 回归测试影响

| 用例 | 是否会失败 | 说明 |
| --- | :-: | --- |
| `IdentityAuthorizationRegressionTest#contentEndpointsAreUnaffected` | **不会** ✓ | 只断言 `/assessmentQuestion/info/1` 学生 **200 + code=0**；Public DTO 仍 200 |
| `Phase2CoreRegressionTest`（C 组）| **不会** ✓ | 明确**不对** `/assessmentQuestion/list` 做断言 |
| 其它 46 条 | 不会 ✓ | 不涉及题目字段 |

⇒ **无需为 DTO 修改任何测试** ✓（本轮也未改 ✗）。

---

## 9. 相关新发现（本批不修，登记）

| ID | 级别 | 事实 |
| --- | :-: | --- |
| **`DEF-QUESTION-ADMIN-ENDPOINT-MISSING`** | 🔴 **P1 Functional Blocker**（**非安全**）| 管理端调用 `saveExamquestion` / `updateExamquestion` / `deleteExamquestion` → **实测 404**；真实映射为 `saveAssessmentQuestion` / `updateAssessmentQuestion` / `deleteAssessmentQuestion` ⇒ **管理端题目新增/编辑/删除不可用** ✗ |
| 题目写入口无角色限制 | 🔴 P1 Security（归 S2-C）| `/assessmentQuestion/add`、`/saveAssessmentQuestion`、`/update` 等仅要求登录，**无管理员校验** ⇒ 学生可新增/改题 ✗ |

---

## 10. 待裁定项（**方案通过后才进入 S2-B 实现**）

| # | 问题 | 选项 |
| :-: | --- | --- |
| 1 | DTO 数量 | **2 个（推荐）** / 3 个 / `@JsonView` |
| 2 | `/page`、`/info` 的角色分流 | **管理员 → Admin DTO；其余 → Public（推荐）** / 全部 Public（管理端会坏）|
| 3 | `options` 清洗方式 | **DB 修正 + 维护校验（推荐）** / 仅 DTO 规范化 / 暂不处理 |
| 4 | 学生“提交后看解析” | 保持现状（既有功能）/ 增加「仅已提交」门槛 |
| 5 | `/detail/{id}` | 本次仅改为 Public DTO（推荐）/ 一并做归属收口（属 S2-C）|
| 6 | 回归方式 | live 探针（推荐，同 S2-A）/ 新增自动化用例（会改 47/47 计数）|

---

## 11. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | S2-B-0 只读设计：4 端点调用面（list 匿名活调用 / page+info 双角色 / detail 无调用方）· 字段矩阵 · **options 4/6 题泄露**（DB 原始数据）· 复习语义合法 · **推荐 2 DTO + 角色分流** · 兼容性破坏已声明 · 测试不受影响 · 新登记管理端题目端点 404 · **零改动** ✓ |
