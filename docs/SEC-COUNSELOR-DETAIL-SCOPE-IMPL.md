# 逐题作答 / 错题：咨询师读取范围收敛 —— 实现报告

> **性质**：实现（🏁 **CLOSED · 用户 2026-10-25 验收通过 · `bdde50b`**）
> **基线**：`HEAD = origin = bdde50b` · LEAD 0 · worktree CLEAN · 改动 **4 文件 +345 / −2**
>
> **CLOSED 依据**：① 4 文件均在批准清单内 ② 18 项新增 + 全量 **149/149** BUILD SUCCESS
> ③ `HEAD = origin` · LEAD 0 · worktree CLEAN ④ 咨询师统一拒绝；学生/管理员/record C1 规则按报告不变
>
> **⚠️ 审查边界**：用户**未独立访问仓库、未重新运行测试**，结论基于提交方提供的证据。
>
> **关闭范围严格限定**：仅「咨询师读取 `assessment_answer` / `assessment_wrong_question`」这一缺陷。
> **不表示**整个系统所有数据资源都不存在类似越权问题，也**不表示**多学生数据分布隔离已验证。
>
> **STAGED REVIEW PASS → APPROVED FOR COMMIT**（用户 2026-10-25）
> · 用户审查依据为提交方提供的实际 diff 摘录与测试报告，**未独立访问仓库、未重跑测试**
> · 用户明确接受「本批未使用独立 fixture」的解释：修复在**查询执行之前按角色拒绝**，
>   不依赖学生记录分布；**但多学生隔离未验证** —— 若未来改为行级过滤，必须重新覆盖多学生场景
> **裁定来源**：用户 2026-10-25 —— ① 咨询师**默认不可见**逐题数据（B）② `DataScope` 契约缺口独立批次（B）③ 本批最小范围止血（A）
> **本批定位**：只关闭「咨询师可读 `assessment_answer` / `assessment_wrong_question`」

---

## 一、结论

| 项 | 修复前 | 修复后 |
| --- | :-: | :-: |
| `counselor1/2/3` × `answer/{page,list,info}` | **200 + 全量** 🔴 | **403** ✅ |
| `counselor1/2/3` × `wrongQuestion/{page,list,info}` | **200 + 全量** 🔴 | **403** ✅ |
| `student1`（本人）| 200 · 5 条 | **200 · 5 条**（不变）|
| `student2`（他人）| 200 · 0 条 | **200 · 0 条**（不变）|
| `admin` | 200 · 全量 | **200 · 全量**（不变）|
| 未认证 | 200 + `code=401` | **200 + `code=401`**（不变）|
| `assessment_record` 既有 C1 规则 | — | **完全未改** |

> **关键区分**：本批**不是**把 `assessment_record` 的「已同意预约」规则复制过来，
> 而是按裁定 B 对**整类资源**拒绝 —— 已同意预约的 `counselor1` 同样 403。

---

## 二、修复前证据（先证明「确实是越权」，再动手）

### 2.1 数据身份证明（不是「条数相同」的推断）

```sql
assessment_answer        : id 1–5，全部 student_id = 1
assessment_wrong_question: id 1–3，全部 student_id = 1
counseling_appointment   : counselor1–student1 status=2（已同意）
                           counselor2–student1 status=1（待审核）
                           counselor3–student1 status=1（待审核）
```

⇒ `counselor2 / counselor3` **与学生 1 没有任何「已同意」关系**，却读到学生 1 的全部逐题记录。

### 2.2 三条路径**各自独立**泄露（这是本批最重要的取证）

| 路径 | 修复前实测 | 原因 |
| --- | --- | :-: |
| `/answer/page` | `counselor2` → 200 · 5 条 | XML 无 `params.counselorId` 分支 ⇒ 参数被静默丢弃 |
| `/answer/list` | `counselor2` → 200 · 5 条 | 与 `/page` 共用同一条 `selectListView` |
| **`/answer/info/1`** | **`counselor2` → 200 · `studentId=1` · `questionId=5`** | `assertOwnership(..., null)` 在「老师 + 实体无 `counselor_id` + 未传回调」组合下**放行** |

**同一老师、同一条数据、对照 `assessment_record`**：

