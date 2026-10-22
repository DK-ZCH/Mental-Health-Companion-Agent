# S2-D —— 剩余 P1 只读审计 / 裁定（admin 凭据 · `/info` IDOR · 教师写授权 · 两个 404）

> 日期：2026-10-20 ｜ 基线：`cd24257`（S2-C2 已推送，LEAD=0）
> 模式：**只读审计 / 裁定** —— 不改 Controller/Service/DB/前端/测试 ✗，不 commit ✗
> 目标：重排剩余 S2 风险，逐项回答「是否修复 / 修复方式 / 合法访问模型」，**不直接写代码**。

---

## 0. 结论速览

1. **最严重不是种子 `admin/admin`，而是 admin 账号生命周期存在多个独立高风险授权缺口**：`/adminUser/register` 匿名注册（role 客户端可控 ⇒ **匿名提权入口**）；`/save` `/update` `/delete` 分别构成管理员账户**创建/修改/删除的未授权管理能力** —— 需单列重排，**不统一表述为「账户接管/提权」**。
2. **用户管理端点（学生/老师/管理员）的 `save`/`add`/`delete` 普遍无守卫**：仅 `update` 已收口（`assertSelfOrAdmin`），`save/add/delete` 仍是「登录即可增删他人账号」。
3. **`/info` IDOR 需分两类**：个人数据类（留言/收藏/预约/记录/作答/错题/学生/管理员）**已收口** ✓；内容公开类（试卷/题目/知识/通知/字典/配置/老师）无守卫，但**大多对应 `@IgnoreAuth` 公开资源**，增量风险有限，需按「是否含 PII」逐一定性。
4. **两个 404**：功能缺陷，**本批不修**（复核确认）。

---

## 1. 审计 1 —— admin 默认凭据 + 账号生命周期

### 1.1 `AdminUserEntity` 字段

| 字段 | `@JsonIgnore` | 绑定语义 |
| --- | :-: | --- |
| `id` | 否 | 可绑定 |
| `username` | 否 | 可绑定 |
| `password` | **getter 上** | **实测：当前项目 Jackson 配置下不可经 JSON 绑定**（序列化输出被忽略；反序列化亦未绑定，见 §1.1bis）|
| `role` | **否** | **可绑定**（无任何限制）|

> ⚠️ 关键（**实测修正**）：`password` 的 `@JsonIgnore` 在 **getter** 上，**在当前项目实际 Jackson 配置下同时阻止了反序列化绑定** —— admin update 提交 `password=hacked` 后 DB 仍为原值（§1.1bis）。**不扩大为「@JsonIgnore 在 getter 上永远阻止反序列化」的普遍规则**，仅对本 Entity + 本配置成立。

### 1.1bis 绑定证据链（`/update` 提权路径）—— 实测修正

```text
admin update {"id":7,"role":"学生","password":"hacked"}
  ↓ Spring Boot 默认 Jackson 反序列化
  ↓ setRole("学生")        → role 绑定 ✓（role 无 @JsonIgnore、setter 无限制）
  ↓ setPassword("hacked")  → password **未绑定** ✗（实测 DB 仍为 123456）
  ↓ usersService.updateById(user) → DB
```

| 字段 | 实测结果 | 结论 |
| --- | --- | --- |
| `role` | 客户端 `"学生"` **可进入实体**（S2-D1 前）| **可绑定 ⇒ 需防护**（S2-D1 已加服务端恢复原 role）|
| `password` | 客户端 `"hacked"` **未落库**（DB 仍 123456）| **不可绑定**（当前 Entity + Jackson 配置）|

> **结论修正（撤销此前判断）**：经当前项目实际 Jackson 反序列化测试确认，`AdminUserEntity.password` 在现有 `@JsonIgnore` 与 ObjectMapper 配置下**不可通过 JSON 请求体绑定**；admin update 提交 `password=hacked` 后数据库仍保持原值。**此前「password 高可信可绑定」的结论撤销**。
> **不扩大为普遍规则**：这是「当前 Entity + 当前 Jackson 配置」下的实测结论，不等于「`@JsonIgnore` 在 getter 上永远阻止反序列化」。
> ⇒ S2-D1 对 admin update 需要防护的客户端可绑定权限字段为 **`role`**（`password` 由 `@JsonIgnore` 天然保护）。

