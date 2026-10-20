# S2-C1 —— owner+admin 删除授权收口（留言 / 收藏 / 预约）

> 日期：2026-10-20 ｜ 基线：`bb29b50`（S2-B 已推送，LEAD=0）
> 状态：**已实现 · 🚧 未提交** ✗ ｜ 改动 **4 文件**（3 控制器 + 1 守卫原语）

---

## 1. 改动清单

| 文件 | 变更 | 要点 |
| --- | :-: | --- |
| `com/security/OwnershipGuard.java` | 新增 | `assertOwnerOrAdmin(request, target, ownerStudentId)` —— 管理员放行、学生仅本人、**其余（含心理老师、未知）拒绝**、记录不存在拒绝（防存在性预言机）|
| `CounselorMessageController.java` | 改 `/delete` | 逐条 `assertOwnerOrAdmin` 后再 `removeByIds`（堵 `SEC-MESSAGE-DELETE-IDOR`）|
| `CounselorFavoriteController.java` | 改 `/delete` | 同上（堵 `SEC-FAVORITE-DELETE-IDOR`）|
| `CounselingAppointmentController.java` | 改 `/delete` | 同上（堵 `SEC-APPOINTMENT-DELETE-IDOR`）|

**为何不用 `assertOwnership`**：它会允许“心理老师按 counselorId 删除学生的数据”，而这三类资源是**学生个人数据**，教师不应具备删除权限 ⇒ 新增 owner+admin 专用原语。

---

## 2. 跨用户负向测试（9 项 + 匿名，全部实测）

| # | 场景 | 结果 |
| :-: | --- | --- |
| 1 | 学生 a1 删除自己的留言 | **200 code=0** ✓ |
| 2 | 学生 a1 删除 a2 的留言 | **403 code=403** ✓ |
| 3 | 管理员删除 a2 的留言 | **200 code=0** ✓ |
| 4 | 学生 a1 删除自己的收藏 | **200** ✓ |
| 5 | 学生 a1 删除 a2 的收藏 | **403** ✓ |
| 6 | 管理员删除 a2 的收藏 | **200** ✓ |
| 7 | 学生 a1 删除自己的预约 | **200** ✓ |
| 8 | 学生 a1 删除 a2 的预约 | **403** ✓ |
| 9 | 管理员删除 a2 的预约 | **200** ✓ |
| 10 | 匿名删除留言 | **HTTP 200 + `code=401`**（既有契约，未被破坏）✓ |

> 负向测试用**临时探针行**（`S2C1-*` 内容）完成；403 场景不产生删除，剩余探针行事后 SQL 清理。

---

## 3. 数据恢复核对

| 表 | 行数 | max(id) | 探针残留 |
| --- | :-: | :-: | :-: |
| `counselor_message` | **6** ✓ | 6 | 0 ✓ |
| `counselor_favorite` | **6** ✓ | 6 | 0 ✓ |
| `counseling_appointment` | **3** ✓ | 3 | 0 ✓ |

> `AUTO_INCREMENT` 因探针插入/删除产生漂移（不回退，属环境状态）；`auth_token` 测试令牌已清理（3 → 2 ✓，只删 `id>3`）。

---

## 4. 回归

| 项 | 结果 |
| --- | :-: |
| `mvn -B test` | **47 / 47** · BUILD SUCCESS ✓ |
| 匿名删除 | 仍按既有 401 契约拒绝 ✓ |
| 前端 | 0 改动 ✓（消息删除入口**未新增**，按裁定）|

---

## 5. 未做 / 不在本批 ✗

❌ 留言前端删除入口（按裁定不新增）· ❌ S2-C2（9 个 admin-only 资源）· ❌ `/detail/{id}` 死 API 删除 · ❌ 配置公开读取收紧 · ❌ 两个 404（`DEF-RECORD-ADMIN-ENDPOINT-MISSING` / `DEF-QUESTION-ADMIN-ENDPOINT-MISSING`）

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | S2-C1：`assertOwnerOrAdmin` + 3 个 `/delete` 收口；**10 项测试全通过**（本人 200 / 他人 403 / 管理员 200 / 匿名 401）；数据恢复（6/6/3、探针残留 0）；47/47；**未提交** ✗ |