```text
counselor2 → record/info/1   → 403   ✅（Step 13 已接关系回调）
counselor2 → answer/info/1   → 200   🔴（无回调）
counselor2 → wrong/info/1    → 200   🔴（无回调）
```

> **若只修分页 SQL，`/info/{id}` 会原样保留漏洞** —— 这正是用户要求「路径覆盖」的原因。

---

## 三、修复方案（为什么这样最小）

### 3.1 新增一个原语（不是复用 `assertOwnership`）

```java
// OwnershipGuard.java
public static void assertCounselorNotAllowed(HttpServletRequest request) {
    if (ROLE_COUNSELOR.equals(currentRole(request))) {
        deny();          // → HTTP 403 + code=403
    }
}
```

| 与 `assertOwnership` 的差别 | 说明 |
| --- | --- |
| `assertOwnership` | 判「**这一条记录**是否属于你」——需要目标对象，同角色对不同记录结论可不同 |
| `assertCounselorNotAllowed` | 判「**这类资源**你是否有资格访问」——与具体记录无关 ⇒ **无存在性预言机** |

### 3.2 六个调用点（3 路径 × 2 模块）

| 模块 | `/page` | `/list` | `/info/{id}` |
| --- | :-: | :-: | :-: |
| `AssessmentAnswerController` | ✅ | ✅ | ✅ |
| `AssessmentWrongQuestionController` | ✅ | ✅ | ✅ |

**放置位置**：`/page` 与 `/list` 中置于 `DataScope.apply` **之前**（先拒绝，不进入查询构造）；
`/info` 中置于 `getById` **之前**（不查库即拒绝）。

### 3.3 刻意**没有**采用的两个方案

| 方案 | 为什么不用 |
| --- | --- |
| 在 XML 加 `EXISTS`（复用 record 的 C1 规则）| **违反裁定 B** —— 会让「已同意预约」的咨询师看到逐题数据 |
| 在 XML 加 `and 1=0` | 不覆盖 `/info/{id}`；且属「靠 SQL 魔法表达授权」，可读性与可测性都差 |

---

## 四、先红后绿（用户要求的证据顺序）

### 4.1 修复前（红）

```text
命令：mvn -B test -Dtest=CounselorAssessmentDetailScopeTest
结果：Tests run: 18, Failures: 7, Errors: 0
```

失败的 **7 条恰好是全部咨询师拒绝用例**：

```text
allCounselorsDeniedOnAnswerPage        expected: 403  but was: 200
counselorDeniedOnAnswerList            expected: 403  but was: 200
counselorDeniedOnAnswerInfo            expected: 403  but was: 200
counselorDeniedOnWrongQuestionPage     expected: 403  but was: 200
counselorDeniedOnWrongQuestionList     expected: 403  but was: 200
counselorDeniedOnWrongQuestionInfo     expected: 403  but was: 200
deniedResponseLeaksNothing             （同上，200 导致断言失败）
```

其余 **11 条当时已通过**（学生/管理员/未认证/record 边界）⇒ 证明**不是测试写错或数据缺失**。

### 4.2 修复后（绿）

```text
命令：mvn -B test -Dtest=CounselorAssessmentDetailScopeTest
结果：Tests run: 18, Failures: 0, Errors: 0 · BUILD SUCCESS
```

### 4.3 全量回归

```text
命令：mvn -B test
结果：Tests run: 149, Failures: 0, Errors: 0 · BUILD SUCCESS
```

| 测试类 | 数量 |
| --- | :-: |
| **CounselorAssessmentDetailScopeTest（新增）** | **18** |
| AgentControllerRegressionTest | 10 |
| IdentityAuthorizationRegressionTest | 30 |
| Phase2CoreRegressionTest | 17 |
| AssessmentToolsTest | 14 |
| AgentIdentityTest | 11 |
| PasswordHasher | 16 |
| CounselorAssessmentScope（2 个类）| 7 + 9 |
| OwnershipGuard 关系回调 | 10 |
| student/detail IDOR | 7 |
| **合计** | **149** |

---

## 五、运行时验证（真实 HTTP，非 MockMvc）

部署：jar `21:06:36` · PID `31608` · 10s 就绪。

### 5.1 咨询师拒绝（8/8）

