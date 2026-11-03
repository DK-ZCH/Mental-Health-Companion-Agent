# `/student/detail/{id}` 调用方审计与接口契约

> **性质**：只读审计（📐 **零代码改动 · 待用户确认**）
> **基线**：`HEAD = origin = e15f104` · LEAD 0 · worktree CLEAN
> **上游**：`PHASE4-STUDENT-READ-AUDIT.md` · 用户 2026-10-25 裁定「只做第 1 项：调用方审计」
> **本批定位**：确认**谁在调用**、接口契约是什么、修复会影响谁 —— **不改代码**

---

## 一、结论

| 项 | 结论 |
| --- | --- |
| 仓库内可识别的调用方 | **0**（新 Vue · 旧 Admin 源码 · 旧 Admin dist · 后端内部 · 测试，全部无）|
| 接口是否可安全加授权 | ✅ **与 `/info/{id}` 实现几乎完全相同，仅少一行** |
| 🔴 **必须由用户确认的点** | **直接照抄 `/info` 的授权行，咨询师仍然会全量可读**（详见 §五）|

> ⚠️ **表述纪律（按你的要求）**：
> 只能说「**在仓库可识别范围内未发现调用方**」，
> **不得**写成「没有调用方」—— 见 §六 审计边界。

---

## 二、调用方审计矩阵

### 2.1 搜索范围与结果

| # | 来源 | 方法 | 结果 |
| :-: | --- | --- | :-: |
| 1 | **新 Vue 前端**（`frontend/src`）| 全仓搜 `student/detail` | ❌ **0 命中** |
| 2 | **旧 Admin 源码**（`src/main/resources/admin/admin/src`，262 文件）| 逐文件搜 `student/detail` / `yonghu/detail` | ❌ **0 命中** |
| 3 | **旧 Admin 编译产物**（`dist`，12 文件）| 原始读取 + 正则统计 | ❌ `student`=0 · `yonghu/detail`=0 |
| 4 | **后端 Java 内部调用** | 搜 `student/detail` | ❌ **0 命中** |
| 5 | **测试代码** | 搜 `student/detail` | ❌ **0 命中** |

### 2.2 旧 Admin `dist` 的一个不一致（重要）

```text
dist/app.c0510d7c.js  ：student=0 次 · 学生=145 次 · yonghu=506 次 · yonghu/detail=0 · yonghu/info=7
src/views/modules/yonghu/add-or-update.vue:253 ：url: `student/info/${id}`
```

**dist 里是 `yonghu/info`，源码里已是 `student/info`** ⇒

- `dist` 是**旧版本编译产物**，与 `src` 不同步；
- 后端已**不存在** `/yonghu` 映射（仅有 `/student`）⇒ dist 里的 `yonghu/*` 调用**本来就会 404**；
- ⇒ **旧 Admin 的学生模块在 `dist` 版本下本就部分失效**（既有状态，非本批引入）。

> 这不改变本批结论（dist 与 src 都不含 `detail` 调用），但说明
> **「旧 Admin 是否在跑 dist」这一事实本身也无法从仓库确认**。

### 2.3 旧 Admin 学生模块的完整调用清单（源码）

| 端点 | 调用位置 |
| --- | --- |
| `student/page` | `list.vue:656` |
| `student/info/{id}` | `add-or-update.vue:253` |
| `student/save` / `student/update` | `add-or-update.vue:268` |
| `student/delete` | `list.vue:716` |
| `student/resetPassword` | `list.vue:738` |
| `student/batchInsert` | `list.vue:751` |
| **`student/detail/{id}`** | **❌ 未出现** |

---

## 三、接口契约

### 3.1 定义

```java
@RequestMapping("/detail/{id}")                 // ⚠️ 未限定 method ⇒ GET/POST/DELETE 均可命中
public R detail(@PathVariable("id") Long id, HttpServletRequest request){
    StudentEntity yonghu = yonghuService.getById(id);
    if(yonghu !=null){
        StudentView view = new StudentView();
        BeanUtils.copyProperties( yonghu , view );
        dictionaryService.dictionaryConvert(view, request);
        return R.ok().put("data", view);
    }else {
        return R.error(511,"查不到数据");
    }
}
```

