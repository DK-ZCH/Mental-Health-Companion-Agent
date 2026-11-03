# S2-F-1-1 · 匿名 `resetPass` 安全下线设计

> **性质**：实现设计（📐 待批准 · **不写代码**）
> **基线**：`HEAD = origin = d27c10a` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-F-1-0-ANONYMOUS-RESETPASS-AUDIT.md`](./S2-F-1-0-ANONYMOUS-RESETPASS-AUDIT.md)（方案 A 已批准）

---

## 0. 目标（单一）

**删除两个 `@IgnoreAuth` 匿名密码重置端点**，对齐 D7 先例（`/adminUser/resetPass`）。
**不做**：不改 `resetPassword`、不改登录/注册、不碰密码字段与哈希、不改前端、不改 DB。

---

## 1. 删除范围（精确）

| # | 文件 | 方法 | 行范围（含 Javadoc）| `@IgnoreAuth` |
| :-: | --- | --- | :-: | :-: |
| 1 | `StudentController.java` | `resetPass(String username, HttpServletRequest)` | **L376–L393** | ✅ 是 |
| 2 | `CounselorController.java` | `resetPass(String username, HttpServletRequest)` | **L353–L370** | ✅ 是 |

### 1.1 当前代码（将被删除）

```java
// StudentController L376-393
    /**
     * 忘记密码
     */
    @IgnoreAuth
    @RequestMapping(value = "/resetPass")
    public R resetPass(String username, HttpServletRequest request) {
        StudentEntity yonghu = yonghuService.getOne(new QueryWrapper<StudentEntity>().eq("username", username));
        if(yonghu!=null){
            yonghu.setPassword("123456");
            boolean b = yonghuService.updateById(yonghu);
            if(!b){
               return R.error();
            }
        }else{
           return R.error("账号不存在");
        }
        return R.ok();
    }
```

```java
// CounselorController L353-370（同构，实体为 CounselorEntity）
    /**
     * 忘记密码
     */
    @IgnoreAuth
    @RequestMapping(value = "/resetPass")
    public R resetPass(String username, HttpServletRequest request) { ... }
```

---

## 2. 保留下线依据注释（D7 风格）

删除方法后，在原位置保留：

```java
// ── S2-F-1-1：已【下线】匿名密码重置端点 {@code /student/resetPass} ──
// 下线依据（S2-F-1-0 调用面核查，全部为零）：
//   Java：仅本方法定义，无内部调用；老前端 0；Vue3 0；管理端 src/dist 0；无脚本/配置引用
//   系统无任何找回密码身份验证基础设施（邮件 0 / 短信 0 / reset token 0 / 验证码从不校验）
// 风险定性：@IgnoreAuth + 仅凭 username 即可把密码重置为 123456 ⇒ 匿名账户接管（P1）。
// 影响面：管理端「重置密码」按钮走 /student/resetPassword（admin-only），与本端点无关；
//        忘记密码降级为管理员介入（DEF-NO-SELF-SERVICE-PASSWORD-RECOVERY 已登记）。
// 回归：见 IdentityAuthorizationRegressionTest 用例 20 —— 断言该路径返回 404。
```

**Counselor 版**同构，把 `student/resetPass` → `counselor/resetPass`、`/student/resetPassword` → `/counselor/resetPassword`。

> 目的：明确「有意废弃」而非「遗漏」，防止后续维护者误恢复（与 D7 一致）。

---

## 3. 测试契约改动（**必要组成**）

### 3.1 现状（会失败）

```java
// IdentityAuthorizationRegressionTest L448-453 —— 用例 20 的「对照」段
MvcResult contrast = perform("/student/resetPass?username=not-exist-user-xyz", null);
assertThat(contrast.getResponse().getStatus())
        .as("对照：/student/resetPass 属「忘记密码」流程，按 D3 保持存在")
        .isNotEqualTo(404);          // ← 下线后必然 FAIL
