# Phase 3 / Step 7 · R11 —— 收藏（`/counselorFavorite/*`）**写契约审计**（只读）

> **性质**：只读审计 ✓ —— **未改后端** ✗ · **未改 Vue** ✗ · **未执行任何写端点** ✗
> **基线**：`a7a9a58`（R10 已推送 ✓）· worktree CLEAN ✓

---

## 1. 结论表

| 审计项 | 实际（实测 / 代码） | R11 结论 |
| --- | --- | --- |
| **`/add`（前端保存）** | `resolveWriteOwner(ADD_FAVORITE)` ⇒ **学生强制本人** ✓；查重 `(counselor_id, student_id, favorite_type)` ✓；重复 → **`511「您已经收藏过了」`** ✓；服务端写 `favorited_at`/`created_at` ✓ | ✅ **采用**（唯一新增途径 ✓）|
| **`/save`（后端保存）** | 按角色设 `studentId`/`counselorId` ✓；同样查重 ✓；重复 → `511「表中有相同数据」` ✓ | ⚠️ 不用（`/add` 更严格 ✓）|
| **`/update`** | `assertWritableTarget` ✓ + `resolveWriteOwner` ✓ + 查重 ✓ + `UpdateGuard` ✓ | ❌ **R11 不需要**（收藏无可编辑业务字段 ✓）|
| **`/delete`** | **`removeByIds(Arrays.asList(ids))`，无任何归属校验** ✗🔴 | ⚠️ **唯一取消途径**（前端只传**自己 list 里的 id** ✓）→ 登记 🔴 |
| **`/list` / `/page`** | 均注入 `DataScope.apply(request, params, "studentId", "counselorId")` ✓；实测 A → **2 条** ✓（自己 id=3/6 ✓）| ✅ 可用 |
| **`/info/{id}`** | `assertOwnership(studentId, counselorId)` ✓；实测 A 访问 **他人** `info/1` → **403** ✓✓ | ✅ 安全（R11 不需要）|
| **`/detail/{id}`** | **无守卫** ✗（= 已登记 `SEC-DETAIL-IDOR` P1 ✓）| ❌ **禁用** ✗ |
| **`/batchInsert`** | 旧批量导入（依赖 `static/upload` + session ✓）；无业务价值 ✓ | ❌ 不用 ✗ |
| **归属语义** | 收藏 = **(学生, 老师, 类型)** 三元组 ✓；`studentId` **服务端决定** ✓；`counselorId` 由前端选择（老师侧被强制为本人 ✓）| ✅ |
| **唯一性** | 应用层查重 ✓；**DB 无唯一索引** ✗（仅 `PRIMARY KEY(id)` ✓）⇒ **并发竞态可产生重复** ⚠️ | ⚠️ 前端必须处理 511 ✓ |
| **`favoriteType`** | 字典 `counselor_favorite_type` **仅 `1 = 收藏`** ✓；**若传 null ⇒ `eq(favorite_type, null)` 永不匹配 ⇒ 查重被绕过** ✗⚠️ | ⚠️ **前端必须显式传 `1`** ✓ |
| **状态语义** | **无 `status` 字段** ✓（无审核/状态机 ✓）| ✅ 模型简单 ✓ |
| **DB** | `counselor_favorite(id, counselor_id, student_id, favorite_type, favorited_at, created_at)` ✓；现有 **6 行** ✓；**无外键** ✗ | ⚠️ 前端只传真实 `counselorId` ✓ |
| **R9 对接** | `GET /counselorFavorite/list?counselorId=<id>` → **1** ✓（A 收藏老师1）/ **0** ✓ ⇒ **可用于"是否已收藏"** ✓✓ | ✅ 明确 ✓ |
| **失败契约** | 已收藏 → `511` ✓ · 取消 → `code=0` ✓ · `info` 他人 → **403** ✓ · **目标不存在 → 无校验（会插入孤儿）** ✗ · 未登录 → `401`（HTTP 200 + code ✓）| ⚠️ 见 §4 |