| 维度 | 契约 |
| --- | --- |
| 路径 | `/student/detail/{id}` |
| 方法 | **未限定**（`@RequestMapping` 无 `method`）—— GET/POST 等均可 |
| 认证 | ✅ 需登录（`AuthorizationInterceptor` 对 `/**` 生效；未登录 → `HTTP 200 + code=401`）|
| **授权** | ❌ **无任何校验** |
| 参数 | `id`（`Long`，路径变量）|
| 成功响应 | `{"code":0,"data":{...StudentView}}` — HTTP 200 |
| 不存在 | `{"code":511,"msg":"查不到数据"}` — **HTTP 200**（业务错误码）|

### 3.2 返回字段（实测）

```text
id · username · name · phone · avatarUrl · gender · email · createdAt · genderLabel
```

| 字段 | 是否返回 |
| --- | :-: |
| `password` | ❌（`@JsonIgnore`）|
| `idCardNo` | ❌（`@JsonIgnore`）|
| **`phone`** | **🔴 返回** |
| **`email`** | **🔴 返回** |

### 3.3 与 `/info/{id}` 的逐行对比

```text
/info/{id}                              /detail/{id}
─────────────────────────────────────   ─────────────────────────────────────
getById(id)                             getById(id)
✅ assertOwnership(...)                 ❌ 【缺失这一行】
if (yonghu != null) { ... }             if (yonghu != null) { ... }
  BeanUtils.copyProperties → View          BeanUtils.copyProperties → View
  dictionaryConvert                        dictionaryConvert
  return R.ok().put("data", view)          return R.ok().put("data", view)
else return R.error(511, "查不到数据")     else return R.error(511, "查不到数据")
```

⇒ **两个方法除授权行外完全相同** —— 说明 `/detail` 是 `/info` 的历史副本，
在 ③/Step 5 为 `/info` 补授权时**漏掉了它**。

---

## 四、`OwnershipGuard.assertOwnership` 对各角色的实际行为（关键）

若按方案 A 直接复用，需要先明确它会**放行谁**：

```java
public static void assertOwnership(request, target, ownerStudentId, ownerCounselorId)
```

| 角色 | 判定 | 结果 |
| --- | --- | --- |
| 记录不存在（`target == null`）| `deny()` | **403** |
| 管理员 | `return`（显式放行）| ✅ 放行 |
| 学生 | `currentUserId == ownerStudentId` | 本人 ✅ / 他人 **403** |
| **心理老师** | `ownerCounselorId == null` → **`return`（放行）** | 🔴 **放行** |
| 未知 / null 角色 | `deny()` | **403** |

> `/info` 传入的 `ownerCounselorId` 是 `null` ⇒ **心理老师在 `/info` 也是放行的**。

---

## 五、🔴 必须由你确认的一点

### 5.1 事实

```text
方案 A = 照搬 /info 的授权行
        ↓
学生读他人 → 403            ✅ IDOR 被修复
管理员     → 放行            ✅ 与现状一致
心理老师   → 【仍然放行】    ⚠️ 与 /info 现状一致，但业务规则未裁定
未登录     → 401             ✅ 既有契约
```

### 5.2 这意味着什么

**本批只修「学生横向越权」，不修「咨询师可读全体学生资料」**。

理由：

| 理由 | 说明 |
| --- | --- |
| 你的裁定 | 第 2 项（咨询师范围）= **D，继续审计**，明确「三项不捆绑」|
| 最小改动 | 与 `/info` 保持一致 ⇒ 不引入新的授权语义 |
| 不擅自缩权 | 若本批顺手把咨询师也禁掉，等于**未经裁定就改变了咨询师的既有能力** |
| 可解释 | 修复后 `/detail` 与 `/info` 行为**完全一致**，无「两个入口不一样」的怪象 |

### 5.3 若你希望本批同时限制咨询师

那就需要**同时裁定第 2 项**（`student` 行级规则），但这与「三项不捆绑」冲突。

**我的建议**：本批只修学生 IDOR，并在报告中**显式登记**：

```text
已知残留（本批不修）：
  · 心理老师仍可通过 /detail/{id} 与 /info/{id} 读取任意学生资料（含 phone/email）
  · 该行为与 /info 现状一致，属「咨询师行级规则未裁定」的既有状态
  · 待第 2 项（字段分类 + 行级规则）裁定后统一处理
```

