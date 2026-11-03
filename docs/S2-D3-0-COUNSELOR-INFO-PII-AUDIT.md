# S2-D3-0 · `counselor/info` PII 字段级只读取证

> **性质**：只读取证 · **零代码 · 零 DB · 零 commit**
> **基线**：`HEAD = origin = bff4abe` · worktree CLEAN
> **日期**：2026-10-22
> **范围**：① `/counselor/info/{id}` 实际返回字段 ② 角色访问矩阵 ③ 真实调用方 ④ 是否需 D3 实现

---

## 0. 一句话结论

`/counselor/info/{id}` **没有归属校验、没有角色限制**，任何已登录用户（学生 / 老师）都能读取**任意**老师的 `username` + `phone` + `email`。
但**唯一真实调用方是 Admin 管理端**（4 处），Vue3 与老前端均 0 调用 ⇒ 收口不会影响任何前台业务。

---

## 1. 实现事实（静态）

```java
// CounselorController.java L90-106
@RequestMapping("/info/{id}")                    // ← 无 @IgnoreAuth（需登录）
public R info(@PathVariable("id") Long id, HttpServletRequest request){
    CounselorEntity xinlilaoshi = xinlilaoshiService.getById(id);   // ← 无任何归属/角色校验
    if(xinlilaoshi !=null){
        CounselorView view = new CounselorView();
        BeanUtils.copyProperties( xinlilaoshi , view );             // ← 实体 → View 全字段复制
        dictionaryService.dictionaryConvert(view, request);
        return R.ok().put("data", view);
    }else { return R.error(511,"查不到数据"); }
}
```

| 项 | 事实 |
| --- | --- |
| 认证 | 无 `@IgnoreAuth` ⇒ 走拦截器，**匿名 401** ✓ |
| 授权 | **无 `OwnershipGuard` 任何调用** ✗ |
| 返回类型 | `CounselorView extends CounselorEntity` ⇒ **继承全部 11 字段** |
| `password` | `CounselorEntity.getPassword()` 标 `@JsonIgnore`（L172）⇒ **序列化屏蔽** ✓ |
| `counselorName` | `CounselorView` **无此字段**（只有 `genderLabel`）|

---

## 2. 实际 HTTP JSON 返回字段（实测，非静态推断）

以 `GET /xinlijiankangxitong/counselor/info/1`（admin token）实测返回 `data` 字段：

```text
id, username, name, phone, avatarUrl, gender, email,
expertise, resume, introduction, createdAt, genderLabel
```

| 字段 | 类别 | 敏感度 | 说明 |
| --- | --- | :-: | --- |
| `id` | 主键 | 低 | 业务必需 |
| `username` | **账户名**（登录凭证）| 🟡 中 | 管理端编辑页需要；前台无消费 |
| `name` | 展示名 | 低 | 公开可展示 |
| `phone` | **手机号 PII** | 🔴 高 | 管理端编辑页需要；前台详情页也在展示（经 `/list`）|
| `email` | **邮箱 PII** | 🔴 高 | 同上 |
| `avatarUrl` | 头像 | 低 | 公开可展示 |
| `gender` / `genderLabel` | 性别 | 低 | 公开可展示 |
| `expertise` | 擅长 | 低 | 公开可展示 |
| `resume` | 履历 | 低 | 公开可展示 |
| `introduction` | 介绍 | 低 | 公开可展示 |
| `createdAt` | 创建时间 | 低 | 内部管理字段（非 PII）|
| `password` | 密码 | — | **未出现** ✓（`@JsonIgnore` 生效）|

> 结论：**PII 面 = `phone` + `email` + `username`（账户名）**，三者均随 `info` 全量返回。

---

## 3. 角色访问矩阵（实测）

请求 `GET /counselor/info/{id}`，三种 token 实测：

| 请求者 | id=1（a1 本人） | id=2（a2 他人） | id=3（a3 他人） | 判定 |
| --- | --- | --- | --- | :-: |
| **匿名** | HTTP 200 · `body.code=401`「请先登录」| 同左 | — | ✅ 拒绝（既有认证契约）|
| **student（a1, id=1）** | `code=0` · 全字段含 phone/email | `code=0` · **全字段** | `code=0` · **全字段** | ✗ **越权** |
| **counselor（a1, id=1）自己** | `code=0` · 全字段 | — | — | 合理（自身）|
| **counselor（a1, id=1）他人** | — | `code=0` · **全字段** | — | ✗ **横向越权** |
| **admin** | `code=0` · 全字段 | `code=0` · 全字段 | — | ✅ 管理端需要 |

