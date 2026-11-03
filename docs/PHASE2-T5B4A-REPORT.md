# Phase 2 / Step 5 第二阶段 批 4-A 交付报告：读范围收敛（`DataScope` fail-closed）

> **范围**（按设计文档 §7 批 4 拆分）：**② `DataScope`（fail-closed）**
> **状态**：✅ **已完成**（33/33 全绿 + 反向对照 + 数据库回基线）
> **前置**：[`PHASE2-T5B-DESIGN.md`](./PHASE2-T5B-DESIGN.md) §7「批 4」、[`PHASE2-T5B3B-REPORT.md`](./PHASE2-T5B3B-REPORT.md)
> **本批拆分**：4-A = 读范围收敛（**本报告**）；**4-B = ④⑤ 写原语统一**（下一步，独立提交）

---

## 1. 交付物

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | `com/security/DataScope.java` | **新增**：读范围收敛原语（fail-closed） |
| 2 | `com/security/OwnershipGuard.java` | `deny()` 改为**同包可见**（供 `DataScope` 复用统一拒绝形态） |
| 3–14 | **12 个 Controller 的 `/page`** | 用 `DataScope.apply(...)` 替换手写角色链 |
| 15 | `IdentityAuthorizationRegressionTest` | **+3 用例**（17–19，矩阵 3/11/12） |

**零新增依赖**。

---

## 2. 问题：fail-open 的读范围分支（散落 12 处）

改造前，**每个** `/page` 都手写同一段：

```java
String role = String.valueOf(request.getSession().getAttribute("role"));
if (false)
    return R.error(511, "永不会进入");
else if ("学生".equals(role))
    params.put("studentId", request.getSession().getAttribute("userId"));
else if ("心理老师".equals(role))
    params.put("counselorId", request.getSession().getAttribute("userId"));
// ← 没有 else：未登记角色落到这里 ↓
```

**缺口（fail-open）**：`role` 为 `null` 或任何未登记取值时，**跳过全部分支 → 不加任何过滤 → 返回全量**。

### 2.1 反向对照实测到的真实泄漏（重要）

把 fail-closed 临时禁用（恢复旧行为）后，用「未知角色」token 请求 `/xinlilaoshiLiuyan/page`：

```text
HTTP 200 + {"code":0,"data":{"total":6, ... }}          ← 全表 6 条，跨学生
行内包含：studentPhone / studentEmail / studentAvatarUrl
          counselorPhone / counselorEmail / counselorResume ...
```

**即「未知角色 = 无过滤 = 全表 + 关联用户 PII」** —— 这不是理论风险，而是**实测复现**。

> ⚠️ 与 D2 区分：D2 是「**匿名** `/list` 可枚举」；本项是「**已登录但角色未登记**的 `/page` 返回全量」。
> 两者根因不同（D2 = `@IgnoreAuth`；本项 = 角色分支缺 else），**不要互相替代**。

---

## 3. 实现

```java
public static void apply(HttpServletRequest request, Map<String, Object> params,
                         String studentField, String counselorField)
```

| 角色 | 收敛方式 | 与改造前对比 |
| --- | --- | --- |
| `管理员` | **不加过滤** | 不变（B 类：管理端本就查看全量） |
| `学生` | `studentId = 当前用户`（覆盖客户端同名参数） | 不变 |
| `心理老师` | `counselorId = 当前用户` | 不变 |
| **其他 / null** | **拒绝（HTTP 403）** | 🔴 **由「返回全量」改为 fail-closed** |

拒绝复用批 1 的统一形态（`ForbiddenException` → `HTTP 403 + code=403`），因此**不新增契约**。

### 3.1 刻意未扩张（守边界）

| 项 | 处置 |
| --- | --- |
| 教师侧**可见范围**业务规则（老师能看到所服务学生的哪些数据） | **原样保留**既有口径 —— 该规则在代码与数据中无依据（批 1 登记项），不凭空发明 |
| 另外 **3 个** `/page` 端点（本就无任何角色分支，对所有人全量） | **不动** —— 为其新增收敛属**行为扩张**，需另行取证 |
| 免鉴权 `/list` 匿名枚举（D2） | **不动** —— 属 Step 3 待决策 D2 |
| 写路径 `/save` 等（④⑤ 写原语统一） | **4-B**，独立提交 |

---

## 4. 验收（设计文档矩阵 3/11/12）

```text
mvn -B clean test → BUILD SUCCESS
IdentityAuthorizationRegressionTest   19 项 ✓ （批1:8 + 批2:3 + 批3B:5 + 批4A:3）
Phase2CoreRegressionTest              14 项 ✓
合计                                  33/33  ✓
```

| 矩阵 | 场景 | 用例 | 结果 |
| --- | --- | --- | --- |
| **3** | 学生伪造 `studentId=2` 查询 page | 17 | ✅ 仍 `total=2`，且**每一行** `studentId` 都等于当前学生（范围未被放大） |
| **11** | 心理老师查询 page | 18 | ✅ 每一行 `counselorId` 都等于老师自己（`total>0` 防空跑） |
| **12** | **未知角色**查询 page | 19 | ✅ **403 + code=403**（不再默认全量） |

### 4.1 数据库状态检查

```text
测试前：m1:s2,m2:s1,m3:s3,m4:s2,m5:s3,m6:s1 | auth_token 3 行
测试后：m1:s2,m2:s1,m3:s3,m4:s2,m5:s3,m6:s1 | auth_token 3 行   → ✅ 完全一致
```

### 4.2 反向对照（精确隔离）

| 用例 | fail-closed 禁用后 |
| --- | --- |
| 19 未知角色 page | **失败**（`expected: 403 but was: 200`，并返回 `total=6` 全表）✓ |
| 17 学生 page 范围 | **仍通过** ✓ |
| 18 老师 page 范围 | **仍通过** ✓ |

→ 证明「未知角色 fail-closed」与「学生/老师范围收敛」是**相互独立**的两条规则，断言各自精准 ✓

### 4.3 收敛完整性校验

```text
DataScope.apply 调用点:                        12 处   ✓
残留 params.put("studentId"/"counselorId") 旧分支: 0 处   ✓
```

---

## 5. 观察项

| # | 项 | 说明 |
| --- | --- | --- |
| 1 | 批 4-A 代码**尚未推送** | 按规则 §9.1：push 需明确授权 |
| 2 | **失败行为的可探测性** | 未知角色现在返回 403；而「学生查询」返回 200 —— 该差异**不泄漏数据**（fail-closed），但与「记录不存在也 403」的既有约定一致 ✓ |
| 3 | 另外 3 个无角色分支的 `/page` | 全量对所有人开放（含管理员账号等）→ 属**行为扩张**，需单独取证 |
| 4 | `/save` 等写路径的 unknown-role fail-open | 由 **4-B（④⑤ 写原语统一）** 处理 |
| 5 | 教师侧写授权与 `counselorId` 维度 | 需业务规则证据（批 3B 已登记） |

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-08 | 批 4-A 交付：新增 `DataScope`（fail-closed 读范围）+ 12 处 `/page` 收敛；**33/33 全绿**；矩阵 3/11/12 通过；反向对照实测复现旧行为下「未知角色拿到全表 + PII」；数据库前后一致 |