---

## 六、审计边界（明确未覆盖项）

| 项 | 状态 | 说明 |
| --- | :-: | --- |
| 新 Vue 前端 | ✅ 已覆盖 | 全仓搜 |
| 旧 Admin 源码 | ✅ 已覆盖 | 262 文件 |
| 旧 Admin `dist` | ✅ 已覆盖 | 原始读取 + 正则 |
| 后端内部调用 | ✅ 已覆盖 | 无 |
| 测试代码 | ✅ 已覆盖 | 无 |
| **外部第三方客户端 / 移动端 / 小程序** | ❌ **未覆盖** | 仓库内不存在，无法确认是否有人在用 |
| **已部署但不在仓库的旧版本** | ❌ **未覆盖** | 无法确认线上是否跑着更旧的包 |
| **人工脚本 / Postman 集合 / 抓包工具** | ❌ **未覆盖** | 不可枚举 |
| **旧 Admin `dist` 与 `src` 的同步状态** | ⚠️ **已确认不同步** | dist 含 `yonghu/info`×7，src 已是 `student/info` |

> **结论措辞**：本批证明的是
> **「在仓库可识别的 5 类来源中未发现调用方」**，
> 而非「系统内没有调用方」。

---

## 七、修复建议（待批准后执行）

### 7.1 方案 A（推荐）

```java
StudentEntity yonghu = yonghuService.getById(id);
// 与 /info 保持一致：学生仅可读本人；管理员放行；
// ⚠️ 咨询师仍放行（ownerCounselorId=null），属第 2 项待裁定范围
OwnershipGuard.assertOwnership(request, yonghu, id.intValue(), null);
if(yonghu !=null){ ... }
```

**保留端点**（按你的要求，不因未找到调用方就删除）。

### 7.2 测试清单（按你的要求 6 项 + 补充）

| # | 场景 | 期望 |
| :-: | --- | :-: |
| T1 | 学生读**本人** | 200 + code=0 |
| T2 | **学生读他人** | **403** |
| T3 | 咨询师读学生 | 🟡 **200**（与 `/info` 一致，属待裁定）|
| T4 | 管理员读学生 | 200 |
| T5 | 未登录 | **200 + code=401**（既有契约，不得改）|
| T6 | **不存在的 id** | **403**（`target==null → deny`，与 `/info` 一致；**不是** 511）|
| T7 | `/info` 与 `/detail` 行为一致性 | 同一身份对同一 id，两者状态码相同 |

> **T6 需要特别注意**：`assertOwnership` 在 `target == null` 时直接 `deny()`（403），
> 所以修复后「不存在的 id」会从 `200 + code=511` 变成 **`403`**。
> **这是行为变更**，必须显式确认是否接受（与 `/info` 当前行为一致）。

### 7.3 状态码契约

```text
401 → 未认证（拦截器，HTTP 200 + code=401）
403 → 已认证但无权（ForbiddenException，HTTP 403 + code=403）
200 + code=511 → 记录不存在【修复前】
403           → 记录不存在【修复后，与 /info 一致】
```

---

## 八、待你确认（3 项）

| # | 问题 | 选项 | 建议 |
| :-: | --- | --- | --- |
| **1** | 是否接受「咨询师本批仍可读」 | A. 接受（与 `/info` 一致，留待第 2 项）<br>B. 本批一并限制（需同时裁定第 2 项）| **A** |
| **2** | 是否接受「不存在的 id → 403（原 511）」 | A. 接受（与 `/info` 一致）<br>B. 保留 511（需自定义判定，偏离 `/info`）| **A** |
| **3** | 是否保留端点 | A. 保留（按你的要求）<br>B. 加 `@Deprecated` 注释标记 | **A + 加注释** |

---

## 九、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `e15f104` / `e15f104` · LEAD 0 |
| worktree | **CLEAN** |
| 代码 / DB / 前端 / pom | **0 改动** |
| 实测方式 | 文件读取 + 正则统计 + 代码阅读，**无 HTTP 写入** |
| 文档 | 新增本文件 |

**未写任何代码，等你确认 §八 的 3 项。**
