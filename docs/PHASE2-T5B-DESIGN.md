# Phase 2 / Step 5 第二阶段设计：身份边界实现方案 + 接口边界 + 回归测试设计

> **阶段**：Step 5 第二阶段（**设计**）· **状态**：⏳ **待确认**（确认后才写代码）
> **前置**：[`PHASE2-T5-IDENTITY-AUDIT.md`](./PHASE2-T5-IDENTITY-AUDIT.md)（第一阶段取证，**已验收通过**）
> **本文件**：**零代码改动**，只做方案 / 边界 / 测试设计

---

## 0. 目标重述（采用你的表述）

> **当前用户身份本身是可信的；真正的问题不是「客户端能伪造 userId」，而是部分接口拿到业务对象后，没有继续验证「这个对象是否属于当前用户」。**
>
> **Provider 不是目的，授权边界才是目的。**

因此本阶段交付的是**一条完整的信任边界**，而不是把 `session.getAttribute("userId")` 换个写法：

```text
HTTP / Agent Tool
       │  不可信的 id / 参数
       ▼
Controller
       ├── 当前用户身份   → CurrentUserProvider
       ├── 当前用户范围   → DataScope（fail-closed）
       └── 目标对象 id    → OwnershipGuard / authorizedTargetUser / stampOwner
       ▼
Service → DAO → DB
```

---

## 0.1 🔒 实现前已锁定的决策（2026-10-05）

| # | 决策 | 状态 |
| --- | --- | --- |
| **D1** | 授权拒绝 = **`HTTP 403` + `{code:403, msg:"无权访问"}`**；且明确其为**新增行为契约**，验证须含成功路径 | 🔒 **已锁定** |
| **D3** | 密码重置：**只修 2 个 `/resetPassword?id=`**；`/resetPass` 系列**不凭空设计新规则**；`/users/resetPass` 是否下线**以调用证据为准** | 🔒 **已锁定** |

---

## 1. 边界原语设计（接口层面，非实现）

| # | 原语 | 职责 | 关键约束 |
| --- | --- | --- | --- |
| ① | `CurrentUserProvider` | `currentUserId()` / `currentRole()` / `currentUsername()` | **只读**；**最后一个做**（纯收敛） |
| ② | `DataScope` | 按 role 收敛**读**范围（等价于现有 `params.put("studentId", ...)`） | **fail-closed**：未知 role → 拒绝，不默认全量 |
| ③ | `OwnershipGuard` | 判定「目标对象是否属于当前用户」 | **本阶段核心**；覆盖 `/info/{id}` |
| ④ | `stampOwner(entity)` | 写路径：**当前用户自己创建**时，归属由服务端赋值 | 用于 A 类 |
| ⑤ | `authorizedTargetUser(entity)` | 写路径：**管理员/老师代表目标用户操作**时，目标需显式授权 | 用于 B 类，**不得与 ④ 混淆**；**证据驱动约束见下** |

> ⚠️ ④ 与 ⑤ 的区分是关键：`学生创建自己的记录` 与 `管理员创建某学生的记录` **业务语义不同**，因此**不能机械替换为 `stampOwner`**。

**⑤ 的准入约束（D5 收紧，必须遵守）**：

```text
❌ 不允许：因为 role == "管理员"，就在所有接口自动套用 authorizedTargetUser
✅ 必须：先证明该接口原本就存在「管理员/心理老师 → 代表目标用户操作」的业务语义
        （证据形式：前端确有该入口 / 现有代码已有该分支 / 现有测试覆盖该行为）
❌ 无证据的接口：一律「拒绝」，而不是「默认放行」
```

---

## 2. fail-closed 角色策略（D5）

**现状问题是「查全量是 `else` 行为」，而不是「管理员能查全量」**：

```java
// 现状：else 兜底 → 未知 role / null 都落入"全量"
if ("学生".equals(role)) { ... }
else if ("心理老师".equals(role)) { ... }
```

**目标语义（显式授权 + 默认拒绝）**：

| role | scope | 说明 |
| --- | --- | --- |
| `学生` | `studentScope(currentUserId)` | 与现状一致 |
| `心理老师` | `counselorScope(currentUserId)` | 与现状一致 |
| `管理员` | `allScope()` | **显式授权**（不再是 else 兜底） |
| **其他 / null / 空** | **拒绝** | 🔴 从「默认全量」改为「默认拒绝」 |

---

## 3. 接口边界设计：15 个 `/info/{id}` 逐接口分类（本阶段核心成果）

