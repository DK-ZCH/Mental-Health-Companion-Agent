# Phase 3 / Step 7 · R11 —— 收藏（**待提交** ✗）

> **范围（已批准 ✓）**：`/me/favorites` 我的收藏 + `/counselors/:id` 收藏按钮接入 + `favoriteType` 固定 1（api 层唯一来源 ✓）
> **禁止项（遵守 ✓）**：❌ 后端修改 · ❌ `/detail/{id}` · ❌ `file` API · ❌ 修任何安全项 · ❌ 由 `counselorId` 推导删除目标
> **基线**：`a7a9a58`（R10 已推送 ✓）

---

## 1. 改动清单（**5 文件 = 2 新 + 3 改**）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `api/favorite.ts` | **A** | `FAVORITE_TYPE = 1`（内部常量 ✓）· `listMyFavorites()` ✓ · `findFavoriteByCounselor()` ✓ · `addFavorite()` ✓ · `removeFavorite()` ✓ |
| 2 | `views/FavoriteListView.vue` | **A** | `/me/favorites`：列表 + 确认框 + 取消 + 空态 ✓ |
| 3 | `views/CounselorDetailView.vue` | M | 收藏按钮：真实状态（已收藏/收藏）+ 切换 ✓；预约/留言仍为后续批次 ✓ |
| 4 | `router/index.ts` | M | `+ /me/favorites`（`requiresAuth` ✓）|
| 5 | `layouts/PublicLayout.vue` | M | `+ 收藏` 导航 |

**后端 0** ✗ · `file` API 0 ✗ · `/detail` 0 ✗

---

## 2. 验收（实测）

| 项 | 内容 | 结果 |
| :-: | --- | --- |
| **A 静态** | R11 视图/API 中 PII 字面量 | **0** ✓ |
| | `/detail/` | **0** ✓ |
| | `file` 端点 | **0** ✓ |
| | 写调用总数 | **3** = `student/update` + `favorite/add` + `favorite/delete` ✓ |
| | `FAVORITE_TYPE = 1` 单点常量 | ✓（调用方不各自传参 ✓）|
| | `vue-tsc --noEmit` | **exit 0** ✓ |
| **B 我的收藏** | A → | **共 2 条** ✓（= DB ✓）|
| **C 状态判断** | 老师 1（已收藏）| 按钮 = **已收藏（点击取消）** ✓ |
| | 老师 3（未收藏）| 按钮 = **收藏** ✓ |
| **D 新增** | 点「收藏」老师 3 | 变「已收藏」 ✓ · **DB 7 行**，新增 `id=75 s=1 c=3 t=1` ✓ · **原 id 3/6 完好** ✓ |
| **E 取消（R9 内）** | 点「已收藏（点击取消）」| **DB 6 行** ✓ · A 的 = **id 3(c1) + id 6(c2)** ✓✓ |
| **F 取消（列表页）** | 重新收藏 → `/me/favorites` 3 条 → 取消**新记录** | 确认框 ✓ → **DB 6 行** ✓ |
| **G 最终 DB** | **全表 = 基线** | `id 1..6` 的 `(student, counselor, type)` **逐条一致** ✓✓（`(3,2,1) (2,3,1) (1,1,1) (3,1,1) (2,2,1) (1,2,1)` ✓）|
| **H 回归 / Console** | `mvn test` · 浏览器 | **47 / 47** ✓ · **Errors 0 / Warnings 0** ✓ |

---

## 3. ⚠️ 测试过程中的一次**误点与中止**（如实登记）

| 步骤 | 事实 |
| --- | --- |
| 误判 | 我按「升序」推断第 3 个「取消收藏」按钮 = 新记录 ✗ —— 实际列表为**降序** ⇒ 该按钮解析为 `favorite-remove-3`（**原始记录** ✗）|
| 处置 | 在确认框弹出后**点击「取消」** ✓，未执行删除 ✓ |
| 复核 | `count=7` ✓、`id=3` 完好 ✓ |
| 纠正 | 改点第 1 个按钮，**先核对 CLI 解析出的 selector** = `favorite-remove-76` ✓（= 新增记录 ✓）后才确认 ✓ |
| 结果 | 新记录被删 ✓ → **DB 回到 6 行 ✓**，原始记录**从未被删** ✓ |

**教训（写入流程）** ✓：破坏性按钮必须先确认 selector / data-test 指向的记录，再确认 ✓。

---

## 4. ⚠️ 自增游标漂移（如实登记，非数据差异）

```text
AUTO_INCREMENT: 75 → 77        （新增 75/76 两条测试记录，删除后游标不回退）
行数据：id 1..6 与基线**完全一致** ✓
```

⇒ **记录集合等价** ✓；仅 `AUTO_INCREMENT` 游标前移 ⚠️（MySQL 正常行为 ✓，不影响业务 ✓）。

---

## 5. 未触发 / 未验证（✗）

| # | 项 | 说明 |
| :-: | --- | --- |
| 1 | **`code=511`「您已经收藏过了」** | 按钮态（已收藏 → 只提供取消）**在 UI 上阻止了重复收藏** ✓ ⇒ 未能在浏览器触发 ✗；该分支已由 `ApiError.code===511` 处理 ✓（代码 ✓）+ 后端契约已实测 ✓ |
| 2 | `SEC-FAVORITE-DELETE-IDOR` 越权删除 | **未执行** ✗（避免改动他人数据 ✓，代码已取证 ✓）|
| 3 | 多标签页/并发重复收藏 | 未测 ✗（涉及 `DEF-FAVORITE-NO-UNIQUE-INDEX` P2 ✓）|
| 4 | 未截图 ✗ | DOM + 计数 + DB 证据 ✓ |

---

## 6. 安全登记（**均 Identified / Unfixed，未修** ✗）

| ID | 级别 | 状态 |
| --- | :-: | :-: |
| `SEC-FAVORITE-DELETE-IDOR` | 🔴 **P1** | Identified / Unfixed（前端仅传自有 id ✓）|
| `DEF-FAVORITE-NO-UNIQUE-INDEX` | 🟡 P2 | Identified / Unfixed（前端固定 type=1 不解决并发 ✓）|
| `DEF-FAVORITE-ORPHAN-RISK` | 🟡 P2 | Identified / Unfixed |
| `SEC-DETAIL-IDOR` · `SEC-FILE-DOWNLOAD-TRAVERSAL`(P0) · `SEC-FILE-UPLOAD-ANON`(P1) · P2×2 | — | 全部维持 **Unfixed** ✓（R11 未触碰 ✗）|

---

## 7. 未做（✗）

未改后端 ✗ · 未修 `/delete` ✗ · 未修安全项 ✗ · 未碰 `file` ✗ · 未碰 `/detail` ✗ · 未与 R12/R13 混批 ✗

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | R11 实现（5 文件）· **A–H 全过**（A 2 条 ✓ · 已收藏/未收藏状态 ✓ · 新增 +1 且原记录完好 ✓ · 两处取消路径 ✓ · **最终 DB 记录集合逐条 = 基线** ✓ · Console 0 ✓ · **47/47** ✓）；如实登记**一次误点已中止** ⚠️ 与 **AUTO_INCREMENT 漂移** ⚠️；511 分支**未在 UI 触发**（按钮态阻止 ✓）✗；安全项全部维持 Unfixed ✓ |
