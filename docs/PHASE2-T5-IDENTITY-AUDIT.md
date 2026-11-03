# Phase 2 / Step 5 第一阶段：身份来源与 `userId` 信任边界取证

> **阶段**：Step 5（统一身份）· **第一阶段：仅取证**
> **状态**：✅ **已验收通过（2026-10-05）**；迁移边界已定 → 第二阶段设计见 [`PHASE2-T5B-DESIGN.md`](./PHASE2-T5B-DESIGN.md)
> **代码改动**：⛔ **零**
> **硬边界**：未触碰 Spring Security / Token 表结构 / 登录机制 / 前端重建 / Agent / DTO 重构 / D1 / D2
> **取证方式**：静态全仓检索 + **运行时越权实测**（只读 GET，未修改任何数据）

---

## 0. 一句话结论

**身份根是可信的** —— token → `auth_token` 表 → Session，全链路在服务端完成，客户端**无法**伪造身份。
读路径（`/page`）与写路径（`/save`）**已存在服务端身份覆盖机制，且经实测确认有效**（客户端传 `studentId=2` 仍只返回自己的数据）。

**但存在 4 类真实缺口**，其中 2 类已**实测证实**：

| # | 缺口 | 证据等级 |
| --- | --- | --- |
| **P0-1** | **15 个 `/info/{id}` 无归属校验 → 水平越权（IDOR）** | 🔴 **实测证实**（a1 读到 student 2 / student 3 的记录） |
| **P0-2** | **13 个 `/list` 免鉴权 + 无身份过滤 → 匿名枚举全表** | 🔴 **实测证实**（无 Token 拿到全表 6 条） |
| **P0-3** | 密码重置接口无归属/角色校验（`/resetPassword`）与匿名重置（`/resetPass`） | 🟠 静态确证（未做写入实测） |
| **P1-4** | `/add` 与 `/update` 的身份覆盖**部分缺失或被注释** → 客户端 `studentId` 可进入写入 | 🟠 静态确证（未做写入实测） |

---

## 1. 身份来源全图

```text
登录（/yonghu/login | /xinlilaoshi/login | /users/login）
   │  身份来自服务端查询：service.getOne(username) → 校验 password
   ▼
TokenService.generateToken(userId, username, tableName, role)
   │  32 位随机串、1 小时过期、按 (user_id, role) upsert
   ▼
auth_token 表（user_id / username / user_type / role / token / expired_at）   ← 【身份唯一根】
   │
   ▼
AuthorizationInterceptor.preHandle
   │  getTokenEntity(token)：校验存在 + 未过期 → 否则写 {code:401}
   │  ★ 全仓【唯一】身份写入点（第 73–76 行）：Session 写入 4 个属性
   ▼
Session: userId / role / tableName / username
   │
   ├──────────────► /page  读路径：params.put("studentId"|"counselorId", session)  ★ 覆盖客户端值
   │                （24 处 / 12 Controller）
   ├──────────────► /save  写路径：entity.setStudentId(session)                    ★ 覆盖客户端值
   │                （10 处生效 / 6 Controller）
   ├──────────────► "我的数据"类接口：(Integer) session.getAttribute("userId")
   │
   └─── ✗ 缺口 ───► /info/{id}、/list、/add、/update  ← 本节要收敛的目标
```

**量化**：

| 项 | 数值 |
| --- | --- |
| Session 身份**写入**点 | **1 处**（`AuthorizationInterceptor:73–76`，4 个属性） |
| Session 身份**读取**点 | **103 处 / 14 个 Controller** |
| 读路径身份注入（`params.put`） | **24 处 / 12 个 Controller**（**全部在 `/page`**） |
| 写路径身份覆盖（`/save` 生效） | **10 处 / 6 个 Controller**；**另 9 处已被注释**（在 `/update` 内） |
| 前端传 `studentId` 的位置 | 用户端 ≈30 处；**管理端 0 处** |

---

## 2. 逐向量取证结果（你列的 10 项）