**关键区分**：

- **"能访问 endpoint" ≠ "返回字段合理"**：student 能访问是认证层放行（只要登录），但字段层没有任何收口。
- student 侧**没有合理业务场景**：Vue3 老师目录走 `/counselor/list`，老前端老师详情页也走 `/counselor/list`。
- counselor 侧**查他人无场景**；查自己走 `/counselor/session`（L369），不依赖 `info`。

---

## 4. 真实调用方矩阵（全仓库检索 + 逐处确认）

### 4.1 `/counselor/info/{id}` —— 4 处，**全部为 Admin 管理端**

| 调用方 | 行 | 用途 | 消费字段 |
| --- | :-: | --- | --- |
| `admin/.../xinlilaoshi/add-or-update.vue` | L285 | 老师编辑页回填 `ruleForm = data.data` | **全字段**（模板 v-model：`username`/`name`/`phone`/`avatarUrl`/`gender`/`email`/`expertise`/`resume`/`introduction`）|
| `admin/.../xinlilaoshiOrder/add-or-update.vue` | L308 | 级联选择回填 `xinlilaoshiForm` | 模板仅用 `xinlilaoshiForm.counselorName` ⚠️ **坏字段**（View 无此字段）⇒ 实际恒空 |
| `admin/.../xinlilaoshiLiuyan/add-or-update.vue` | L234 | 同上 | 同上（`counselorName` 坏字段）|
| `admin/.../xinlilaoshiCollection/add-or-update.vue` | L232 | 同上 | 同上（`counselorName` 坏字段）|

### 4.2 其他技术栈调用统计

| 技术栈 | `/counselor/info` 调用 | 说明 |
| --- | :-: | --- |
| Vue3 `frontend/src` | **0** | 只用 `/counselor/list`，且 `api/counselor.ts` 已做安全字段白名单投影 ✓ |
| 老前端 `src/main/resources/front/` | **0** | 用 `/counselor/list` · `/counselor/detail` · `/counselor/session` |
| 后端 Controller/Service 内部调用 | **0** | 无内部复用 |
| `src/test/` | **0** | 无测试覆盖（含回归类）|

**⇒ `/counselor/info/{id}` 的真实消费者只有 Admin 管理端。**

---

## 5. 同类端点顺带取证（不属 D3 修改范围，仅登记）

### 5.1 `/counselor/detail/{id}`（L423）

| 请求者 | 实测 | 判定 |
| --- | --- | :-: |
| 匿名 | 401「请先登录」 | ✅ |
| student | `code=0` 全字段（含 phone/email/username）| ✗ 横向越权 |

- 消费者：老前端 `front/pages/xinlilaoshiOrder/add.html:431`（学生预约页）。
- ⚠️ 但模板绑定的是 `xinlilaoshi.counselorPhone` / `counselorEmail` / `counselorResume` / `counselorAvatarUrl` —— **后端实体无 `counselor` 前缀字段** ⇒ 页面一直显示空。
- 结论：`detail` 的"前台业务消费"是**坏字段假消费**，真实字段无人使用。

### 5.2 `/counselor/session`（L369）—— 🔴 发现跨表 ID 混用

```java
Integer id = CurrentUserProvider.currentUserIdOrNull(request);
CounselorEntity xinlilaoshi = xinlilaoshiService.getById(id);   // ← 不校验 role=tableName
```

实测：**student a1（id=1）带自己的 token 调 `/counselor/session` → 返回 counselor(id=1, a1) 的全字段**（含 phone/email）。

- 根因：token 里有 `tableName`，但该方法只用 `userId` 直接查 `counselor` 表。
- 影响：只要 `student.id` 与某 `counselor.id` 数值重合，学生即可读到该老师 PII。
- 消费者：老前端老师中心页 `front/pages/xinlilaoshi/center.html:499`（正常应为 counselor 角色）。

### 5.3 `/counselor/list`（L403，`@IgnoreAuth`）

| 请求者 | 实测 | 判定 |
| --- | --- | :-: |
| **匿名** | HTTP 200 `code=0`，3 条，**每条含 `username`/`phone`/`email`** | ✗ 公开 PII |

