# Phase 3 / Step 7 · Batch 4 —— R1 + R17 + R18 设计与覆盖审计（**只读**）

> **性质**：只读审计 + 设计 ✓ —— **零代码改动** ✗ · 零提交 ✗
> **裁定范围**：R1 首页 IA（知识/通知/老师）· R17 记录详情 · R18 错题 —— **零写操作** ✓
> **403 口径已修订**（见 §6）✓
> **基线**：`31dd4a2`（Batch 3 已推送 ✓）· worktree CLEAN ✓

---

## 1. R1 首页 IA —— 纯前端，零新 API ✓

| 块 | API | 现状 |
| --- | --- | --- |
| 知识 | `GET /knowledgeArticle/list` ✓ | 已在 `api/content.ts` ✓ |
| 通知 | `GET /notification/list` ✓ | 已在 `api/content.ts` ✓ |
| 老师 | `GET /counselor/list` ✓ | 已在 `api/counselor.ts` ✓（**仅安全字段** ✓ 白名单投影 ✓）|

* 各块展示 **最新 3 条 + 「查看更多」** → `/knowledge` · `/notifications` · `/counselors` ✓
* **无需新 API** ✓ · **无需新路由** ✓（三条目标路由已存在 ✓）
* 老师块复用 `listCounselors()` ⇒ **PII 不进入视图** ✓（与 Batch 3 同一约束 ✓）

---

## 2. R17 记录详情 —— ⚠️ **必须避开 `/detail/{id}`** 🔴

### 2.1 实测：两条可用数据路径

| 请求 | 匿名 | 学生 A | 学生 B | admin |
| --- | :-: | :-: | :-: | :-: |
| `GET /assessmentRecord/list` | `code=401` ✓ | **1** ✓ | **空** ✓ | 1 ✓ |
| `GET /assessmentAnswer/list?recordNo=<A的>` | `code=401` ✓ | **5** ✓ | **空** ✓✓ | 5 ✓ |

> ✅ **`?recordNo=` 过滤是生效的** ✓（与 Batch 3 的 `?id=` **被忽略** ✗ 形成对照 ✓）
> ✅ 学生 B 拉 A 的 `recordNo` → **空** ✓✓ ⇒ **服务端隔离生效** ✓

**答案字段**（A 的 5 条）✓：`questionContent · questionOptions · studentAnswer · score · questionAnswer · questionAnalysis · questionTypeLabel · questionSortOrder` ⇒ **详情页数据充足** ✓（含解析 ✓）

### 2.2 🔴 实测：`/assessmentRecord/detail/{id}` **无归属校验**

| 请求 | 结果 |
| --- | --- |
| 匿名 `detail/1` | `code=401` ✓（需登录 ✓）|
| **A** `detail/1` | `code=0` ✓（本人）|
| **B** `detail/1` | **`code=0` ✗✗（读到 A 的记录）** 🔴 |

**代码取证** ✓：`AssessmentRecordController` 中
* `/info/{id}` → **L99 `OwnershipGuard.assertOwnership`** ✓
* `/update` → **L170 `assertWritableTarget`** ✓
* **`/detail/{id}` → L309，之后到 L344 无任何 `OwnershipGuard`** ✗

**抽查其他控制器** ✓：

| 控制器 | `detail/{id}` 守卫 |
| --- | :-: |
| `AssessmentRecord` | **无** ✗ |
| `AssessmentAnswer` | **无** ✗ |
| `AssessmentWrongQuestion` | **无** ✗ |
| `CounselorFavorite` | **无** ✗ |
| `CounselingAppointment` | **无** ✗ |
| `CounselorMessage` | **无** ✗ |

⇒ **`/detail/{id}` 是 6 个控制器共有的读取越权面** 🔴（`/info/{id}` 已治理 ✓，`detail` 未治理 ✗）。

### 2.3 本批处置（**不修后端** ✗）

| 做 | 不做 |
| --- | --- |
| ✅ R17 **只用** `/assessmentRecord/list` + `/assessmentAnswer/list?recordNo=`（均已隔离 ✓）| ❌ **前端禁止调用 `/detail/{id}`** ✗（静态验收项 ✓）|
| ✅ 记录本身：从 `list` 按 `recordNo` 前端匹配 ✓（A 仅 1 条 ✓）| ❌ 不修 `detail` 守卫 ✗（后端安全议题 ✓）|
| ✅ 登记 `SEC-DETAIL-IDOR` 🔴（见 §5）| ❌ 不使用 `?id=`/`?recordNo=` 过滤 `assessmentRecord/list`（**未验证生效** ⚠️，以客户端匹配为准 ✓）|

---

## 3. R18 错题 —— 直接可用 ✓

| 请求 | 结果 |
| --- | --- |
| 匿名 `/assessmentWrongQuestion/list` | `code=401` ✓ |
| **A** | **3 条** ✓（= DB ✓）|
| **B** | **空** ✓✓ |
| A **伪造** `?studentId=2` | **仍 3 条** ✓✓（服务端强制 ✓）|

字段 ✓：`id · studentId · paperId · questionId · studentAnswer · answeredAt · paperName · questionContent · questionOptions · questionAnswer · questionAnalysis · questionTypeLabel …`
⇒ 错题页可直接展示题干/我的答案/正确答案/解析 ✓

---

## 4. 隐私字段（沿用 Batch 2/3 纪律 ✓）

