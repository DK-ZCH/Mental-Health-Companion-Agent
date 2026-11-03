# S2-F-2-0 · 注册密码丢失只读审计

> **性质**：只读取证 · **零代码 · 零 DB 变更 · 零 commit**
> **基线**：`HEAD = origin = aaf9bc0` · worktree CLEAN
> **日期**：2026-10-22
> **对象**：`DEF-REGISTER-PASSWORD-DROPPED`（🔴 P1\* 功能阻断）

---

## 0. 一句话结论

`@JsonIgnore` 标注在 `getPassword()` **getter** 上，使 Jackson **双向忽略**该属性（已用运行时探针实证：提交 `password` 后实体字段仍为 `null`）。
两个 `register()` 方法**都不 `setPassword`** ⇒ `save()` 时 `password = null`，而 `insert-strategy: not_null` 使该列**不进入 INSERT** ⇒ DB 落 `NULL`。

**⇒ 注册产生的账号 `password` 为 NULL，无法登录；且登录时 `null.equals()` 会抛 NPE。**

**附带发现**：同一注解还使 `idCardNo` 同样被丢弃（学生注册页有该必填项）⇒ **身份证查重失效**。

---

## 1. 三层证据闭合

### 1.1 层 1 · 框架行为（**运行时实证**，不碰 DB）

用项目 classpath 编译临时探针（`%TEMP%`，运行后已删除），直接对 `ObjectMapper` 验证：

```java
ObjectMapper om = new ObjectMapper();
StudentEntity s = om.readValue(
    "{\"username\":\"probe1\",\"password\":\"secret123\",\"idCardNo\":\"410224199610232001\"}",
    StudentEntity.class);
```

**实测输出**：

```text
STUDENT_IN   username=probe1   password=null   idCardNo=null
COUNSELOR_IN username=probe2   password=null
SERIALIZE    {"id":null,"username":"probe1","name":null,"phone":null,
              "avatarUrl":null,"gender":null,"email":null,"createdAt":null}
```

| 结论 | 证据 |
| --- | --- |
| **反序列化忽略** `password` | 提交 `"secret123"` → `getPassword() == null` ✓ |
| **反序列化忽略** `idCardNo` | 提交 18 位身份证 → `getIdCardNo() == null` ✓ |
| **序列化也不输出**（同一注解）| `SERIALIZE` 结果无 `password` / `idCardNo` ✓ |
| **双向忽略**（非仅单向）| 上两项同时成立 ✓ |

> 框架语义确认：`@JsonIgnore` 落在 getter 上会把整个逻辑属性标记为 ignored（读写皆忽略），
> 除非 setter 显式 `@JsonProperty` 覆盖 —— 三个 Entity 的 setter **均无**任何注解（已核对）。

### 1.2 层 2 · 实际代码（两个端点逐一核对）

| 端点 | 行 | 是否 `setPassword` | 是否 `setIdCardNo` |
| --- | :-: | :-: | :-: |
| `POST /student/register` | L342–358 | ❌ **无** | ❌ 无 |
| `POST /counselor/register` | L322–336 | ❌ **无** | —（该表无此字段）|

```java
// StudentController L342-358（Counselor 同构）
@PostMapping(value = "/register")
public R register(@RequestBody StudentEntity yonghu){
    QueryWrapper<StudentEntity> queryWrapper = new QueryWrapper<StudentEntity>()
        .eq("username", yonghu.getUsername())
        .or().eq("phone", yonghu.getPhone())
        .or().eq("id_card_no", yonghu.getIdCardNo());     // ← 恒为 null（见 §3.1）
    if(yonghuService.getOne(queryWrapper) != null)
        return R.error("...已经被使用");
    yonghu.setCreatedAt(new Date());
    yonghuService.save(yonghu);                            // ← password 从未被赋值
    return R.ok();
}
```

### 1.3 层 3 · 实际 SQL（写入行为推演 + 配置证据）

