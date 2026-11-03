# Phase 2 / T1：空 `SET` 的 `updateById` → 500 —— **Phase 1 只读审计**

> **来源**：`PHASE2-CLOSING-REPORT.md` §3.2 技术债 **T1**
> **审阅锁定的 5 问**：A `/update` 调用语义 · B 空 `SET` 的真实 SQL 路径（**500 产生在哪一层**）· C 前端真实语义 · D 现有契约 · E 最终分类
> **禁令**：❌ 不改代码 ❌ 不写数据库 ❌ 不处理 T8/G3/T3 ❌ **Phase 1 不决定 HTTP 400**
> **状态**：只读审计完成 ✓ —— 分类见 §5（**②**），Phase 2 候选见 §8（**未采纳任何一项**）

---

## 1. B —— 空 `SET` 的真实路径与「500 产生在哪一层」

```text
Controller./update（@RequestBody Entity）
   ↓  ← 15/15 端点此处【无任何“更新体是否为空”防护】（见 §2）
OwnershipGuard（授权层：本人 / 管理员；否则 403）
   ↓
Service.updateById(entity)                      ← MyBatis-Plus 只拼【非空字段】
   ↓  entity 除 id 外全为 null（含 @JsonIgnore 字段，见 §2.3）
DAO 生成的 SQL：UPDATE student SET  WHERE id=1   ← ★ 空 SET ⇒ SQL 语法错误
   ↓
MySQL 1064（解析期拒绝 ⇒ **0 行写入** ✓）
   ↓
org.springframework.jdbc.BadSqlGrammarException
   ↓
GlobalExceptionHandler.handleException（兜底）   ← 它不是 ErrorResponse ⇒ 直接落 500 分支
   ↓
HTTP 500 + body {code:500, msg:"服务器内部错误，请联系管理员"}
```

**产生层判定**：**DAO / MyBatis-Plus 生成 SQL 层**（`com.dao.YonghuDao.updateById-Inline` ✓）
**呈现层**：`GlobalExceptionHandler` 的**未捕获兜底**（`DataAccessException` 不是 `ErrorResponse` ⇒ 不会被 4xx 通道接住 ✓）

**既有决定性证据（复用 4-B 报告，本轮不重复探测、零写库 ✓）** —— `PHASE2-T5B4B-REPORT.md` §5.3：

```text
ERROR com.handler.GlobalExceptionHandler : 未捕获异常
org.springframework.jdbc.BadSqlGrammarException
  ### The error may involve com.dao.YonghuDao.updateById-Inline
  ### Cause: SQLSyntaxErrorException ... near 'WHERE id=1'
```

**无写入证明**：SQL 在 MySQL **解析期**即被拒绝 ✓；4-B 已实测「资料表无变化」✓。（本轮**未**再做任何写请求 ✓）

## 2. A —— `/update` 的真实调用语义

### 2.1 后端：15 个端点，**0 个**有空更新防护

| 端点 | `updateById` | `updateById` 之前只有 | 空更新防护 |
| --- | :-: | --- | :-: |
| `StudentController` | 1 | 授权 ✓ + 固定字段恢复 ✓ + 重名校验 ✓ + `avatarUrl` 空串规范化 ✓ | ❌ **无** |
| `CounselorController` / `KnowledgeArticle` / `Notification` | 各 1 | 重名校验 ✓ + `cover/avatar` 空串规范化 ✓ | ❌ 无 |
| `AssessmentAnswer/Paper/Question/Record/WrongQuestion` | 各 1 | 重名/唯一性校验 ✓ | ❌ 无 |
| `CounselingAppointment` / `CounselorFavorite` / `CounselorMessage` / `SysDictItem` | 各 1 | 重名校验 ✓ / 字典分支 ✓ | ❌ 无 |
| **`AdminUserController`** / **`SysConfigController`** | 各 1 | **无任何前置校验**（`updateById(entity);//全部更新`）| ❌ 无 |

> 即：**「更新体是否含可更新字段」在整个 `/update` 面上没有任何检查** ✓ —— 这是结构性事实，不是某一处遗漏 ✓

### 2.2 前端：**7 个调用点，全部提交完整对象** ✓

