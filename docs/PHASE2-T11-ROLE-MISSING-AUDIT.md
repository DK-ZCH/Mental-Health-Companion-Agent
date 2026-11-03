# Phase 2 / T11：`role` 缺失的 E2E 覆盖缺口 —— **Phase 1 只读审计**

> **来源**：`PHASE2-CLOSING-REPORT.md` §3.2 技术债 **T11** —— "E2E 级「role 缺失」覆盖缺口：现为单元级契约；E2E 需构造空 role token"
> **本轮范围（审阅裁定，严格限定）**：`role` 缺失 · 当前身份读取链 · `DataScope` / `CurrentUserProvider` · 已收敛的 `/list`、`/page` · 现有测试覆盖
> **禁令**：❌ 不修改业务代码 ❌ 不修改数据库 ❌ 不处理教师 G3 ❌ 不处理 T1 ❌ 不处理 T8
> **状态**：只读审计完成 ✓ —— 测试落点待裁定（§6）

---

## 1. 身份读取链（唯一写入源 → 唯一读取实现）

```text
① 登录：generateToken(userId, username, userType, role)  →  写 auth_token 行
        ↓ （此后每个带 Token 的请求）
② AuthorizationInterceptor.preHandle（唯一写入源）
      token → tokenService.getAuthTokenEntity(token)
         ├─ 行存在 → session.setAttribute("role", tokenEntity.getRole())   ← L74
         └─ 行不存在/无 Token → body {"code":401,"msg":"请先登录"} + return false   ← L80–92
        ↓
③ Controller → DataScope.apply / OwnershipGuard.* / CurrentUserProvider.currentRole
        ↓
④ CurrentUserProvider.currentRole(request)：Session 无 role → 返回【真正的 null】（L53–56）
        ↓
⑤ 缺失/未知/空 → 一律 deny()（ForbiddenException → HTTP 403 + code=403）
```

**证据（逐条）**

| 环节 | 位置 | 事实 |
| --- | --- | --- |
| 写入源 | `AuthorizationInterceptor.java:73-74` | 唯一把 `userId`/`role` 写进 Session 的地方；值来自 `auth_token` 行 |
| 读取实现 | `CurrentUserProvider.java:53-56` | `currentRole`：缺失 → **真 null**（非字符串 `"null"`）|
| 依赖方向 | `OwnershipGuard.java:306-317` | `OwnershipGuard.currentUserId/currentRole` **委托**到 `CurrentUserProvider`（实现唯一）|
| 拒绝形态 | `OwnershipGuard.java:324-326` | `deny()` → `ForbiddenException` → `HTTP 403 + code=403` |

## 2. `role` 的四条来源 ⇒ **缺失令牌是真实可达路径**（非理论）

| 登录端点 | `generateToken` 的 role 实参 | 可否为 null/空 |
| --- | --- | :--: |
| `/yonghu/login` | **硬编码** `"学生"`（`StudentController.java:323`）| ❌ |
| `/xinlilaoshi/login` | **硬编码** `"心理老师"`（`CounselorController.java:296`）| ❌ |
| **`/users/login`** | **`user.getRole()` —— 取自 `admin_user` 行**（`AdminUserController.java:53`）| ✅ **可以** |

**Schema 证据（只读）**：`auth_token.role` = `varchar(100)` · **`nullable=YES` · `default=NULL`** ✓
（对照：`user_id` / `username` / `token` 均 `NOT NULL` —— 故「身份不完整」中**只有 role 可缺失**，`user_id` 缺失已被用例 #27 证明不可构造 ✓）

⇒ **可达路径**：`admin_user` 行 role 为空/null（或历史/构造行）→ `/users/login` → **role 为 null/空的 token** → 可调用全部需鉴权端点 ✓

**现状精度（只读数）**：当前库中 `admin_user.role` = `管理员` ✓ ⇒ **此刻并不存在这样的令牌** ✓。
因此 T11 不是"正在被利用的漏洞"，而是 **schema 与代码允许该形态、却没有任何用例守护该不变量** ✗ ✓ —— 与其对照组 #27（`user_id` NOT NULL 已有不变量守护 ✓）正好形成缺口对照 ✓

## 3. 消费面（`role` 被谁读、读到缺失时做什么）