| 证据 | 值 |
| --- | --- |
| `mybatis-plus.global-config.db-config.insert-strategy` | **`not_null`** |
| `student.password` 列定义 | `varchar(200) NULL DEFAULT NULL` |
| `counselor.password` 列定义 | `varchar(200) NULL DEFAULT NULL` |
| `student.id_card_no` 列定义 | `varchar(200) NULL DEFAULT NULL` |

**⇒ `insert-strategy: not_null` 使 `password` / `idCardNo` 为 null 时被排除出 INSERT 列清单**：

```sql
-- 实际生成的 INSERT（推演，列清单不含 null 字段）
INSERT INTO student (username, name, phone, avatar_url, gender, email, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?);
-- → password 列未出现 ⇒ DB 应用 DEFAULT NULL ⇒ 落库为 NULL
```

> ⚠️ **如实声明**：本审计**未做「成功注册」实测**（会写 DB，违反零写入）⇒
> 第 3 层是 **配置证据 + MyBatis-Plus `not_null` 语义推演**，非 SQL 日志实证。
> 建议在 **S2-F-2-1 实现阶段**用可恢复探针（注册→查库→删除）做一次实证。

---

## 2. 两个端点是否完全相同（不能假设）

| 维度 | `/student/register` | `/counselor/register` | 一致？ |
| --- | --- | :-: | :-: |
| `@IgnoreAuth` | ✅ | ✅ | ✅ |
| `@RequestBody` 绑定 Entity | `StudentEntity` | `CounselorEntity` | 结构同构 |
| 查重字段 | `username` + `phone` + **`id_card_no`** | `username` + `phone` | ⚠️ 学生多一项 |
| `setCreatedAt` | ✅ | ✅ | ✅ |
| **`setPassword`** | ❌ | ❌ | ✅ **两者都缺失** |
| `save()` | ✅ | ✅ | ✅ |
| 返回值 | `R.ok()` | `R.ok()` | ✅ |

**⇒ 核心缺陷两者完全相同（都丢 password）；差异仅在学生多一个 `id_card_no` 查重（且该值恒 null）。**

---

## 3. 附带发现

### 3.1 🔴 `idCardNo` 同样被 `@JsonIgnore` 丢弃 → 身份证查重失效

- `StudentEntity.getIdCardNo()`（L196）标 `@JsonIgnore`；
- 学生注册页 `yonghu/register.html:93` 有 **`lay-verify="identity|required"`** 的身份证必填项；
- 探针实证 `idCardNo=null` ⇒ **「该字段未进入 Entity」已完全确认**。

**⚠️ 严谨性修正（用户 2026-10-22 裁定）**：
`register` 中 `.or().eq("id_card_no", null)` 的**最终 SQL 形态尚未运行时确认**。
`QueryWrapper.eq(column, null)` 可能生成 `id_card_no = NULL`（三值逻辑恒 false），
也可能被 MyBatis-Plus 特殊处理（如忽略该条件）—— 二者对"查重是否失效"的影响不同。

> 因此：「**身份证查重永久失效**」目前属 **高概率风险，尚未完成运行时闭环**。
> 需在 **S2-F-2-1 的实际注册探针 + SQL 日志**中确认 `eq("id_card_no", null)` 的最终 SQL 行为。
> **已确认的事实只有一条**：`idCardNo` 因 Jackson 忽略而未进入 Entity。

**⇒ 仍登记 `DEF-REGISTER-IDCARD-DROPPED`（🟠 P2）**：
性质为「注册必填的身份证字段被丢弃 → 档案缺该字段 + 查重语义存疑」，**不是 P1**（未证明可导致权限提升/账户接管）。

### 3.2 登录对 NULL 密码 → **NPE（HTTP 500）**，而非"密码错误"

```java
// StudentController L321（Counselor L301 / AdminUser L50 同构）
if(yonghu==null || !yonghu.getPassword().equals(password))
```

| 场景 | 行为 |
| --- | --- |
| 用户不存在 | `yonghu == null` → 短路 → `R.error("账号或密码不正确")` ✓ |
| 用户存在且 password 正常 | 明文比较 ✓ |
| **用户存在且 `password == null`** | `null.equals(...)` → **NullPointerException → HTTP 500** |