### 1.2 admin 端点矩阵

| 端点 | 守卫 | 风险 |
| --- | :-: | --- |
| `/adminUser/login` | `@IgnoreAuth` | 明文比较；种子 `admin/admin`（`SEC-ADMIN-DEFAULT-CREDENTIAL` P1 已登记）|
| **`/adminUser/register`** | `@IgnoreAuth` | **匿名注册 admin 账号**，`role` 客户端可控 ⇒ **匿名提权** ✗✗ |
| **`/adminUser/save`** | **无** | 任何登录用户可**创建 admin**（`setPassword("123456")` 强制弱口令）✗✗ |
| **`/adminUser/update`** | **无** | 任何登录用户可**改 admin**（`role` **实测可绑定** ⇒ 提权；`password` **实测不可绑定**）✗✗ |
| **`/adminUser/delete`** | **无** | 任何登录用户可**删除 admin** ✗✗ |
| `/adminUser/page` `/list` `/info/{id}` | `assertAdminOnly` | ✓ 已收口 |
| `/adminUser/session` | 无（取当前用户）| 低风险（仅回当前登录 admin 自身）|

### 1.3 调用方（管理端 `admin/src`）

- `users/list.vue:386` → `adminUser/page`（有守卫）
- `users/list.vue:445` → `adminUser/delete`（**后端无守卫**）
- `users/add-or-update.vue:187` → `adminUser/info/{id}`（有守卫）
- `users/add-or-update.vue:204` → `adminUser/save` / `update`（**后端无守卫**）

> UI 是 admin-only 管理端页面，但**后端端点不校验角色** ⇒ 学生/老师凭 token 直调即可越权。

---

## 2. 审计 2 —— `/info/{id}` 访问模型

### 2.1 全量 `/info/{id}` 守卫矩阵（16 处）

| 资源 | 守卫 | 定性 |
| --- | :-: | --- |
| `student` | `assertOwnership` | ✓ 学生本人 + admin |
| `assessmentAnswer` | `assertOwnership` | ✓ |
| `adminUser` | `assertAdminOnly` | ✓ |
| `counselingAppointment` | `assertOwnership` | ✓ |
| `counselorMessage` | `assertOwnership` | ✓ |
| `assessmentWrongQuestion` | `assertOwnership` | ✓ |
| `counselorFavorite` | `assertOwnership` | ✓ |
| `assessmentRecord` | `assertOwnership` | ✓ |
| **`counselor`** | **无** | ⚠️ 老师详情含 PII（phone/email），但 `/list` 已 `@IgnoreAuth` 公开 |
| **`assessmentPaper`** | **无** | 试卷元信息（名称/时长/总分）|
| **`assessmentQuestion`** | **无** | S2-B 已投影公开 DTO（非 admin 无 answer/analysis）✓ |
| **`notification`** | **无** | `/list` 已 `@IgnoreAuth` 公开 |
| **`knowledgeArticle`** | **无** | `/list` 已 `@IgnoreAuth` 公开 |
| **`sysDictItem`** | **无** | `/page` 已 `@IgnoreAuth` 公开字典 |
| **`sysConfig`** | **无** | 轮播图，公开 |

### 2.2 结论

- **个人数据类（8 个）已收口** ✓，不属本批。
- **内容公开类（7 个）无守卫**，但除 `counselor` 外均有 `@IgnoreAuth` 公开列表，`/info` 只是「单条回填」，**增量风险有限**。
- **唯一需裁定**：`counselor/info/{id}` 返回老师 `phone/email` 等 PII；若「老师详情应公开但 PII 应脱敏」则需投影，若「老师 PII 本就公开」则维持现状（当前 `/list` 已公开同字段）。

---

