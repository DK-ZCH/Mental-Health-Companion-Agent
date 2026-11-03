# `/student/detail/{id}` 学生跨用户 IDOR 修复 —— 实现报告

> **性质**：实现（✅ **已实现 · 已部署验证 · 已 commit · 已 push（`d5b75b7`）**）
> **基线**：`HEAD = origin = d5b75b7` · LEAD 0 · worktree CLEAN · 改动 **2 文件（+233 / −0）**
> **上游**：`PHASE4-STUDENT-DETAIL-CALLER-AUDIT.md`（用户 2026-10-25 批准 A/A/A）
> **本批边界**：**只修学生横向越权** —— 不重新定义咨询师数据范围

---

## 一、结论

| 项 | 结果 |
| --- | :-: |
| 先红（修复前必须失败）| ✅ **7 run / 3 failures**（T2/T6/T7，全部 `expected: 403`）|
| 后绿（修复后）| ✅ **7/7** |
| 全量回归 | ✅ **131/131**（124 既有 + 7 新增）|
| 部署 | ✅ PID `37552` · jar `19:14:03` · 7s 就绪 |
| HTTP 实测 | ✅ **7 项全部符合** |
| 主代码改动量 | **+1 行代码**（+9 行注释）|
| `OwnershipGuard` | ✅ **0 改动** |
| 六个 JOIN View / Mapper / Service / Dao / DB | ✅ **0 改动** |

---

## 二、改动清单

| # | 文件 | 类型 | 内容 |
| :-: | --- | :-: | --- |
| 1 | `com/controller/StudentController.java` | 改 | `/detail/{id}` 增加 **1 行** `OwnershipGuard.assertOwnership(request, yonghu, id.intValue(), null)` + 9 行边界注释 |
| 2 | `test/.../StudentDetailIdorRegressionTest.java` | **新增** | 7 例（T1–T7）|

```text
2 files changed, 204 insertions(+), 0 deletions(-)
```

### 2.1 主代码 diff（完整）

```java
  public R detail(@PathVariable("id") Long id, HttpServletRequest request){
      logger.debug("detail方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
      StudentEntity yonghu = yonghuService.getById(id);
+     // 学生跨用户 IDOR 修复（P1）：授权行为与 /info/{id} 完全一致，最小改动。
+     //   · 学生     → 仅本人（本表 id 即学生 id）；读他人 → 403
+     //   · 管理员   → 放行
+     //   · 心理老师 → 本批【仍放行】（实体无 counselor 归属字段，ownerCounselorId=null）
+     //                ⚠️ 这不代表「咨询师全量读取学生资料」已被认定合理 ——
+     //                   属遗留风险，待 student 行级规则裁定后统一处理。
+     //   · 记录不存在 → 403（与 /info 一致；修复前为 200 + code=511）
+     // ⚠️ 刻意【不】修改 OwnershipGuard 的全局逻辑，避免扩大影响面。
+     OwnershipGuard.assertOwnership(request, yonghu, id.intValue(), null);
          if(yonghu !=null){
```

**唯一的行为改变就是这一行** —— 其余全是注释。

---

## 三、先红后绿

### 3.1 修复前（红）

```text
mvn test -Dtest=StudentDetailIdorRegressionTest

[ERROR] Tests run: 7, Failures: 3
        expected: 403   ← T2 学生读他人（当前 200）
        expected: 403   ← T6 不存在的 id（当前 200 + code=511）
        expected: 403   ← T7 与 /info 一致性（当前 200 vs 403）
BUILD FAILURE
```

### 3.2 修复后（绿）

```text
[INFO] Tests run: 7, Failures: 0, Errors: 0
BUILD SUCCESS
```

### 3.3 全量：131/131

| 测试类 | 数量 |
| --- | :-: |
| **`/student/detail IDOR 回归`** | **7** ← 新增 |
| ③ Counselor Assessment Scope | 7 |
| ③ CounselorAssessmentScope | 9 |
| ③ OwnershipGuard 关系回调 | 10 |
| Step 12 AgentIdentity | 11 |
| Step 11 AssessmentTools | 14 |
| Step 11 AgentController | 10 |
| IdentityAuthorization | 30 |
| Phase2Core | 17 |
| PasswordHasher | 16 |
| **合计** | **131** |

---

## 四、🔴 真实 HTTP 验证（不靠代码存在性判定）

部署后实测（PID `37552`）：

| # | 场景 | 实测结果 | 判定 |
| :-: | --- | --- | :-: |
| T1 | student a1 → `/detail/1`（本人）| **200** · `code=0` · `data.id=1` | ✅ |
| **T2** | **student a1 → `/detail/2`** | **403** · `{"msg":"无权访问","code":403}` | ✅ **修复** |
| **T2** | **student a1 → `/detail/3`** | **403** · `{"msg":"无权访问","code":403}` | ✅ **修复** |
| T6 | student a1 → `/detail/999999` | **403** | ✅（原 `200 + code=511`）|
| T3 | counselor a1 → `/detail/2` | **200** · 返回 a2 资料 | 🟡 与 `/info` 一致（遗留风险）|
| T4 | admin → `/detail/2` | **200** | ✅ 既有能力保留 |
| T5 | 无 token → `/detail/1` | **200 + code=401** | ✅ 契约不变 |

### 4.1 `/detail` 与 `/info` 一致性（T7 实测）

| id | `/detail` | `/info` | 一致 |
| :-: | :-: | :-: | :-: |
| 1（本人）| 200 | 200 | ✅ |
| 2（他人）| **403** | **403** | ✅ |
| 999999（不存在）| **403** | **403** | ✅ |

⇒ **两个入口的行为现在完全一致**，消除了「列表/详情不一致」的怪象。

