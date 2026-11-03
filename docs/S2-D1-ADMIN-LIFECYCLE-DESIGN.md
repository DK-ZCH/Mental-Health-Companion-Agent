# S2-D1 —— Admin 生命周期安全收口（实现设计）

> 日期：2026-10-22 ｜ 基线：`cd24257`（S2-C2 已推送，LEAD=0）
> 状态：**实现设计 · 待批准** ✗ ｜ 本设计零代码、零 DB、零 commit
> 上游：`S2-D-REMAINING-P1-AUDIT.md`（v2，已按审阅裁定收紧）

---

## 1. 范围（已裁定）

| 端点 | 处理 | 目标 |
| --- | --- | --- |
| `/adminUser/register` | **废弃**（删除方法，对齐 `/users/resetPass` 下线模式）| 消除匿名提权入口 |
| `/adminUser/save` | 加 `assertAdminOnly` + 服务端强制 `role="管理员"` | 消除未授权创建 |
| `/adminUser/update` | 加 `assertAdminOnly` + 屏蔽 `role` 绑定 | 消除未授权修改 / 提权 |
| `/adminUser/delete` | 加 `assertAdminOnly` | 消除未授权删除 |
| 默认 `admin/admin` | **仅登记**，不修改种子 | 部署时改密（另立工单）|

---

## 2. 关键字段策略（据实，非笼统屏蔽）

| 字段 | `/save` | `/update` | 依据 |
| --- | :-: | :-: | --- |
| `role` | **服务端强制 `"管理员"`** | **服务端恢复库中现值**（屏蔽绑定）| `role` 前端表单**不提交**（`users/add-or-update.vue` 仅 username+password）⇒ 客户端可控 `role` = 恶意提权路径 |
| `password` | 维持现状（强制 `123456`）| **保留**（admin 编辑改密码 = 正常功能）| 前端表单**有 password 字段**（L25-35）⇒ 屏蔽会破坏管理端正常改密 |

> ⚠️ 澄清（相对审计 v1 的收紧 + 实测修正）：审计 v1 写「`/update` 屏蔽 role/password 绑定」，**实际只需屏蔽 `role`**；`password` **不额外屏蔽**（保留代码路径）。
>
> 📌 **实测补充（S2-D1 实现时发现）**：`AdminUserEntity.password` 的 `@JsonIgnore` 在当前 Jackson 配置下**同时阻止反序列化绑定** ⇒ 管理端「编辑密码」实际**提交不生效**（DB 保持原值）。这是**既有功能缺陷**，**不在 S2-D1 授权范围**，单独登记 `DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE`（不修）。

---

## 3. 改动清单（单文件 `AdminUserController.java`）

### 3.1 `/register` 废弃

```java
// 下线依据：S2-D 审计 —— @IgnoreAuth 匿名注册 admin，role 客户端可控 ⇒ 匿名提权入口。
// 全仓库调用面（待实现前复核）：前端 0 / 管理端 0 / 测试 0；无开放注册管理员业务需求。
// 处理：删除整个 register 方法（对齐 /users/resetPass 下线模式，不再保留 @IgnoreAuth 注册入口）。
```

### 3.2 `/save` 加守卫 + 强制 role

```java
@PostMapping("/save")
public R save(@RequestBody AdminUserEntity user, HttpServletRequest request){
    OwnershipGuard.assertAdminOnly(request);          // 仅管理员可创建 admin 账号
    if(usersService.getOne(...eq("username", user.getUsername())) != null) {
        return R.error("学生已存在");
    }
    user.setRole("管理员");                             // 服务端强制角色，忽略客户端 role
    user.setPassword("123456");                         // 维持现状：默认弱口令（另登记）
    usersService.save(user);
    return R.ok();
}
```

### 3.3 `/update` 加守卫 + 屏蔽 role

```java
@RequestMapping("/update")
public R update(@RequestBody AdminUserEntity user, HttpServletRequest request){
    OwnershipGuard.assertAdminOnly(request);          // 仅管理员可编辑 admin 账号
    AdminUserEntity existing = user.getId() == null ? null : usersService.getById(user.getId());
    if (existing != null) {
        user.setRole(existing.getRole());              // 屏蔽 role 绑定（防提权；role 恒为「管理员」）
    }
    com.security.UpdateGuard.assertHasUpdatableFields(user);
    usersService.updateById(user);                      // password 保留（正常改密）
    return R.ok();
}
```

### 3.4 `/delete` 加守卫

```java
@RequestMapping("/delete")
public R delete(@RequestBody Long[] ids, HttpServletRequest request){
    OwnershipGuard.assertAdminOnly(request);          // 仅管理员可删除 admin 账号
    usersService.removeByIds(Arrays.asList(ids));
    return R.ok();
}
```

> `OwnershipGuard.assertAdminOnly` 复用既有原语（L117）；`HttpServletRequest` 由 Spring 注入。

---

## 4. 测试设计（4 角色）

| 场景 | 预期 | 证明 |
| --- | --- | --- |
| anonymous `POST /adminUser/register` | **404**（废弃后）| `SEC-ADMIN-REGISTER-ANONYMOUS` 关闭 |
| anonymous `POST /adminUser/save` `/update` `/delete` | `code=401` | 既有认证契约 |
| student `save` `/update` `/delete` | **403** | `assertAdminOnly` 拒绝 |
| counselor `save` `/update` `/delete` | **403** | 同上 |
| admin `save`（探针）| 200 + 新 admin 落库（role=管理员）| 原业务行为 + role 强制 |
| admin `update`（探针，带恶意 role）| 200 + role 未被改（仍=管理员）| role 屏蔽生效 |
| admin `update`（探针，改 password）| 200 + password 落库 | 正常改密未破坏 |
| admin `delete`（探针）| 200 + 探针删除 | 原业务行为 |

> 探针用 `S2D1-*` 前缀；负向测试（student/counselor 403、anonymous 401/404）**不产生写**；admin 正向测试用探针，事后清理。

### 恢复方案

- admin 正向测试：插入探针 admin → 验证 → 物理删除探针 → `admin_user` 回到种子基线（1 行 id=6）。
- `AUTO_INCREMENT` 漂移（探针插入删除）如实登记，不回退。

---

## 5. 边界（本批不做）

```
❌ 默认 admin/admin 密码修改（仅登记，部署改密另立工单）
❌ S2-D2 用户管理 save/add/delete（学生/老师）
❌ S2-D3 counselor/info PII
❌ 内容公开 /info 收紧
❌ 两个 Admin 404
❌ 前端改动 · DB schema
```

---

## 6. 待批准

| # | 项 | 状态 |
| :-: | --- | --- |
| 1 | `/register` 废弃方式（删除方法，非改 admin-only）| 待批准 |
| 2 | `/save` 加 `assertAdminOnly` + 强制 role | 待批准 |
| 3 | `/update` 加 `assertAdminOnly` + 屏蔽 role（password 保留）| 待批准 |
| 4 | `/delete` 加 `assertAdminOnly` | 待批准 |
| 5 | 是否允许 1 次受控真实写入（admin 正向探针 + 事后清理）| 待批准 |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-D1 实现设计：`/register` 废弃；`/save`/`/update`/`/delete` 加 `assertAdminOnly`；`role` 强制/屏蔽、`password` 保留（正常改密）；默认凭据仅登记；4 角色测试矩阵 + 探针恢复；**零代码零 DB 零 commit** ✓ |
