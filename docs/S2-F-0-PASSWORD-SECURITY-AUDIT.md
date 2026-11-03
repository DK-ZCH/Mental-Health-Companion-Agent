# S2-F-0 · 密码存储安全只读审计

> **性质**：只读取证 · **零代码 · 零 DB 变更 · 零部署 · 零 commit**
> **基线**：`HEAD = origin = d27c10a` · worktree CLEAN
> **日期**：2026-10-22
> **对象**：`SEC-PLAINTEXT-PASSWORD-STORAGE`（🔴 P1，S2-E-2-0 登记）

---

## 0. 一句话结论

三表密码**全部明文**（student 3/3 `123456` · counselor 3/3 `123456` · admin_user 1/1 `admin`），登录**明文 `equals`** 比对，**无哈希 / 无盐 / 无锁定 / 无速率限制**。
此外发现**两条比明文存储更直接的接管链**：
1. 🔴 **`student/resetPass` 与 `counselor/resetPass` 为 `@IgnoreAuth` 匿名**，仅需 username 即可把密码重置为 `123456`；
2. 🔴 **注册页收集密码，但 `@JsonIgnore` 使后端收不到** → 注册账号 `password = NULL`，登录时 `null.equals()` → **NPE**。

---

## 1. ① 三表字段与实际数据（脱敏统计，**不输出真实密码值**）

### 1.1 列定义（`mental_health_companion_agent.sql`）

| 表 | 列 | 类型 | 约束 | 行号 |
| --- | --- | --- | --- | :-: |
| `admin_user` | `password` | `varchar(100)` | **NOT NULL** | L227 |
| `counselor` | `password` | `varchar(200)` | DEFAULT NULL | L243 |
| `student` | `password` | `varchar(200)` | DEFAULT NULL | L323 |

**三列长度均为 100~200** ⇒ 存得下 bcrypt（60）与 SHA-256（64），**无需改 schema 即可迁移**。

### 1.2 数据现状（只读 SQL，脱敏）

| 表 | 总数 | `NULL` | 空串 | `= 123456` | **distinct 值数** | 最短长度 | 最长长度 | 形似 MD5 | 形似 bcrypt |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| `student` | **3** | 0 | 0 | **3** | **1** | 6 | 6 | 0 | 0 |
| `counselor` | **3** | 0 | 0 | **3** | **1** | 6 | 6 | 0 | 0 |
| `admin_user` | **1** | 0 | 0 | 0 | **1** | 5 | 5 | 0 | 0 |

**结论**：
- **零哈希**（MD5 = 0，bcrypt = 0）→ 全部明文；
- student / counselor **各有且仅有 1 个密码值**（即默认 `123456`）；
- admin_user 唯一值长度 5（即已知默认 `admin`，与 `SEC-ADMIN-DEFAULT-CREDENTIAL` 对应）；
- **无 NULL 记录** ⇒ 注册路径当前无落库样本（见 §4 的静态缺陷）。

---

## 2. ② 登录入口（3 处 · 全部明文比较）

| 控制器 | 行 | 查询 | 比较 | 防爆破 | 风险 |
| --- | :-: | --- | --- | :-: | --- |
| `StudentController.login` | **L321** | `getOne(eq("username", username))` | `yonghu.getPassword().equals(password)` | ❌ 无 | 🔴 明文 + NPE 风险 |
| `CounselorController.login` | **L301** | 同上 | `xinlilaoshi.getPassword().equals(password)` | ❌ 无 | 🔴 同上 |
| `AdminUserController.login` | **L50** | `usersService.getOne(...)` | `user.getPassword().equals(password)` | ❌ 无 | 🔴 同上 |

- **无 `MessageDigest` / `BCryptPasswordEncoder` / `Pbkdf2`** 任何哈希调用（全仓库 0 处）；
- **无失败计数 / 锁定 / 验证码强制**（`captcha` 参数存在但服务端未参与比对）；
- `getPassword()` 返回 `null` 时 `.equals()` → **NPE → 500**（见 §4）。

---