| 消费方 | 数量 | 对「缺失 / null / 空 / 未登记角色」的行为 | 结论 |
| --- | :-: | --- | :-: |
| `DataScope.apply(...)` | **19**（12 `/page` + G1 的 6 `/list` + G2 的 `/yonghu/list`）| 非 admin/student/counselor → `OwnershipGuard.deny()` | ✅ **fail-closed** |
| `OwnershipGuard.*` | **33** | `assertOwnership` ④ / `assertAdminOnly` / `assertWritableTarget` ④ / `resolveWriteOwner` 末行 / `assertSelfOrAdmin` 末行 —— **均为 deny()** | ✅ **fail-closed** |
| `CurrentUserProvider.currentRole` 直调 | 8（7 控制器 + 1 即 `OwnershipGuard` 委托）| 见 §5（**分两种情况**）| ⚠️ 见 §5 |

## 4. 覆盖矩阵（现有用例 vs 缺口）

| 场景 | 覆盖用例 | 层级 | 说明 |
| --- | --- | :-: | --- |
| `role` 缺失 → `currentRole` 返回真 null；非字符串 `123` → `"123"`；移除后 → null | **#25** | **单元** | `MockHttpServletRequest` 直调 —— **不经拦截器、不经 `DataScope`、不看响应** |
| `userId` 读可缺失 / 写必须 fail-closed | **#26** | 单元 | 同上 |
| `auth_token.user_id` `NOT NULL` ⇒ 身份不完整不可构造 | **#27** | 集成（真实 DB）| `save` 抛 `DataIntegrityViolationException` + **无残留行** ✓（不变量守卫）|
| **非空但未登记**的角色（`"未知角色"`）→ 读与写**一律拒绝** | **#16** | **E2E** | 同一造令牌形态；读 + 写两侧都覆盖 ✓（**Phase 2 补录**：本表 v1 漏列此用例，结论不受影响 ✓）|
| **非空但未登记**的角色（`"未知角色"`）→ `/page` **403** | **#19** | **E2E**（MockMvc + 真实 token 行 + `finally` 清理）| `tokenService.save(...)` 造令牌 → 断言 403 → 移除 ✓ |
| 学生伪造 `studentId` 不放大读范围 / 教师侧收敛 | #17 / #18 | E2E | `DataScope` 正向能力 ✓ |

### 🔴 缺口（T11 的精确形态）

> **没有任何用例覆盖「role 为 `NULL` / 空字符串」的 token 走完整 HTTP 链路。**
>
> * **#25 不能替代** ✗：它验证的是 `currentRole()` 的**函数契约**，不经过拦截器、不经过 `DataScope`、不校验 HTTP 响应 ✓
> * **#19 不能替代** ✗：它构造的是**非空但未登记**的角色（`"未知角色"`）—— **不等于 NULL/空** ✓
> * 而 §2 已证明 **NULL/空 role 是真实可达路径**（`auth_token.role` 允许 NULL + `/users/login` 的 role 来自 DB）✓

## 5. 审计**新发现**（超出 T11 原始描述；**只登记，不修**）

7 个控制器存在**两条并存的归属路径**，对「未知 / null 角色」的行为**不同**：

| 端点 | 归属写法 | 未知/null 角色时的行为 | 判定 |
| --- | --- | --- | :-: |
| **`/add`** | `OwnershipGuard.resolveWriteOwner(request, AdminWriteOperation.X, 客户端值)` | 末行 `deny()` | ✅ **拒绝** |
| **`/save`** | 旧链：`if(false) … else if("学生") … else if("心理老师") …` —— **无 `else`** | **既不拒绝、也不赋归属** ⇒ 实体保留**客户端提交的** `studentId`/`counselorId` | ⚠️ **需登记** |
| `/update` | `assertWritableTarget` + `resolveWriteOwner` | `deny()` | ✅ 拒绝 |

* 涉及文件：`CounselorMessage` / `CounselorFavorite` / `CounselingAppointment` / `AssessmentRecord` / `AssessmentAnswer` / `AssessmentWrongQuestion`（`currentRole` 块的最近映射均为 **`@RequestMapping("/save")`** ✓）
* **相邻既有登记**：技术债 **T3「13 个 `/save` 无调用方（疑似废弃，不删）」** ✓ —— 本发现与其同一对象，建议**并入 T3 的处置**，不在 T11 内独立修复 ✓
* ⚠️ **重要提醒（刚发生过一次的教训）**：**不要**用回归用例去断言 `/save` 当前的"不拒绝"行为 ✗ —— 那会把一个可疑行为**固化**为正确行为（正如 G2 中被修订的那个用例 ✓）。→ 该行为**只登记**，由 T3/Phase 2 决定 ✓

## 6. 测试落点方案（**待裁定**）

