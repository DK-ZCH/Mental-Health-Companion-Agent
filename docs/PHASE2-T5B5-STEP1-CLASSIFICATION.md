# Phase 2 / Step 5 批 5 · 第一步：Session 读取**只读分类**（零代码改动）

> **性质**：按审阅要求「**先做只读分类，不直接全局替换**」
> **状态**：✅ 分类完成（**未改任何代码**）
> **代码改动**：⛔ **零**
> **范围**：`src/main/java` 下全部 Session **读取**站点

---

## 0. 重要：设计文档的「103 处」与实测不符（需校准）

设计文档 §7 批 5 写的是「① `CurrentUserProvider` 收敛 **103 处** Session 读取」。只读分类的实测结果：

| 口径 | 实测 |
| --- | --- |
| `getSession()` 出现总次数 | **61** |
| `.getAttribute(` 出现总次数 | **57**（含 3 处 `servletContext` 字典缓存，**与身份无关**） |
| **`getSession().getAttribute(`（身份读取）** | **54** |
| 其中：javadoc 注释内的示例（`DataScope`） | 2（**非代码**） |
| 其中：守卫本体（`OwnershipGuard.currentUserId/currentRole`） | 2（**收敛的目标实现**，非待迁移对象） |
| **→ 控制器中待收敛的真实读取** | **50** |
| `RequestContextHolder` / `@SessionAttribute` | **0** |
| Session **写入** | **7**（4 处在 `AuthorizationInterceptor`＝唯一身份来源；3 处是 `servletContext` 字典缓存） |

**结论**：「103」这个数字**与当前代码不符**，需在本批报告中校准为「**控制器 50 处读取**（`role` 21 + `userId` 29）」。
（数字来源未考 —— 因此**不沿用**，以实测为准。）

---

## 1. 身份来源（唯一写入点，已确认）

```java
// AuthorizationInterceptor:73-76  ← 唯一的身份写入点
request.getSession().setAttribute("userId",   tokenEntity.getUserId());
request.getSession().setAttribute("role",     tokenEntity.getRole());
request.getSession().setAttribute("tableName", tokenEntity.getUserType());
request.getSession().setAttribute("username", tokenEntity.getUsername());
```

→ 4 个属性：`userId` / `role` / `tableName` / `username`。
**注意**：`tableName`（= `userType`）与 `username` 在**控制器读取站点中出现 0 次**（仅 `Dictionary` 的字典缓存用 `servletContext`）。
→ 因此待收敛的语义只有两种：**`userId`（29 处）** 与 **`role`（21 处）**。

---

## 2. 分类结果（50 处，逐类列明）

### A 类：语义**一致**，可直接替换（**33 处**）

| 子类 | 形态 | 处数 | 拟替换为 |
| --- | --- | :-: | --- |
| A1 | `String role = String.valueOf(request.getSession().getAttribute("role"));`（`/save` 的 role 分支） | **21** | `CurrentUserProvider.currentRole()` |
| A2 | `setXxxId(Integer.valueOf(String.valueOf(...getAttribute("userId"))))`（自助写路径：`/save` + `/add`） | **11** | `CurrentUserProvider.currentUserId()` |
| A3 | `Integer id = (Integer) request.getSession().getAttribute("userId");`（`/session` 类：`Users:129`、`Xinlilaoshi:365`、`Yonghu:395`） | **3** | 同上 |
| A4 | 交卷判分取自身 id（`Examredetails:421`） | **1** | 同上 |

> A1 的 21 处分布：Dictionary 2 / Exampaper 2 / Examquestion 4 / Examrecord 1 / Examredetails 2 / Examrewrongquestion 1 / Jiankangzhishi 2 / Tongzhi 2 / XinlilaoshiCollection 1 / Xinlilaoshi 1 / XinlilaoshiLiuyan 1 / XinlilaoshiOrder 1 / Yonghu 1。

### B 类：存在**语义差异**，不可盲目替换（**24 处，全部是 A 类站点的属性**）

这不是额外的站点，而是**必须显式处理的语义差异** —— 也正是「不要机械替换」的原因：