## 3. ③ 注册（2 处 · 🔴 密码被丢弃）

### 3.1 前端确实收集密码

| 页面 | 证据 |
| --- | --- |
| `front/front/pages/yonghu/register.html:75` | `<input type="password" name="password" ...>` ← **有密码输入框** |
| `front/front/pages/yonghu/register.html:79` | `name="password2"`（确认密码）|
| `front/front/pages/xinlilaoshi/register.html` | 同构（老师注册页）|

### 3.2 后端丢弃（根因）

```java
// StudentController L342-356
@IgnoreAuth @PostMapping("/register")
public R register(@RequestBody StudentEntity yonghu){
    ...查重...
    yonghu.setCreatedAt(new Date());
    yonghuService.save(yonghu);      // ← 从不 setPassword
}
```

```java
// StudentEntity L156-158
@JsonIgnore
public String getPassword() { ... }
```

**链条**：

```text
前端提交 password: "xxx"
        ↓
Jackson 反序列化 @RequestBody StudentEntity
        ↓
getPassword() 标 @JsonIgnore  ⇒  password 不绑定（丢弃）
        ↓
register() 不 setPassword
        ↓
INSERT student(..., password = NULL)
        ↓
schema: password varchar(200) DEFAULT NULL  ⇒ 允许写入 NULL
        ↓
登录：getPassword() == null → null.equals(...) → NullPointerException → HTTP 500
```

`CounselorController.register`（L315-329）**完全同构**。

### 3.3 现状佐证

当前 `student` / `counselor` 表 **NULL 记录 = 0** ⇒ 自助注册路径**可能从未被真实使用**（或注册后立即走 `resetPass`）。
但**代码路径客观存在**：一旦有人注册，即产生一个**永久无法登录且登录报 500** 的账号。

---

## 4. ④ 创建用户（5 处 · 均强制默认口令）

| 端点 | 鉴权（S2-D2 后）| 密码处理 |
| --- | :-: | --- |
| `student/save`（L135）| admin-only | `setPassword("123456")` |
| `student/add`（L497）| admin-only | `setPassword("123456")` |
| `counselor/save`（L138）| admin-only | `setPassword("123456")` |
| `counselor/add`（L476）| admin-only | `setPassword("123456")` |
| `adminUser/save`（L143）| admin-only | `setPassword("123456")` + `setRole("管理员")` |

- **管理端表单的 password 字段形同虚设**：`users/add-or-update.vue:25` 有输入框，但因 `@JsonIgnore` 不绑定，后端始终写 `123456`。
- 默认口令策略属 `SEC-ADMIN-DEFAULT-CREDENTIAL`，**与本 finding 分开**。

---

## 5. ⑤ update（改密不生效 · 双重失效）

### 5.1 后端

三表 `update` 均直接 `updateById(entity)`；`password` 因 `@JsonIgnore` **不绑定** ⇒ `password == null` ⇒ `update-strategy: not_null` **跳过该列** ⇒ **密码不变**。

### 5.2 管理端「修改密码」页（`admin/src/views/update-password.vue`）—— 🔴 完全不可用

```js
// L57-67 mounted：从 /{sessionTable}/session 取 user
this.user = data.data;          // ← 不含 password（@JsonIgnore）

// L78-87
var password = "";
if (this.user.mima) { password = this.user.mima; }
else if (this.user.password) { password = this.user.password; }
if (this.ruleForm.password != password) {
    this.$message.error("原密码错误");   // ← 永远命中（password 恒为 ""）
    return;
}
```

**双重失效**：
1. **第一层（前端）**：`/session` 不返回 `password` → `password` 恒为 `""` → 用户输入的原密码永远被判「错误」，**连提交都到不了**；
2. **第二层（后端）**：即使绕过前端直接 POST `/{table}/update`，`@JsonIgnore` 仍阻止绑定 → **密码不更新**。

**⇒ `DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE` 根因确认（P2）**：不是"某处漏了"，而是**密码既读不出也写不进**。

> 老前端 `front/` **无「修改密码」页面**（0 匹配）→ 该缺陷仅影响管理端。