> **关键修正**：15 个端点**并非都需要授权**。「内容类」表没有归属字段，**加校验会误伤公开访问**。
> 实测它们的出参形态差异很大，必须**逐接口分类**，不做统一处理。

### 3.1 类别划分

| 类别 | 端点数 | 端点 | 处置 |
| --- | --- | --- | --- |
| 🔴 **需归属授权** | **8** | `examrecord`、`examredetails`、`examrewrongquestion`、`xinlilaoshiOrder`、`xinlilaoshiLiuyan`、`xinlilaoshiCollection`、`yonghu`、`users` | **本阶段修复**（③ OwnershipGuard） |
| ✅ **内容类（无需授权）** | **6** | `config`、`dictionary`、`exampaper`、`examquestion`、`jiankangzhishi`、`tongzhi` | **保持现状**（无归属字段；`notification` 表实测无 `student_id`） |
| ⚠️ **半公开（待你定）** | **1** | `xinlilaoshi` | 老师资料属目录性质；见 §10-D6 |

### 3.2 8 个需授权端点的归属字段与预期

| 端点 | 归属字段 | 学生 | 心理老师 | 管理员 |
| --- | --- | --- | --- | --- |
| `/yonghu/info/{id}` | 记录自身 `id`（学生 PII） | 仅自己 | 授权范围 | 全部 |
| `/xinlilaoshiOrder/info/{id}` | `student_id` + `counselor_id` | 仅自己 | 仅自己服务对象 | 全部 |
| `/xinlilaoshiLiuyan/info/{id}` | `student_id` + `counselor_id` | 仅自己 | 仅自己服务对象 | 全部 |
| `/xinlilaoshiCollection/info/{id}` | `student_id` + `counselor_id` | 仅自己 | 仅自己服务对象 | 全部 |
| `/examrecord/info/{id}` | `student_id` | 仅自己 | 授权范围 | 全部 |
| `/examredetails/info/{id}` | `student_id` | 仅自己 | 授权范围 | 全部 |
| `/examrewrongquestion/info/{id}` | `student_id` | 仅自己 | 授权范围 | 全部 |
| `/users/info/{id}` | 管理员账号 | **拒绝** | **拒绝** | 全部 |

> **注意**：归属字段是 **C 类数据字段**（`student_id`/`counselor_id` 是 6 张表的真实外键）—— 校验只**读**它，绝不删除或改写。

### 3.3 拒绝时的响应形态 🔒 **已锁定（2026-10-05）**

| 结论 | 内容 |
| --- | --- |
| **采用** | **`HTTP 403` + `{code:403, msg:"无权访问"}`** |
| 语义 | `认证失败 → 401`（Step 4 契约，不动）；`已认证但无权 → 403` |
| **性质（重要）** | ⚠️ **这不是单纯的内部重构，而是「新增行为契约」** —— 因此验证**不能只测越权失败**，必须同时覆盖成功路径（见 §6.2/§7） |
| 兼容性 | 前端非 2xx 本就走 `error` 分支（现仅显示通用提示）→ 无破坏 ✓ |

> 被否决的备选：`HTTP 200 + code:403`（把授权失败伪装成成功响应）；`code:0 + 空 data`（无法被测试与 Agent 识别）。

---

## 4. 接口边界设计：密码重置（P0-3，**优先于** `/info/{id}`）

### 4.1 全量盘点：**6 个端点**（v2 修正）

> ⚠️ **v1 设计只列了 3 个，是错的** —— 漏掉 `XinlilaoshiController` 的 2 个。
> 修正来源：v1 的「管理端 0 处调用」是**路径解析失败导致的假阴性**；改用绝对路径重取证据后，才发现管理端对密码重置有**真实调用**，并据此补齐端点清单。

| # | 端点 | 鉴权 | 前端调用证据 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | `/yonghu/resetPassword?id=` | 需登录，**无角色/归属校验** | ✅ **管理端真实调用**：`yonghu/list.vue:738` + 「重置密码」按钮（`isAuth('yonghu','修改')`） | **本阶段修复越权**：限「管理员」—— **保留管理功能**，仅堵住学生越权 |
| 2 | `/xinlilaoshi/resetPassword?id=` | 需登录，**无角色/归属校验** | ✅ **管理端真实调用**：`xinlilaoshi/list.vue:750` + 同上按钮 | 同上（限管理员） |
| 3 | `/yonghu/resetPass?username=` | **`@IgnoreAuth`** | ❌ 用户端 60 文件 + 管理端 82 文件**零调用** | **暂不实现新规则**（见 §4.2） |
| 4 | `/xinlilaoshi/resetPass?username=` | **`@IgnoreAuth`** | ❌ 零调用 | 同上 |
| 5 | `/users/resetPass?username=` | **`@IgnoreAuth`** | ❌ 零调用 | 见 §4.2（**待你确认下线**） |
| 6 | `users/...`（dist 曾疑似调用 `yonghu/resetPass`） | — | ✅ **已排除**：dist 中的 `yonghu/resetPass` 是 `yonghu/resetPassword` 的**前缀误匹配** | 无需处理 |