## 3. 审计 3 —— 教师侧写授权 + 用户管理端点

### 3.1 Counselor 端点矩阵

| 端点 | 守卫 | 风险 |
| --- | :-: | --- |
| `/counselor/update` | `assertSelfOrAdmin(ROLE_COUNSELOR)` | ✓ 老师本人 + admin |
| **`/counselor/save`** | **无** | 任何登录用户创建老师（强制密码 123456）✗ |
| **`/counselor/add`** | **无** | 同上（前端保存入口）✗ |
| **`/counselor/delete`** | **无** | 任何登录用户删老师 ✗ |
| `/counselor/resetPassword` | `assertAdminOnly` | ✓ |
| `/counselor/resetPass` | `@IgnoreAuth` | 匿名重置任意老师密码为 123456（「忘记密码」，D3 已登记）⚠️ |
| `/counselor/register` | `@IgnoreAuth` | 匿名注册老师（业务允许，见下）|
| `/counselor/info/{id}` | 无 | 见 §2 |

### 3.2 Student 端点矩阵（对照）

| 端点 | 守卫 | 风险 |
| --- | :-: | --- |
| `/student/update` | `assertSelfOrAdmin(ROLE_STUDENT)` | ✓ |
| **`/student/save`** | **无** | 任何登录用户创建学生（强制 123456）✗ |
| **`/student/add`** | **无** | 同上 ✗ |
| **`/student/delete`** | **无** | 任何登录用户删学生 ✗ |
| `/student/resetPassword` | `assertAdminOnly` | ✓ |
| `/student/resetPass` | `@IgnoreAuth` | 匿名重置（D3 已登记）⚠️ |
| `/student/register` | `@IgnoreAuth` | 匿名注册学生（业务允许）|
| `/student/info/{id}` | `assertOwnership` | ✓ |

### 3.3 结论

- **`update` 已收口**（学生/老师自助更新，批 4-B）。
- **`save` / `add` / `delete` 普遍无守卫**：这是「用户管理」类 admin-only 功能，与 S2-C2 的「admin-only 删除」同型问题，但**对象是账号本身**，风险更高。
- **`register` 匿名注册**：学生/老师匿名注册属业务设计（可接受）；**但 admin 匿名注册（§1.2）不可接受**。

---

## 4. 审计 4 —— 两个 Admin 404（复核，本批不修）

| ID | 管理端调用 | 调错 URL | 真实 mapping | 判定 |
| --- | --- | --- | --- | :-: |
| `DEF-QUESTION-ADMIN-ENDPOINT-MISSING` | `examquestion/list.vue:512` | `deleteExamquestion` | `deleteAssessmentQuestion` | 功能缺陷，**不因安全批顺手修** ✗ |
| `DEF-RECORD-ADMIN-ENDPOINT-MISSING` | `examrecord/list.vue:508` | `deleteExamrecord` | `deleteAssessmentRecord` | 同上 ✗ |

> 与 S2-C2 结论一致：本批**不修**，除非单开功能修复批次。

---

## 5. 本轮新登记（不修，待裁定）

