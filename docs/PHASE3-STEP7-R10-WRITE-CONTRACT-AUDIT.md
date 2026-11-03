# Phase 3 / Step 7 · R10 —— 个人资料/Avatar **写契约审计**（只读）

> **性质**：只读审计 ✓ —— **未改后端** ✗ · **未改 Vue** ✗ · 零提交 ✗
> **基线**：`81c9f44`（Batch 4 / C1 已过 ✓）· worktree CLEAN ✓

---

## 1. 结论表（先给结论）

| 项目 | 当前实际情况 | R10 结论 |
| --- | --- | --- |
| **`student/update`** | `POST /student/update`，`@RequestBody StudentEntity`；需登录（不在白名单 ✓）；`OwnershipGuard.assertSelfOrAdmin(...)`（L154 ✓）| ✅ **可用**（仅本人）|
| **身份字段** | 学生侧**服务端恢复** `username` / `gender` / `idCardNo`（L158-162 ✓）；`password` 有 `@JsonIgnore` ✓ **不绑定** | ❌ **前端禁止提交**（提交也无效 ✓）|
| **`id`（目标）** | 必须等于**本人** id，否则 `ForbiddenException` → **403** ✓ | ✅ 用 `session.id` ✓，**不从 URL/表单取** ✗ |
| **`UpdateGuard`** | 空更新 → **HTTP 400** ✓（`assertHasUpdatableFields` L179 ✓）| ✅ 前端必须至少提交 1 个可改字段 ✓ |
| **`OwnershipGuard`** | `assertSelfOrAdmin` ✓（管理端放行 ✓ / 老师拒绝 ✓）| ✅ 前端不做替代 ✓ |
| **`file/upload`** | **在白名单内 ⇒ 匿名可上传** 🔴（实测：匿名 POST → `code=0` + 文件名 ✓）；**无扩展名白名单** ✗（`lastIndexOf(".")+1` ✓）| ⚠️ **可用但风险高**（见 §4）|
| **上传路径** | 代码意图 `classpath:static/upload` ✓，但**实测回退为进程 CWD 下 `./upload/`** ✗⚠️ | ⚠️ **关键：上传后 URL 不可访问**（见 §4.3）|
| **文件名** | `System.currentTimeMillis() + "." + 扩展名` ✓（**不使用客户端文件名** ✓ ⇒ 无路径穿越 ✓）| ✅ 可接受（同毫秒并发可能覆盖 ⚠️）|
| **文件大小** | 代码中**无**大小校验 ✗；**未实测上限** ⚠️ | ⚠️ 前端必须自限（如 ≤ 2 MB ✓）|
| **头像 URL** | 上传返回**仅文件名**（`{"code":0,"file":"…"}` ✗），**不返回 URL** ✗ | ⚠️ 前端需自行拼 `…/upload/<file>` ✓ |
| **静态访问** | `GET /upload/<file>` 匿名 **200** ✓（`yonghu1.jpg` / `config1.jpg` / `yonghu2.jpg` ✓）| ✅ 既有头像可显示 ✓ |
| **前端需要字段** | — | **`name` · `phone` · `email` · `avatarUrl`**（仅此 4 项 ✓）|
| **`createdAt`** | **可被绑定的** ✗（未被服务端恢复 ✗）| ⚠️ **P2**：前端**绝不提交** ✓ |
| 🔴 **`SEC-FILE-DOWNLOAD-TRAVERSAL`** | 匿名 `GET /file/download?fileName=../pom.xml` → **HTTP 201 / 5576 B** ✗ ⇒ **路径穿越成立** | 🔴 **建议 P0** · **登记不修** ✗ |
| 🔴 **`SEC-FILE-UPLOAD-ANON`** | 匿名 `POST /file/upload` → **`code=0` + 文件名** ✗ ⇒ 匿名可传任意类型 | 🔴 **建议 P1** · **登记不修** ✗ |

---

## 2. `student/update` 细节（代码取证 ✓）

```java
@RequestMapping("/update")
public R update(@RequestBody StudentEntity yonghu, HttpServletRequest request) {
    StudentEntity existing = yonghuService.getById(yonghu.getId());
    OwnershipGuard.assertSelfOrAdmin(request, ROLE_STUDENT, yonghu.getId(), existing);   // L154 ✓
    if (ROLE_STUDENT.equals(currentRole(request))) {          // L158 ✓
        yonghu.setUsername(existing.getUsername());           // 服务端恢复 ✓
        yonghu.setGender(existing.getGender());               // ✓
        yonghu.setIdCardNo(existing.getIdCardNo());           // ✓
    }
    // 唯一性校验：username / phone / id_card_no 冲突 → R.error(511, …)  // L164-183 ✓
    if ("".equals(avatarUrl) || "null".equals(avatarUrl)) yonghu.setAvatarUrl(null);  // L175 ✓
    UpdateGuard.assertHasUpdatableFields(yonghu);             // 空更新 → 400 ✓
    yonghuService.updateById(yonghu);                         // 按 id 全量更新 ⚠️
}
```