| # | 向量 | 结果 |
| --- | --- | --- |
| 1 | **Session** | 写入**仅 1 处**（拦截器）；读取 **103 处 / 14 Controller**。`tableName` 与 `username` **写入后从未被读取**（死属性） |
| 2 | **Controller 入参** | 无 `@RequestParam userId`；但 **13 个 `/list`** 与 **15 个 `/info/{id}`** 直接以路径/查询参数接收目标 id；`/page` 接收 `Map params`（含任意过滤字段） |
| 3 | **DTO / Entity** | 无 `dto` 包（T-1 已清 `entity/model`、`entity/vo`）；`TokenEntity` 带 `userId`；**6 个业务实体**带 `studentId`/`counselorId`（`assessment_record`、`assessment_answer`、`assessment_wrong_question`、`counseling_appointment`、`counselor_favorite`、`counselor_message`） |
| 4 | **Service** | ✅ **不单独接收 `userId`** —— 仅 `TokenService.generateToken(userId, ...)`（签发路径，身份来源可信）。业务 Service 只收 `params`/`QueryWrapper` |
| 5 | **Mapper XML** | `userId` **0 匹配** —— SQL 用 `student_id`/`counselor_id` 列名；过滤由 `Query.putAll(params)` + `MPUtil` 生成 |
| 6 | **ThreadLocal** | **0 匹配** —— 无线程级身份机制（`com/thread` 只有一个空转的 `MyThreadMethod`，与身份无关） |
| 7 | **Token** | ⚠️ 见下方说明 |
| 8 | **管理员身份** | `admin_user.role = "管理员"`；`auth_token` 实测：`a1/student/学生`、`a1/counselor/心理老师`、`admin/admin_user/管理员`。**同一张表、同一套模型** ✓。`/page` 中 `"管理员"` **两个分支都不进 → 不做身份注入 → 可查全量**（符合 B 类，但**未知角色同样默认获得全量视野**） |
| 9 | **前端** | **管理端 `userId` 只被声明与赋值，从不发送**（实测 0 命中）✓。用户端 ≈30 处 `studentId: localStorage.getItem("userId")` —— 但被服务端覆盖保护 ✓。**1 处例外见 P1-5** |
| 10 | **特殊接口** | `@IgnoreAuth` 共 **25 处**：**13 个 `/list`** + 3 组 login/register + **2 个 `resetPass`（免鉴权密码重置）** + `file/download` + `config/list`、`config/detail`、`dictionary/page` |

### 2.1 Token 向量的两个隐患（静态确证）

| 隐患 | 说明 |
| --- | --- |
| **无 Token → 用户 映射失效控制** | `getTokenEntity` 只校验「存在 + 未过期」；**无登出即失效机制**（`/logout` 只做 `session.invalidate()`，**不失效 token**）→ 拿到 token 后 1 小时内持续有效，登出无效 |
| **`user_type` 与实际表名解耦** | token 的 `user_type` 写入 Session 的 `tableName`，但**全仓从未读取** `tableName` → 身份表名信息实际未被使用（死属性） |

---

## 3. 身份信任边界分类（A / B / C / D）

### A. 当前登录用户身份 —— ✅ 必须来自服务器（现状基本正确）

| 场景 | 位置 | 状态 |
| --- | --- | --- |
| 身份写入 | `AuthorizationInterceptor:73–76` | ✅ 唯一入口 |
| 读路径注入 | 12 个 `/page`（24 处） | ✅ **实测覆盖有效** |
| 写路径覆盖（`/save`） | 6 个 Controller（10 处生效） | ✅ 生效 |
| 前端下单 | `XinlilaoshiOrderController:338` | ✅ `setStudentId(userId)` 无条件覆盖 |
| 「我的信息」 | `/yonghu/session`、`/users/session` 等 | ✅ 从 Session 取 id |

### B. 管理员 / 老师查询其他用户 —— ⚠️ 可存在目标 id，但需权限判断

| 现状 | 判定 |
| --- | --- |
| `role = "管理员"` → 不注入 → 查全量 | ✅ 语义正确（管理员本应如此） |
| `role = "心理老师"` → 注入 `counselorId` | ✅ 只见自己的服务对象 |
| ⚠️ **role 白名单缺失** | 任何**非「学生」非「心理老师」**的角色值（含未来新增角色）都会**默认获得全量视野** —— 应显式白名单化 |

### C. 业务对象中的历史/关联 userId —— ✅ 属数据字段，不能删

`student_id` / `counselor_id` 是 **6 张表的真实外键**（含级联查询、`View` 渲染）。
**Step 5 绝不能做「全局替换删除 `userId`」** —— 这类字段要保留，只需保证**写入时由服务端赋值**。

### D. 客户端可控且本应被禁止的 userId —— 🔴 缺口所在

见 §5 缺口清单。

---

## 4. 运行时实测证据（只读 GET，未修改数据）

> 方法：启动应用 → 以 **a1（student id = 1）** 登录取得 token → 分别验证「覆盖是否生效」与「越权是否成立」。
> 种子数据：`counselor_message` id=1 属 student **2**；`counselor_favorite` id=1 属 student **3**。