---

## 6. ⑥ reset（4 个端点 · 其中 2 个匿名 · 🔴 P1）

| 端点 | 鉴权 | 行为 | 判定 |
| --- | :-: | --- | :-: |
| `GET /student/resetPassword?id=` | **admin-only**（Step 5 批2）| `setPassword("123456")` | ✅ 合理（管理端按钮）|
| `GET /counselor/resetPassword?id=` | **admin-only**（Step 5 批2）| 同上 | ✅ 合理 |
| **`POST /student/resetPass?username=`** | 🔴 **`@IgnoreAuth` 匿名** | 查 username → `setPassword("123456")` → `updateById` | 🔴 **P1** |
| **`POST /counselor/resetPass?username=`** | 🔴 **`@IgnoreAuth` 匿名** | 同上 | 🔴 **P1** |
| `POST /adminUser/resetPass` | — | **已下线（D7，404）** | ✅ |

### 6.1 🔴 匿名账户接管链（最直接的 P1）

```text
① 匿名已知 username（例：/counselor/list 曾长期公开返回 username —— 已由 S2-E-2-1/d27c10a 修复）
② POST /student/resetPass?username=a1     ← 无验证码、无身份校验、无邮件/短信确认
③ 密码被无条件重置为 123456
④ POST /student/login?username=a1&password=123456 → 200 + token
```

**关键**：`resetPass` **不需要旧密码**，只要能提供 `username`。因此**已泄露过的 username 仍可被利用**（修复 DTO 不回收已泄露信息）。

**⇒ 新登记 `SEC-ANONYMOUS-PASSWORD-RESET`（P1）** —— 影响 student + counselor 两张表。

> `adminUser/resetPass` 已在 D7 下线，属正确先例；学生/老师侧同类端点**尚未处理**。

---

## 7. ⑦ password 读取泄漏面

| 通道 | 状态 | 说明 |
| --- | :-: | --- |
| JSON 序列化（响应）| ✅ **屏蔽** | 三表 `getPassword()` 均 `@JsonIgnore`；S2-E-2-1 原始 JSON 实测 0 命中 |
| JSON 反序列化（请求）| ✅ 屏蔽（但造成 §3/§5 缺陷）| 同一注解的副作用 |
| **`toString()`** | 🔴 **包含 password** | 三表 `toString()` 均拼接 `password=` |
| **Controller 日志** | 🟡 潜在 | `logger.debug("...yonghu:{}", yonghu.toString())`（默认 INFO 不输出；开启 debug 即泄漏）|
| SQL 日志 | 🟡 潜在 | `logging.level.com.dao: debug` 打印 SQL 模板与参数（含 `?` 绑定值）|
| 数据库 / 备份 | 🔴 **明文** | 本 finding 本体 |

**⇒ 新登记 `DEF-PASSWORD-IN-TOSTRING`（P3）**：日志泄漏面，独立于存储形态。

---

## 8. ⑧ 前端兼容矩阵

| 前端 | 页面 | 依赖 | 迁移影响 |
| --- | --- | --- | :-: |
| 老前端（Layui）| `login.html` | `POST {table}/login?username=&password=` | ✅ 服务端改哈希后无感 |
| 老前端 | `yonghu/register.html` · `xinlilaoshi/register.html` | **提交 password**（被丢弃）| ⚠️ 需随 `DEF-REGISTER-PASSWORD-DROPPED` 一并修 |
| 老前端 | 忘记密码 | `{table}/resetPass?username=` | ⚠️ 端点本身需先修（P1）|
| 老前端 | 修改密码 | **无此页面** | — |
| Vue3（5173）| `LoginView.vue` | `GET /student/login?username=&password=`（**密码在 URL query**）| ⚠️ 见下 |
| Vue3 | 修改密码 | **无此功能** | — |
| Admin（Vue2）| `login.vue` | `POST /adminUser/login` | ✅ |
| Admin | `update-password.vue` | `/{table}/session` + `/{table}/update` | 🔴 **当前完全不可用**（§5.2）|
| Admin | `users/add-or-update.vue` | password 字段 | ⚠️ 形同虚设（后端固定 123456）|

