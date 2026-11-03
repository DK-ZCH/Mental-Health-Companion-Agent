# S2-C-0 —— 删除 / 详情端点的归属模型只读审计

> 日期：2026-10-20 ｜ 基线：`bb29b50`（S2-B 已推送，LEAD=0）
> 模式：**只读审计** —— 不改 Controller/Service/Mapper/DB/前端/测试 ✗，不 commit ✗
> 核心结论：**`/delete` 需按资源拆成「owner-only」与「admin-only」两档；`/detail/{id}` 全仓库零调用，可整体收口。**

---

## 1. 方法

- 逐资源检索 `/delete`、`/detail`、`/info` 在旧 `front/`、管理端 `admin/src`、新 `frontend/src` 的真实调用方
- 结合 S1 静态表（文件级 `OwnershipGuard` 命中 = 0 ⇒ 该文件 `/delete` 必无归属校验）
- 引用既有实测/登记（`SEC-*-DELETE-IDOR`、`SEC-DETAIL-IDOR`）

---

## 2. `/delete` 调用方矩阵（实测）

| 资源 | 旧 front（学生）| 新 frontend（学生）| 管理端 admin/src | 结论 |
| --- | :-: | :-: | :-: | --- |
| 留言 `counselorMessage` | — | **不提供删除**（R13）| `list.vue:725` ✓ | admin 在用 |
| 收藏 `counselorFavorite` | `xinlilaoshiCollection/list.html:406` ✓（取消）| `removeFavorite` ✓（自有 id）| `list.vue:695` ✓ | 学生 + admin |
| 预约 `counselingAppointment` | — | `removeAppointment` ✓（自有 id）| `list.vue:846` ✓ | 学生 + admin |
| 试卷 `assessmentPaper` | — | — | `list.vue:497` ✓ | admin |
| 题目 `assessmentQuestion` | — | — | `list.vue:512` → **`deleteExamquestion`（404 ✗）** | admin（调用已坏）|
| 知识 `knowledgeArticle` | — | — | `list.vue:682` ✓ | admin |
| 通知 `notification` | — | — | `list.vue:682` ✓ | admin |
| 配置 `config` | — | — | `list.vue:428` ✓ | admin |
| 字典 `sysDictItem` | — | — | 多个 `dictionary*.list.vue:370` ✓ | admin |
| 记录 `assessmentRecord` | — | — | `list.vue:508` → **`deleteExamrecord`（404 ✗）** | admin（调用已坏）|
| 作答 `assessmentAnswer` | — | — | `list.vue:493` ✓ | admin |
| 错题 `assessmentWrongQuestion` | — | — | `list.vue:564` ✓ | admin |

---

## 3. `/detail/{id}` 调用方矩阵（实测，**关键结论**）

对 `/(detail|info)/` 的全仓库检索命中 **18 处，全部是 `/info/{id}`**；`/detail/{id}` 命中 **0**。

⇒ **`/detail/{id}`（前端详情）在整个仓库（旧 front + 管理端 + 新前端）没有任何调用方** ✓
⇒ 既有的 `SEC-DETAIL-IDOR`（`/detail/{id}` 无归属守卫）虽是真实越权读能力，但**当前无活跃消费**，收口风险极低。

---

## 4. 资源 × 操作 × 角色 × 归属模型（据实落定，非预设）

| 资源 | `/delete` 归属模型 | `/detail` 现状 | 依据 |
| --- | --- | --- | --- |
| **留言** | **owner + admin** | 无守卫 · 零调用 | 学生语义上应可删自己的（当前前端未暴露，见待裁定 #1）；admin 在用 |
| **收藏** | **owner + admin** | 无守卫 · 零调用 | 学生（旧+新）取消自己的；admin 在用 |
| **预约** | **owner + admin** | 无守卫 · 零调用 | 学生（新）取消自己的；admin 在用 |
| **试卷** | **admin-only** | 无守卫 · 零调用 | 仅 admin；学生只读（R14）|
| **题目** | **admin-only** | 无守卫 · 零调用 | 仅 admin（其端点 404）|
| **知识** | **admin-only** | 无守卫 · 零调用 | 仅 admin |
| **通知** | **admin-only** | 无守卫 · 零调用 | 仅 admin |
| **配置** | **admin-only** | `@IgnoreAuth` **匿名**（仅轮播图，内容良性）| 仅 admin 删 |
| **字典** | **admin-only** | 无 `/detail` | 仅 admin |
| 记录 / 作答 / 错题 | **admin-only** | 无守卫 · 零调用 | 学生不得删测评历史（只读 R16–R18）；admin 在用 |

