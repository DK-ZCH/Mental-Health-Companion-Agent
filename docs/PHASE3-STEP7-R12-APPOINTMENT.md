# Phase 3 / Step 7 · R12 —— 预约（**待提交** ✗）

> **范围（已批准 ✓）**：`/me/appointments` + `/counselors/:id` 预约表单（**字典驱动时段** ✓）+ `status` 固定 `1`
> **正式语义（已裁定 ✓）**：**取消 = `/delete` 物理删除** ✓ · `status=3` = **老师审核拒绝** ✗ 不可混用 · 前端**不做任何状态修改** ✗
> **禁止项（遵守 ✓）**：❌ 后端修改 · ❌ `/detail/{id}` · ❌ `file` API · ❌ 修任何安全项
> **基线**：`828a076`（R11 已推送 ✓）

---

## 1. 改动清单（**6 文件 = 3 新 + 3 改**，与批准一致 ✓）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `api/appointment.ts` | **A** | `STATUS_PENDING = 1`（内部常量 ✓）· `listMyAppointments()` · `addAppointment()` · `removeAppointment()` |
| 2 | `api/dict.ts` | **A** | `listDictItems(dictCode)` —— **字典驱动** ✓（不硬编码 ✗）|
| 3 | `views/AppointmentListView.vue` | **A** | `/me/appointments`：表格（`timeSlotLabel`/`statusLabel`）+ **仅「待审核」可取消** ✓ |
| 4 | `views/CounselorDetailView.vue` | M | 「预约」→ 弹窗（日期 + **字典时段**）→ `/add` ✓ |
| 5 | `router/index.ts` | M | `+ /me/appointments`（`requiresAuth` ✓）|
| 6 | `layouts/PublicLayout.vue` | M | `+ 预约` 导航 |

**后端 0** ✗ · `/detail` 0 ✗ · `file` 0 ✗

---

## 2. 验收（实测）

| 项 | 内容 | 结果 |
| :-: | --- | --- |
| **A 静态** | R12 视图/API 中 PII 字面量 | **0** ✓ |
| | `/detail/` | **0** ✓ |
| | `file` 端点 | **0** ✓ |
| | **`status` 硬编码数字** | **0** ✓（只用常量 `STATUS_PENDING` ✓）|
| | 写调用总数 | **5** = `student/update` · `favorite/add` · `favorite/delete` · `appointment/add` · `appointment/delete` ✓ |
| | `vue-tsc --noEmit` | **exit 0** ✓ |
| **B 列表** | A → | **共 3 条** ✓（含 `timeSlotLabel`/`statusLabel` ✓）|
| | **状态=2（同意）的记录** | **无取消按钮** ✓（`—`）✓ |
| **C 新增** | 老师 1 + 今天 `2026-10-20` + 时段 `1`（字典）| **DB 4 行** ✓：新增 `id=5 s=1 c=1 d=2026-10-20 slot=1 **st=1**` ✓✓ · **原 id 1–3 完好** ✓ |
| **D 取消** | 列表页取消**新记录**（先核对 selector = `appointment-cancel-5` ✓）| **DB 3 行** ✓ |
| **G 最终 DB** | **逐条 = 基线** | `id1 (s1,c2,2022-03-30,slot8,st1)` · `id2 (s1,c3,2022-03-30,slot8,st1)` · `id3 (s1,c1,2022-03-31,slot3,st2)` ✓✓ |
| **E 过去日期** | 日期 `2020-01-01` → 提交 | 提示「不能预约过去的日期…」✓ · **DB 仍 3 行（零写）** ✓✓ |
| **F Console** | — | **Errors 0 / Warnings 0** ✓ |
| **H 回归** | `mvn test` | **47 / 47** ✓（后端未改 ✗）|

### ⚠️ 措辞边界（按裁定 ✓）

* 过去日期只能表述为 **「前端阻止过去日期请求（零写）」** ✓
* **不得**表述为「系统不允许预约过去日期」✗ —— 后端无此约束（`DEF-APPOINTMENT-NO-PAST-CHECK` P2 ✓）
* `status = 1` 只能表述为 **「前端固定业务请求」** ✓；**不得**表述为已修复 `STATUS-SPOOF` ✗

---

## 3. ⚠️ 与批准方案的一处**实现偏差**（如实登记）