| 测试 | 请求 | HTTP | code | 关键字段 | 判定 |
| --- | --- | --- | --- | --- | --- |
| A1 | `留言 page`（a1） | 200 | 0 | total=**2**, studentId=1 | ✅ 只见自己 |
| A2 | `留言 page&studentId=2`（a1 试图查他人） | 200 | 0 | total=**2**, studentId=**1** | ✅ **覆盖生效**：客户端传参被忽略 |
| **B1** | `留言 info/1`（属 student 2） | 200 | 0 | **studentId=2** | 🔴 **越权成立** |
| **B2** | `收藏 info/1`（属 student 3） | 200 | 0 | **studentId=3** | 🔴 **越权成立** |
| **C1** | `留言 list`（**无 Token**） | 200 | 0 | total=**6** | 🔴 **匿名枚举全表**（a1 自己仅 2 条） |
| **C2** | `收藏 list`（**无 Token**） | 200 | 0 | total=**6** | 🔴 **匿名枚举全表** |

> A2 是关键对照：它同时证明了「`/page` 的身份注入真实有效」与「越权并非来自参数注入」，把缺口精确定位到 `/info/{id}` 与 `/list`。

---

## 5. 缺口清单（按严重度，标注证据等级）

| # | 缺口 | 规模 | 证据等级 |
| --- | --- | --- | --- |
| **P0-1** | **`/info/{id}` 无归属校验** → 任一登录学生可按 id 读取他人记录（留言/收藏/预约/测评/错题等） | **15 个端点** | 🔴 **实测证实** |
| **P0-2** | **13 个 `/list` 免鉴权 + 无身份过滤** → 无 Token 遍历全表（含关联用户信息） | 13 个端点 | 🔴 **实测证实**（= Step 3 待决策 D2 的技术根源） |
| **P0-3** | **密码重置越权（v2 修正为 6 个端点）**：① `/yonghu/resetPassword?id=`、② `/xinlilaoshi/resetPassword?id=` 需登录但**无角色/归属校验** → 任一登录用户可重置他人密码为 `123456`（**账户接管**）；③ `/yonghu/resetPass`、④ `/xinlilaoshi/resetPass`、⑤ `/users/resetPass` 为 **`@IgnoreAuth`** → **匿名**重置任意账号（含**管理员**）。<br>⑥ 澄清：`admin/dist` 中的 `yonghu/resetPass` 是 `yonghu/resetPassword` 的**前缀误匹配**，非真实调用 | **6 个端点**（v1 误记为 3，漏 ②④） | 🟠 静态确证（**未做写入实测，避免改动数据**）；**前端调用证据**：①② 有管理端真实调用（`yonghu/list.vue:738`、`xinlilaoshi/list.vue:750`），③④⑤ 前端零调用 |
| **P1-4** | **写路径身份覆盖缺失**：`/save` 生效但 `/update` 内的覆盖**已被注释**（9 处）；部分 `/add`（如 `ExamrecordController.add`）完全采信客户端 `studentId` → 客户端可指定写入归属 | ≈9–13 处 | 🟠 静态确证（未做写入实测） |
| **P1-5** | 前端 `examrewrongquestion/list.html:211,229` 用 **URL 参数** `http.getParam("userId")` 当 `studentId` 提交 | 1 处 ×2 | 🟠 静态确证。**当前被服务端覆盖保护**，但一旦覆盖逻辑变动即成越权 —— 属脆弱设计 |
| **P2-6** | `xinlilaoshi/confirm.html:49–50` 把 `counselorId` 与 `studentId` **都**设为 localStorage 的 `userId` → 预约可能挂到错误老师 | 1 处 | 🟠 静态确证（数据完整性缺陷，非越权） |
| **P2-7** | Session 的 `tableName`/`username` 写入后从未读取（死属性）；`chat/chat.html` 传 `userId` 但**后端无 chat 模块**（死页面） | — | 🟠 静态确证 |
| **P2-8** | `/logout` 只 `session.invalidate()`，**不失效 token** → 登出后 token 在有效期内仍可用 | 1 处 | 🟠 静态确证 |

### 5.1 问题归属登记：匿名 `/list` 枚举（**隔离 ≠ 遗忘**）

> 本项**不在 Step 5 修复**（保持问题隔离），但必须留下明确的责任链，避免日后出现「这个问题之前不是已经审过了吗？」。