---

## 2. 端点在读/写面的完整清单（实测 + 代码）

| 端点 | 鉴权 / 范围 | R11 用否 |
| --- | --- | :-: |
| `POST /counselorFavorite/add` | 登录 ✓；`studentId` 服务端强制 ✓；查重 ✓ | ✅ **新增收藏** |
| `POST /counselorFavorite/delete` | 登录 ✓；**无归属校验** ✗ | ⚠️ **取消收藏（仅传自有 id）** |
| `GET /counselorFavorite/list` | `DataScope` ✓；`?counselorId=` **过滤生效** ✓ | ✅ **我的收藏 / 是否已收藏** |
| `GET /counselorFavorite/page` | `DataScope` ✓ | ✅（分页可选）|
| `GET /counselorFavorite/info/{id}` | `assertOwnership` ✓（他人 → 403 ✓）| ❌ 不需要 |
| `GET /counselorFavorite/detail/{id}` | **无守卫** ✗ | ❌ **禁用** |
| `POST /counselorFavorite/update` | 守卫齐全 ✓ | ❌ 不需要 |
| `POST /counselorFavorite/save`、`/batchInsert` | — | ❌ 不用 |

---

## 3. 数据现状（DB 实测 ✓）

```text
counselor_favorite: rows = 6
id=1 student=3 counselor=2 type=1
id=2 student=2 counselor=3 type=1
id=3 student=1 counselor=1 type=1   ← A(a1) 的
id=4 student=3 counselor=1 type=1
id=5 student=2 counselor=2 type=1
id=6 student=1 counselor=2 type=1   ← A(a1) 的
```

* A 的 `/list` = **2** ✓（id 3、6 ✓ = 自己 ✓）
* A 的 `/list?counselorId=1` = **1** ✓（已收藏老师 1 ✓）
* A 的 `/info/1` = **403** ✓（id=1 属 student 3 ✓）

---

## 4. 失败契约（逐个明确）

| 场景 | 结果 | 前端处理 |
| --- | --- | --- |
| 未登录点收藏 | HTTP 200 + `code=401` ✓ | 拦截器清态 + 跳登录（`?redirect=`）✓ |
| 已收藏再收藏 | `code=511`「您已经收藏过了」✓ | 提示 + **刷新收藏状态**（可能已被其他标签页收藏 ✓）|
| 取消收藏成功 | `code=0` ✓ | 刷新列表 ✓ |
| 取消**不存在/已删** id | `removeByIds` → 影响 0 行 ✓ 仍返回 `code=0` | 视为成功 + 刷新 ✓ |
| `counselorId` 不存在 | **无校验 → 会插入孤儿行** ✗⚠️ | **只从 R8/R9 的真实 id 传入** ✓ |
| 学生改他人 `info` | **403** ✓ | 不需处理（前端不调用 ✓）|
| 学生传他人 id 调 `/delete` | **会成功删除** ✗🔴 | 前端**不构造**此类请求 ✓（登记 backlog ✓）|

---

## 5. 🔴 新登记（**不修** ✗）

| ID | 级别建议 | 内容 |
| --- | :-: | --- |
| **`SEC-FAVORITE-DELETE-IDOR`** | 🔴 **P1** | `/counselorFavorite/delete` 无归属校验 ⇒ 任一登录用户可按 id 删除**他人**收藏 ✗（代码取证 ✓，未执行删除 ✓）|
| `DEF-FAVORITE-NO-UNIQUE-INDEX` | 🟡 P2 | 唯一性仅应用层 ⇒ 并发可重复收藏 ⚠️ |
| `DEF-FAVORITE-ORPHAN-RISK` | 🟡 P2 | 无外键 + `/add` 不校 `counselorId` ⇒ 可产生孤儿收藏 ⚠️ |

> **本批（R11）不修** ✗ · 与 `SEC-DETAIL-IDOR` / `SEC-FILE-*` 一样保持 **Identified / Unfixed** ✓