**⇒ 一旦有账号通过注册产生，其登录会返回 500（可被用于探测"该 username 存在且密码为 NULL"）。**

### 3.3 数据佐证（当前库）

| 表 | 记录数 | `password IS NULL` | `id_card_no IS NULL` |
| --- | :-: | :-: | :-: |
| `student` | 3 | **0** | **0** |
| `counselor` | 3 | **0** | — |

**⇒ 当前无 NULL 记录**：说明注册路径**很可能从未被真实使用**（或注册后立刻走已下线的 `resetPass` 补了密码）。
但**代码路径客观存在**，一旦使用即产生不可登录账号。

---

## 4. 完整因果链（已闭合）

```text
① 前端 yonghu/register.html:75  name="password"（用户真实输入）
② HTTP POST /student/register  body 含 password
③ Spring @RequestBody → Jackson 反序列化
④ getPassword() 标 @JsonIgnore ⇒ 反序列化忽略  【层1 实证：password=null】
⑤ register() 不 setPassword                     【层2 代码】
⑥ save() 时 password==null
⑦ insert-strategy: not_null ⇒ INSERT 列不含 password 【层3 配置+推演】
⑧ DB: student.password = NULL（DEFAULT NULL）
⑨ 登录: null.equals(...) → NPE → HTTP 500
⇒ 注册账号永久不可登录（且每次登录 500）
```

---

## 5. 影响面

| 环节 | 影响 |
| --- | --- |
| **学生注册** | 🔴 密码丢失 → 不可登录 |
| **老师注册** | 🔴 同上 |
| 学生身份证 | 🟠 丢失 → 查重失效 + 档案缺失 |
| 登录 | 🟠 NULL 密码账号 → 500（异常探测面）|
| 管理端创建（`save`/`add`）| ✅ 不受影响（服务端显式 `setPassword("123456")`）|
| 管理端改密 | ❌ 本就失效（`DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE`）|
| 前端提交其它字段 | ✅ 正常（`name`/`phone`/`gender`/`email` 等无 `@JsonIgnore`）|

---

## 6. 修复方案候选（**本批不实现**，待裁定）

| 方案 | 形态 | 优点 | 风险 | 评价 |
| --- | --- | --- | --- | :-: |
| **A** | `getPassword()` 的 `@JsonIgnore` → **`@JsonProperty(access = WRITE_ONLY)`** | 一行注解；注册可收密码，响应仍不泄露 | ⚠️ **同时让 `/update` 能绑定 password** → 改密行为变化（需授权 + 旧密码校验），且三表都要改 | 🟡 需配套 |
| **B（推荐）** | 新增**注册专用 DTO**（`RegisterRequest`，含 `username`/`password`/`name`/`phone`/`idCardNo`/`gender`/`email`）| 不影响 `update` 语义；注册与资料更新彻底解耦 | 新增 1 个类 + 改 2 个 Controller 签名 | ✅ **最小副作用** |
| C | 保留 `@JsonIgnore`，注册时服务端生成随机密码并返回/邮件通知 | 不暴露 password 绑定 | 需邮件基础设施（当前 0）；用户体验差 | ❌ |
| D | 注册后强制走"设置密码"链接（reset token）| 安全 | 需 token + 有效期 + 邮件/短信（当前 0）| ⏳ 未来 |

**共同前置**：注册密码落地后，还需**密码存储**仍为明文（`SEC-PLAINTEXT-PASSWORD-STORAGE`）→ 属 S2-F-3/F-4，**不与本批合并**。

### 6.1 与 `DEF-NO-SELF-SERVICE-PASSWORD-CHANGE` 的边界（按你的要求拆分）

```text
S2-F-2
├─ 注册时密码正确写入        ← 本批 P1* 功能阻断
└─ 登录后修改自己的密码      ← P2 功能缺失（独立子项，可同批设计但分步实现）
```

