# Phase 2 / Step 5 · D7 独立安全小批次：下线 `/users/resetPass`

> **性质**：独立安全小批次（**不并入批 3 / 批 4**，按你的要求单独提交、可单独回滚）
> **状态**：✅ **已完成并提交**（`d6d8d03`，2 files / +44 −14）
> **决策依据**：设计文档 §4.2 D3 + §10 D7；调用面核查见 [`PHASE2-T5B3A-WRITE-PATH-AUDIT.md`](./PHASE2-T5B3A-WRITE-PATH-AUDIT.md) §6

---

## 1. 为什么下线（风险定性）

```java
@IgnoreAuth                                   // ← 免鉴权
@RequestMapping(value = "/resetPass")
public R resetPass(String username, ...) {
    UsersEntity user = usersService.getOne(... eq("username", username));
    user.setPassword("123456");               // ← 直接置为固定密码
    usersService.update(user, null);
}
```

`admin_user` 是**管理端账号表** → **匿名**传入 `username=admin` 即可把管理员密码重置为 `123456` → **管理端账户接管**。

> 补充事实：种子数据里 `admin` 的密码**本身就是 `admin`**（`admin_user` 表实测：`6, admin, admin`）→ 该端点的边际风险不是「猜不到密码」，而是「**无需密码即可强制改写**」这一能力本身不应存在。

## 2. 调用面核查（全部为零，且含消歧复核）

| 区域 | 结果 |
| --- | --- |
| Java | 仅该方法 6 行定义，**无内部调用** |
| 用户端（Layui） | **0** |
| 管理端 src | **0** |
| 管理端 dist | **0** |
| scripts / bin / deploy / docker | 目录不存在 |
| 配置 / 路由 / 业务说明 | **0** |

> **本轮消歧复核**（避免前缀误匹配）：`users/resetPass` = **0**；`resetPass[^w]` = **0**（即 dist 中命中项都是 `resetPassword`）。

## 3. 影响面（明确不动的东西）

| 端点 | 处置 |
| --- | --- |
| `/users/resetPass` | 🔴 **本次下线** |
| `/yonghu/resetPass`、`/xinlilaoshi/resetPass`（用户/老师「忘记密码」） | ⏸ **按 D3 不动** |
| `/yonghu/resetPassword`、`/xinlilaoshi/resetPassword`（管理端「重置密码」按钮） | ✅ 批 2 已限管理员，**本批未动** |

代码中保留了说明性注释（含下线依据与影响面），便于 Git 历史回答「为什么删除这个接口」。

## 4. 验证

```text
mvn -B clean package → BUILD SUCCESS
IdentityAuthorizationRegressionTest   20 项 ✓（新增用例 20）
Phase2CoreRegressionTest              14 项 ✓
合计                                  34/34  ✓
```

**运行期实测**：

| 请求 | HTTP | body |
| --- | --- | --- |
| `/users/resetPass?username=admin`（无 Token） | **404** | `{"msg":"请求的资源不存在","code":404}` |
| `/yonghu/resetPass?username=not-exist-user-xyz`（**对照**） | 200 | `{"msg":"账号不存在","code":500}` |

> **对照的意义**：同名端点仍存在 → 证明 404 是「**该路径被删除**」，而不是「所有 `/resetPass` 都 404」或全局 404 行为。

**数据未被改动（独立核对，非空跑）**：

```text
用例 20：请求前后 usersService 读到的 admin 密码一致  ✓
独立核对：admin_user 表 → 6, admin, admin（仍为种子原值）✓
```

> ⚠️ 过程记录：首次独立核对我误用了表名 `users`（真实表为 **`admin_user`**），导致快照取到空值、该对比成了**空跑**。已改用真实表名重做并纠正结论 —— 这正是「防跑空」要求存在的理由。

## 5. 观察项

| # | 项 | 说明 |
| --- | --- | --- |
| 1 | D7 代码**尚未推送** | 按规则 §9.1：push 需明确授权 |
| 2 | `admin` 账号种子密码为 `admin` | 弱口令（种子数据）。**不在本批处理** —— 改凭据属部署/运维决策，且会破坏「开箱可用」；建议单独立项 |
| 3 | `/yonghu/resetPass`、`/xinlilaoshi/resetPass` 仍为 `@IgnoreAuth` | 属「忘记密码」流程（真实业务需求），按 D3 不动；如需收紧（如加验证码/限流）应独立立项 |

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-08 | D7 下线 `/users/resetPass`：零调用面证据 + 消歧复核 + 运行期 404 验证 + 对照端点仍可用 + 数据未改动独立核对（并纠正了一次表名误用导致的空跑） |