---

## 6. 隐私字段（沿用纪律 ✓）

`/list` 与 `/info` 响应含 **`studentPhone`/`studentEmail`/`counselorPhone`/`counselorEmail`** ✗
⇒ R11 页面**只渲染**：`counselorName` · `counselorAvatarUrl` · `counselorExpertise` · `favoriteTypeLabel` · `favoritedAt` ✓
⇒ 静态验收：R11 视图 `phone|email` 命中 = **0** ✓

---

## 7. R9 对接设计（**待批准** ✗）

```text
/counselors/:id  「收藏」按钮
  未登录 → /login?redirect=/counselors/{id}            （现有 ✓）
  已登录 → GET /counselorFavorite/list?counselorId={id}
             ├─ 1 条 → 显示「已收藏」+ 点击 = 取消（DELETE 自有 id）
             └─ 0 条 → 显示「收藏」 → POST /add {counselorId, favoriteType:1}
```

* **`favoriteType` 恒为 `1`** ✓（字典仅此项；传 null 会绕过查重 ✗）
* 取消时 **只使用本页 list 返回的 `id`** ✓（不拼、不猜 ✓）
* 失败：`511` → 提示「已收藏」并刷新状态 ✓

---

## 8. 拟定实现清单（**待批准后才写** ✗）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `api/favorite.ts` | **A** | `listMyFavorites()` · `isFavorited(counselorId)` · `addFavorite(counselorId)`（type=1 ✓）· `removeFavorite(id)` |
| 2 | `views/FavoriteListView.vue` | **A** | `/me/favorites`：列表 + 取消 + 空态 |
| 3 | `views/CounselorDetailView.vue` | M | 收藏按钮接入（替换「后续批次开放」↑ 仅此一个入口 ✓）|
| 4 | `router/index.ts` | M | `+ /me/favorites`（`requiresAuth` ✓）|
| 5 | `layouts/PublicLayout.vue` | M | `+ 收藏` 导航 |

**5 文件（2 新 + 3 改）** · 后端 **0** ✗ · `file` API **0** ✗ · `/detail` **0** ✗

**验收草案（A–H）**：

| 项 | 内容 |
| :-: | --- |
| A 静态 | `phone\|email` = **0** ✓ · `/detail/` = **0** ✓ · `file` 端点 = **0** ✓ · 写操作 = **2**（add / delete，允许 ✓）· `favoriteType` 常量 `1` 显式 ✓ |
| B 我的收藏 | A → **2 条**（= DB ✓）· 取消 → 变 **1** → 再取消 → **0** → **恢复**（重新 add ✓）|
| C 已收藏判断 | R9 页对老师1 → 「已收藏」✓；对老师3 → 「收藏」✓ |
| D 失败契约 | 重复收藏 → `511` 提示 ✓ |
| E 身份 | 收藏 `studentId` 由服务端决定 ✓（前端不提交 ✓）|
| F Console | 0 error ✓ |
| G 回归 | `mvn 47/47` ✓（后端未改 ✗）|
| H Git/DB | 单一提交 ✓ · **DB 恢复基线 6 行** ✓ |

---

## 9. 未做（✗）

未执行任何写端点 ✗ · 未改后端 ✗ · 未改 Vue ✗ · 未修任何安全项 ✗ · 未测 `/delete` 越权（避免改动数据 ✓，代码已取证 ✓）

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | R11 写契约审计：采用 `/add`（服务端强制 studentId ✓ + 查重 ✓ + 511 ✓）+ `/list?counselorId=`（**过滤生效** ✓）+ `/delete`（**无守卫 ✗ ⇒ 仅传自有 id**）；`favoriteType` 必须显式 =1（否则绕过查重 ⚠️）；DB 无唯一索引/无外键；登记 `SEC-FAVORITE-DELETE-IDOR` P1 + P2×2；5 文件清单 + A–H（**待批准** ✗）|