---

## 五、🔴 验收记录：本批目标 vs 遗留风险（分开记录）

> 按用户 2026-10-25 要求，**不得把「接口一致」当成「权限设计正确」**。

### 5.1 ✅ 本批目标（已完成）

```text
修复「学生读取其他学生资料」的横向越权（IDOR）。
验收：student a1 → /detail/2、/detail/3 由 200 变为 403（HTTP 实测）。
```

### 5.2 🟡 遗留风险（本批**未处理**，且**未被认定合理**）

```text
心理老师仍可通过 /detail/{id} 与 /info/{id} 读取任意学生资料（含 phone / email）。

原因：OwnershipGuard 对「心理老师 + 实体无 counselor 归属字段」的处理是【放行】，
      /info 传的 ownerCounselorId = null，因此 /info 现在也是同一行为。
      本批沿用该行为（最小改动、不擅自缩权/扩权）。

⚠️ 明确声明：
   · 这不代表「咨询师全量读取学生资料」已被认定合理；
   · 也不代表符合最小权限原则；
   · 属 `student` 行级规则待裁定范围（用户裁定第 2 项 = D，继续审计）。
```

### 5.3 为什么必须分开写

| 若混为一谈会怎样 |
| --- |
| 「`/detail` 修好了」→ 被误读为「学生资料访问已安全」 |
| 而实际上：**咨询师仍可读全体学生**、**phone/email 仍在响应中**、**6 个 JOIN View 未处理** |

---

## 六、行为变更（已确认接受）

| 场景 | 修复前 | 修复后 |
| --- | :-: | :-: |
| `/detail/{不存在的 id}` | `200 + {"code":511,"msg":"查不到数据"}` | **`403 + {"code":403,"msg":"无权访问"}`** |
| 原因 | 无授权判断，直接走 `if(yonghu != null)` 的 else | `assertOwnership(target==null)` → `deny()` |
| 与 `/info` | 不一致 | **一致**（实测确认）|

> 用户 2026-10-25 已裁定 **接受** 该变更（选项 A：与 `/info` 一致）。
> 附带安全收益：**不再存在「id 是否存在」的探测差异**（403 统一，无法区分「无权限」与「不存在」）。

---

## 七、边界确认（严格未扩散）

| 项 | 状态 |
| --- | :-: |
| `OwnershipGuard` | ✅ **0 改动**（用的是既有入口，未改全局逻辑）|
| 六个 JOIN View | ✅ **0 改动** |
| Mapper / XML | ✅ **0 改动** |
| Service / Dao / Entity | ✅ **0 改动** |
| DB / DDL / 前端 / pom | ✅ **0 改动** |
| 咨询师数据范围 | ✅ **未重定义**（保持与 `/info` 一致）|
| 其他接口 | ✅ **未重构** |
| 端点是否保留 | ✅ **保留**（按裁定 A）|

---

## 八、测试覆盖（对应你要求的 6 项 + 1 项补充）

| # | 你的要求 | 对应用例 | 结果 |
| :-: | --- | --- | :-: |
| 1 | 学生读本人 | T1 | ✅ |
| 2 | **学生读其他学生** | **T2** | ✅ **403** |
| 3 | 学生读不存在的 ID | T6 | ✅ 403 |
| 4 | 未登录契约 | T5 | ✅ 200 + code=401 |
| 5 | 管理员合法访问 | T4 | ✅ 200 |
| 6 | 咨询师与 `/info` 一致 | T3 | ✅ 200（**遗留风险已在报告注明**）|
| 7 | `/detail` 与 `/info` 一致性 | T7 | ✅ 三者状态码一致 |

> **T2 额外断言**：403 响应体**不含** `a2` / `17703786902`（防止「拒绝了但仍回数据」）。

---

## 九、Staged Review

```text
M  src/main/java/com/controller/StudentController.java
A  src/test/java/com/regression/StudentDetailIdorRegressionTest.java

2 files changed, 204 insertions(+), 0 deletions(-)
check: OK · 未暂存/未跟踪: 0
未动 OwnershipGuard / View / Mapper / Service / Dao / DB / 前端 / pom  ✅
```

| 项 | 值 |
| --- | --- |
| HEAD / origin | `e15f104` / `e15f104` · LEAD 0 |
| commit | ❌ **未提交** |
| 服务 | 后端 `8080` PID `37552`（jar `19:14:03`）· 前端 `5173` |

---

## 十、建议 commit message

```text
fix(security/step14): /student/detail 学生跨用户 IDOR —— 复用 OwnershipGuard 授权

- /student/detail/{id} 增加 1 行 assertOwnership，与 /info/{id} 完全一致
- 学生读他人资料由 200 变为 403（HTTP 实测 a1 → /detail/2、/detail/3）
- 不存在的 id 由 200+code=511 变为 403（已确认，与 /info 对齐）
- 不改 OwnershipGuard 全局逻辑、不改 6 个 JOIN View、不改 DB / 前端
- 测试：先红后绿（7 run / 3 failures → 7/0）；全量 131/131（新增 7 例）
- 遗留风险（未处理、未认定合理）：咨询师仍可读取任意学生资料，
  与 /info 现状一致，属 student 行级规则待裁定范围
```

---

## 十一、下一步

| 待办 | 状态 |
| --- | --- |
| staged review → 用户复核 → commit → push | 等用户 |
| 六个 JOIN View 字段必要性审计 | 待用户指令（第 3 项）|
| `student` 字段分类 + 行级授权矩阵 | 待用户指令（第 2 项）|
| 咨询师行级规则 | 待裁定 |

**未提交；未动六个 View、未动咨询师数据范围、未进入 Phase 4。**