| # | 差异 | 现状语义 | 替换后若不做处理会怎样 |
| :-: | --- | --- | --- |
| B1 | `String.valueOf(session.getAttribute("role"))` | Session **没有** `role` 时得到字符串 **`"null"`**（而**不是** `null`） | 换成类型化 `currentRole()` 后得到 `null` → 分支判断的**取值域变了**。当前所有比较（`"学生".equals(role)` 等）在两种情形下**恰好都不命中**，行为一致；但这是**巧合等价**，必须显式声明，不能默认 |
| B2 | `Integer.valueOf(String.valueOf(...))` | 先在 `"null"` 上 `Integer.valueOf("null")` → **抛 `NumberFormatException`** | 换成类型化访问器 → 变成 `null` 或受控异常 → **失败模式变化**（现状是 500，替换后可能是别的码） |
| B3 | `(Integer) session.getAttribute("userId")` | 类型不符时抛 **`ClassCastException`** | 同上，失败模式变化 |

> **处理原则（本批确定）**：A 类站点替换时，**必须同时显式声明**上述语义差异，并在**未登录/属性缺失**场景补一条回归用例 ——
> 否则「看起来等价」的替换会静默改变**异常类型**与**空值形态**。

### C 类：特殊场景，**单独处理**（**13 处**）

| 子类 | 形态 | 处数 | 为什么特殊 |
| --- | --- | :-: | --- |
| C1 | `Integer studentId = Integer.valueOf(String.valueOf(...getAttribute("userId")));` 位于 **`/batchInsert`（Excel 批量上传）** 内 | **13** | 按批 3A 结论：**Excel 导入是另一种身份语义**（批量创建/更新，数据自带归属列）→ 不能与「自助写入」共用同一套假设。（抽样确认 `Examrecord:211` 位于 `/batchInsert`；其余 12 处为**同一模式**，**待逐条确认**） |

> 首轮抽样仅逐条确认了 1 处（`Examrecord:211` → `/batchInsert`）。**其余 12 处按模式推定**，分类报告如实标注为「待确认」。

### D 类：非代码（**2 处**，不计入）

`DataScope` javadoc 中的示例代码引用。

### E 类：收敛的**目标实现**（**2 处**，不计入待迁移）

`OwnershipGuard.currentUserId()` / `currentRole()` 本体（`OwnershipGuard:302/308`）。

---

## 3. 拟定的收敛形态（供下一步实现，**本步不写代码**）

```java
// com.security.CurrentUserProvider  —— 只读，纯收敛，不引入新授权规则
public static Integer currentUserId(HttpServletRequest request)   // 语义 = OwnershipGuard 现有实现
public static String  currentRole(HttpServletRequest request)
public static String  currentUsername(HttpServletRequest request)
public static String  currentUserTable(HttpServletRequest request)
```

**迁移顺序建议**（每步独立提交、独立验证）：

1. **A1（21 处 `role`）** —— 与批 4-A 的 `DataScope` 同源，风险最低；
2. **A2/A3/A4（11+3+1 处 `userId`）** —— 自助写路径，已有批 3B/4-B 的回归覆盖；
3. **B1/B2/B3 的语义差异** —— 与第 1、2 步**同批**显式处理（补未登录/缺失属性的回归用例）；
4. **C1（13 处 Excel）** —— **最后单独处理**，需先完成其身份语义取证（与批 3A 的「Excel 独立处理」一致）。

---

## 4. 待确认 / 待取证

| # | 项 | 说明 |
| --- | --- | --- |
| 1 | 设计文档「103 处」的来源 | 实测 54（代码 52 / 控制器 50）→ 建议在批 5 报告中**以实测数字为准**并注明差异 |
| 2 | C1 的 12 处是否确为 `/batchInsert` | 抽样 1 处确认，其余按模式推定，**待逐条确认** |
| 3 | `Dictionary:111/154/226` 三处上下文 | 建议逐条确认后再归类 |
| 4 | 是否需要 `currentUsername`/`currentUserTable` | 控制器读取站点中**出现 0 次** → 可**先不提供**，避免造无用 API |

---

## 5. A1 逐条语义确认（21 处 `role`）—— **先别改代码**，本步已完成