### 8.1 附带发现：Vue3 登录用 GET + query 传密码

`frontend/src/api/auth.ts:20`：`http.get('/student/login', { params: { username, password } })`
→ 密码进入 **URL query** → 可能落 access log / 浏览器历史 / Referer。
**登记 `DEF-LOGIN-PASSWORD-IN-QUERY`（P2）**（老前端同为 `GET`，见 `login.html:249`）。

---

## 9. 影响面总表

| 环节 | 受影响 | 说明 |
| --- | :-: | --- |
| student | ✅ | 明文 · 匿名 resetPass · 注册丢密码 |
| counselor | ✅ | 明文 · 匿名 resetPass · 注册丢密码 |
| admin_user | ✅ | 明文（`admin`）· 改密失效 · resetPass 已下线 |
| 注册 | ✅ | 密码被丢弃 → NULL |
| 登录 | ✅ | 明文比较 + NPE 风险 |
| 修改密码 | ✅ | 三端均无效（管理端页面不可用，后端不绑定）|
| 重置密码 | ✅ | admin 版合理；匿名版 P1 |
| Token | ❌ 不受影响 | token 与密码解耦，无密码派生 |
| 其他业务数据 | ❌ 不受影响 | — |

---

## 10. 新登记问题（本批只登记，不修）

| ID | 级别（**已裁定**）| 事实 | 归属 |
| --- | :-: | --- | --- |
| **`SEC-PLAINTEXT-PASSWORD-STORAGE`** | 🔴 **P1（安全）** | 三表明文、无哈希/盐、明文比较 | 迁移设计（S2-F-3）|
| **`SEC-ANONYMOUS-PASSWORD-RESET`** | 🔴 **P1（安全）** → ✅ **CLOSED（`aaf9bc0`）** | `student/resetPass` + `counselor/resetPass` 为 `@IgnoreAuth`，仅需 username 即重置为 `123456` → **匿名账户接管** | **已由 S2-F-1-1 下线两端点（HTTP 404）** |
| **`DEF-REGISTER-PASSWORD-DROPPED`** | 🔴 **P1\*（功能阻断，非安全 P1）** | 注册页收集密码，`@JsonIgnore` 使后端丢弃 → `password = NULL` → 无法登录 + 登录 NPE(500)；**未证明可接管他人账户** | S2-F-2 |
| `DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE` | 🟡 **P2** | 管理端改密页**双重失效**（读不到原密码 + 后端不绑定）| 已有登记，根因已确认 |
| `DEF-LOGIN-PASSWORD-IN-QUERY` | 🟠 **P2（暂定，独立处理）** | 登录用 GET + URL query 传密码（Vue3 + 老前端）；级别待核查 access log / 路由后确认 | **独立立项** |
| `DEF-PASSWORD-IN-TOSTRING` | 🟡 **P3** | 三表 `toString()` 拼接 password → 日志泄漏面 | 独立小批 |
| `SEC-ADMIN-DEFAULT-CREDENTIAL` | 🟡 | 默认 `admin/admin` · 默认 `123456` | **与明文存储分开**（不合并）|

> **`P1\*` 说明**：统一严重度体系下表示「必须尽快修复」，但**性质是功能阻断，不是账户安全漏洞** —— 避免与安全 P1 混淆。
>
> **定性原则（用户裁定）**：
> 1. 密码存储安全 ≠ 默认凭据安全 → `SEC-PLAINTEXT-PASSWORD-STORAGE` 与 `SEC-ADMIN-DEFAULT-CREDENTIAL` 分开；
> 2. 把 `123456` 转成 `bcrypt("123456")` **不改善安全性**（明文泄露 → 可破解的弱 hash）→ 默认口令需独立策略；
> 3. 明文存储 ≠ 密码进 URL → 后者属传输/日志泄漏面，独立立项。

---

## 11. 迁移方案候选（**本批不选、不实现**）

