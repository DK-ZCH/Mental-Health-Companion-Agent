# S2-C2 —— admin-only 删除授权 + `/detail` 死 API 删除（实现设计）

> 日期：2026-10-20 ｜ 基线：`c167a96`（S2-C1 已推送，LEAD=0）
> 状态：**实现设计 · 待批准** ✗ ｜ 本设计零代码、零 DB、零 commit
> 上游：`S2-C2-0-ADMIN-DELETE-DETAIL-AUDIT.md`（已按用户 4 项裁定修订）

---

## 1. 范围（已裁定）

| 项 | 数量 | 说明 |
| --- | :-: | --- |
| 删除能力加 `assertAdminOnly` | **11** | 9 × `/delete` + 2 × 级联 `deleteAssessmentQuestion` / `deleteAssessmentRecord` |
| `/detail/{id}` 删除 | **8** | 全仓库调用 0，`/info` 才是活端点 |
| 不改 | — | ❌ 修两个 404 · ❌ 前端 · ❌ `/info` · ❌ `/info` IDOR · ❌ DB · ❌ 测评答题/评分 |

---

## 2. 文件级改动清单（9 个 Controller）

### 2.1 守卫原语

**复用已有 `OwnershipGuard.assertAdminOnly(request)`**（L117，非 admin 一律 `deny()` → 403），**不新增原语**。

### 2.2 逐个 Controller 改动点

| # | 文件 | 新增 `import OwnershipGuard` | 加守卫的方法 | 删 detail 方法 |
| :-: | --- | :-: | --- | :-: |
| 1 | `AssessmentPaperController.java` | **需要**（现无）| `delete`（L174）| `detail`（L270）|
| 2 | `AssessmentQuestionController.java` | 已有 | `delete`（L226）· `deleteAssessmentQuestion`（L481）| `detail`（L322）|
| 3 | `KnowledgeArticleController.java` | **需要** | `delete`（L170）| `detail`（L257）|
| 4 | `NotificationController.java` | **需要** | `delete`（L170）| `detail`（L257）|
| 5 | `SysConfigController.java` | **需要** | `delete`（L107，`Long[]`）| `detail`（L64）|
| 6 | `SysDictItemController.java` | **需要** | `delete`（L194）| —（无 detail）|
| 7 | `AssessmentRecordController.java` | 已有 | `delete`（L198）· `deleteAssessmentRecord`（L395）| `detail`（L306）|
| 8 | `AssessmentAnswerController.java` | 已有 | `delete`（L199）| `detail`（L307）|
| 9 | `AssessmentWrongQuestionController.java` | 已有 | `delete`（L206）| `detail`（L296）|

> 需要新增 import 的 5 个：`AssessmentPaper` / `KnowledgeArticle` / `Notification` / `SysConfig` / `SysDictItem`。
> 已有 import 的 4 个：`AssessmentQuestion` / `AssessmentRecord` / `AssessmentAnswer` / `AssessmentWrongQuestion`。

### 2.3 改动形态（每处 delete）

```java
@RequestMapping("/delete")
public R delete(@RequestBody Integer[] ids, HttpServletRequest request){
    OwnershipGuard.assertAdminOnly(request);   // 非 admin → 403（fail-closed）
    // 原有删除逻辑不变
    xxxService.removeByIds(Arrays.asList(ids));
    return R.ok();
}
```

> `config/delete` 参数为 `Long[]`，其余为 `Integer[]`——签名各自保持。
> `request` 由 Spring 自动注入（`@RequestBody` 与 `HttpServletRequest` 并存无冲突）。

### 2.4 detail 方法删除

每个 `detail(@PathVariable("id") Long id, HttpServletRequest request){...}` 方法**整段删除**（含注解、方法体）。删除后：
- 不影响同文件 `/info/{id}`（独立方法）
- 不影响 `dictionaryConvert` 等共享私有逻辑（detail 与 info 各自独立实现）

---

## 3. 测试设计（4 角色 × 11 端点）