上述接口的响应**都含** `studentPhone` / `studentEmail` / `studentAvatarUrl` ✗（本人数据 ✓，但仍属 PII ✓）。

| 做 | 不做 |
| --- | --- |
| ✅ 页面**只渲染**：记录/题目/答案/得分/解析/时间 | ❌ **不渲染** `studentPhone` / `studentEmail` ✗ |
| ✅ 静态验收：R17/R18 视图 `phone|email` 命中 = **0** ✓ | ❌ 不改后端字段投影（phone-email 批次 ✓）|

---

## 5. 🟡 新登记（**不修** ✗，需你定级）

> **`SEC-DETAIL-IDOR`**：多控制器 `/detail/{id}` 缺归属校验 ⇒ 已登录学生可读取**他人**单条记录（实测：学生 B → `/assessmentRecord/detail/1` = 学生 A 的记录 ✓）。
> · 影响面：`assessmentRecord` · `assessmentAnswer` · `assessmentWrongQuestion` · `counselorFavorite` · `counselingAppointment` · `counselorMessage` 的 `/detail/{id}`
> · **未修复** ✗ · **未定级** ✗（建议 **P1 / 待裁定** —— 属既有读取越权，非本批引入 ✓）
> · 旧 `front` 的 `*/detail.html` 很可能依赖该端点 ⚠️（`front/` 未纳管 ✗，本批未核 ✓）
> · **本批前端不使用该端点** ✓ ⇒ **不影响 Batch 4 交付** ✓

---

## 6. 403 口径修订（**正式生效** ✓）

| 项 | 内容 |
| --- | --- |
| 原口径 | 403 = Batch 3 必验项 ✗ |
| **新口径** ✓ | **403 = 长期安全回归项；当前无自然业务触发场景，不要求人为制造请求** ✓ |
| 依据 | 403 仅在「已登录 + 越权资源」出现 ⇒ **正确 UI 不产生此类请求** ✓ |
| 已有证据 | 后端 403 实测 ✓ · `http.ts` 403 分支静态 ✓ · 401 有完整浏览器链路 ✓ |
| 未来验证时机 | R10 自身资源权限 ✓ · R11–R13 写操作 ✓ · 管理端入口 ✓ · 其他真实"登录但无权"UI ✓ |
| 负向探针 | **不做** ✗ |

> ⚠️ 说明：Batch 2/3 报告中"Batch 3 必验"字样为**历史记录** ✓，以本节为准 ✓（不改写历史文档 ✗）。

---

## 7. 拟定实现清单（**待批准** ✗）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `views/HomeView.vue` | M | 三块（知识/通知/老师）各 3 条 + 「查看更多」|
| 2 | `views/RecordDetailView.vue` | **A** | R17：记录摘要 + 答题明细（**不用 `/detail/{id}`** ✓）|
| 3 | `views/WrongQuestionListView.vue` | **A** | R18：错题列表（题干/我的答案/正确答案/解析）|
| 4 | `api/assessment.ts` | M | `+getRecordByNo()`（list 匹配 ✓）· `+listAnswers(recordNo)` ✓ · `+listWrongQuestions()` ✓ |
| 5 | `router/index.ts` | M | `+ /me/records/:recordNo` · `+ /me/wrong-questions`（均 `requiresAuth` ✓）|
| 6 | `layouts/PublicLayout.vue` | M | `+ 错题` 导航 |

**6 文件（2 新 + 4 改）** ✓ · **后端 0** ✗ · **零写操作** ✓

---

## 8. 拟定验收（A–H）

| 项 | 内容 |
| :-: | --- |
| **A 静态** | R17/R18 视图 `phone|email` = **0** ✓ · **`/detail` 调用 = 0** ✓ · 无 `axios.`/`Token` 手写 ✓ · 无 mock ✓ · `vue-tsc` = 0 ✓ |
| **B 路由** | `/me/records/:recordNo` ✓ · `/me/wrong-questions` ✓ · 未登录 → `/login?redirect=` ✓ |
| **C 数据** | A：记录 **1** ✓ · 明细 **5** ✓ · 错题 **3** ✓（= DB ✓ 非 Mock ✓）· 空 `recordNo` → 空态 ✓ |
| **D 权限** | 匿名 401 → 前端回登录 ✓ · **学生 B 打开 A 的 `recordNo` → 空态（不泄露）** ✓✓ · 伪造 `studentId` 无效 ✓ · admin 按原行为 ✓ |
| **E 403** | 按新口径：**不作要求** ✓（若自然出现则记录）|
| **F Console** | 0 error / 0 warning ✓ |
| **G 回归** | `mvn 47/47` ✓（后端未改 ✗）|
| **H Git** | 单一提交 ✓（6 文件 + 本审计记录）|

---

## 9. 未做（✗）

未改代码 ✗ · 未提交 ✗ · 未修 `detail` 守卫 ✗ · 未核旧 `front` 是否使用 `detail` ⚠️ · 未做 `npm run build` ✗ · 未做负向 403 探针 ✗

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | Batch 4 只读审计：R1 零新 API ✓；R17 **必须避开 `/detail/{id}`**（实测 B 可读 A 记录 🔴，6 控制器均无守卫 ✗）；R18 直接可用（A=3/B=空/伪造参数无效 ✓）；权限矩阵四角色 ✓；登记 `SEC-DETAIL-IDOR` 🔴；**403 口径修订为长期挂账** ✓；6 文件清单 + A–H 验收（**待批准** ✗）|
