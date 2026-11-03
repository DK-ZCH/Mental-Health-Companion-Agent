# Phase 3 / Step 7 / R14–R15 —— 试卷列表与答题契约只读审计

> 日期：2026-10-20 ｜ 基线：`217e54b`（R13 已推送，LEAD=0）
> 性质：**只读审计** —— 零代码改动 ✗、零业务写操作 ✗（仅匿名 GET）
> **结论先行：R14 可做（公开只读）；R15 存在「分数由客户端决定」的契约缺陷，建议暂缓，先定计分归属。**

---

## 1. 数据现状（只读）

| 项 | 值 |
| --- | --- |
| 试卷 | **2 张**：`1 考试试卷1`（100 分 / 100 分钟 / status=1）、`2 试卷2`（10 分 / 60 分钟）|
| 题目 | paper 1 = **5 题**（分值 10/34/41/11/4，合计 **100** ✓）；paper 2 = **1 题**（10 分）|
| 测评记录 | **1 条**：`record_no=1648530233813` · student 1 · paper 1 · `total_score=15` |
| 答题明细 | **5 条**（record_no 同名）|
| 一致性 | `record.total_score=15` = `sum(answer.score)=15` ✓（明细中 q5=4、q4=11 正确，其余 0）|

> 说明：分值分布在题目行（`assessment_question.score`），**不在前端**；但提交时分数由前端传入（见 §3）。

---

## 2. R14 试卷列表 —— 契约可用 ✓

| 端点 | 认证 | 实测 | 结论 |
| --- | --- | --- | --- |
| `GET /assessmentPaper/list` | **`@IgnoreAuth` 公开** | 200 · `total=2` · ids `2,1` | ✓ 可直接用于 R14 |
| `GET /assessmentQuestion/list?paperId=1` | **公开** | 200 · `total=5` | ⚠️ **R14 不使用**：响应含 `answer` / `analysis`（见 §2bis）|
| `GET /assessmentQuestion/page` | 需登录 | 200 · `code=401` | 需登录，不使用 |
| `GET /assessmentPaper/page` | 需登录 + DataScope | — | 不使用 |
| `GET /assessmentPaper/info/{id}` | 需登录，**无归属守卫** | — | 公开目录，风险低；本批不用 |
| `GET /assessmentPaper/detail/{id}` | 需登录，**无守卫** | — | `SEC-DETAIL-IDOR`（既有 P1）⇒ **禁用** ✗ |

试卷字段：`id, name, durationMinutes, totalScore, status, isDeleted, createdAt, statusLabel` ✓（无 PII ✓）

**R14 可安全实现的内容**：试卷名 / 时长 / 总分 / 状态 ✓ —— **不含“题量”** ✗（原因见 §2bis）。

### 2bis. 🔴 新发现：公开接口泄露正确答案（`SEC-QUESTION-ANSWER-PUBLIC` P1）

匿名请求 `GET /assessmentQuestion/list?paperId=1` 的**实际响应字段**：

```text
id, paperId, content, options, score, answer, analysis, questionType, ...
                                       ↑ 正确答案   ↑ 解析
首条样本：{"id":5, ..., "options":"[{\"text\":\"答案A\",\"code\":\"A\"},{\"text\":\"正确答案B\",\"code\":\"B\"}, ...]", "answer":"B", "analysis":"无"}
```

| 事实 | 判定 |
| --- | --- |
| **`answer`（正确答案）匿名可读** | 🔴 **P1 Security** |
| **`analysis`（解析）匿名可读** | 🔴 同上 |
| `options` 文本里直接出现「正确答案B」 | ⚠️ 更严重（答案写在选项文案中）|
| R14 为显示“题量”而调用该端点 | ❌ **会把答案键下载进浏览器** ⇒ **R14 放弃题量字段** |

> ⇒ R14 只调用 `/assessmentPaper/list`（字段：`id,name,durationMinutes,totalScore,status,statusLabel`，**无题目/无答案** ✓）。
> “题量”需等**count-only 端点**或后端字段投影后再展示（不在 R14 范围 ✗）。

---

## 3. R15 答题 —— 🔴 契约层缺陷（**R15 不建议现在做** ✗）

### 3.1 旧前端调用的端点在当前后端**不存在**（404 实测）

```text
front/pages/exampaper/exam.html:302        → POST assessmentAnswer/saveExamredetails?examrecordId=…
admin/src/views/modules/exampaper/exam.vue:296 → 同一 URL
后端 src/main/java 全量检索               → **无 saveExamredetails 映射** ✗
实测 GET /assessmentAnswer/saveExamredetails → HTTP 404 ✓
```