```text
counselor1 answer/page        HTTP=403 code=403
counselor2 answer/page        HTTP=403 code=403
counselor3 answer/page        HTTP=403 code=403
counselor2 answer/list        HTTP=403 code=403
counselor2 answer/info/1      HTTP=403 code=403
counselor1 wrong/page         HTTP=403 code=403
counselor2 wrong/list         HTTP=403 code=403
counselor2 wrong/info/1       HTTP=403 code=403
```

### 5.2 反向自检（防止「全局空结果」式假通过）

```text
student1 answer/page              HTTP=200 code=0 total=5   ← 本人数据仍在
student2 answer/page              HTTP=200 code=0 total=0
student2 answer/page studentId=1  HTTP=200 code=0 total=0   ← 覆盖生效
admin answer/page                 HTTP=200 code=0 total=5   ← 管理端不受限
admin wrong/page                  HTTP=200 code=0 total=3
admin answer/info/1               HTTP=200 code=0
student2 answer/info/1            HTTP=403 code=403          ← 他人记录
未认证 answer/page                HTTP=200 code=401          ← 契约不变
```

### 5.3 边界：`assessment_record` 既有规则未被改动

```text
counselor1(已同意) record/info/1   HTTP=200 code=0
counselor2(待审核) record/info/1   HTTP=403 code=403
counselor1(已同意) record/page     HTTP=200 total=1
counselor2(待审核) record/page     HTTP=200 total=0
```

⇒ 与修复前**完全一致**，证明本批没有错误地把「一律拒绝」扩散到 record 表。

---

## 六、路径覆盖核实（用户验收门槛 #4）

| 检查 | 方法 | 结果 |
| --- | --- | --- |
| 两模块全部端点 | 逐个枚举 `@RequestMapping` | answer 10 个 / wrongQuestion 8 个，**读取仅 3 个**（`page`/`list`/`info`），全部已拦 |
| 是否还有其他 Controller 读这两张表 | 全仓搜 `examredetailsService` / `examrewrongquestionService` | 仅 3 个文件：两个自身 Controller + `AssessmentRecordController`（**2 处均为 `delete` 级联删除，非读取**）|
| 是否还有其他 XML 跨表读 | 搜 `FROM assessment_answer` / `FROM assessment_wrong_question` | 仅各自 Dao，**无跨表读取** |
| 是否残留旧端点 | 搜索 `IgnoreAuth` | 两模块 **0 处** |

---

## 七、边界（本批刻意未做）

| 未做 | 原因 |
| --- | --- |
| 修改 `DataScope` 契约缺口 | 用户裁定 **B · 独立批次** |
| 在 XML 加行级过滤 | 裁定 B 下不需要，且不覆盖 `/info` |
| 改动 `assessment_record` 的 C1 规则 | 用户明确「不修改」 |
| 处理写路径（`/save` `/update` `/delete`）| 写授权规则属另一议题，本批只关闭**读取** |
| 旧 Admin 前端菜单/提示适配 | 咨询师若仍点到该菜单会看到 403 —— 登记为体验项（同 `UX-AGENT-ROLE-UNAVAILABLE` 模式）|
| 旧 Admin 浏览器导出人工验证 | **仍为独立待办，本批未处理、未关闭** |
| Multi-Tool | **继续暂停** |

### 新增登记

| 工单 | 说明 |
| --- | --- |
| `UX-COUNSELOR-DETAIL-403` | 咨询师若从旧 Admin 菜单进入「答题明细/错题」，将收到 403。数据侧已正确阻断，属前端菜单可见性问题，留前端阶段 |

---

## 八、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `d6db0b6` / `d6db0b6` · LEAD 0 |
| 改动 | **3 文件 +56/−2**（`OwnershipGuard` / 两个 Controller）+ **1 新测试（18 例）** |
| 未 commit | ✅ 按裁定「不等于批准立即提交」 |
| 测试 | **149/149**（131 基线 + 18 新增）|
| 运行时 | 8/8 咨询师 403 · 反向自检全通过 · record 边界不变 |
| DB / SQL / 前端 / pom | **0 改动** |
| 服务 | 后端 `8080` PID `31608` · 前端 `5173` |

**等你复核 staged diff。通过后再 commit + push。**