按审阅要求：A1 实施前先逐条确认 21 个调用点，并先定 `currentRole()` 契约。

### 5.1 三项结构化检查（穷举了 `role` 的可能用法）

| # | 检查 | 结果 | 意义 |
| :-: | --- | --- | --- |
| ① | 是否存在 `role.equals(...)`（改为 `null` 会 **NPE**） | **0 命中** ✅ | 消除最大风险 |
| ② | 是否有代码依赖**字符串 `"null"`** | 仅 1 处：`StringUtil:6` 的通用空值判断（对 `null` 与 `"null"` **等价处理**）✅ | 无依赖 |
| ③ | `role` 是否**传出该站点**（`put` / 作为参数 / 返回） | 5 处 `"role"` 字面量**全部与 Session 读取无关**（登录响应 ×3、拦截器写入 ×1、`TokenServiceImpl` 查询 ×1）✅ | 值只用于**本地比较** |

### 5.2 逐条形态（21 处全部查看）

| 形态 | 处数 | 内容 | 处理 |
| --- | :-: | --- | --- |
| **Live**：有 `else if("…".equals(role))` 分支体 | **7** | `Examrecord:134`（`/save` 学生）、`Examredetails:134`（`/save`）、`Examredetails:376`（`/add`）、`Examrewrongquestion:142`（`/save`）、`XinlilaoshiCollection:132`（`/save` 老师+学生）、`XinlilaoshiLiuyan:132`（`/save` 老师+学生）、`XinlilaoshiOrder:132`（`/save` 老师+学生） | 全部为 `"字面量".equals(role)` 安全形态 → 迁移 `currentRole()` |
| **Dead**：`if(false)` 死分支后 `role` **再无使用** | **14** | `Dictionary:111`、`Dictionary:154`、`Exampaper:116`、`Exampaper:147`、`Examquestion:123`、`Examquestion:156`、`Examquestion:340`、`Examquestion:379`、`Jiankangzhishi:115`、`Jiankangzhishi:143`、`Tongzhi:115`、`Tongzhi:143`、`Xinlilaoshi:114`、`Yonghu:116` | **删除该死变量**（不是迁移）—— 其中 6 处已逐条确认（`Dictionary:111/154`、`Exampaper:116/147`、`Xinlilaoshi:114`、`Yonghu:116`），其余 8 处为**同形态推定**（标注待确认） |

> ⚠️ **这把 A1 的规模从「21 处迁移」改成「7 处迁移 + 14 处死代码清理」** —— 而这个区别只有在逐条确认后才看得见。

### 5.3 `currentRole()` 契约（依据 5.1 判定）

```text
Session 无 role
    ↓
currentRole() → null      ← 决定：返回 null（真正的缺失）
```

**依据**：①+②+③ 三条结构化检查已证明「21 处的 `role` 只用于本地 `"字面量".equals(...)` 比较，且无任何 NPE 形态、无 `"null"` 依赖、无外传」→
对**全部可能形态**而言，`null` 与 `"null"` **行为等价**。

**定性（按审阅要求）**：这是**已确认的语义收敛**（将 `"null"` 字符串收敛为真正的 `null`），**不是**机械替换；
B1 因此**不是被「顺手修掉」**，而是**经穷举验证后显式声明**。

> ⚠️ 仍未依赖「38 项测试通过」来证明等价 —— 该回归网**未必覆盖 role 缺失路径**（审阅意见正确），等价性由 5.1 的**结构化检查**给出，不靠测试兜底。

### 5.4 A1 实施计划（**待批准，本步未改代码**）

```text
提交 1：7 处 Live → CurrentUserProvider.currentRole()
        + 新增「role 缺失」回归用例（补审阅指出的覆盖缺口）
提交 2：14 处 Dead → 删除死变量（纯清理，零行为变化）
```

---

## 6. A1 实施结果（已获批准后执行）

### 6.1 交付物（2 个独立提交）

| 提交 | 内容 | 文件 |
| --- | --- | --- |
| **A1a** | 新增 `com/security/CurrentUserProvider` + **7 处 Live** 迁移 + **role 缺失契约控制用例** | `CurrentUserProvider.java`（新）+ 6 个 Controller + 测试类 |
| **A1b** | **14 处 Dead** 死变量删除（纯清理，零行为变化） | 7 个 Controller |

