# Phase 3 / Step 7 · R10 —— 个人资料编辑（方案 A：不做上传 · **待提交** ✗）

> **范围（已批准 ✓）**：`/me` 查看 + 编辑 `name` / `phone` / `email` / `avatarUrl` → `student/update`
> **禁止项（全部遵守 ✓）**：❌ `file/upload` · ❌ `file/download` · ❌ 后端修改 · ❌ 修文件漏洞 · ❌ 改上传目录 · ❌ 新增静态映射 · ❌ 修 `createdAt` · ❌ 与 R11–R13 混批
> **基线**：`81c9f44`（Batch 4 已推送 ✓）

---

## 1. 改动清单（**2 文件**）

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `api/student.ts` | M | `+updateStudent()` —— **白名单构造**：只发送 `id` + 4 个可改字段 ✓ |
| 2 | `views/MeView.vue` | M | 查看态 + 编辑态（4 字段）+ 客户端校验 + 错误分派 + 保存后重取 session ✓ |

**后端 0** ✗ · **file API 0** ✗ · **新增路由 0** ✓

---

## 2. 身份边界（关键设计 ✓）

```text
token → 拦截器 → Session → HttpServletRequest
                                 ↓
            student/update  →  OwnershipGuard.assertSelfOrAdmin  ✓
                                 ↓
                        id 必须 = 本人（他人 → 403）
```

前端实现 ✓：

| 约束 | 实现 |
| --- | --- |
| `id` 来源 | `me.id`（`/student/session`）→ 回退 `auth.userId` ✓；**绝不从 URL/表单取** ✗ |
| 提交字段 | **仅** `id` + `name` / `phone` / `email` / `avatarUrl` ✓ |
| 身份字段 | `username` / `gender` / `idCardNo` / `password` / `createdAt` **从不提交** ✗（静态检查 0 命中 ✓）|
| `avatarUrl` | 仅接受 **`http(s)://`** 或 **站内 `/upload/…`** ✓；**不做任何服务端 URL 抓取** ✗（避免引入 SSRF 面 ✓）|

---

## 3. 验收（实测）

| 项 | 内容 | 结果 |
| :-: | --- | --- |
| **A 静态** | `file` 端点字面量（全前端）| **0** ✓✓ |
| | `/detail/` | **0** ✓ |
| | 身份字段赋值（`username:/gender:/idCardNo:/password:/createdAt:`）| **0** ✓ |
| | 写操作总数 | **1**（仅 `POST /student/update` ✓）|
| | `vue-tsc --noEmit` | **exit 0** ✓ |
| **B 查看** | `/me` | `用户名 a1` · `姓名 学生姓名1` · `性别` · `手机` · `邮箱` · `注册时间` ✓ |
| **C 编辑保存** | 改名 → `学生姓名1T` → 保存 | 页面更新 ✓ · **DB `student.name = 学生姓名1T`** ✓✓（真实落库 ✓）|
| | 保存后 | 自动重取 `/student/session` ✓（以服务端为准 ✓）|
| **D 校验（非法手机）** | 手机 `123` → 保存 | **提示「手机号格式不正确」** ✓ · **DB 未变** ✓（未发请求 ✓）|
| **E 业务错误（511）** | 手机改为**他人**号码 → 保存 | 提示「该手机号/账号/身份证号已被占用」✓ · **DB 未变** ✓ |
| **F 数据复原** | 恢复 `学生姓名1` / `17703786901` | **DB `3|学生姓名1|17703786901` = 基线** ✓✓ |
| **G 回归** | `mvn test` | **47 / 47**（failures 0 · errors 0 ✓；后端未改 ✗）|
| **H Console** | 全程 | **Errors 0 / Warnings 0** ✓ |

---

## 4. ⚠️ 能力边界（如实登记 ✗）

| # | 项 | 说明 |
| :-: | --- | --- |
| 1 | **不支持头像上传** ✗ | 方案 A ✓：仅接受 URL；`file/upload` 匿名 + 落 CWD 导致 URL 404 ⇒ **不纳入调用链** ✓ |
| 2 | **无法"清空"字段** ⚠️ | MyBatis-Plus 忽略 null ⇒ 清空邮箱/头像**不会生效**（提交空串仍被忽略 ✓）|
| 3 | **`createdAt` 不可见为可改** ✓ | 未提供输入项 ✓；后端可绑属性登记 P2 ✓ |
| 4 | **403（改他人）未做 UI 级验证** ✗ | 正确 UI **不会**产生该请求 ✓（`id` 恒为本人 ✓）；后端 `assertSelfOrAdmin` 已静态确认 ✓ |
| 5 | 头像预览 | 编辑态显示 URL 文本 ✓；查看态**未渲染头像图** ⚠️（避免与"上传"能力混淆 ✓；如需显示可后续加 ✓）|

---

## 5. 安全登记（**均已裁定：登记、不修** ✗）

| ID | 级别 | 内容 | 状态 |
| --- | :-: | --- | :-: |
| `SEC-FILE-DOWNLOAD-TRAVERSAL` | 🔴 **P0** | `/file/download` `@IgnoreAuth` + 未过滤 `..` ⇒ 匿名可读 classpath 任意文件（实测 `../pom.xml` 201/5576B ✗）| **Identified / Unfixed** |
| `SEC-FILE-UPLOAD-ANON` | 🔴 **P1** | `/file/upload` 白名单 ⇒ 匿名上传 + 无扩展名白名单 | **Identified / Unfixed** |
| `DEF-UPLOAD-PATH-DRIFT` | 🟡 P2 | 上传落 CWD `./upload/`，与静态服务目录不一致 ⇒ URL 404 | **Identified / Unfixed** |
| `DEF-STUDENT-CREATEDAT-BINDABLE` | 🟡 P2 | `createdAt` 可被学生提交（未服务端恢复）| **Identified / Unfixed** |
| `SEC-DETAIL-IDOR` | 🔴 P1 | 6 控制器 `/detail/{id}` 无守卫（Batch 4 已绕开 ✓）| **Identified / Unfixed** |

> 后续修复 `SEC-FILE-*` 时应**重新设计文件根目录 + 路径规范化 + 资源映射边界** ✓
> **不能**只加 `if (fileName.contains(".."))` 之类的字符串黑名单 ✗

---

## 6. 未做（✗）

未改后端 ✗ · 未调用 file API ✗ · 未修任何安全项 ✗ · 未加静态映射 ✗ · 未实现上传 ✗ · 未截图 ✗ · 未提交（本轮待裁定）✗

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | R10 实现（2 文件）· **A–H 全过**（`file` 字面量 **0** ✓ · 身份字段 **0** ✓ · 写操作 **1** ✓ · tsc 0 ✓ · 真实落库 ✓ · 非法手机拦截且零写 ✓ · **511 冲突提示且零写** ✓ · **DB 复原到基线** ✓ · Console 0 ✓ · **47/47** ✓）；能力边界 5 项 + 安全登记 5 项（P0/P1/P2×2/P1）**均不修** ✗ |
