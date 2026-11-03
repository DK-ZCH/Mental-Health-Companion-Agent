# Phase 3 / Step 7 · R12 —— 预约（`/counselingAppointment/*`）**写契约审计**（只读）

> **性质**：只读 ✓ —— **未改后端** ✗ · **未改 Vue** ✗ · **未执行任何写端点** ✗
> **基线**：`828a076`（R11 已推送 ✓）· worktree CLEAN ✓

---

## 1. 🔴 核心结论：**删除 / 取消 / 状态变更 三者严格区分**

| 概念 | 本系统实际 | 证据 |
| --- | --- | --- |
| **状态字典** | `appointment_status` = **`1 待审核` / `2 同意` / `3 拒绝`** | DB 字典实测 ✓ |
| **"已取消"状态** | **不存在** ✗✗ | 字典仅 3 项 ✓ |
| **`status=3` 的语义** | **老师侧审核「拒绝」** ✗ —— **不是学生取消** | 语义 + `reviewComment`（审核意见）字段 ✓ |
| **`/delete`** | `removeByIds(ids)` **物理删除** ✓ | 代码 L186 ✓ |
| **`/update`** | 可改 `status`/`date`/`slot`，**无状态机校验** ✗ | 代码 L149–178 ✓ |

> ⇒ **R12 的"取消预约"只能走 `/delete`（物理删除）** ✓
> ⇒ **绝不能用 `status=3` 冒充"取消"** ✗（那是老师的审核动作 ✓，会造成语义污染 ✗）
> ⇒ R12 前端**不支持任何状态修改** ✗（审核/同意/拒绝属老师侧，未实现 ✓）

---

## 2. 端点清单与 R12 取舍

| 端点 | 鉴权 / 范围 | R12 |
| --- | --- | :-: |
| `POST /counselingAppointment/add` | 登录 ✓；**校验老师存在** ✓（`511 查不到该心理老师`）；**`studentId = 当前用户`** ✓（服务端强制）；`appointmentNo = 时间戳` ✓；`appliedAt/createdAt` ✓；**`status` 不设默认** ✗ | ✅ **新增预约** |
| `POST /counselingAppointment/delete` | **无归属校验** ✗🔴 | ⚠️ **取消（仅传自有 id）** |
| `GET /counselingAppointment/list` | `DataScope` ✓；**`?status=` 过滤生效** ✓ | ✅ **我的预约** |
| `GET /counselingAppointment/page` | `DataScope` ✓ | ✅（分页可选）|
| `GET /counselingAppointment/info/{id}` | `assertOwnership` ✓（A→own 200 ✓；**不存在 → 403** ⚠️ 非 404）| ❌ 不需要 |
| `GET /counselingAppointment/detail/{id}` | **无守卫** ✗（= `SEC-DETAIL-IDOR` P1） | ❌ **禁用** |
| `POST /counselingAppointment/update` | 守卫齐全 ✓；但**查重被 `.eq("id",0)` 短路** ✗、**无状态机** ✗ | ❌ **R12 不用** |
| `POST /counselingAppointment/save`、`/batchInsert` | 旧后端/导入 | ❌ 不用 |

---

## 3. 字段与字典

**实体** ✓：`id · appointmentNo · counselorId · studentId · appointmentDate(Date) · timeSlot · status · reviewComment · appliedAt · createdAt`

**字典 `time_slot`** ✓（8 项，`item_code` 即 `time_slot` 值）：

```text
1 08:00-09:00   2 09:00-10:00   3 10:00-11:00   4 11:00-12:00
5 14:00-15:00   6 15:00-16:00   7 16:00-17:00   8 17:00-18:00
```

**列表响应** ✓：含 `timeSlotLabel` · `statusLabel` ✓（可直接展示）
⚠️ 同时含 `studentPhone/Email` · `counselorPhone/Email` ✗ ⇒ **不渲染** ✓

---

## 4. 数据现状（DB 实测 ✓）

```text
counseling_appointment: AUTO_INCREMENT=5，3 行
id=1 no=1648525919361 s=1 c=2 date=2022-03-30 slot=8 status=1
id=2 no=1648530223381 s=1 c=3 date=2022-03-30 slot=8 status=1
id=3 no=1648530268888 s=1 c=1 date=2022-03-31 slot=3 status=2
```

* **全部属 student 1（A）** ✓ ⇒ **B 的 `/list` = 空** ✓✓（天然隔离 ✓）
* A 的 `/list` = **3** ✓ · `?status=2` = **1** ✓

**表约束** ✗：仅 `PRIMARY KEY(id)` —— **无唯一索引** · **无外键** ⚠️

---

## 5. 归属与身份边界

```text
/add    ⇒ studentId = CurrentUserProvider.requireCurrentUserId()  ✓（服务端强制，前端不提交）
/update ⇒ assertWritableTarget + resolveWriteOwner ✓（R12 不用）
/list   ⇒ DataScope(studentId, counselorId) ✓
/info   ⇒ assertOwnership ✓
/delete ⇒ 【无任何校验】✗🔴
```

---

## 6. 🔴 新登记（**不修** ✗）