| 方案 | 位置 | 优点 | 缺点 | 评价 |
| :-: | --- | --- | --- | --- |
| **A（推荐）** | `IdentityAuthorizationRegressionTest`（与 #19 同址，新增 1–2 个用例）| 复用既有造令牌 + `finally` 清理的**成熟模式** ✓；与身份/授权主题一致 ✓；单一套件即可回答"role 缺失" ✓ | 用例数 28→29/30（**该套件计数会变**，需同步文档 ✓）| ✅ **推荐** |
| B | `Phase2CoreRegressionTest` | 主套件 | 该套件是**业务基线**（分页/敏感字段/异常契约）✗ 主题不符 ✗ | ✗ |
| C | 新建 `RoleMissingE2ETest` | 主题纯粹 | 为一个场景新增套件 ✗；重复 #19 的脚手架 ✗ | ✗ |

**用例设计草案（方案 A）**

```text
T11-a  role = NULL 的 token
       ① 造令牌：tokenService.save(new AuthTokenEntity(STUDENT_A1_ID, "a1", "student", null, token, 未来时间))
       ② 断言：/xinlilaoshiLiuyan/page?page=1&limit=10  → assertDenied(...)  （DataScope 路径）
       ③ 断言：/xinlilaoshiLiuyan/list                  → assertDenied(...)  （G1 收敛后的 /list 路径）
       ④ finally：tokenService.remove(...)              （对齐 #19，零残留）

T11-b  role = ""（空字符串）的 token —— 同上三步
       （NULL 与 "" 都必须覆盖：前者经 setAttribute(null) 语义、后者经 String.valueOf("") 语义）

复用既有助手：assertDenied(result, label) 已是【三重断言】——
  HTTP 403 + body.code=403（而非 401）+ body.msg 统一为拒绝语 ✓
⇒ 新用例无需重写契约断言，直接复用即可（同时保证与 #19 的判定口径完全一致）✓
```

**反向对照（必须做，且不能"自证"）**

| 层 | 设计 |
| --- | --- |
| ① 配对正向断言 | 同一端点上，**合法学生 token → 200 + code=0**；证明 403 来自**角色**而非端点坏掉 ✓（#19 与 #8 已有同型先例）|
| ② 一次性反向对照（实施阶段做一次，不入库）| 临时让 `DataScope` 对未登记角色**不拒绝**（改 1 行）→ 新用例**必须失败** → 立即还原 ✓（与 G1 模块 1 的做法一致 ✓）|

## 7. 本审计**未覆盖 / 未验证**（如实登记）

| # | 项 | 原因 |
| :-: | --- | --- |
| 1 | NULL/空 role 的**真实 HTTP 行为** | 需写入一条 token 行 ✗ —— 属本轮禁令（不改数据库）；**Phase 2 用测试内的 `tokenService.save`**（如 #19 ✓）完成，不手工写库 ✓ |
| 2 | `getAuthTokenEntity(token)` 是否过滤 `expired_at` | 未核（与本缺口无关，但影响"过期令牌"语义）✓ 建议另立小项 |
| 3 | `/save` 的前端可达性 | 引用既有 **T3**（13 个 `/save` 无调用方）✓ 不重复取证 |
| 4 | `/users/login` 当 `admin_user.role` 为空时的**登录响应** | 未实测（需改数据 ✗）；可只读推断为 `role:null` 写入令牌 ✓ |

## 8. 结论与 Phase 2 建议

1. **T11 缺口成立且精确** ✓：不是"没有测 role"，而是**没有测「NULL/空 role 走完整 HTTP 链路」**；#19（非空未登记角色）与 #25（单元契约）**都不能替代** ✓
2. **缺失角色在读取侧是 fail-closed 的** ✓（19 处 `DataScope` + 33 处 `OwnershipGuard` 静态可证 ✓）—— 待 Phase 2 用 E2E 用例**实证** ✓
3. **新发现一条相邻缺口** ✓：`/save` 的旧角色链**无终态拒绝** ⇒ 未知/null 角色时**归属字段保留客户端值** ✗ —— 建议**并入 T3** 处置，**并明确禁止用回归用例固化该行为** ✓
4. **Phase 2 交付物（待批准）**：方案 A 的 **2 个 E2E 用例（NULL / ""）+ 配对正向断言 + 一次性反向对照** + 用例计数文档同步 ✓ —— **不改任何业务代码** ✓

---

## 8bis. Phase 2 实施结果（**仅测试**，生产代码零改动 ✓）