| 项 | 批准方案 | 实际 | 原因 |
| --- | --- | --- | --- |
| 时段控件 | `el-select` 下拉 | **`el-radio-group` 单选按钮组** | `el-select` 的 readonly input + 弹层在 Playwright 下**无法稳定操作** ✗（点击超时 ✓）；改单选后**仍为字典驱动** ✓（`/sysDictItem/page?dictCode=time_slot` → 8 项 ✓），可测性更好 ✓ |
| 文件数 / 范围 | 6 文件 | **6 文件** ✓ | 无扩大 ✓（控件内部替换 ✓）|

---

## 4. ⚠️ 自增游标漂移（同 R11）

```text
counseling_appointment AUTO_INCREMENT: 5 → 6     （新增 id=5 测试记录，删除后游标不回退）
行数据：id 1..3 与基线**逐条一致** ✓
```

---

## 5. 未验证 / 未做（✗）

| # | 项 | 说明 |
| :-: | --- | --- |
| 1 | `511「查不到该心理老师」` | 未在 UI 触发 ✗（表单 `counselorId` 恒来自真实 R9 页面 id ✓）；后端契约已从代码确认 ✓ |
| 2 | `/delete` 越权删除 | **未执行** ✗（`SEC-APPOINTMENT-DELETE-IDOR` P1，代码取证 ✓）|
| 3 | 时段冲突 / 重复预约 | 未测 ✗（`DEF-APPOINTMENT-SLOT-CONFLICT` / `NO-UNIQUE` P2 ✓；本次刻意用**不同日期+时段** ✓ 不与既有记录冲突 ✓）|
| 4 | **已同意（status=2）预约的取消策略** | **R12 尚未定义/实现已同意预约的取消策略，因此暂不提供取消入口** ✓（已同意/已拒绝显示 `—` ✓）· **不作为最终业务规则** ✗ —— 当前**没有证据**证明「已同意预约绝对不能取消」⚠️ |
| 5 | 截图 | 未截图 ✗ |

---

## 6. 安全登记（**全部 Identified / Unfixed，未修** ✗）

| ID | 级别 | 状态 |
| --- | :-: | :-: |
| `SEC-APPOINTMENT-DELETE-IDOR` | 🔴 **P1** | Identified / Unfixed（前端仅传自有 id ✓）|
| `SEC-APPOINTMENT-STATUS-SPOOF` | 🔴 **P1** | Identified / Unfixed（前端固定 `1` 不等于修复 ✗）|
| `DEF-APPOINTMENT-NO-UNIQUE` | 🟡 P2 | Identified / Unfixed |
| `DEF-APPOINTMENT-SLOT-CONFLICT` | 🟡 P2 | Identified / Unfixed |
| `DEF-APPOINTMENT-NO-PAST-CHECK` | 🟡 P2 | Identified / Unfixed |
| R11 及更早的 `SEC-FAVORITE-*` · `SEC-DETAIL-IDOR` · `SEC-FILE-*` · `SEC-PASS-RESET` · `SEC-ADMIN-REG` · `SEC-STUDENT-PAGE-POLICY` | — | 全部维持 **Unfixed** ✓ |

> 附：`application.yml` 实测 **`multipart.max-file-size: 1000MB`** ⇒ `SEC-FILE-UPLOAD-ANON`（P1）**无实际大小限制** ⚠️（仅登记，R12 未触碰 ✗）

---

## 7. 未做（✗）

未改后端 ✗ · 未修 `/delete` ✗ · 未修 `status` 校验 ✗ · 未碰 `file` ✗ · 未碰 `/detail` ✗ · 未与 R13 混批 ✗

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | R12 实现（6 文件）· **A–H 全过**（A=3 条 ✓ · 新增 `st=1` 且原记录完好 ✓ · 取消新记录 ✓ · **DB 逐条恢复基线** ✓ · **过去日期零写拦截** ✓ · Console 0 ✓ · **47/47** ✓）；**时段控件由 `el-select` 改为 `el-radio-group`**（仍字典驱动 ✓，如实登记 ⚠️）；`AUTO_INCREMENT 5→6` 漂移 ⚠️；两项 P1 + 三项 P2 维持 **Unfixed** ✓；「已同意预约的取消策略」未定 ⚠️ |