| 方案 | 形态 | 优点 | 风险 |
| :-: | --- | --- | --- |
| **A** 一次性迁移 | 启动/脚本把明文全转 bcrypt | 终态干净 | 旧数据迁移不可逆；回滚需备份 |
| **B** 登录时渐进迁移 | 明文校验成功后立即写 bcrypt | 常见兼容路线 | 需判断"明文 vs hash"、并发登录、登录失败不得升级、未登录老账号永久停留明文 |
| **C** 双格式兼容 + 独立迁移 | hash→bcrypt 校验；明文→明文校验后升级 | 风险最可控 | 需明确最终清理机制（否则明文残留）|

**共同前置条件**（无论选哪个）：
1. 先修 `SEC-ANONYMOUS-PASSWORD-RESET`（否则迁移后仍可被匿名重置）；
2. 先修 `DEF-REGISTER-PASSWORD-DROPPED`（否则新账号无法登录）；
3. 明确 `admin_user.password NOT NULL` 与 bcrypt 长度的兼容（当前 100 字符足够）；
4. 备份 + 回滚脚本；
5. 测试账号与 47/47 回归契约（登录相关测试需覆盖明文→hash 过渡）。

---

## 12. 已裁定事项（用户 2026-10-22）

| # | 问题 | 裁定 |
| :-: | --- | --- |
| 1 | 拆分 | ✅ **接受**：`SEC-ANONYMOUS-PASSWORD-RESET` P1 **独立于**明文存储 |
| 2 | 修复顺序 | ✅ **S2-F-1 匿名 resetPass → S2-F-2 注册丢密码 → S2-F-3 迁移设计 → S2-F-4 迁移实现** |
| 3 | 哈希算法 | ⏸ **暂定 bcrypt**（本项目生态与迁移成本更优），参数在 S2-F-3 定 |
| 4 | 迁移方案 | ⏸ **不接受直接选 C**：S2-F-3 必须同时覆盖「开发/未来生产规模 + 回滚 + 未登录老账户 + 迁移完成判定」后再定 B/C |
| 5 | 默认口令 | ✅ **应处理但不与哈希绑定**：`123456 → bcrypt("123456")` 不改善安全性；默认凭据独立策略 |
| 6 | `DEF-LOGIN-PASSWORD-IN-QUERY` | ✅ **单独立项**，级别待核查 access log / Spring MVC 路由后确认（P1/P2 暂定） |

### 12.1 分层路线（已裁定）

```text
S2-F-0  密码安全审计                  ✅ 完成（本文件）
   ↓
S2-F-1  匿名 resetPass 修复           ← 下一步（先做设计审计 S2-F-1-0）
   ↓
S2-F-2  注册密码丢失修复
   ↓
S2-F-3  密码存储迁移设计
   ↓
S2-F-4  密码哈希迁移实现
```

**理由**：认证生命周期本身仍是坏的 —— 若先做哈希迁移，而 `resetPass` 仍可匿名把密码重置为 `123456`，系统依然存在账户接管。

---

## 13. 状态声明

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = d27c10a` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 schema · 零部署 · 零 commit** ✓ |
| 数据影响 | **无写入**（全部 `SELECT`；未重置任何密码、未迁移任何数据）|
| 敏感信息处理 | **报告不含任何真实密码值**；仅记录 `123456`/`admin` 的**计数**与长度/形态分类 |
| 审计方式 | 静态代码检索 + 只读 SQL 统计 + 前端页面核对 |
| 文档 | 新增本文件 |

---

## 14. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-F-0 取证：三表字段与脱敏数据统计（全明文、零哈希、distinct=1）· 3 登录入口明文 equals · 注册密码被 `@JsonIgnore` 丢弃 · 5 创建入口强制 123456 · update 改密双重失效（前端读不到 + 后端不绑定）· **2 个匿名 `resetPass`（P1 新登记）** · `toString`/SQL 日志泄漏面 · 前端兼容矩阵（含 GET+query 传密码）· 3 迁移方案候选（不选）· **零改动** |