| ID | 级别 | 事实 |
| --- | :-: | --- |
| **`SEC-ADMIN-REGISTER-ANONYMOUS`** | 🔴 **P1（最高优先）** | `/adminUser/register` `@IgnoreAuth` 匿名注册 admin，`role` 客户端可控 ⇒ **匿名提权入口**（独立提权能力，已强证据）|
| **`SEC-ADMIN-SAVE-NO-GUARD`** | 🔴 **P1** | `/adminUser/save` 无守卫 + 强制弱口令 123456 ⇒ 登录用户可**创建 admin 账户**（未授权创建；攻击前提=已登录任意角色）|
| **`SEC-ADMIN-UPDATE-NO-GUARD`** | 🔴 **P1** | `/adminUser/update` 无守卫，`role` **实测可绑定** ⇒ **修改 admin 的 role（提权）**；`password` **实测不可绑定**（@JsonIgnore，见 §1.1bis）。未授权修改能力 |
| **`SEC-ADMIN-DELETE-NO-GUARD`** | 🔴 **P1** | `/adminUser/delete` 无守卫 ⇒ 登录用户可**删除 admin 账户**（未授权删除 / 账户破坏；**不完全等同提权**）|
| **`SEC-USER-MGMT-SAVE-DELETE-NO-GUARD`** | 🟠 **P2（聚合发现）** | 学生/老师 `save`/`add`/`delete` 无守卫（admin-only 管理功能无权限）。⚠️ 实现时**须拆到具体 endpoint**，不得给整个 Controller 套 `assertAdminOnly`；「个人资料修改=self/admin」与「创建/删除账户=admin-only」分离 |
| `DEF-COUNSELOR-INFO-PII` | 🟡 **升级为待字段取证** | `counselor/info/{id}` 无守卫，需**先做字段级取证**：若返回 phone/email/idCard 等 PII ⇒ 「公开接口暴露不必要个人信息」（安全/隐私风险，非单纯产品问题）；若仅 name/avatar/职称/简介 ⇒ 无问题 |

> 既有、不重复登记：`SEC-ADMIN-DEFAULT-CREDENTIAL` P1（种子 admin/admin）· `SEC-DETAIL-IDOR` · 两个 404 · `DEF-REGRESSION-TEST-DATA-MUTATION` TBD · 教师业务写入口无（批 4-B Q1）。

---

## 6. 待裁定项

| # | 批次 | 裁定 |
| :-: | --- | --- |
| 1 | **S2-D1 Admin 生命周期** | ✅ 已批准进入实现设计。`/register` 处理方式（倾向**直接废弃匿名注册**，除非有开放注册需求）；`/save` `/update` `/delete` 加 `assertAdminOnly`；`/update` 屏蔽 `role` 绑定（`password` 实测不可绑定，无需处理）；默认 `admin/admin` 纳入 D1 设计但先确认生产部署语义 |
| 2 | **S2-D2 用户管理 save/add/delete** | ✅ 独立批次，随后处理。创建/删除账户 → admin-only；个人资料修改 → self/admin（维持 `assertSelfOrAdmin`），**拆到具体 endpoint** |
| 3 | **S2-D3 counselor/info PII** | ⚠️ 先做字段级取证，再决定脱敏或维持现状 |
| 4 | **内容公开 `/info`** | ❌ **不收紧**（已有公开 `/list`，属正常公开详情接口）|
| 5 | **两个 Admin 404** | ❌ 继续留在功能债务，不进入安全批次 |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v3 | 2026-10-22 | **按 S2-D1 实测修正 password 结论**：撤销「password 高可信可绑定」；经当前项目 Jackson 实测确认 `AdminUserEntity.password` **不可经 JSON 绑定**（admin update 提交 `password=hacked`，DB 仍 123456）。**不扩大为普遍规则**（仅当前 Entity + 当前配置成立）；§1.1 / §1.1bis / §1.2 / §5 / §6 同步修正，需防护字段 = `role` |
| v2 | 2026-10-22 | 按审阅裁定收紧：①「账户接管/提权」拆为多个独立授权缺口（register=匿名提权入口；save/update/delete=创建/修改/删除未授权管理能力）；②补 §1.1bis role/password 绑定证据链（role 确定可绑定、password 高可信 write-only —— **password 部分已被 v3 实测撤销**）；③`DEF-COUNSELOR-INFO-PII` 升级为「待字段取证」；④拆批 D1（admin 生命周期，已批准设计）/D2（用户管理）/D3（counselor PII）；⑤内容公开 `/info` 不收紧、两 404 不进安全批 |
| v1 | 2026-10-20 | S2-D 只读审计：admin 生命周期失控（register 匿名 + save/update/delete 无守卫，5 个新 P1）；用户管理端点 save/add/delete 无守卫（P2）；`/info` 8 已收口 + 7 公开类无守卫（counselor PII 待定）；两个 404 复核不修；6 项待裁定；**零改动** ✓ |
