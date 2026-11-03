# S2-F-1-0 · 匿名 `resetPass` 修复设计审计

> **性质**：只读审计 · **零代码 · 零 DB 变更 · 零 commit**
> **基线**：`HEAD = origin = d27c10a` · worktree CLEAN
> **日期**：2026-10-22
> **核心问题**：`student/resetPass` 与 `counselor/resetPass` **是否存在必须保留的匿名「忘记密码」业务需求？系统是否具备任何身份验证机制？**
> **上游**：[`S2-F-0-PASSWORD-SECURITY-AUDIT.md`](./S2-F-0-PASSWORD-SECURITY-AUDIT.md)（`SEC-ANONYMOUS-PASSWORD-RESET` P1）

---

## 0. 一句话结论

**两个端点均无任何真实调用方（前端 0 / 管理端 0 / 后端内部 0 / 测试 0 业务用途），系统也没有任何「找回密码」身份验证基础设施（邮件 0 / 短信 0 / reset token 0 / 验证码不校验）。**
所谓「属忘记密码流程」只是**脚手架默认生成 + 历史 D3 裁定的推断**，**从未有调用面证据支持**。

**⇒ 建议按 D7 先例直接下线**（而非加固），并将「自助找回密码缺失」登记为功能项。

---

## 1. 调用面全穿透（5 类逐项）

| 类别 | 检索 | 结果 |
| --- | --- | --- |
| **老前端**（`src/main/resources/front`）| `resetPass` / `忘记密码` / `找回密码` / `forget` | **0 处** |
| **Vue3**（`frontend/src`）| 同上 | **0 处** |
| **管理端**（`admin/admin/src`）| 同上 | **0 处**（只有 `resetPassword?id=`，是 admin-only 的另一端点）|
| **后端内部调用** | `resetPass(` 在 `src/main/java` | 仅 2 处**定义**（`StudentController:381`、`CounselorController:358`），**0 处调用** |
| **测试** | `src/test` | **0 处业务用途**；仅 1 处作为「**对照组**」（见 §5）|

### 1.1 登录页没有任何「忘记密码」入口

| 页面 | 存在的链接 | 缺失 |
| --- | --- | --- |
| 老前端 `login.html:124` | 「注册学生」 | ❌ 无「忘记密码」 |
| Vue3 `LoginView.vue` | 仅登录表单 | ❌ 无 |
| Admin `login.vue` | 仅登录表单 | ❌ 无 |

**⇒ 用户在产品界面中根本无法触达 `resetPass`。**

---

## 2. 端点存活实测（匿名，零写入）

> 使用**不存在的 username** 探测，避免任何数据变更。

| 端点 | 实测 | 判定 |
| --- | --- | --- |
| `POST /student/resetPass?username=__probe_not_exist__` | **HTTP 200 · code=500 · 「账号不存在」** | 🔴 **匿名可达、端点存活** |
| `POST /counselor/resetPass?username=__probe_not_exist__` | 同上 | 🔴 **存活** |
| `POST /adminUser/resetPass?username=admin` | **HTTP 404** | ✅ 已下线（D7 先例）|

**数据核对（探测后）**：`student` 3 条 / `counselor` 3 条 / `admin_user` 1 条，默认值计数与审计前**完全一致** ⇒ **零写入** ✓

---

## 3. 是否具备真「找回密码」能力：**全部为 0**

| 能力 | 检索结果 |
| --- | --- |
| 邮件发送（`MailSender` / `JavaMail` / `jakarta.mail`）| **0** |
| 短信发送（`sms` / `Sms` / `短信`）| **0** |
| 一次性 reset token（表 / 字段 / 逻辑）| **0** |
| 安全问题 / 密保 | **0** |
| 验证码校验 | **0**（`login(..., String captcha, ...)` 参数存在，但**服务端从未使用**）|
| 重置频率限制 / IP 限流 | **0** |

**⇒ 现有 `resetPass` 的「身份凭证」只有 `username` 一个字符串，无任何二次验证。**

---

## 4. 业务需求判定（回答核心问题）

### 4.1 「必须保留」的证据：**无**

| 可能的业务需求 | 证据 |
| --- | --- |
| 用户在产品中点击「忘记密码」 | ❌ 无入口（3 端均无）|
| 客服/运营脚本批量调用 | ❌ 0 处脚本 |
| 第三方/移动端接入 | ❌ 0 处 |
| 文档规定的业务流程 | ❌ 仅有「D3 定案不动」的历史裁定，**无需求描述** |

### 4.2 历史登记的来源（用于解释，不作为保留理由）

| 文档 | 原表述 |
| --- | --- |
| `PHASE2-CLOSING-REPORT.md:81` | `D-4 /yonghu/resetPass、/xinlilaoshi/resetPass 仍 @IgnoreAuth —— 属「忘记密码」流程，D3 定案不动` |
| `jiaojie.md:392 / 428` | `G3（🔴 公开 resetPass）` · 后端挂账 |
| `jiaojie.md:1008` | `S6 🟠 resetPass 系列免鉴权` |
| `jiaojie.md:568` | 「需先业务确认是否仍被调用，**不凭"看起来危险"直删**」← 本次审计即为该确认 |

**⇒ 「属忘记密码流程」是**推断**，从未完成调用面确认；本次确认结果为「零调用方 + 零入口」。**

### 4.3 与 D7 的可比性

| 维度 | `/adminUser/resetPass`（D7 已下线）| `/student/resetPass` · `/counselor/resetPass`（本批）|
| --- | --- | --- |
| 鉴权 | `@IgnoreAuth` | `@IgnoreAuth` |
| 凭证 | 仅 `username` | 仅 `username` |
| 行为 | 密码 → `123456` | 密码 → `123456` |
| 前端调用 | 0 | **0** |
| 影响账户 | 管理员（**更高危**）| 学生 / 老师 |
| 裁定 | ✅ **已删除（404）** | ⏳ **本批待裁定** |