- 属 **既有挂账**（`docs/jiaojie.md` phone-email 批次；`frontend/src/api/counselor.ts:6` 注释已明确记录）。
- 消费者：Vue3（已做字段投影 ✓）· 老前端 `list.html`（只用 name/avatarUrl）· 老前端 `detail.html`（**展示 `detail.phone` / `detail.email`**）。
- **不属 S2-D3**，仅确认现状未变化。

---

## 6. 字段必要性判定

| 字段 | Admin 编辑页 | Admin 级联页 | Vue3 前台 | 老前端 | 判定 |
| --- | :-: | :-: | :-: | :-: | --- |
| `id` | ✅ | ✅ | ✅ | ✅ | 必要 |
| `name` | ✅ | ✅（`counselorName` 坏字段，实际需要 `name`）| ✅ | ✅ | 必要 |
| `avatarUrl` | ✅ | — | ✅ | ✅ | 必要 |
| `gender` / `genderLabel` | ✅ | — | ✅ | — | 必要 |
| `expertise` | ✅ | — | ✅ | ✅ | 必要 |
| `resume` | ✅ | — | ✅ | ✅ | 必要 |
| `introduction` | ✅ | — | ✅ | — | 必要 |
| `username` | ✅（编辑页可改）| — | ✗ | ✗ | **仅 admin** |
| `phone` | ✅（编辑页可改）| — | ✗ | 仅 detail 页（走 `/list`）| **仅 admin** |
| `email` | ✅（编辑页可改）| — | ✗ | 仅 detail 页（走 `/list`）| **仅 admin** |
| `createdAt` | ✗（只读展示）| — | ✗ | ✗ | 可保留 |

**⇒ 3 个 PII 字段（`username`/`phone`/`email`）的全部真实消费者都是 Admin 编辑页。**

---

## 7. 新登记问题（分级 · 本批只登记，不修）

> ⚠️ **优先级声明**：`SEC-COUNSELOR-SESSION-TABLE-ID-CONFUSION` 是**已完成实际利用的 P1 独立安全项**，
> 严重程度**高于** `/counselor/info`，**不得因"顺带发现"而被弱化或降级**。

| ID | 级别 | 事实 | 归属 |
| --- | :-: | --- | --- |
| **`SEC-SESSION-TABLE-ID-CONFUSION`**（原 `SEC-COUNSELOR-SESSION-TABLE-ID-CONFUSION`）| 🔴 **P1（最高优先级）** | `/counselor/session` 只用 `userId` 查 `counselor` 表，**不校验 `tableName`/`userType`**。实测：student a1(id=1) 带**自己的 token** → 返回 counselor a1(id=1) 的 `username`/`phone`/`email` 全字段。**⚠️ 已由 [`S2-E-0-SESSION-TABLE-CONFUSION-AUDIT.md`](./S2-E-0-SESSION-TABLE-CONFUSION-AUDIT.md) 扩大取证：同类端点共 3 个（student/counselor/adminUser），且 counselor→student 亦实测成立（双向越权）** | **独立 P1 批次**（不计入 D3 acceptance）|
| **`SEC-COUNSELOR-INFO-CROSS-USER-PII`** | 🟠 P2（本批收口）| `/counselor/info/{id}` 无归属/角色校验，任何登录用户可读任意老师 `username`/`phone`/`email` | **S2-D3-1**（方案 A）|
| **`SEC-COUNSELOR-DETAIL-CROSS-USER-PII`** | 🟠 P2 | `/counselor/detail/{id}` 同样无归属校验（student 实测可读全字段）；前台调用为坏字段假消费 | 独立项 |
| **`DEF-COUNSELOR-LIST-PUBLIC-PII`** | ✅ **已拆分定级并关闭** | `/counselor/list` **匿名**即返回 `username`/`phone`/`email` → 经 [`S2-E-2-0`](./S2-E-2-0-COUNSELOR-LIST-PUBLIC-PII-AUDIT.md) 取证后已拆为：`SEC-COUNSELOR-LIST-USERNAME-EXPOSURE` **P1** · `SEC-COUNSELOR-LIST-CONTACT-PII` **P2** · `DEF-COUNSELOR-LIST-CREATEDAT-EXPOSURE` P3；修复设计见 [`S2-E-2-1`](./S2-E-2-1-COUNSELOR-PUBLIC-DTO-DESIGN.md) | **S2-E-2**（不在 D3）|
| `DEF-COUNSELOR-CASCADE-FIELD-MISMATCH` | 🟡 | 3 个 Admin 级联页 + 老前端预约页绑定 `counselorName`/`counselorPhone` 等**不存在的字段** ⇒ 回显恒空 | 功能债务 |