```text
问题：13 个 /list 免鉴权（@IgnoreAuth）+ 无身份过滤 → 无 Token 可遍历全表
  ├── 起源：Step 3 待决策 D2（"免鉴权 /list 是否收敛"）
  ├── Step 5 第一阶段：✅ 已完成根因取证（@IgnoreAuth × 缺 DataScope 收敛）
  │                    证据：PHASE2-T5-IDENTITY-AUDIT.md §4（无 Token → total=6）
  ├── Step 5 第二阶段：❌ 不修复（PHASE2-T5B-DESIGN.md §9 同步登记）
  └── 归属：后续「权限治理阶段」，建议与 D1（HTTP 401 契约迁移）一并评估
```

---

## 6. `CurrentUserProvider` 应当抽象什么（**建议，待你确认**）

---

## 6. `CurrentUserProvider` 应当抽象什么（**建议，待你确认**）

取证结果表明需要区分**三种不同性质**的东西，不要混成一个类：

| 候选 | 抽象内容 | 替换/覆盖规模 | 性质 |
| --- | --- | --- | --- |
| **① 身份读取入口** | `currentUserId()` / `currentRole()` / `currentUsername()` | **103 处**（14 Controller） | 纯收敛，零行为变更 |
| **② 身份注入/覆盖** | `applyDataScope(params)`（读路径）+ `stampOwner(entity)`（写路径） | **24 + 10 ≈ 34 处** | 把已经正确的模式统一，防漏 |
| **③ 归属校验** | `assertOwner(ownerId)` / `assertOwnedOrAdmin(ownerId)` | **15 个 `/info/{id}`**（+`/update`、`/add`） | **新增能力**（补越权缺口），非抽象 |

**建议优先级**：**③ > ② > ①**
理由：③ 是**真实越权缺口**（已实测），Agent 化后 Tool 若走这些接口后果放大；② 防「未来新增接口漏注入」；① 只是可读性收敛。

> ⚠️ 三者都**不得**触碰 C 类字段（`student_id`/`counselor_id` 作为数据列必须保留）。

---

## 7. 硬边界遵守声明（逐条对照你的要求）

| 禁止项 | 状态 |
| --- | --- |
| Spring Security | ⛔ 未引入 |
| 修改 Token 表结构 | ⛔ 未改（`auth_token` 原样） |
| 修改登录机制 | ⛔ 未改 |
| 前端 Vue3 重建 | ⛔ 未做 |
| Agent / Tool | ⛔ 未做 |
| DTO 大重构 | ⛔ 未做 |
| 删除 `userId` 参数 | ⛔ 未删（并已论证 C 类不可删） |
| 把管理员查询他人改成「只能查自己」 | ⛔ 未改（B 类现状保留） |
| 顺手修 D1（HTTP 401） | ⛔ 未动 |
| 顺手处理 D2（`/list`） | ⛔ 未动（仅取证其技术根源，未修改） |

---

## 8. 待确认项（确认后才进入实现）

| # | 决策项 | 建议 |
| --- | --- | --- |
| **D1** | `CurrentUserProvider` 的范围：仅「身份读取入口」／同时含「注入 + 归属校验」 | **三者都做，但分批**：先 ③ 归属校验（堵越权），再 ② 注入统一，最后 ① 收敛 |
| **D2** | 15 个 `/info/{id}` 的水平越权是否纳入 Step 5 修复 | **纳入**（**实测证实**，且是 Agent 化前必须堵的信任边界） |
| **D3** | 13 个 `/list` 匿名枚举（= Step 3 的 D2）是否在 Step 5 一并处理 | **不在此步处理**（保持问题隔离；但需确认它最终归属哪一步，避免长期悬空） |
| **D4** | 密码重置越权（P0-3）是否纳入 Step 5 | **纳入**（属身份/授权范畴；风险等级最高） |
| **D5** | `role` 是否显式白名单化（避免未知角色默认全量视野） | **是**（低成本、防未来角色扩展踩坑） |
| **D6** | P1-4 写路径覆盖缺失是否纳入 | **纳入 P0-1 的同批修复**（同一类「归属不可信」问题） |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-05 | Step 5 第一阶段取证：10 个身份向量全量扫描；身份来源全图；A/B/C/D 信任边界分类；**运行时越权实测（6 项）**；8 项缺口清单（2 项实测证实 / 6 项静态确证）；`CurrentUserProvider` 三个候选抽象与优先级建议；6 项待确认 |
| v2 | 2026-10-05 | **第一阶段验收通过**；新增 §5.1 匿名 `/list` 问题的归属登记（隔离≠遗忘，指向后续权限治理阶段）；状态更新并链接第二阶段设计文档。核心判断据实修正为：**问题不是「客户端可伪造 userId」，而是「拿到对象后缺少归属授权」** |