| ID | 级别建议 | 内容 |
| --- | :-: | --- |
| **`SEC-APPOINTMENT-DELETE-IDOR`** | 🔴 **P1** | `/delete` 无归属校验 ⇒ 任一登录用户可按 id 删**他人**预约 ✗（代码取证 ✓，未执行 ✗）|
| **`SEC-APPOINTMENT-STATUS-SPOOF`** | 🔴 **P1** | `/add` 与 `/update` **均不限制 `status`** ✗ ⇒ 学生可直接创建/改成 **`status=2 同意`** ✗（**绕过老师审核** ⚠️）|
| `DEF-APPOINTMENT-NO-UNIQUE` | 🟡 P2 | 无唯一索引（`student+counselor+date+slot`）⇒ 可重复预约 ⚠️ |
| `DEF-APPOINTMENT-SLOT-CONFLICT` | 🟡 P2 | `/add` **无时段冲突校验** ✗ ⇒ 同一老师同一时段可被多人预约 ⚠️ |
| `DEF-APPOINTMENT-NO-PAST-CHECK` | 🟡 P2 | 无「过去日期」校验 ✗ ⇒ 可预约历史日期 ⚠️ |
| ⚠️ 观察 | — | `/info` 对**不存在**的 id 返回 **403**（而非 404）⚠️（契约瑕疵，不单列风险）|

> **R12 前端必须自身固定 `status = 1`** ✓（api 层常量 ✓，与 R11 `favoriteType=1` 同法 ✓）—— 这只能**降低**误用，不能修复后端 ✗

---

## 7. R9 对接设计（**待批准** ✗）

```text
/counselors/:id  「预约」按钮
  未登录 → /login?redirect=/counselors/{id}          （现有 ✓）
  已登录 → 打开预约表单（日期 + 时段）
             · 日期必须 ≥ 今天（前端校验 ✓，后端无校验 ✗）
             · 时段来自字典 time_slot（8 项 ✓）
             · 提交 → POST /add { counselorId, appointmentDate, timeSlot, status: 1 }
             · 成功 → 跳 /me/appointments（或留页提示 ✓）
```

**取消预约**（`/me/appointments`）：

```text
点击「取消预约」→ 二次确认（**必须先核对 selector 指向的记录** ✓ R11 教训）
     → POST /delete [该列表返回的自有 id]   （后端无归属校验 ✗ ⇒ 绝不由参数推导 target ✓）
```

---

## 8. 拟定实现清单（**待批准后才写** ✗）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `api/appointment.ts` | **A** | `STATUS_PENDING = 1`（常量 ✓）· `listMyAppointments()` · `addAppointment()`（status 固定 1 ✓）· `removeAppointment(id)` |
| 2 | `api/dict.ts` | **A** | `listDictItems('time_slot')` —— 时段**字典驱动** ✓（不硬编码 ✗）|
| 3 | `views/AppointmentListView.vue` | **A** | `/me/appointments`：列表（`timeSlotLabel`/`statusLabel`）+ 取消（确认 + 自有 id ✓）|
| 4 | `views/CounselorDetailView.vue` | M | 「预约」打开表单（日期 + 时段）→ `/add` ✓ |
| 5 | `router/index.ts` | M | `+ /me/appointments`（`requiresAuth` ✓）|
| 6 | `layouts/PublicLayout.vue` | M | `+ 预约` 导航 |

**6 文件（3 新 + 3 改）** · 后端 **0** ✗ · `/detail` **0** ✗ · `file` **0** ✗

**验收草案（A–H）**：

| 项 | 内容 |
| :-: | --- |
| A 静态 | PII **0** · `/detail/` **0** · `file` **0** · **`status` 仅出现常量 `1`**（无 2/3 ✓）· 写调用 **4**（update/add-favorite/delete-favorite/add-appointment? —— 实际统计以代码为准）|
| B 列表 | A → **3 条** ✓（含 `timeSlotLabel`/`statusLabel` ✓）· B → **0** ✓ |
| C 新增 | 未来日期 + slot 1 → **4 行**（`status=1` ✓，`student_id=1` ✓）|
| D 取消 | 删除**新记录** → **3 行** ✓ · **原 id 1–3 逐条完好** ✓✓ |
| E 校验 | 过去日期被前端拦截（零写 ✓）· 时段来自字典 ✓ |
| F Console | 0 error ✓ |
| G 回归 | `mvn 47/47` ✓（后端未改 ✗）|
| H Git/DB | 单一提交 ✓ · **DB 记录集合 = 基线** ✓ |

---

## 9. 未做（✗）

未执行写端点 ✗ · 未改后端 ✗ · 未修 `/delete` ✗ · 未修 `status` 校验 ✗ · 未与 R13 混批 ✗

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | R12 审计：**状态字典无"已取消"** ⇒ **取消 = `/delete` 物理删除** ✓（`status=3` 是老师拒绝 ✗ 不可混用）；`/add` 校验老师存在 ✓ + 强制 studentId ✓ 但**不设 status 默认** ✗；`/delete` **无守卫** ✗；登记 P1×2 + P2×3；6 文件清单 + A–H（**待批准** ✗）|