> 21 处**全部处理**：7 迁移 + 14 删除 —— 两种处置都让 `Session.getAttribute("role")` 从 Controller 消失。

### 6.2 8 项验收逐条实测

| # | 验收项 | 实测 |
| :-: | --- | --- |
| 1 | 21 个调用点全部处理 | `CurrentUserProvider.currentRole(` = **7** 处（= 7 个 Live 站点，行号已登记）+ 14 处删除 ✅ |
| 2 | Controller 中 `getAttribute("role")` 消失 | **0** ✅ |
| 3 | `AuthorizationInterceptor` 仍是身份写入源 | 4 处 `setAttribute` 完好 ✅ |
| 4 | `CurrentUserProvider` 无反向依赖 Controller | **0** ✅（只依赖同包 `OwnershipGuard`）|
| 5 | role 缺失行为未被无意改变 | 由 **§5.1 三条结构化检查**证明等价（不靠测试兜底）✅ |
| 6 | 原有回归继续通过 | **39/39** ✅（38 + 新增契约用例）|
| 7 | role 缺失的回归控制 | **新增用例 25**：无 role → `null`；正常 → 原值；非字符串 → 同 `String.valueOf` 语义；移除后 → `null` ✅ |
| 8 | 不出现新的授权绕过 | 行为等价 + 全部授权类用例通过 ✅ |

**全仓 `getAttribute("role")` 残留仅 2 处**：`CurrentUserProvider` 的 javadoc 示例（非代码）+ `OwnershipGuard:308`（守卫本体，**本步边界禁止改动**）✅

### 6.3 补验：此前「同形态推定」的 8 处已全部逐条确认

`Examquestion:340`（`/saveExamquestion`）与 `Examquestion:379`（`/updateExamquestion`）经读取确认：
两者读出 `role` 后**直接进入业务逻辑**，`role` 再无使用 → **确为死变量** ✅
→ 至此 **21 处全部逐条确认完毕，无推定项**（6.1 的 7/14 划分是实测结论，不是模式推断）。

### 6.4 诚实标注（本步未做 / 已知不完美）

| # | 项 | 说明 |
| :-: | --- | --- |
| 1 | **E2E 级「role 缺失」路径仍未覆盖** | 现只有**单元级契约控制**（用例 25）。迁移的 7 处都在**写**端点，其 E2E 需要构造空 role 的 token + 满足各端点的查重/字段约束与种子数据 → 本步未做。**等价性因此依赖 §5.1 的结构化证明，而非测试** —— 这是本步已知的覆盖缺口，建议 A2 阶段一并补 |
| 2 | 删除死变量后**留下空行** | 为**最小化 diff**，未做额外格式化（空行不影响行为）。如需整理可单开清理提交 |
| 3 | `CurrentUserProvider` 当前**委托** `OwnershipGuard.currentRole` | 避免出现第二份身份读取实现；按本步边界**未改动守卫**，依赖方向的反转留待后续步骤 |
| 4 | 全仓仍有 `OwnershipGuard:308` 一处 role 读取 | 守卫本体，属**收敛目标自身的实现**，非待迁移对象 |

---

## 7. A2 实施结果（`userId` 写路径，提交 `241e688`）

### 7.1 29 处 `userId` 的五类归属判定（**逐条带端点**，非推定）

| 类别 | 处数 | 站点 | 处置 |
| --- | :-: | --- | --- |
| **C 特殊（Excel）** | **13** | **全部位于 `/batchInsert`** —— Dictionary:226 / Exampaper:196 / Examquestion:199 / Examrecord:212 / Examredetails:213 / Examrewrongquestion:220 / Jiankangzhishi:183 / Tongzhi:183 / XinlilaoshiCollection:212 / Xinlilaoshi:191 / XinlilaoshiLiuyan:198 / XinlilaoshiOrder:197 / Yonghu:202 | **隔离不动**（身份语义待单独取证）✅ 现已**逐条确认**，无推定项 |
| **写路径 · self** | **13** | 10 处 `Integer.valueOf(String.valueOf(userId))` + **3 处 `(Integer)` 强转**（`Examrecord:376` `/saveExamrecord`、`Examredetails:422` `/addUnsaved`、`XinlilaoshiOrder:343` `/add`） | **本批迁移 → `requireCurrentUserId()`** |
| **读自己** | **3** | `/session` ×3（`Users:129`、`Xinlilaoshi:365`、`Yonghu:395`） | **留待 A3**（`currentUserIdOrNull()`，允许缺失）|