```

### 3.2 改后（用例 20 完整形态）

```java
@Test
@DisplayName("20. D7+S2-F-1-1：匿名密码重置端点全部下线（admin/student/counselor → 404，且不产生密码变更）")
void anonymousResetEndpointsAreRemoved() throws Exception {
    String before = usersPassword("admin");
    assertThat(before).as("前置：admin 账号应存在").isNotNull();

    // ① D7（既有）：admin
    MvcResult admin = perform("/adminUser/resetPass?username=admin", null);
    assertThat(admin.getResponse().getStatus())
            .as("adminUser/resetPass 已下线，应为 404").isEqualTo(404);
    assertThat(usersPassword("admin"))
            .as("请求不得产生任何密码变更").isEqualTo(before);

    // ② S2-F-1-1（新增）：student
    MvcResult student = perform("/student/resetPass?username=not-exist-user-xyz", null);
    assertThat(student.getResponse().getStatus())
            .as("student/resetPass 已下线（S2-F-1-1），应为 404").isEqualTo(404);

    // ③ S2-F-1-1（新增）：counselor
    MvcResult counselor = perform("/counselor/resetPass?username=not-exist-user-xyz", null);
    assertThat(counselor.getResponse().getStatus())
            .as("counselor/resetPass 已下线（S2-F-1-1），应为 404").isEqualTo(404);

    // ④ 对照锚点更换：用仍存在的公开端点证明「不是全局 404」
    MvcResult anchor = perform("/counselor/list", null);
    assertThat(anchor.getResponse().getStatus())
            .as("对照：/counselor/list 仍存在（证明 404 是该路径被删，而非全局 404）")
            .isNotEqualTo(404);

    assertThat(usersPassword("admin"))
            .as("所有请求均不得改动任何密码").isEqualTo(before);
}
```

### 3.3 契约变更清单

| 项 | 原 | 新 |
| --- | --- | --- |
| `/adminUser/resetPass` | `isEqualTo(404)` | **不变** |
| `/student/resetPass` | `isNotEqualTo(404)` | **`isEqualTo(404)`** |
| `/counselor/resetPass` | 未被断言 | **新增 `isEqualTo(404)`** |
| 「非全局 404」锚点 | `/student/resetPass` | **`/counselor/list`**（`@IgnoreAuth` 公开 · 回归已证 200）|
| 用例数 | 47 | **47（不增不删，仅改用例内容）** |

> **不新增 `@Test`**，只在用例 20 内扩展断言 ⇒ 总数保持 47。

---

## 4. 文件清单

| # | 文件 | 动作 | 预计行数 |
| :-: | --- | --- | :-: |
| 1 | `src/main/java/com/controller/StudentController.java` | 删 L376–393（18 行）+ 保留下线注释（约 9 行）| ≈ **−9** |
| 2 | `src/main/java/com/controller/CounselorController.java` | 删 L353–370（18 行）+ 保留下线注释（约 9 行）| ≈ **−9** |
| 3 | `src/test/java/com/regression/IdentityAuthorizationRegressionTest.java` | 用例 20 改写（断言 + 锚点）| ≈ **+12 / −6** |
| 4 | ⚠️ `src/main/java/com/controller/AdminUserController.java`（**待裁定**）| L81 过时注释校准（1 行）| **+1 / −1** |

**核心改动 = 3 个文件**（选不校准 AdminUser 注释）/ **4 个文件**（选校准）。

---

## 5. HTTP 验证矩阵（部署后实测）

| # | 用例 | 期望 | 说明 |
| :-: | --- | --- | --- |
| 1 | `POST /student/resetPass?username=a1`（anonymous）| **404** | 端点已删除 |
| 2 | `POST /counselor/resetPass?username=a1`（anonymous）| **404** | 端点已删除 |
| 3 | `POST /adminUser/resetPass?username=admin`（anonymous）| **404** | D7 既有，不回归 |
| 4 | `GET /student/resetPassword?id=3`（anonymous）| `code=401` | 拦截器（需登录）|
| 5 | `GET /student/resetPassword?id=3`（**admin**）| **200** | 管理端能力保持 |
| 6 | `GET /student/resetPassword?id=3`（**student**）| **403** | admin-only 保持 |
| 7 | `GET /counselor/resetPassword?id=3`（**admin**）| **200** | 同上 |
| 8 | `GET /counselor/resetPassword?id=3`（**counselor**）| **403** | 同上 |
| 9 | **数据核对**：`student`/`counselor` 密码默认值计数 | 与执行前**完全一致** | 证明 404 请求零写入 |

> 用例 9 是**关键**：证明 404 不是「先改密码再 404」。

---

## 6. 全仓库残留搜索（实施后必做）

| 检索 | 期望结果 |
| --- | --- |
| `student/resetPass` 在 `src/main/java` | **0**（仅注释中出现 `{@code /student/resetPass}` 属说明文字）|
| `counselor/resetPass` 在 `src/main/java` | **0** |
| `resetPass` 在 `src/main/resources`（前端）| **0** |
| `resetPass` 在 `frontend/src` | **0** |
| `resetPass` 在 `src/main/java` | **仅** 2 处下线注释 + `AdminUserController` 既有 D7 注释 |
| `resetPass` 在 `src/test` | **仅** 用例 20 的断言路径字符串 |

**允许存在**：历史审计文档（`docs/`，未入 Git）、测试断言字符串、下线依据注释。

---

## 7. 明确不做（边界锁死）

```text
❌ 不修改 /student/resetPassword、/counselor/resetPassword（admin-only 保持不变）
❌ 不修改 login（3 处明文比较不做任何改动）
❌ 不修改 register（DEF-REGISTER-PASSWORD-DROPPED 属 S2-F-2）
❌ 不修改 password 字段 / Entity / View / @JsonIgnore
❌ 不引入 bcrypt / Argon2 / PBKDF2（属 S2-F-3/F-4）
❌ 不修改前端（老前端 / Vue3 / Admin）
❌ 不修改 DB schema / 不重置任何密码
❌ 不新增验证码 / 邮件 / 短信 / reset token 基础设施
❌ 不重构 Controller、不动其他认证逻辑
```

---

## 8. 风险与回滚

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| 误删相邻方法 | 🟠 | 按精确行范围 + 保留 Javadoc 边界删除；`mvn compile` 校验 |
| 47/47 失败 | 🟠 **预期内** | 用例 20 必须同步改（§3），改后应恢复 47/47 |
| 忘记密码能力"消失" | 🟡 **已裁定接受** | 降级为管理员 `resetPassword`（admin-only，已存在）|
| 有人依赖匿名找回 | 🟢 低 | S2-F-1-0 已证调用面全零 |
| 回滚 | — | 单提交 `git revert`，无 DB/schema 变更 |

---

## 9. 待你批准的设计项

| # | 项 | 本设计取值 |
| :-: | --- | --- |
| 1 | 删除方式 | **删除方法体 + 保留下线依据注释**（D7 风格）|
| 2 | 是否删除 `@IgnoreAuth` 注解所在整段 | ✅ 是（整段 L376–393 / L353–370）|
| 3 | 测试用例 20 | **扩展断言**（admin + student + counselor 三端点 404），**不新增 @Test** |
| 4 | 对照锚点 | **`/counselor/list`**（`@IgnoreAuth` 公开，回归已证 200）|
| 5 | 用例数量 | **47 不变** |
| 6 | **`AdminUserController` L81 过时注释** | ⚠️ **待裁定**：是否允许同步校准 1 行（现文：「用户/老师端…走各自的 `/resetPass`（按 D3 不动）」已过时）|
| 7 | 文件数 | **3**（不校准）/ **4**（校准）|

---

## 10. 实施纪律

```text
设计批准 → 改代码 + 改测试 → mvn test（47/47）
   → 停旧 jar → package → 启新 jar → 确认 PID/jar 时间戳
   → HTTP 9 项矩阵（含数据零写入核对）→ 残留搜索
   → staged review → 你审核 → commit → 你审核 → push
```

---

## 11. 状态

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = d27c10a` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓（设计阶段）|
| 文档 | 新增本文件 |

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-F-1-1 设计：删除 2 个 `resetPass`（精确行 L376–393 / L353–370）+ 保留下线注释；测试用例 20 扩展三端点 404 + 锚点换为 `/counselor/list`（47 不变）；3 文件（可选 4）；HTTP 9 项含数据零写入；残留搜索清单；**待裁定 #6（AdminUser 过时注释是否校准）**；零改动 |