**⇒ 同类端点、同类零调用，D7 已确立"下线"先例。**

---

## 5. ⚠️ 实施影响：测试契约必须同步修改

`IdentityAuthorizationRegressionTest:448-453`（用例 20）当前**依赖 `/student/resetPass` 存在**：

```java
// 对照：同名「忘记密码」端点（用户端）按 D3 保持不变 →
//      证明上面的 404 是「该路径被删除」，而非「所有 /resetPass 都 404」或全局 404 行为
MvcResult contrast = perform("/student/resetPass?username=not-exist-user-xyz", null);
assertThat(contrast.getResponse().getStatus())
        .as("对照：/student/resetPass 属「忘记密码」流程，按 D3 保持存在")
        .isNotEqualTo(404);          // ← 下线后必然失败
```

**⇒ 若下线，47/47 中该用例会 FAIL。** 实施时必须同步：

1. `isNotEqualTo(404)` → **`isEqualTo(404)`**；
2. 原「对照锚点」（用 `student/resetPass` 证明非全局 404）失效 → 需改用**仍存在的端点**作锚点
   （建议：`/student/login` 或 `/counselor/list`，均非 404 且与密码无关）；
3. 断言文案同步更新；
4. 回归数仍为 **47/47**（只改用例内容，不增删用例）。

> 这是本批**唯一**的测试契约变更点，必须显式登记。

---

## 6. 修复方案候选（**本批不实现**，待裁定）

| 方案 | 形态 | 优点 | 缺点 | 评价 |
| --- | --- | --- | --- | :-: |
| **A（推荐）** | **下线 2 个 `resetPass`**（删除方法 + 保留下线依据注释，对齐 D7） | 彻底消除匿名接管；零业务影响（0 调用）| 失去"自助找回"（本就不可达）| ✅ **首选** |
| B | 保留但加验证码 / 限流 | 改动小 | **本质未变**：凭证仍是 `username`，攻击者可自动化绕过/慢速爆破 | ❌ 治标 |
| C | 实现真找回（reset token + 邮件/短信）| 产品完整 | 需新建邮件/短信基础设施 + token 表 + 前端页面 → **范围远超本批** | ⏳ 未来需求，登记不实现 |
| D | 改为需登录 + 验证旧密码 | 安全 | 那是「修改密码」而非「找回」；用户忘记密码时**不可用** | 🟡 与 A 不冲突，可并行立项（见 §7）|

### 6.1 方案 A 的"忘记密码"降级路径

下线后，用户忘记密码的唯一途径 = **联系管理员**，由管理员用已存在的 admin-only 端点重置：

```text
管理端 yonghu/list.vue:738     → GET /student/resetPassword?id=    (admin-only ✓)
管理端 xinlilaoshi/list.vue:750 → GET /counselor/resetPassword?id= (admin-only ✓)
```

**⇒ 业务能力未丢失，只是从"匿名自助"降级为"管理员介入"，这正是安全与可用性的合理权衡。**

---

## 7. 需新登记的功能项（非安全）

| ID | 性质 | 说明 |
| --- | --- | --- |
| **`DEF-NO-SELF-SERVICE-PASSWORD-RECOVERY`** | 🟡 **功能缺失** | 下线 `resetPass` 后，系统无自助找回密码能力；用户忘记密码需管理员重置。若产品需要自助能力，应另行设计 **一次性 reset token + 邮件/短信验证**，不得恢复"仅凭 username"的流程。 |
| **`DEF-NO-SELF-SERVICE-PASSWORD-CHANGE`** | 🟠 **功能缺失（P2）** | 学生 / 老师 **均无"修改密码"功能**（老前端无页面、Vue3 无功能、后端 `update` 不绑定 password）→ 详见 `S2-F-2` 范围。 |

---

## 8. 待你裁定

| # | 问题 |
| :-: | --- |
| 1 | 是否批准**方案 A：直接下线 2 个 `resetPass`**（对齐 D7 先例）？ |
| 2 | 是否接受 **"忘记密码"降级为管理员介入**（`resetPassword` admin-only）？ |
| 3 | 下线时是否**保留下线依据注释**（D7 风格）？ |
| 4 | 测试契约改动（§5）是否批准为**本批必要组成**（`isEqualTo(404)` + 更换对照锚点）？ |
| 5 | `DEF-NO-SELF-SERVICE-PASSWORD-RECOVERY` / `DEF-NO-SELF-SERVICE-PASSWORD-CHANGE` 是否按 §7 登记？ |
| 6 | 是否同意 `S2-F-2`（注册丢密码）与 `S2-F-3`（哈希迁移设计）继续排在后面？ |

---

## 9. 状态声明

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = d27c10a` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓ |
| 数据影响 | **零写入**（探测仅用不存在的 username；探测后三表计数与默认值完全一致）|
| 实测方式 | 真实 HTTP（PID 46704）· 匿名探测 3 端点 |
| 文档 | 新增本文件 |

---

## 10. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-F-1-0 取证：调用面 5 类全穿透（前端/管理端/后端内部/测试均为 0）· 3 端无「忘记密码」入口 · 端点存活实测（student/counselor 200+「账号不存在」· adminUser 404）· 真找回能力 6 项全为 0 · 业务需求无证据（「属忘记密码流程」仅为历史推断）· 与 D7 逐维可比 · **测试契约必然失败点**（`isNotEqualTo(404)`）· 4 方案（推荐 A 下线）· 2 项功能债务；**零改动** ✓ |