| # | 文件 | URL | 提交载荷 | 前置校验 |
| :-: | --- | --- | --- | :-: |
| 1 | `admin/…/xinlilaoshiOrder/list.vue:911` | `xinlilaoshiOrder/update` | `_this.form` ✓ | 有（`reviewComment` 必填 ✓）|
| 2 | `front/…/center.vue:255` | `` `${sessionTable}/update` `` | `this.ruleForm` ✓ | 有（`username` 非空 ✓）|
| 3 | `admin/…/pay.vue:91` | `` `${this.table}/update` `` | `this.obj` ✓ | — |
| 4 | `front/…/update-password.vue:95` | `` `${sessionTable}/update` `` | `this.user` ✓ | 有（两次密码一致 ✓）|
| 5 | `front/…/recharge.html:140` | `` `${table}/update` `` | `vue.user` ✓ | — |
| 6 | `front/…/center.html:511` | `` table + '/update' `` | `data` ✓ | — |

⇒ **没有任何调用点提交 `{}` / 全空字段 / 只含 `@JsonIgnore` 字段的对象** ✓（载荷均为「页面加载的完整表单/对象」✓）

### 2.3 为什么会出现「除 id 外全空」

`@JsonIgnore` 使以下字段**不从 JSON 绑定**：`StudentEntity.password(L156)`、`StudentEntity.idCardNo(L196)`、`CounselorEntity.password(L172)`、`AdminUserEntity.password(L46)`（另各 `*View` 的同类字段不影响入参 ✓）。

⇒ 载荷若**只含**这些字段（如 `{"id":1,"password":"x"}`）⇒ 实体只剩 `id` ⇒ **空 SET** ✓（这正是 4-B 那次 500 的成因 ✓）

## 3. C —— 前端真实语义（决定问题性质）

* **正常 UI 流程无法产生空更新** ✓：7/7 提交完整对象 ✓，多处有必填前置校验 ✓
* ⇒ 本问题**不是「正常业务流程错误」** ✓，而是 **「后端对非法/不完整输入的防御不足」** ✓ —— 与审阅的预判一致 ✓
* ⚠️ **唯一需二期确认的边角**：`update-password.vue:95` 提交 `this.user` ✓ —— 若该对象只是 `{id, password, mima}`（`password` 不绑定、`mima` 非实体字段）✗，则它是**唯一可能由真实 UI 触发空 SET 的调用点** ✗ ⇒ 需在 Phase 2 核对该对象的最小构成 ✓
* 📌 **仅登记（不在 T1 范围）**：`update-password.vue` **存在** ⇒ 这是「自助改密码」（业务决策 **P2**）的**前端线索** ✓ —— 是否可达/是否死页面属 P2，不在此处理 ✓

## 4. D —— 现有契约（避免「看到 500 就机械改 400」）

| 场景 | 现有形态 | 出处 |
| --- | --- | --- |
| **无法解析/绑定**（坏 JSON、缺参、类型不符）| **HTTP 400** + `{code:400, msg:"请求参数错误"}` | `GlobalExceptionHandler.handleBadRequest` ✓ |
| **业务前置不满足**（重名等）| **HTTP 200** + `R.error(511, "…")` | 15 个 `/update` 的重名分支 ✓ |
| **已认证但无权** | **HTTP 403** + `code=403` | `handleForbidden` ✓ |
| **未认证** | HTTP 200 + `code=401`（D1 已定，不改）| 拦截器 ✓ |
| **未捕获异常** | **HTTP 500** + `{code:500, msg:"服务器内部错误…"}` | `handleException` ✓ |

**既有测试覆盖**：

| 覆盖 | 用例 | 说明 |
| --- | --- | --- |
| `/yonghu/update` 坏 JSON → 400 | `Phase2CoreRegressionTest` #9b（L353）| 只覆盖**解析失败** ✗ 不覆盖"解析成功但无可更新字段" ✗ |
| `/yonghu/update` 学生改他人 → 403 | `IdentityAuthorizationRegressionTest` #22（L482）| 授权层 ✓ |
| 老师改学生（原空 SET 触发载荷 `{"id":1,"password":"…"}`）| #23（L510）→ 现为 **403** ✓ | **授权层先拦** ⇒ 该类载荷已不可达 ✗ |
| 未知角色 `/update` | #16（L374）→ 403 ✓ | fail-closed ✓ |
| **空 SET（解析成功、字段全不可绑定）** | **无任何用例** ✗ | ← **T1 的空白** ✓ |