### 4.2 D3 修订后的策略（采纳「不凭空设计业务规则」原则）

> **安全修复可以基于证据，但不能为了「修安全问题」凭空创造业务规则。**

| 端点 | 本阶段动作 |
| --- | --- |
| `/yonghu/resetPassword?id=`、`/xinlilaoshi/resetPassword?id=` | ✅ **本阶段修复越权**（限管理员）—— 有明确证据：管理端确实用它，学生不该用 |
| `/users/resetPass?username=` | ⏸ **以调用证据为准**：现有证据为「前端零调用 + 匿名重置管理员账号」→ **建议下线**，但**需你拍板**（见 §10-D3） |
| `/yonghu/resetPass?username=`、`/xinlilaoshi/resetPass?username=` | ⏸ **暂不实现任何新的身份验证规则**。它们是「忘记密码」入口还是历史遗留危险接口，现有证据不足以判定 → **保留现状 + 保留证据**，待单独确认「合法密码恢复业务」后再处理 |

> ⚠️ **测试安全要求（你明确要求）**：**不得**为了验证漏洞而真的把某个用户密码改成 `123456`。
> §6.3 给出的方案是「**前置快照 + 请求 + 后置比对**」，测试自身不产生任何数据变更。

---

## 5. 接口边界设计：写路径（D6，规模约 48 个端点）

写端点清点：**11 个 Controller 各有 `save`/`add`/`update`/`delete`**（44 个）+ `config`/`users` 各有 `update`/`delete`（4 个）= **约 48 个**。

### 5.1 分类规则（先分类，再决定原语）

| 类 | 语义 | 处置 |
| --- | --- | --- |
| **A** | 当前用户**自己创建** | `stampOwner(entity)` —— 覆盖客户端归属 |
| **B** | 管理员/老师**代表目标用户**操作 | `authorizedTargetUser(entity)` —— 目标需显式授权 |
| **C** | 历史/关联字段（如"预约的老师"） | **保留客户端值**，但需校验目标存在（现已有 `getById(counselorId)` 校验 ✓） |
| **D** | 客户端**不应决定**却被采信 | 归入 A（自己创建）或 B（授权代操作），**不得保留** |

### 5.2 已知需要处理的写路径证据（第一阶段取证）

| 位置 | 现状 | 判定 |
| --- | --- | --- |
| `/save`（6 个 Controller，`studentId`） | 覆盖**生效** | ✅ 已正确，分类为 A，纳入 `stampOwner` 统一 |
| `/save`（3 个 Controller，`counselorId`） | 覆盖**生效** | ✅ 已正确（老师/管理员代操作），归 B |
| **`/update` 内 9 处覆盖** | **已被注释** → 采信客户端 `studentId` | 🔴 **按 A/B 逐接口补齐**（非法则拒绝） |
| **`/add`（如 `ExamrecordController.add`）** | **完全采信客户端 `studentId`** | 🔴 同上 |
| `XinlilaoshiOrderController.add:338` | `setStudentId(userId)` **无条件覆盖** | ✅ 正确，归 A |

> **实施原则**：**不做「搜到 `studentId` 就全改」的批量替换**。每个写接口先落分类，再决定 `stampOwner` / `authorizedTargetUser`，无分类依据的一律**先记录不修改**。

---

## 6. 回归测试设计（**先设计测试，再改实现**）

### 6.1 测试类

| 项 | 设计 |
| --- | --- |
| 新增类 | `src/test/java/com/regression/IdentityAuthorizationRegressionTest.java` |
| 形态 | 与 T-2 一致：`@SpringBootTest` + `@AutoConfigureMockMvc`（走完整 MVC + 拦截器链，**不 mock**） |
| 依赖 | **零新增依赖** —— DB 前后快照改用**已有 Service** 读取（如 `yonghuService.getById(id).getPassword()`） |
| 基类复用 | 从 `Phase2CoreRegressionTest` 复制 `perform(uri, token)` / 登录辅助（暂不抽公共基类，避免动已有 14 项） |

### 6.2 身份授权回归矩阵（逐行映射到具体请求与种子数据）

