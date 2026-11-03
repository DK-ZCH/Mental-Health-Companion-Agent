# S2-D2 —— 用户账号管理授权（学生/老师 save/add/delete）实现设计

> 日期：2026-10-22 ｜ 基线：`90f27d8`（S2-D1 已推送，LEAD=0）
> 状态：**实现设计 · 待批准** ✗ ｜ 本设计零代码、零 DB、零 commit
> 上游：`S2-D2-USER-MGMT-AUTH-AUDIT.md`（v1，已按审阅裁定收紧 P2 表述）

---

## 1. 范围（已裁定）

| 端点 | 处理 | 依据 |
| --- | --- | --- |
| `student/save` · `student/add` · `student/delete` | 加 `assertAdminOnly` | 新增/删除学生 = 管理功能 |
| `counselor/save` · `counselor/add` · `counselor/delete` | 加 `assertAdminOnly` | 新增/删除老师 = 管理功能 |
| `student/update` · `counselor/update` | **不动** | 已有 `assertSelfOrAdmin`（自助资料修改）|
| `student/register` · `counselor/register` | **不动（保留匿名）** | 自助注册，业务允许（与 S2-D1 下线 admin register 性质不同）|
| `resetPassword` | **不动** | 已有 `assertAdminOnly` |
| `resetPass` | **不动** | D3 已登记 |
| 默认弱口令 `123456` | **不动** | 独立密码策略债务 |

---

## 2. P2 定义（本批验收口径）

> **`SEC-USER-MGMT-SAVE-DELETE-NO-GUARD`（P2）**：**认证用户越权执行账户管理操作** —— 任意已登录 student/counselor 可创建/删除其他学生/老师账户；**当前未形成管理员权限提升证据**（非匿名提权、非直接获得管理员角色），但具有**跨用户账户创建/删除能力**。

> ⚠️ 本批**只解决「谁有权创建/删除账户」**，不同时改变「账户创建后密码是什么」（默认 `123456` 独立登记）。

---

## 3. 字段绑定风险（实现前必须实测，不在本批修）

### 3.1 静态字段矩阵

| Entity | `id` | `password` | `role` | 其他 owner-like |
| --- | :-: | :-: | :-: | --- |
| `StudentEntity` | **无 `@JsonIgnore` ⇒ 可绑定** ⚠️ | getter `@JsonIgnore`（S2-D1 实测不可绑定）| **不存在** ✓ | `idCardNo` 有 `@JsonIgnore` |
| `CounselorEntity` | **无 `@JsonIgnore` ⇒ 可绑定** ⚠️ | getter `@JsonIgnore` | **不存在** ✓ | — |

> 结论：**无 `role` 字段 ⇒ 无 S2-D1 式 role 提权风险**；但 `id` 可绑定 ⇒ 存在「客户端指定主键创建」（数据完整性风险，非提权）。

### 3.2 实现前实测要求（验收标准之一）

```text
admin save 探针，body 含：
  {"id": <大值>, "username": "...", "password": "hacked", "role": "管理员"}
  ↓
检查 DB 最终值：
  · id 是否 = 客户端指定值？  → 若是 ⇒ 登记 DEF-USER-CREATE-ID-BINDABLE（不修）
  · role 是否被忽略？          → Entity 无该字段，预期忽略（若 FAIL_ON_UNKNOWN 报错则记录）
  · password 是否 = 123456？  → 预期强制（@JsonIgnore + setPassword("123456")）
```

> **若发现 `id` 可绑定**：**只登记 finding，本批不修**（参照 S2-D1 `role` 发现流程，避免授权修复与主键策略混合变更）。

---

## 4. 改动清单（2 文件）

| 文件 | 端点 | 改动 |
| --- | --- | --- |
| `StudentController.java` | `/save`（L113）· `/add`（L475）· `/delete`（L190）| 补 `HttpServletRequest request` + `OwnershipGuard.assertAdminOnly(request)` 首行 |
| `CounselorController.java` | `/save`（L111）· `/add`（L442）· `/delete`（L179）| 同上 |

> `assertAdminOnly` 原语已存在（`OwnershipGuard` L117）；两文件均已 import `OwnershipGuard` 与 `HttpServletRequest`（S2-D1 前已存在）。

### 改动形态

```java
@RequestMapping("/save")
public R save(@RequestBody StudentEntity yonghu, HttpServletRequest request){
    OwnershipGuard.assertAdminOnly(request);   // 新增/删除用户 = admin-only
    // 原有逻辑不变（含 setPassword("123456")，本批不动）
    ...
}
```

---

## 5. 测试矩阵

### 5.1 4 角色 × 6 端点

| 端点 | anonymous | student | counselor | admin |
| --- | :-: | :-: | :-: | :-: |
| `student/save` | 401 | **403** | **403** | 200（探针）|
| `student/add` | 401 | **403** | **403** | 200（探针）|
| `student/delete` | 401 | **403** | **403** | 200（探针）|
| `counselor/save` | 401 | **403** | **403** | 200（探针）|
| `counselor/add` | 401 | **403** | **403** | 200（探针）|
| `counselor/delete` | 401 | **403** | **403** | 200（探针）|

### 5.2 admin 正向 + 恶意字段测试（§3.2）

| 场景 | 验证 |
| --- | --- |
| admin `student/save`（恶意 `id` 大值 + `role` + `password`）| id 是否绑定 / role 是否忽略 / password 是否强制 123456 |
| admin `counselor/save`（同上）| 同上 |
| admin `student/delete`（探针）| 200 + 探针删除 |
| admin `counselor/delete`（探针）| 200 + 探针删除 |

> 若 `id` 绑定 ⇒ 登记 `DEF-USER-CREATE-ID-BINDABLE`（不修）；若 `role` 被忽略 ⇒ 符合预期。

### 5.3 探针与恢复

- 探针用 `S2D2-*` 前缀；student/counselor 各建 1 条 → 测试 → 物理删除。
- 恢复核对：`student` / `counselor` 行数与 `max(id)` 回基线（seed：student=3 / counselor=3）。
- `AUTO_INCREMENT` 漂移如实登记，不回退。

---

## 6. 边界（本批不做）

```
❌ 默认弱口令 123456 策略
❌ resetPass 匿名（D3）
❌ 学生/老师「删除自己账号」业务设计
❌ 前端 · DB schema · 两个 404 · 默认 admin 凭据
❌ id 绑定若确认（只登记，不修）
```

---

## 7. 待批准

| # | 项 | 状态 |
| :-: | --- | --- |
| 1 | 6 端点加 `assertAdminOnly`（2 文件）| 待批准 |
| 2 | `/register` 保留匿名 · `/update` 不动 | 待批准 |
| 3 | 字段绑定实测（§3.2）作为验收标准，发现即登记不修 | 待批准 |
| 4 | 是否允许受控真实写入（探针 2 条 + 事后清理）| 待批准 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-D2 实现设计：6 端点加 `assertAdminOnly`（2 文件）；`/register` 保留匿名、`/update` 不动；**P2 定义=认证用户越权账户管理**；字段绑定实测（`id` 可绑定待确认、`role` 不存在、`password` 不可绑定）纳入验收；默认弱口令与 `resetPass` 不处理；**零代码零 DB 零 commit** ✓ |