**性质（措辞纪律，按审阅要求）**：本批**不是**「修复 role 缺失漏洞」✗ —— 生产代码读取侧本就 fail-closed ✓；缺的是**不变量守护** ✓ ⇒ **「行为错误 ≠ 覆盖缺口」**，本项属于后者 ✓

| 项 | 内容 |
| --- | --- |
| 变更范围 | **仅** `IdentityAuthorizationRegressionTest`（+47 行：2 用例 + 1 共用体）|
| 新增 | **#28** `role = NULL` · **#29** `role = ""` —— 各断言 `/xinlilaoshiLiuyan/page` 与 `/list` **均 403** ✓ |
| 断言口径 | 复用 `assertDenied`（**三重**：HTTP 403 + `body.code=403` + 统一 `msg`）✓ —— 与 #19 完全一致 ✓ |
| **正向控制**（每组内）| 同端点 + 合法学生 token → **200 + code=0** ✓ ⇒ 证明 403 来自**角色缺失**，而非端点被错误拦截 ✓ |
| 造令牌 / 清理 | 沿用 #19 模式：`tokenService.save(...)` + `finally remove(...)` ✓ |
| 回归 | `mvn clean test` → **46/46** ✓（身份套件 **28 → 30** + 业务基线 16 ✓）|
| 库基线 | `auth_token` 前 3 / 后 3 ✓（用例自造令牌**全部清理** ✓）|

### 8b.1 一次性反向对照（**不提交** ✓）

```text
临时把 DataScope 对未登记角色的 OwnershipGuard.deny() 改为 return;
        ↓
两个新用例【均失败】✓✓
  [role=NULL 查询 /page 应被拒绝（HTTP 403），实际 body={"code":0,…,"total":6,…}]  expected: 403 but was: 200
  [role=""   查询 /page 应被拒绝（HTTP 403），实际 body={"code":0,…,"total":6,…}]  expected: 403 but was: 200
        ↓（失败信息即泄漏本身：total=6 = 全表 ⇒ fail-open 形态）
立即还原 → git diff src/main 为空 ✓ → 全量回归 46/46 ✓
```

⇒ 用例捕获的是「**缺失角色必须拒绝**」这一**契约** ✓，而非"当前实现恰好返回 403" ✓

### 8b.2 禁止改动清单核对（逐项）

| 禁止项 | 是否触碰 |
| --- | :-: |
| Controller / Service / DAO / XML | ❌ 未触碰 ✓ |
| `CurrentUserProvider` / `DataScope` / `OwnershipGuard` / `AuthorizationInterceptor` | ❌ 未触碰（`DataScope` 仅一次性实验并**完全还原**，`git diff src/main` 为空 ✓）|
| DB schema / 生产配置 / Agent 代码 | ❌ 未触碰 ✓ |

### 8b.3 同批登记（**不在 T11 处理** ✓）

* `/save` 的旧角色链**无终态拒绝**（未知/null 角色 ⇒ 归属字段保留客户端值 ✗）→ **并入技术债 T3** ✓
* ⛔ **明确禁止**为该行为编写"role 缺失时 `/save` 仍成功"的回归用例 ✗ —— 那会把可疑行为**固化**为契约（G2 已发生过一次同类教训 ✓）
* 审计**补录**：既有 **#16**（非空未登记角色，**读 + 写**两侧 E2E）已在 §4 矩阵补列 ✓
* 另发现（仅登记，未改动）：本套件内存在**两个「21.」编号**（L460「学生改自己资料」与 L594「管理端账号…」）→ 属既有笔误 ✓，避免在 T11 内顺手改测试语义 ✓

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2 | 2026-10-16 | **Phase 2 完成（仅测试，`d59079c`）**：新增 **#28/#29**（NULL / 空 role → `/page`+`/list` 403，复用 `assertDenied` 三重断言 + 每组**正向控制**）；**46/46**；**一次性反向对照命中**（破坏 `deny()` → 两用例失败，且失败信息即泄漏本身 `total=6`）→ 立即还原、`src/main` **零 diff**；**零生产代码改动**；审计**补录 #16**；登记 `/save` 无终态拒绝**并入 T3（禁止用例固化）**；另登记套件内重复「21.」编号 → 见 §8bis |
| v1 | 2026-10-16 | T11 Phase 1 只读审计（**零代码、零写库**）：身份链逐环节取证；`role` 四条来源 + schema ⇒ **缺失令牌可达**；消费面 19/33/8 与 fail-closed 判定；覆盖矩阵指出**精确缺口**；**新发现 `/save` 无终态拒绝**（并入 T3，禁止用用例固化）；给出 3 个测试落点方案与用例草案 + 反向对照设计 |