⇒ **旧答题提交链路在本后端不可用**（现存答题数据为种子/历史数据）✗。
⇒ R15 **不能照抄旧前端**；必须基于真实存在的端点重新设计。

### 3.2 真实存在的写端点及其缺陷

| 端点 | 方法/语义 | 缺陷 |
| --- | --- | --- |
| `POST /assessmentAnswer/saveAssessmentAnswer?examrecordId=` | 保存一条作答；答对则 `record.totalScore += answer.score` | 🔴 **`score` 由客户端提交**（`@RequestBody` 直接采信）⇒ 学生可提交 `score=1000` ✗<br>🔴 `if("学生id".equals(role))` 是**字面量 bug**（永不成立）⇒ `studentId` **不强制**、采信客户端 ✗<br>🔴 `examrecordId` 来自 **query** 且**无归属校验** ⇒ 可累加**他人**记录总分 ✗ |
| `POST /assessmentAnswer/add` | 直接插入作答行 | 同上：`studentId` / `score` / `recordNo` 全客户端可控，无归属 ✗ |
| `GET /assessmentAnswer/addUnsaved?paperId=&recordNo=` | **GET 产生写操作** ✗：为缺答题生成「未作答」明细 + 错题 | 副作用 GET ✗；按 `recordNo+studentId` 去重；`paperId` 可伪造 |
| `POST /assessmentRecord/saveAssessmentRecord?paperId=` | 创建记录（`totalScore=0` · `recordNo=时间戳` · **studentId 服务端强制** ✓）| 这是唯一归属正确的入口 ✓ |
| `POST /assessmentRecord/add` | 创建记录 | 🔴 **不强制 studentId**（与 `/save` 不同）⇒ 可为他人建记录 ✗<br>🔴 `totalScore` 客户端可控 ✗ |
| `POST /assessmentRecord/delete` · `/deleteAssessmentRecord` | 删除记录（后者连带删明细）| 🔴 **无归属校验** ⇒ 可删他人记录 ✗ |

**计分模型事实**：

```text
前端提交 { studentId, questionId, studentAnswer, score: defen }   ← score 来自前端计算
后端：save(answer) → 若 question.answer == studentAnswer → record.totalScore += answer.score
```

⇒ **分数不是服务端算出来的**，而是「客户端给多少分，服务端加多少分」✗。
即使答案正确性由服务端比对，**分值仍由客户端决定**（可提交 `score=1000`）。

### 3.3 G2「提交非原子」的实际情况

比 G2 更严重：不是「多次请求、缺事务」，而是 **每次请求都单独写库且分值/归属可被客户端控制**：

```text
创建记录（1 次）
→ 每题保存答案（N 次，各自独立提交）
→ 错题另写一张表（第 N+1..2N 次）
```

失败中断即产生「部分作答 + 部分错题 + 已累加分数」的不一致数据 ✗。

---

## 4. 登记项（**全部 Identified / Unfixed** ✓）

> **级别口径（按审阅要求区分，避免把“缺接口”写成安全漏洞）**
> · **P1 Security** —— 可被外部利用的安全 / 数据完整性缺陷；
> · **P1 Functional Blocker** —— 功能链缺失或不可用，**不是安全漏洞** ✗。
> `DEF-` 前缀一律表示**功能 / 设计缺陷**，即便级别为 P1。