> 「其他用户」一律 **拒绝**（fail-closed）✓

---

## 5. 已确认的守卫缺口（对齐既有登记）

| 资源 | `/delete` | `/detail` | 既有 ID |
| --- | :-: | :-: | --- |
| 留言 | **无** ✗ | 无 ✗ | `SEC-MESSAGE-DELETE-IDOR` · `SEC-DETAIL-IDOR` |
| 收藏 | **无** ✗ | 无 ✗ | `SEC-FAVORITE-DELETE-IDOR` · `SEC-DETAIL-IDOR` |
| 预约 | **无** ✗ | 无 ✗ | `SEC-APPOINTMENT-DELETE-IDOR` · `SEC-DETAIL-IDOR` |
| 试卷 | **无** ✗ | 无 ✗ | `SEC-PAPER-DELETE-IDOR` |
| 题目 / 知识 / 通知 / 配置 / 字典 | **无** ✗（S1 文件级 `OwnershipGuard=0`）| 无（配置为匿名）✗ | 见 S1 |
| 记录 / 作答 / 错题 | **无** ✗ | 无 ✗ | `SEC-RECORD-DELETE-IDOR` 等 |

---

## 6. 本轮新登记（不修）

| ID | 级别 | 事实 |
| --- | :-: | --- |
| **`DEF-RECORD-ADMIN-ENDPOINT-MISSING`** | 🔴 **P1 Functional Blocker**（非安全）| 管理端 `assessmentRecord/deleteExamrecord` → **404**；真实映射为 `deleteAssessmentRecord` ⇒ 管理端删记录不可用 |
| （已有）`DEF-QUESTION-ADMIN-ENDPOINT-MISSING` | 🔴 P1 Functional | 管理端 `assessmentQuestion/deleteExamquestion` → 404（本次再确认）|

---

## 7. 建议的 S2-C 实现方向（**待裁定，本批不实施** ✗）

```text
A. owner + admin（留言 / 收藏 / 预约）
   /delete 加归属校验：学生 → 仅本人记录；admin → 放行；其他 → 403
   （复用 assertWritableTarget + resolveWriteOwner 同款思路，但 delete 需要专门断言）

B. admin-only（试卷 / 题目 / 知识 / 通知 / 配置 / 字典 + 记录/作答/错题）
   /delete 加 assertAdminOnly

C. /detail/{id}
   全仓库零调用 ⇒ 三种候选：
   ① 删除端点   ② 重定向/复用 /info/{id}   ③ 加 assertOwnership 对齐 /info
   （推荐 ②或③；①需业务确认无外部依赖）
```

---

## 8. 待裁定项

| # | 问题 | 说明 |
| :-: | --- | --- |
| 1 | 留言是否开放「学生删自己」入口 | 当前新前端未暴露；决定 delete 守卫是否需学生分支 |
| 2 | 记录/作答/错题的 `/delete` 是否确为 admin-only | 学生是否应能删自己的测评历史（当前前端只读）|
| 3 | `/detail/{id}` 处理方式 | 删除 / 复用 /info / 加 guard 对齐 |
| 4 | 配置 `/detail/{id}` 是否收紧 | 内容仅轮播图，是否仍允许匿名 |
| 5 | S2-C 分批 | 是否「owner 3 资源」与「admin-only 9 资源」拆两个实现批次 |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | S2-C-0 只读审计：`/delete` 拆 **owner+admin（留言/收藏/预约）** 与 **admin-only（6 内容/配置 + 记录/作答/错题）**；**`/detail/{id}` 全仓库零调用**（18 处调用全为 `/info`）；新登记 `DEF-RECORD-ADMIN-ENDPOINT-MISSING`；5 项待裁定；**零改动** ✓ |