**要点** ✓：

1. **身份边界闭合** ✓：`token → CurrentUserProvider/Session → assertSelfOrAdmin` ⇒ **不能修改他人** ✓（他人 id → 403 ✓）
2. **服务端权威字段** ✓：`username / gender / idCardNo` 学生侧被覆盖 ✓（提交无效 ✓）
3. **`password` 不可绑定** ✓（`@JsonIgnore` ✓）
4. ⚠️ **全量 `updateById`**：MyBatis-Plus 默认**忽略 null 字段** ✓ ⇒ 未提交字段不会被清空 ✓（但**无法用 null 清空** ✗ —— R10 不提供"清空邮箱"能力 ✓）
5. ⚠️ **`createdAt` 可绑** ✗（未恢复 ✓）⇒ 学生可篡改自己的注册时间 ⚠️ → **前端绝不提交** ✓ + 建议 **P2 登记**
6. ⚠️ **唯一性冲突返回 `code=511`**（非 4xx ✓）⇒ 前端需把 `511` 当**业务错误提示** ✓（`ApiError.code=511` ✓ 可区分 ✓）

---

## 3. `StudentEntity` 字段与可绑定面

| 字段 | 注解 | 学生可否绑定 | R10 前端 |
| --- | --- | :-: | :-: |
| `id` | `@TableField("id")` | ✅（仅本人 ✓）| 用 `session.id` ✓ |
| `username` | — | ⚠️ 绑定但**被恢复** ✓ | ❌ 不提交 |
| `password` | — | ❌ `@JsonIgnore` ✓ | ❌ 不提交 |
| `name` | — | ✅ | ✅ **可改** |
| `phone` | — | ✅（唯一性校验 ✓）| ✅ **可改** |
| `idCardNo` | — | ⚠️ 绑定但**被恢复** ✓ | ❌ 不提交 |
| `avatarUrl` | — | ✅ | ✅ **可改** |
| `gender` | — | ⚠️ 绑定但**被恢复** ✓ | ❌ 不提交 |
| `email` | — | ✅ | ✅ **可改** |
| `createdAt` | `fill = INSERT` | ⚠️ **可绑定未被恢复** ✗ | ❌ **不提交**（P2）|

---

## 4. `file/upload` 细节（**风险集中区** 🔴）

### 4.1 代码事实

```java
@RequestMapping("/upload")                        // 无 @IgnoreAuth ⇒ 依赖拦截器白名单
public R upload(@RequestParam("file") MultipartFile file, String type) {
    String fileExt = originalFilename.substring(lastIndexOf(".")+1);   // ✗ 无白名单
    File path = new File(ResourceUtils.getURL("classpath:static").getPath());
    File upload = new File(path.getAbsolutePath(), "/upload/");
    String fileName = new Date().getTime() + "." + fileExt;            // ✓ 不含客户端名
    file.transferTo(dest);
    return R.ok().put("file", fileName);                              // ✗ 只给文件名
}
```

### 4.2 实测

| 探测 | 结果 |
| --- | --- |
| 匿名 `POST /file/upload`（空文件）| **`{"code":0,"file":"1792462945547.txt"}`** ✗ ⇒ **白名单放行 + 匿名写入成功** 🔴 |
| 产物落点 | **仓库根 `upload/`**（进程 CWD）✗⚠️ —— `classpath:static` 解析失败后回退 `new File("")` |
| `GET /upload/1792462945547.txt` | **404** ✗ ⇒ **CWD 下的上传文件不被静态服务** |
| `GET /upload/yonghu1.jpg`（`src/main/resources/static/upload/` 内既有文件）| **200 / 12,969 B** ✓ |
| `GET /file/download?fileName=../pom.xml`（匿名）| **201 / 5,576 B** ✗ ⇒ **路径穿越** 🔴 |
| `GET /file/download?fileName=../../../../pom.xml` | 500（越界后不存在 ✓）|

> **探针副作用已清理** ✓：删除 `upload/1792462945547.txt`（3 B）✓；`upload/` 变为空目录（Git 不记账 ✓）；`git status` 恢复 **CLEAN** ✓。

### 4.3 ⚠️ R10 的**核心阻塞点**

