# Phase 3 / Step 7 / R14 —— 试卷浏览（实现与验收）

> 日期：2026-10-20 ｜ 基线：`217e54b`（R13 已推送，LEAD=0）
> 状态：**R14 已实现 · 🚧 未提交** ✗ ｜ Git 可见 **4 文件**（3 M + 1 A）
> 范围：**仅试卷浏览** —— 不含题量 / 答题 / 记录 / 计分 ✗（方案 A，已批准）

---

## 1. 实现清单

| 文件 | 变更 | 要点 |
| --- | :-: | --- |
| `frontend/src/api/assessment.ts` | M | 新增 `listPapers()` + `AssessmentPaperItem`；**字段白名单**（仅元信息）|
| `frontend/src/views/AssessmentPaperListView.vue` | **A** | `/assessment` 卡片列表：名称 / 时长 / 总分 / 状态 |
| `frontend/src/router/index.ts` | M | `assessment` 公开路由（无 `requiresAuth`）|
| `frontend/src/layouts/PublicLayout.vue` | M | 导航「试卷」（`data-test="nav-papers"`）|

**未改动**：后端 **0** ✓ · Admin **0** ✓ · `front/` **0** ✓ · `api/http.ts` **0** ✓

---

## 2. 关键边界：为什么**没有题量**

`GET /assessmentQuestion/list?paperId=` **匿名**返回：

```text
answer（正确答案）· analysis（解析）· options（选项文案里直接写「正确答案B」）
```

⇒ 为显示“题量”而调用该端点，会把**答案键下载进浏览器** ✗。
因此 R14 **只**调用 `/assessmentPaper/list`，**放弃题量字段** ✗，并登记 **`SEC-QUESTION-ANSWER-PUBLIC` 🔴 P1 Security（Unfixed）**。

---

## 3. 静态检查

| 检查 | 结果 |
| --- | :-: |
| `vue-tsc --noEmit` | **exit 0** ✓ |
| lint | **0** ✓ |
| 页面出现 开始答题 / 开始测评 / 立即测试 / 进入答题 / 查看成绩 | **0** ✓ |
| 调用 `/assessmentQuestion/*` | **0** ✓ |
| 调用 `assessmentRecord/(add\|save)` · `assessmentAnswer/(add\|save)` | **0** ✓（全 `frontend/src` 扫描）|
| 渲染题目 / 答案 / 解析 | **0** ✓ |
| 写操作 | **0** ✓ |

> 静态扫描命中的 2 处“开始答题 / assessmentQuestion”均在 `api/assessment.ts` 的**注释**中（说明为何不用），非 UI 文案、非调用 ✓。

---

## 4. 浏览器验收（公开页 · 无需登录）

| 项 | 实测 | 判定 |
| :-: | --- | :-: |
| 页面加载 | `/assessment` · 标题 `心理健康陪伴系统` | ✓ |
| 试卷数 | **`共 2 份`** | ✓ = DB `assessment_paper` 2 行 |
| 试卷名 | `考试试卷1` / `试卷2` | ✓ 非 Mock |
| 总分 / 时长 | `100` / **`100 分钟`** | ✓ = DB（100/100）|
| 状态 | `启用`（字典 `assessment_paper_status` 1=启用）| ✓ |
| 答题入口 | `开始答题` / `开始测评` → **No matches found** | ✓ **未出现** |
| Console | **Errors 0 / Warnings 0** | ✓ |

---

## 5. 明确不做 ✗

| 项 | 原因 |
| --- | --- |
| 题量 | 需调用题目接口 ⇒ 下载答案键（§2）|
| 开始答题 / 进入答题 / 查看成绩 | R15 暂缓；不允许出现任何答题入口 ✓ |
| 创建测评记录 / 提交答案 / 计分 | R15 暂缓（`SEC-ANSWER-SCORE-SPOOF` 等 P1 Unfixed）|
| 题目内容 / 选项 / 答案 / 解析 | 不加载 ✗ |
| `/assessmentPaper/detail/{id}` | `SEC-DETAIL-IDOR`（既有 P1）⇒ 不用 ✓ |

---

## 6. 安全登记（维持 **Unfixed** ✓）

| ID | 级别 | 状态 |
| --- | :-: | --- |
| `SEC-QUESTION-ANSWER-PUBLIC` | 🔴 **P1 Security** | Unfixed（**本轮新发现**：匿名可读答案/解析）|
| `SEC-ANSWER-SCORE-SPOOF` · `SEC-ANSWER-OWNER-SPOOF` · `SEC-RECORD-SCORE-UPDATE-IDOR` · `SEC-RECORD-DELETE-IDOR` · `SEC-PAPER-DELETE-IDOR` | 🔴 P1 Security | 全部 Unfixed（R15 及写路径）|
| `DEF-ANSWER-ENDPOINT-MISSING` | 🔴 **P1 Functional Blocker**（**非安全漏洞**）| Unfixed |
| `DEF-PAPER-WRITE-NO-ADMIN` · `DEF-ANSWER-GET-WRITES` | 🟠 P2 | Unfixed |
| `SEC-DETAIL-IDOR` | 🔴 P1 Security | 既有；本批不用 `/detail` ✓ |

---

## 7. 表述纪律

| 可以 ✓ | 不可以 ✗ |
| --- | --- |
| 「R14 试卷浏览已实现」 | 「测评功能已完成」 |
| 「展示试卷基本信息（名称/时长/总分/状态）」 | 「可以参加测评 / 得出测评分数」 |
| 「在线答题暂未开放」 | 「答题功能已就绪，等待上线」 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | R14 实现：4 文件（3 M + 1 A）；**仅浏览**（无题量/无答题/无写操作）；静态 7 项全 0 + `vue-tsc` 0 + lint 0；浏览器 `共 2 份` / `考试试卷1` / `100 分钟` / `启用` / 无答题入口 / Console 0-0；新登记 `SEC-QUESTION-ANSWER-PUBLIC` **P1 Security**（匿名答案键）；**未提交** ✗ |