| ID | 级别 | 事实 |
| --- | :-: | --- |
| **`SEC-QUESTION-ANSWER-PUBLIC`** | 🔴 **P1 Security** | `GET /assessmentQuestion/list?paperId=` **匿名**返回 `answer` / `analysis`，且 `options` 文案含“正确答案B” ✗ ⇒ 未登录者可直接取答案键，测评本身失去意义（**实测 ✓**，见 §2bis）|
| **`SEC-ANSWER-SCORE-SPOOF`** | 🔴 **P1 Security** | `/saveAssessmentAnswer` 直接采信客户端 `score` 并累加进记录总分 ✗（**已由代码 + 旧前端取证**；未执行利用 ✗）|
| **`SEC-ANSWER-OWNER-SPOOF`** | 🔴 **P1 Security** | `if("学生id".equals(role))` 字面量 bug ⇒ `studentId` 未强制；`/add` 亦不强制 ⇒ 作答可归属他人 ✗ |
| **`SEC-RECORD-SCORE-UPDATE-IDOR`** | 🔴 **P1 Security** | `examrecordId` 来自 query 且无归属 ⇒ 可给**他人记录**累加分数 ✗ |
| **`SEC-RECORD-DELETE-IDOR`** | 🔴 **P1 Security** | `/delete`、`/deleteAssessmentRecord` 无归属校验 ⇒ 可删他人记录与明细 ✗ |
| **`SEC-PAPER-DELETE-IDOR`** | 🔴 **P1 Security** | `/assessmentPaper/delete` 为**逻辑删除**且无守卫 ⇒ 任一登录用户可下架所有试卷 ✗ |
| **`DEF-ANSWER-ENDPOINT-MISSING`** | 🔴 **P1 Functional Blocker**（**非安全漏洞** ✗） | 旧前端/管理端调用 `saveExamredetails` → **404**，答题提交链路不可用 ✗ |
| `DEF-PAPER-WRITE-NO-ADMIN` | 🟠 P2 | `/save` `/add` `/update` 无管理员校验 ⇒ 学生可建/改试卷 ✗ |
| `DEF-ANSWER-GET-WRITES` | 🟠 P2 | `GET /addUnsaved` 产生写操作（副作用 GET）✗ |
| `SEC-DETAIL-IDOR` | 🔴 P1 | 既有；`/assessmentPaper/detail`、`/assessmentRecord/detail` 同样命中 ✓（本次不新增）|

---

## 5. 建议（**待裁定** ✗）

| 方案 | 内容 | 评价 |
| :-: | --- | --- |
| **A** ✅ | 只做 **R14 试卷浏览**（公开只读：名称/时长/总分/状态），**不放“开始答题”**；**题量不做**（§2bis 答案键暴露）| **已批准并实施** ✓；不含答题闭环（刻意如此）|
| **B** | R14 + R15（基于 `/saveAssessmentRecord` + `/saveAssessmentAnswer`），并在 UI 明示「分数由前端提交、可被伪造」 | ⚠️ 会把**不可信分数**呈现为测评结果 ⇒ 与“证据纪律”冲突 |
| **C** | 暂缓 R14/R15，先由你裁定**计分归属**（服务端计分 / 或明确接受客户端分值并登记） | ✅ 最稳；代价是本批无交付 |

**裁定（2026-10-20）：批准 A** ✓
1. **R14 = 试卷浏览**：名称 / 时长 / 总分 / 状态；**不含题量**（§2bis）；**不放**开始答题 / 开始测评 / 立即测试 / 进入答题 / 查看成绩 ✗；不创建记录、不计分 ✗；
2. **R15 暂缓 ⏸**：分值来源、答题归属、记录归属、计分一致性、写接口均未形成可信服务端闭环；
3. 拒绝 B ✗ —— 前端无法修复 `score` 伪造（后端不校验），做了只是把缺陷包装成功能。

---

## 6. 未验证 / 不能验证

| # | 项 | 原因 |
| :-: | --- | --- |
| 1 | `saveAssessmentAnswer` 实际伪造分值 | **需写操作** ✗（未批准）|
| 2 | 跨学生累加 / 跨学生删记录 | 同上 ✗ |
| 3 | `addUnsaved` 重复调用是否重复插入 | 同上 ✗ |
| 4 | 旧前端 `saveExamredetails` 是否曾在其他版本可用 | 无证据 ✗（只登记当前 404）|

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2 | 2026-10-20 | **口径修正 + 新发现**：① 级别区分为 **P1 Security** / **P1 Functional Blocker**（`DEF-ANSWER-ENDPOINT-MISSING` **非安全漏洞**）；② 新登记 **`SEC-QUESTION-ANSWER-PUBLIC` P1 Security**（`/assessmentQuestion/list` **匿名返回 `answer`/`analysis`**，`options` 文案含答案）；③ **R14 放弃“题量”**（计数须下载答案键）⇒ 仅 `id/name/durationMinutes/totalScore/status/statusLabel`；④ **方案 A 已批准并实施**，R15 暂缓 |
| v1 | 2026-10-20 | R14/R15 只读审计：R14 公开只读可用 ✓；**R15 分值由客户端提交并累加** ✗ + `studentId` 字面量 bug ✗ + `examrecordId` 无归属 ✗ + 旧端点 404 ✗；新登记 **P1×6 + P2×2**（全部 Unfixed）；建议 A（仅 R14）/ B / C **待裁定** ✗ |