### 7.1 四个端点的边界对照（防止被混为一谈）

| Endpoint | 当前情况 | D3 是否处理 |
| --- | --- | :-: |
| `/counselor/info/{id}` | 登录用户可读任意咨询师 PII | **✅ 本批收口（assertAdminOnly）** |
| `/counselor/session` | **Student token 可跨表读 Counselor PII** | ❌ 独立 P1（最高优先级）|
| `/counselor/detail/{id}` | 非预期角色可读 PII | ❌ 独立项（P2）|
| `/counselor/list` | **匿名即可读 PII** | ❌ 独立项（待重新定级）|

---

## 8. 已裁定：方案 A（S2-D3-1）

### 8.1 裁定

**方案 A 批准**：`/counselor/info/{id}` 增加 `OwnershipGuard.assertAdminOnly(request)`。

否决 B/C 的理由：

| 方案 | 形态 | 否决理由 |
| :-: | --- | --- |
| B | 非 Admin 字段投影 | **过度设计**：当前非 Admin 消费者 = 0；若未来产品需要，应重新设计公开 DTO，而非预留"可访问但字段受限"的接口 |
| C | `assertSelfOrAdmin` | **冗余**：老师本人已有 `/counselor/session`；为保留冗余入口而扩大 `info` 访问面无正当性 |

### 8.2 范围精确锁死（S2-D3-1）

```text
修改（唯一）：
  CounselorController.java · /counselor/info/{id}
      ↓
  OwnershipGuard.assertAdminOnly(request)   // 1 行

不修改：
  ✗ /counselor/list          （匿名 PII，独立项）
  ✗ /counselor/detail/{id}    （P2，独立项）
  ✗ /counselor/session        （P1，独立批次）
  ✗ CounselorView / CounselorEntity（不做 DTO 投影）
  ✗ Vue3 / 老前端 / Admin     （0 改动）
  ✗ DB / 其他 Controller
```

### 8.3 测试矩阵

| 请求者 | `/counselor/info/1` | 断言 |
| --- | --- | --- |
| anonymous | `body.code=401` | 既有认证契约保持不变 |
| student | `403` | `assertAdminOnly` 拒绝 |
| counselor | `403` | 含查他人（id=1/2/3 均拒绝）|
| **admin** | **200 + 原完整字段** | **不只是"403 挡住了"**，须证明管理端编辑功能仍正常（11 字段齐全、`password` 仍不出现）|

**附加只读回归断言（不修 `/session`）**：
D3 改动后确认 `/counselor/session` 的**现有行为未被影响**（student token 仍返回同 id counselor 数据 —— 该缺陷按原样保留，仅验证无新增回归）。

### 8.4 实施纪律

```text
修改 → 47/47 回归 → 停旧 jar → package → 启新 jar →
确认 PID / jar 时间戳 → HTTP 四角色测试 → staged review → commit → push
```

---

## 9. 状态声明

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = bff4abe` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓ |
| 实测方式 | 真实 HTTP（8080 运行中，context-path `/xinlijiankangxitong`）· 三 token（student/counselor/admin）|
| 数据影响 | **无**（全部为 GET 只读，未写入任何数据）|

---

## 10. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v2 | 2026-10-22 | 用户裁定：**方案 A 批准**（`info` 加 `assertAdminOnly`）；§7 重新分级 —— `SESSION-TABLE-ID-CONFUSION` 提为**最高优先级 P1 独立项**（已完成实际利用，不得弱化）· `LIST-PUBLIC-PII` 由 🟡 改为**待重新定级**（匿名暴露面 > info）；§8 改为已裁定 + D3-1 范围精确锁死 + admin 正向验收要求 + `/session` 只读回归断言 |
| v1 | 2026-10-22 | S2-D3-0 只读取证：info 实测字段（11 字段，password 屏蔽）+ 5 角色矩阵（匿名 401 / student 任意读 / counselor 任意读 / admin 200）+ 调用方（Admin 4 处、Vue3 0、老前端 0）+ 顺带发现 session 跨表 ID 混用（P1）+ detail 横向 PII（P2）+ 级联坏字段；3 方案裁定；**零改动** ✓ |