| # | 场景 | 请求（a1 = student id 1） | 预期 |
| --- | --- | --- | --- |
| 1 | 学生访问**自己**的 `/info/{id}` | `留言 info/2`（属 student 1） | ✅ 200 + `code=0` |
| 2 | 学生访问**他人** `/info/{id}` | `留言 info/1`（属 student 2） | ❌ 拒绝 |
| 3 | 学生伪造 `studentId` 查询 page | `留言 page&studentId=2` | ✅ 仍只返回自己的 2 条 |
| 4 | 学生**创建**自己的对象 | `/xinlilaoshiLiuyan/add` + 客户端 `studentId=2` | ✅ 落地归属 = **1**（服务端覆盖） |
| 5 | 学生**修改他人**对象 | `留言 update`（id 属 student 2） | ❌ 拒绝 **且 DB 未变化** |
| 6 | 学生**修改自己**对象 | `留言 update`（id 属 student 1） | ✅ 成功 |
| 7 | 学生构造**他人归属**写入 | `/examrecord/add` + `studentId=2` | ❌ 拒绝或落地归属强制为 1 |
| 8 | 学生重置**自己**密码 | 按现有业务规则 | 维持现状 |
| 9 | 学生重置**他人**密码 | `resetPassword?id=<student 2>` | ❌ 拒绝 **且 student 2 密码 DB 未变化** |
| 10 | 管理员访问目标学生对象 | `admin` token + student 2 的 `info` | ✅ 允许 |
| 11 | 心理老师访问授权范围对象 | `counselor` token（a1 的 counselor 身份） | 按业务规则（需先确认种子数据 counselor_id 分布） |
| 12 | **未知 role** | 构造/模拟非三类 role 的 token | ❌ **不得默认全量** |
| 13 | 无 Token 访问受保护对象 | 不带 `Token` 头 | ❌ 401（沿用 Step 4 契约） |

### 6.3 越权**写**操作的断言方式（你的明确要求）

```text
① 前置快照：用已有 Service 读取目标对象（含 password）→ 记录
② 发送越权请求（期望被拒）
③ 后置比对：再次读取 → 断言与①完全一致
④ 同时断言响应为拒绝（HTTP/body）
```

**关键点**：测试**只读**目标数据，**不制造任何真实修改** —— 因此可以安全地在真实库上运行（与 T-2 一致）。
**第 12 项（未知 role）**：因 `auth_token` 表由测试库真实数据驱动，实施时需确认如何构造（直接插入一条测试 token 或临时改 role）——**待实现阶段取证后再定**，不在本设计内假定。

### 6.4 反空跑要求（沿用 Step 4 教训）

每条新断言都必须配**反向对照**：临时禁用对应校验 → 断言必须失败。至少覆盖：
`/info/{id}` 归属校验、密码重置校验、`/update` 归属校验。

---

## 7. 实施批次与验收

> **批 1 的收窄（采纳你的建议）**：批 1 **只做授权本身**，不掺入任何结构性重构。
> 理由：**若测试失败，必须能立刻判断「是授权逻辑错，还是身份抽象重构错」** —— 这是 Step 4 做得稳的原因（每个变化都有独立反向控制）。

| 批次 | 内容 | 验收 |
| --- | --- | --- |
| **批 1** ✅ | ③ `OwnershipGuard` + 8 个 `/info/{id}`<br>**（不含 `DataScope`、不含 `CurrentUserProvider`）** | ① 新增授权回归（**含成功路径**：学生访问自己 ✓ / 管理员访问目标 ✓ / 原业务成功路径 ✓）<br>② **原 14 项回归全绿**<br>③ 越权断言 + 反向对照 → **已完成，见 [`PHASE2-T5B1-REPORT.md`](./PHASE2-T5B1-REPORT.md)**（22/22 全绿；反向对照 4/4 失败） |
| **批 2** | 密码重置：**只修 2 个 `/resetPassword?id=`**（限管理员） | 矩阵 9 通过 + **DB 未变化**断言；管理端重置功能仍可用 |
| **批 3** | 写路径（D6）：`/update` 9 处 + `/add` 逐接口分类修复 | 矩阵 4/5/6/7 通过 |
| **批 4** | ② `DataScope`（fail-closed）+ ④⑤ 写原语统一 | 矩阵 3/11/12 通过 |
| **批 5** | ① `CurrentUserProvider` 收敛 103 处 Session 读取 | 现有 14 项 + 新增全部通过 |
| **批 6** | 文档与交付报告 | 报告 + 主计划校准 |