方案 A 会同时触及「改密」语义 ⇒ **若选 A，必须把"改密需旧密码校验"一并设计**，不能只改注解。
**方案 B 可只修注册，把改密留到 P2 子项。**

---

## 7. 已裁定事项（用户 2026-10-22）

| # | 问题 | 裁定 |
| :-: | --- | --- |
| 1 | 三层证据 | ✅ **通过**（含第 3 层为配置+推演的如实声明）|
| 2 | 修复方案 | ✅ **选 B：注册专用 DTO**。❌ 否决 A —— `WRITE_ONLY` 会同时让 `/update` 能绑定 password，把「修注册」扩大成「修注册 + 改密授权 + 旧密码校验」|
| 3 | `DEF-REGISTER-IDCARD-DROPPED` | ✅ **批准登记 🟠 P2**（数据完整性；未证明可权限提升）|
| 4 | 可恢复探针 | ✅ **批准**，需严格「before → register → inspect → cleanup → final」并留存 HTTP / DB / **SQL 日志** / 基线四类证据 |
| 5 | 密码强度 | ⏸ **F-2 不制定**；仅要求「不 null / 不空串」；复杂度留 F-3/F-4 |
| 6 | 登录后改密 | ✅ **继续独立为 P2**（安全模型与注册完全不同，不合并）|

### 7.1 审计文字修正（已执行）

`eq("id_card_no", null)` 的**最终 SQL 行为未运行时确认** → 「身份证查重永久失效」降级为
**高概率风险，待 S2-F-2-1 探针第 ④ 步确认**；唯一已确认事实是「`idCardNo` 未进入 Entity」。

### 7.2 必须显式记录（用户强调）

> F-2 修复后，**新注册账号的密码仍以明文写入数据库**（`SEC-PLAINTEXT-PASSWORD-STORAGE` 仍 OPEN），
> 由 **S2-F-4** 统一迁移。此事实**不得隐藏** —— 已写入 [`S2-F-2-1-REGISTER-DTO-DESIGN.md`](./S2-F-2-1-REGISTER-DTO-DESIGN.md) §1。

→ 设计见 [`S2-F-2-1-REGISTER-DTO-DESIGN.md`](./S2-F-2-1-REGISTER-DTO-DESIGN.md)

### 7.3 闭环状态（`41c2b7b` 已推送）

| ID | 状态 |
| --- | :-: |
| `DEF-REGISTER-PASSWORD-DROPPED`（🔴 P1\* 功能阻断）| ✅ **CLOSED**（S2-F-2-1：INSERT 列清单含 password + 查库值等于提交值 + 探针登录 200）|
| `DEF-REGISTER-IDCARD-DROPPED`（🟠 P2）| ✅ **CLOSED**（S2-F-2-1：id_card_no 落库 + 重复身份证命中既有查重分支且 0 插入）|
| `SEC-PLAINTEXT-PASSWORD-STORAGE`（🔴 P1）| ❌ **仍 OPEN**（新注册仍明文，→ S2-F-3 设计 / F-4 实现）|

---

## 8. 状态声明

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = aaf9bc0` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓ |
| 数据影响 | **零写入**（仅 `SHOW COLUMNS` / `SELECT`；探针不连 DB）|
| 临时探针 | 已在 `%TEMP%` 编译运行，**运行后删除**（`cleaned=True`）✓ |
| 文档 | 新增本文件 |

---

## 9. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-F-2-0 取证：**三层证据**（Jackson 运行时探针 `password=null`/`idCardNo=null` · 两 register 均不 setPassword · `insert-strategy: not_null` + `DEFAULT NULL` 推演）· 两端点同构对比（学生多 idCardNo 查重）· 🔴 **附带发现 idCardNo 同样被丢弃 → 身份证查重失效**（新登记）· NULL 密码登录 **NPE/500** · 现有 0 条 NULL 记录佐证 · 4 修复方案（推荐 B 注册专用 DTO）· 6 项待裁定；**零改动** ✓ |