```text
上传成功 → 返回 fileName → 文件落在 进程CWD/upload/ → 但 /upload/<file> 返回 404
⇒ 前端拿到 fileName 也拼不出可访问的头像 URL ✗
```

**可选方案（需你裁定，R10 未动手 ✗）**：

| 方案 | 内容 | 代价 |
| --- | --- | :-: |
| **A** | R10 **只做「粘贴头像 URL」**，不做文件上传 ✗ | 功能降级 ⚠️，但零阻塞 ✓ |
| **B** | 上传后前端仍拼 `/upload/<file>`，**接受 dev 下 404**，登记缺陷 ⚠️ | 功能"看起来完成但显示不出" ✗ **不建议** |
| **C** | 后端加静态映射/改上传目录 ✗ | **超出 R10**（后端改动）✗ |
| **D** | R10 上传**写入后立即用 `/file/download?fileName=<file>` 预览** ⚠️ | hmm：download 走 CWD 同目录 ✓ 可能可读 ✓（待验证 ⚠️）但**等保风险** ✗ |

> **建议 A 或 D**（需先验证 D 是否可读 ✓）；**B 应排除** ✗ —— 避免"假完成" ✓。

---

## 5. 与旧 `front` 的关系（**不能照搬** ✗）

旧 `front` 资料页可能提交**已被 Phase 2/5 收紧的字段**（`username` / `gender` / `idCardNo` / `createdAt`）✗。
⇒ R10 **只提交 4 个字段**：`id`（来自 session）+ `name` + `phone` + `email`（+ `avatarUrl` 方案 A 时）✓。
⇒ 不参考旧页面的**字段清单**，只参考其**交互流程** ✓。

---

## 6. 新登记（**不修** ✗）

| ID | 级别建议 | 内容 |
| --- | :-: | --- |
| **`SEC-FILE-DOWNLOAD-TRAVERSAL`** | 🔴 **P0** | `/file/download` `@IgnoreAuth` + 未过滤 `..` ⇒ **匿名可读 classpath 内任意文件**（实测 `../pom.xml` 201/5576B ✗）；classpath 含 `application.yml` ⇒ **配置/凭据泄露风险** ⚠️ |
| **`SEC-FILE-UPLOAD-ANON`** | 🔴 **P1** | `/file/upload` 在白名单 ⇒ **匿名上传** + **无扩展名白名单** ✗ ⇒ 任意类型落地 ⚠️（若 CWD 被静态服务则风险升级 ✗）|
| `DEF-UPLOAD-PATH-DRIFT` | ⚠️ P2 | 上传实际落 CWD `./upload/`，与静态服务目录不一致 ⇒ 上传成功但 URL 404 ✗ |
| `DEF-STUDENT-CREATEDAT-BINDABLE` | ⚠️ P2 | `createdAt` 可被学生提交（未恢复）✗ |

> 全部**未修复** ✗ · **未定级（建议值）** ⚠️ · **R10 范围外** ✗

---

## 7. R10 拟定实现范围（**待你裁定** ✗）

若采纳**方案 A（粘贴头像 URL，不做上传）**：

| # | 文件 | 内容 |
| :-: | --- | --- |
| 1 | `api/profile.ts`（新）| `updateStudent({id,name,phone,email,avatarUrl})` ✓ |
| 2 | `views/MeView.vue`（改）| 显示 + **编辑表单**（4 字段 ✓）+ 保存 ✓ |
| 3 | `router/index.ts` | 不变 ✓（`/me` 已存在 ✓）|
| 4 | `views/MeView.vue` | 校验：手机/邮箱格式 ✓ · 511 冲突提示 ✓ · 400 空更新防护 ✓ |

**验收（拟）**：A 只提交 4 字段 ✓（静态断言：不含 `username/gender/idCardNo/password/createdAt` ✓）· B 保存成功 → 重新拉 `/student/session` 校验落库 ✓ · C **改他人 id 不被 UI 触发** ✓（前端恒用 `session.id` ✓）· D 511 冲突提示 ✓ · E Console 0 error ✓ · F 47/47 ✓ · G 单一提交 ✓

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | R10 写契约审计：`student/update` 可用（self-only ✓ + 服务端恢复 3 字段 ✓ + 空更新 400 ✓ + 511 冲突 ✓）；**`file/upload` 匿名 + 无类型白名单 + 落 CWD 导致 URL 404** 🔴；**`file/download` 路径穿越实测成立** 🔴；登记 4 项（P0/P1/P2×2）；**R10 上传方案待裁定**（建议 A 或 D，排除 B）✗ |