> 每批**独立提交、独立可回滚**；批 1–3 是本阶段的**安全价值**所在，批 4–5 是**结构收敛**。
> **批 1 验收通过前，不启动批 2。**

---

## 8. 兼容性与影响面（防破坏 B 类能力）

| 关注点 | 结论 |
| --- | --- |
| 管理端功能 | 管理员 = `allScope()`（**显式授权**）→ 现有查询能力**不变** |
| 心理老师功能 | `counselorScope` 与现状一致 |
| 前端改动 | **不需要**（不新增参数；拒绝响应走现有 error 分支） |
| 现有 14 项测试 | 预期**全绿**（不改成功路径）—— 若某项失败，说明分类有误，须先修正分类 |
| C 类字段 | 只读不写、不删（`student_id`/`counselor_id` 保留） |

---

## 9. D3 责任登记：匿名 `/list` 枚举（**隔离 ≠ 遗忘**）

按你的要求，为该问题登记明确的责任链（同时写入 `PHASE2-T5-IDENTITY-AUDIT.md` 与本文件）：

```text
问题：13 个 /list 免鉴权 + 无身份过滤 → 匿名枚举全表（含关联用户信息）
  ├── 起源：Step 3 待决策 D2（"免鉴权 /list 是否收敛"）
  ├── Step 5 第一阶段：✅ 已完成根因取证（定位到 @IgnoreAuth × 无 DataScope）
  ├── 本阶段（Step 5 第二阶段）：❌ 不修复（保持问题隔离）
  └── 归属：后续「权限治理阶段」（建议与 D1 HTTP 401 迁移一并评估）
```

---

## 10. 待你确认（设计层 6 项）

| # | 决策项 | 结论 / 建议 |
| --- | --- | --- |
| **D1** | 授权拒绝的响应形态 | 🔒 **已锁定**：`HTTP 403 + {code:403,msg:"无权访问"}`（§3.3，含「新增行为契约」定性） |
| **D2** | `/users/info/{id}` 是否也纳入（管理端账号） | **纳入**：学生/老师一律拒绝 |
| **D3** | 密码重置策略 | 🔒 **已锁定**（§4.2）：只修 2 个 `/resetPassword?id=`；`/yonghu/resetPass`、`/xinlilaoshi/resetPass` **暂不设计新规则**；**`/users/resetPass` 是否下线待你拍板**（证据：前端零调用 + 匿名重置管理员） |
| **D4** | 实施批次是否按 §7 顺序 | **批 1 已收窄**（只做 `OwnershipGuard` + 8 个 `/info/{id}`，验收通过前不启动批 2） |
| **D5** | 是否本阶段就引入 `authorizedTargetUser`（B 类） | **是**，但受 §1 准入约束：**不得仅因 role=管理员 自动套用**，须先证明该接口原有此业务语义；无证据则拒绝 |
| **D6** | `xinlilaoshi/info/{id}`（老师资料） | 视为**半公开**（已登录学生可看）→ **保持现状**；若你要求收紧，需先确认前端"老师详情页"是否必须 |
| **D7** | 🆕 `/users/resetPass` 是否本阶段下线 | **建议下线**（前端零调用 + 匿名重置管理员账号）；若你不同意，则本阶段**原样保留并登记归属** |

---

## 11. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-05 | Step 5 第二阶段**设计**：三层边界原语 + fail-closed 角色策略；15 个 `/info/{id}` **逐接口分类**（8 需授权 / 6 内容类 / 1 半公开）；密码重置 3 端点策略；写路径 ~48 端点分类规则（`stampOwner` vs `authorizedTargetUser`）；**身份授权回归矩阵 13 项 + DB 前后快照断言设计**；6 批实施顺序；D3 责任登记；6 项待确认 |
| v2 | 2026-10-05 | 按评审收紧三处：① **D1 锁定** `HTTP 403 + code=403` 并定性为「新增行为契约」→ 批 1 验收须含**成功路径**；② **D3 修订**：**修正密码重置端点清单 3 → 6 个**（v1 漏 `XinlilaoshiController` 的 2 个，根因是路径解析失败导致的假阴性），并确立「**不凭空设计业务规则**」原则 —— 只修 2 个 `/resetPassword`，`/resetPass` 系列暂缓；③ **D5 收紧**：`authorizedTargetUser` 增加**证据驱动准入约束**（不得仅因 role=管理员 自动套用）。另：**批 1 收窄**（不含 `DataScope`/`CurrentUserProvider`，验收通过前不启动批 2）；新增 §0.1 已锁定决策、§10-D7 |