### 3.1 角色预期（对齐 S2-C1 已验证口径）

| 角色 | 预期 | 机制 |
| --- | --- | --- |
| anonymous（无 Token）| 拒绝（既有契约 `code=401`）| `AuthorizationInterceptor` 在进入 Controller 前拦截 |
| student | **403** | `assertAdminOnly` → `deny()` → `ForbiddenException` → `GlobalExceptionHandler` |
| counselor | **403** | 同上 |
| admin | **200**（原业务行为）| `assertAdminOnly` 放行，删除逻辑照旧 |

### 3.2 负向测试重点（新 P1 堵口证明）

对 **`deleteAssessmentQuestion`** 与 **`deleteAssessmentRecord`** 专门做：

| 场景 | 预期 | 证明 |
| --- | --- | --- |
| student 直调 `POST /assessmentQuestion/deleteAssessmentQuestion` | 403 | `SEC-QUESTION-CASCADE-DELETE-NO-GUARD` 被堵 |
| counselor 直调 `POST /assessmentRecord/deleteAssessmentRecord` | 403 | `SEC-RECORD-CASCADE-DELETE-NO-GUARD` 被堵 |
| admin 直调（探针）| 200 + 级联生效 | 原业务行为未破坏 |

### 3.3 测试数据探针与恢复（admin 200 场景）

| 端点类型 | 探针准备 | admin 删除后验证 | 恢复策略 |
| --- | --- | --- | --- |
| 物理删 `/delete`（知识/通知/字典/作答/错题）| 各插 1 探针行 | 200 + 行消失 | 无需（已删）|
| 软删 `/delete`（试卷）| 插 1 探针（`is_deleted=1`）| 200 + `is_deleted=2` | 物理删探针行 |
| 物理删 `/delete`（题目/记录，死端点）| 各插 1 探针 | 200 + 行消失 | 无需（已删）|
| **级联** `deleteAssessmentQuestion` | 探针题挂在真实试卷 + 记录删除前试卷 `total_score` | 200 + 题删 + 试卷分数回扣 | **恢复试卷 `total_score`**（+回扣值）|
| **级联** `deleteAssessmentRecord` | 探针记录 + 探针作答明细（`record_no` 关联）| 200 + 记录删 + 作答明细级联删 | 无需（级联已删，无残留）|

> 探针全部用 `S2C2-*` 前缀内容；测试后逐表核对行数/max(id)/`AUTO_INCREMENT`，`AUTO_INCREMENT` 漂移如实登记（不回退）。

### 3.4 回归

| 项 | 预期 |
| --- | :-: |
| `mvn -B test` | 47/47（S2-C2 不改读路径、不改 `/info`，回归应无影响）|
| `vue-tsc --noEmit` | 0（前端 0 改动）|
| 后端编译 | BUILD SUCCESS |

---

## 4. 边界（本批不做）

```
❌ 修两个 404（deleteExamquestion / deleteExamrecord）
❌ 前端改动（含 admin 页面 URL）
❌ /info 改动 · /info IDOR
❌ DB 结构 · 种子数据
❌ 测评答题/评分逻辑
❌ 教师侧写授权（批 4）
```

---

## 5. 待批准

| # | 项 | 状态 |
| :-: | --- | --- |
| 1 | 9 文件改动范围（§2）| 待批准 |
| 2 | 11 delete + 8 detail 具体形态（§2.3 / §2.4）| 待批准 |
| 3 | 测试探针 + 恢复策略（§3.3，含级联端点探针副作用）| 待批准 |
| 4 | 是否允许真实写测试（探针插入/删除 + 试卷 `total_score` 回扣后恢复）| 待批准 |

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | S2-C2 实现设计：9 文件 · 11 delete 加 `assertAdminOnly`（复用原语）+ 8 detail 删除；5 文件需新增 import；4 角色负向测试（级联端点专门堵口）；探针 + 恢复策略；**零代码零 DB 零 commit** ✓ |