⇒ 空 SET 的**可达面已收缩**为：**授权通过者**（本人学生 / 管理员）+ 载荷零个可绑定字段 ✓

## 5. E —— 最终分类

> ## **② 非法 / 不完整客户端请求 → 应定义「参数错误」语义**

**判定依据（逐条对应 A–D）**：

| 判断 | 依据 |
| --- | --- |
| ❌ 不是 ①（正常业务请求 → 实现错误）| 7/7 前端调用点均提交完整对象 ✓，无 UI 路径产生空更新 ✓（§3）|
| ✅ **是 ②** | 「更新体不含任何可更新字段」= **请求不完整** ✓；当前以 **DB 语法错误**形式暴露 ✗ —— 是**防御缺失** ✓，非业务流缺陷 ✓ |
| ❌ 不是 ③（合法但无实际修改 → 空操作语义）| 若产品需要"无字段即成功且不改"应显式定义；**当前无任何此类证据** ✗；且 4-B 的成因是「客户端想改不可改字段」✗，不是"确认无修改" ✓ |
| ❌ 不是 ④（服务端内部异常 → 保持 500）| 异常**源于客户端载荷** ✓（SQL 由入参推导）✓；500 只是**兜底呈现** ✗，不是语义归属 ✓ |
| ❌ 不是 ⑤（证据不足）| A/B/C/D 四问均已取证 ✓ |

**边界（严格遵守）**：本分类**不预设 HTTP 状态** ✗ —— ②只说明「应定义参数错误语义」✓；
究竟落在 **HTTP 400** 还是项目既有的 **HTTP 200 + `R.error(511)`**（业务前置模式），**属 T1 Phase 2 的设计决策** ✓（§8 仅列候选，未采纳 ✓）

## 6. 精确复现条件（供 Phase 2 用例设计）

```text
前置：调用者【通过授权】（本人学生 / 管理员；其他角色已在授权层 403 ✗ 到不了）
载荷：JSON 中【零个可绑定字段】—— 例：
      {"id": 1}                                    ← 只有主键
      {"id": 1, "password": "x"}                    ← password 为 @JsonIgnore
      {"id": 1, "password": "x", "idCardNo": "..."}  ← 两者都不绑定
结果：HTTP 500 + code=500（DAO 层 BadSqlGrammarException）且【0 行写入】
```

## 7. 未覆盖 / 受限（如实登记）

| # | 项 | 说明 |
| :-: | --- | --- |
| 1 | 本轮**未做**任何写请求探测 | 4-B 已有容器级决定性证据（异常 + 生成 SQL + 表无变化）✓ ⇒ 复用即可，**保持零写库** ✓ |
| 2 | 15 个端点仅抽读 `StudentController` 全文 | 其余 14 个以「`updateById` 前 6 行」全量核对 ✓（均无空更新防护 ✓）；**未逐个读全文** ✗ |
| 3 | `update-password.vue` 的 `this.user` 最小构成 | 未核（§3 边角）→ Phase 2 必核 ✓ |
| 4 | 前端 7 个调用点是否**全部**可达（有无死页面）| 未核（属 P2/前端视角）✓ 不影响 E 的分类 ✓ |

## 8. Phase 2 候选（**仅列，不决定**）

| 候选 | 语义 | 影响面 / 风险 |
| --- | --- | --- |
| **A** `HTTP 400 + code=400` | 复用既有"参数错误"通道 ✓ | 需在 15 个 `/update` 统一加前置判断 ✓（或抽公共方法 ✓）；对前端是**新状态码** ✗ ⇒ 两套前端在非 2xx 分支只显示"请求接口失败" ✗（`GlobalExceptionHandler` 注释已载明 ✓）|
| **B** `HTTP 200 + R.error(511, "…")` | 复用"业务前置不满足"通道 ✓ | 与重名等既有语义一致 ✓；前端可显示提示语 ✓；但 `511` 语义本就要收敛（D-3）✗ |
| **C** 空更新 = 幂等成功（`200 + code=0`，不改任何行）| "无字段即无操作" | 需产品确认 ✓（当前**无证据**支持该语义 ✗）|
| **D** 保持 500 | 视为内部错误 | 与 §5 分类矛盾 ✗ |

