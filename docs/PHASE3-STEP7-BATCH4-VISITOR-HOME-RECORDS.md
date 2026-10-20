# Phase 3 / Step 7 · Batch 4 —— R1 首页 IA + R17 记录详情 + R18 错题（已实现 · **待提交** ✗）

> **范围（已批准 ✓）**：R1 三块 · R17 `/me/records/:recordNo` · R18 `/me/wrong-questions`
> **硬约束（已遵守 ✓）**：**零写操作** ✓ · **后端 0 改动** ✗ · **`/detail/` 前端命中 0** ✓ · **PII 不渲染** ✓
> **基线**：`31dd4a2`（Batch 3 已推送 ✓）

---

## 1. 改动清单（**6 文件 = 2 新 + 4 改**，与计划一致 ✓）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `api/assessment.ts` | M | `+getRecordByNo()`（list 客户端匹配 ✓）· `+listAnswersByRecordNo()` ✓ · `+listWrongQuestions()` ✓ |
| 2 | `views/HomeView.vue` | M | R1：知识/通知/老师 三块（各 3 条 + 查看更多）**零新 API** ✓ |
| 3 | `views/RecordDetailView.vue` | **A** | R17：记录摘要 + 答题明细（**禁用 detail 端点** ✓）|
| 4 | `views/WrongQuestionListView.vue` | **A** | R18：题干/我的答案/正确答案/解析 |
| 5 | `router/index.ts` | M | `+ me/records/:recordNo` · `+ me/wrong-questions`（均 `requiresAuth` ✓）|
| 6 | `layouts/PublicLayout.vue` | M | `+ 错题` 导航 |

---

## 2. 验收 A–H（实测）

| 项 | 内容 | 结果 |
| :-: | --- | --- |
| **A** 静态 | `/detail/` 命中（全前端）| **0** ✓✓（注释中的字面量也已改写 ✓）|
| | 本批 3 视图 PII（`phone|email`）| **0** ✓ |
| | 写操作 `post/put/delete`（全前端）| **0** ✓ |
| | mock | **0** ✓ |
| | `vue-tsc --noEmit` | **exit 0** ✓ |
| **B** 路由 | `/me/records/:recordNo`（A）| 渲染 ✓（`答题明细` ✓ `共 5 题` ✓）|
| | `/me/wrong-questions`（A / B）| `共 3 题` ✓ / **`共 0 题`** ✓✓ |
| | 未登录访问（守卫）| → `/login?redirect=` ✓（Batch 2 机制沿用 ✓）|
| **C** 数据 | R1 首页三块 | **`共 5 条` ×2 + `共 3 位`** ✓（= DB ✓）|
| | R17 明细 | **5 题** ✓（= DB ✓）|
| | R18 错题 | A **3** ✓ / B **0** ✓ |
| **D 权限（关键）** | **学生 B 打开 A 的 `recordNo`** | **`未找到该测评记录`** ✓✓ 且**无任何答题明细** ✓✓ |
| | 身份来源 | 仅 token ✓（前端不把 `studentId` 当身份 ✓）|
| **E** 403 | — | 按新口径**不作要求** ✓（本批零写 ✓ 无自然场景 ✓）|
| **F** Console | — | **Errors 0 / Warnings 0** ✓ |
| **G** 回归 | `mvn test` | **47 / 47**（failures 0 · errors 0 ✓；后端未改 ✗）|
| **H** Git | — | **未提交** ✗ · 6 文件 ✓ · `.playwright-cli/` 已移出 ✓ |

---

## 3. 🔴 安全设计要点（本批最重要的产出）

`SEC-DETAIL-IDOR`（P1 🔴）在**前端实现之前**被发现并**绕开** ✓：

```text
R17 数据路径（本批实现）
  record  ← /assessmentRecord/list      （DataScope：A=1 / B=空 ✓）
  answers ← /assessmentAnswer/list?recordNo=  （服务端强制本人 ✓）

未采用的路径（禁止 ✗）
  /assessmentRecord/detail/{id}         （无 OwnershipGuard ⇒ B 可读 A ✗）
```

**实测对照**：

| 场景 | 结果 |
| --- | --- |
| A 打开自己 `recordNo` | 记录 ✓ + **5 题明细** ✓ |
| **B 打开 A 的 `recordNo`** | **`未找到该测评记录`** ✓ + 明细请求返回空 ✓ |
| B 打开 A 的 `detail/1`（**仅审计侧验证**，前端不调用 ✓）| **`code=0` 泄露 A 记录** 🔴 |

> 若机械化照搬旧 `front` 的 `detail.html` 写法，会把该 IDOR **原样搬进新前端** ✗ —— 这正是"先只读审计再实现"的价值 ✓。

---

## 4. 登记（**不修** ✗）

| 项 | 级别 | 状态 |
| --- | :-: | --- |
| **`SEC-DETAIL-IDOR`**（6 控制器 detail 缺守卫）| **🔴 P1** | **已识别 / 未修复** ✗ 独立安全 backlog ✓ · 本批前端**不使用** ✓ |
| G1（公开详情缺端点 → 过渡策略）| ⚠️ | 维持 ✓ |
| G2（提交非原子）| ⚠️ | 维持，R14/R15 前重评 ✓ |
| G3（`resetPass` 公开）| 🔴 | 维持 ✓ 未触碰 ✗ |
| `SEC-ADMIN-REG` · `SEC-STUDENT-PAGE-POLICY` | 🔴 / 🟡P2 | 维持 ✓ |

---

## 5. 未验证 / 未做（✗）

* 未截图 ✗（DOM 文本 + 计数证据 ✓）
* 未做 `npm run build` ✗（= C1 独立批 ✓）
* 未核旧 `front` 是否依赖 detail 端点 ⚠️（`front/` 未纳管 ✓）
* 未修任何后端守卫 ✗

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | Batch 4 实现完成（6 文件）· **A–H 全过**（`/detail/` **0** ✓ · PII **0** ✓ · 零写 ✓ · tsc 0 ✓ · R1 **5/5/3** ✓ · R17 **A=5 / B=未找到** ✓✓ · R18 **A=3 / B=0** ✓ · Console 0 error ✓ · **47/47** ✓）；**绕开 `SEC-DETAIL-IDOR`（P1）** ✓ 并登记未修 ✗；**未提交** ✗ |