### 7.2 `currentUserId()` 契约（A2 裁定）

```text
读自己 / 特殊场景  →  currentUserIdOrNull()     缺失 → null（保留既有行为）
写路径 / self      →  requireCurrentUserId()    缺失 → 拒绝（HTTP 403 + code=403）
```

**为什么不能统一返回 `null`**：写路径用于 `entity.setStudentId(...)` —— 返回 `null` 时 MyBatis-Plus **跳过该字段**
→ **客户端提交的归属值就会生效** = 归属校验形同虚设（fail-open）。
**为什么用 403 而不是 401**：401 在本项目由拦截器的 `HTTP 200 + code=401` 表达（D1 已锁定）；此处复用既有拒绝形态，
**不新增契约**。

### 7.3 🔴 关键发现：**「身份不完整」状态根本不可构造**（由用例 27 的首次失败查出）

首版 E2E 用例想构造「role 正常、`user_id` 为空」的 token 来验证 403，结果被 **schema 拒绝**：

```text
DataIntegrityViolationException: Field 'user_id' doesn't have a default value
```

**含义**：`auth_token.user_id` 为 **NOT NULL 且无默认值** → 身份进入 Session 的唯一来源（拦截器 ← token 行）
**必然是完整的** → 「已认证但 userId 为空」**不可达**。

**因此必须修正本批的风险定性（不夸大）**：

| 原表述 | 修正后 |
| --- | --- |
| 3 处 `(Integer)` 强转是 **fail-open 漏洞** | ⚠️ 是**潜在代码形态问题 / 不可达状态下的不一致**，**不是线上漏洞** |
| 10 处 `valueOf` 的 500 → 403 是 **失败模式变更** | ⚠️ 同样**不可达**；真实链路中两者都表现为「userId 一定存在」 |

→ 本批在**所有可达状态下行为不变** ✅；它消除的是**潜在 fail-open** 与**失败模式不一致**（防御性收敛）。

### 7.4 验收

```text
mvn clean test → 41/41 全绿（新增用例 26、27）
Controller 中 getAttribute(userId) 残留：16 = Excel 13 + session 3   ✅ 符合预期
requireCurrentUserId 调用：13                                        ✅
```

| # | 用例 | 内容 |
| :-: | --- | --- |
| 26 | 契约控制 | 读：缺失 → `null`；写：缺失 → 抛 `ForbiddenException`（**不得返回 null**）|
| 27 | **不变量守卫** | 断言 schema **拒绝** `user_id` 为空的 token —— 若该约束被放开，本用例失败即提示「fail-closed 分支变为可达」✅ |

> 用例 27 取代了原计划的 E2E：**该 E2E 在构造上不可能实现**，而其「不可能」本身就是证据。

### 7.5 诚实标注

| # | 项 |
| :-: | --- |
| 1 | `/session` 的 3 处**未在本批迁移**（留 A3），故 `getAttribute("userId")` 残留 16 处 |
| 2 | Excel 13 处**仍未动**（`/batchInsert` 身份语义待单独取证）|
| 3 | `CurrentUserProvider` 仍**委托** `OwnershipGuard`（本批边界禁止改守卫）|
| 4 | 用例 27 是**schema 层**的守卫：它依赖 MySQL 的 NOT NULL 约束，不覆盖「其他数据源写入 token 行」的场景 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-08 | 批 5 第一步：只读分类（**零代码**）。实测 Session 读取 54 处（控制器 50）；按形态分 A/B/C/D/E 五类；识别 3 项语义差异（`"null"` 字符串、`NumberFormatException`、`ClassCastException`）与 13 处 Excel 特殊场景；校准设计文档的「103」；给出分步迁移顺序与 4 项待取证 |