> 无论选哪一项，Phase 2 都需先补一条**当前缺失的用例**（空 SET 的可观测契约）✓ —— 一条会成为"**契约固化**"的用例，必须先有决策再写 ✓（避免重演"把缺口固化为正确行为"✗）

---

## 8bis. Phase 2 实施结果（方向 A：**HTTP 400 + code=400** ✓）

| 项 | 内容 |
| --- | --- |
| 变更 | 新增 `com.security.BadRequestException`（400 语义）+ `com.security.UpdateGuard#assertHasUpdatableFields`（除主键外至少一个非空字段；主键识别 = `@TableId` 或字段名 `id` 双保险）|
| 400 通道 | `GlobalExceptionHandler` 新增 `@ExceptionHandler(BadRequestException)` → `HTTP 400 + code=400 + 明确 msg` ✓ |
| 注入点 | **15/15** 个 `/update` 各 1 行（**授权通过之后、`updateById` 之前**）⇒ 「未授权仍 403、授权后参数不足才 400」✓ |
| 规模 | 约 **20 行**（15 行调用 + 2 个新文件 + 1 个映射 + 1 个用例）；**不改表结构、不改 401/403/500 契约** ✓ |
| 回归 | `mvn clean test` → **47/47** ✓（身份 30 + 业务基线 **16 → 17** ✓）|
| 新用例 | `9e`：`{"id":2}` 与 `{"id":2,"password":"x"}` → **400 + code=400** ✓，且 `/yonghu/info/2` 前后 `username`/`name`/`phone` **完全不变** ✓（证明拒绝发生在 **DAO 之前** ✓）|
| 库基线 | `auth_token` 前后均 **3** ✓ |

### 8b.1 一次性反向对照（**不提交** ✓）

```text
临时移除 /yonghu/update 的防护
        ↓
用例失败 ✓✓
   [空更新：仅提交 id 应返回 HTTP 400]   expected: 400   but was: 500
        ↓
立即还原 → 无残留临时注释（扫描 = 0）✓ → 全量回归 47/47 ✓
```

⇒ 用例捕获的是「**空更新必须按参数错误拒绝**」这一**新契约** ✓，而非"实现恰好返回 400" ✓

### 8b.2 边界遵守（按 Phase 2 收尾闸门）

| 禁止扩张项 | 是否触碰 |
| --- | :-: |
| 参数校验（Step 4 D4 已定案暂缓）| ❌ 未触碰 ✓ |
| `code` 语义收敛 `511` ×163（D-3 已定案暂缓）| ❌ 未触碰 ✓ |
| HTTP 401 契约（D1 已定案）| ❌ 未触碰 ✓ |
| T3–T10 / G3 / T8 | ❌ 未处理（留在 backlog）✓ |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2 | 2026-10-18 | **Phase 2 完成（`d3293b5`，方向 A）**：`UpdateGuard` + `BadRequestException` → **HTTP 400 + code=400**；**15/15** 个 `/update` 接入（授权之后、DAO 之前）；新用例 `9e`（400 + 库内前后不变）；**47/47**；**一次性反向对照命中**（移除防护 → `expected 400 but was 500`）→ 立即还原、无残留；**未触碰** 参数校验 / `511` 语义 / HTTP 401 / T3–T10 / G3 / T8 → 见 §8bis；**T1 收尾 ⇒ Phase 2 Freeze** ✓ |
| v1 | 2026-10-18 | T1 Phase 1 只读审计（**零代码、零写库**）：B 全链路取到 500 产生层（DAO/MP 生成 SQL）+ 复用 4-B 容器级证据；A 15 个 `/update` **全部无空更新防护** + 7 个前端调用点**全部提交完整对象**；C ⇒ **非正常业务流**（唯一边角 `update-password.vue` 待二期确认）；D 现有四类契约与**空 SET 无任何用例**；E 分类 **②**（**不预设 HTTP 状态**）；§8 仅列 Phase 2 候选 |
