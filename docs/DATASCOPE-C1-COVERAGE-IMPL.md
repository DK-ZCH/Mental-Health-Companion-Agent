# Batch：DataScope C1 coverage completion —— 实现报告

> **性质**：实现（✅ **已 commit · 已 push（`9a1cb70`）· 用户批准**）
> **基线**：`HEAD = origin = 9a1cb70` · LEAD 0 · worktree CLEAN · 改动 **1 新增测试文件 +267 · 生产代码 0 改动**
>
> **⚠️ 审查边界**：用户**未直接读取 staged diff、未独立运行测试**，结论基于提交方提供的证据。
>
> **本批证明的是**：当前测试覆盖的查询路径对相应 XML 条件**具有敏感性**。
> **不能据此断言**所有入口都已受保护 —— `/page` 新增入口的风险仍在（**缺乏统一契约检查**）。
> **裁定来源**：用户 2026-10-25 —— 批准为 `counseling_appointment` / `counselor_favorite` 补 M2，**仅加测试不改业务代码**
> **本批定位**：补齐 C1 的唯一测试盲区；**不碰 C3 · 不碰 C4 · 不碰 `DataScope` 实现**

---

## 一、为什么要做这一批

Phase A 语义矩阵核查发现：C1 四模块的**授权实现**都已生效，但**测试覆盖不齐**。

| 模块 | 学生侧范围断言 | 咨询师侧范围断言 |
| --- | :-: | :-: |
| `assessment_record` | ✅ | ✅ + 变异实验 |
| `counselor_message` | ✅ T17 | ✅ T18 |
| **`counseling_appointment`** | 🔴 无 | 🔴 无 |
| **`counselor_favorite`** | 🔴 无 | 🔴 无 |

> **关键澄清**：`Phase2CoreRegressionTest` 确实请求过这两个模块的 `/page`，
> 但那是「响应不含 `password` / 身份证」的**字段检查**，**不验证行级范围** ⇒ 不能算授权覆盖。
> 若不加测试，将来有人删掉 XML 条件，**没有任何用例会变红**。

---

## 二、测试设计（7 例）

| # | 用例 | 断言 | 作用 |
| :-: | --- | --- | --- |
| **A1** | appointment · 学生维度 | student1 `total=3` 且全部 `studentId=1`；**student2 `total=0`** | 有权/无权双向 |
| **A2** | appointment · 咨询师维度 | counselor1/2/3 各 `total=1` 且全部 `counselorId=本人`；**admin 对照 `total=3`** | 收窄 + 反向对照 |
| **B1** | favorite · 学生维度 | student1/2/3 各 `total=2` 且全部本人；**admin 对照 `total=6`** | 收窄 + 反向对照 |
| **B2** | favorite · 咨询师维度 | counselor1 `=2` / counselor2 `=3`（**条数不同**）且全部本人；admin 对照 6 | 双向证明收敛 |
| **C1** | 学生提交他人 `studentId` | 结果与不提交**完全一致** | 服务端覆盖生效 |
| **C2** | 老师提交他人 `counselorId` | 结果与不提交**完全一致** | 同上 |
| **D1** | 未认证 | 两个模块均 `200 + code=401` | 既有契约不变 |

### 防「空数据假通过」的设计

```text
每个用例都先断言【有权一侧 total > 0】（A1/A2/B1/B2 均有），
再断言【无权一侧被排除】。
⇒ 若种子数据缺失导致两边都为 0，前置断言会失败，而不是「通过」。
```

同时每个模块都用 **admin 全量**做反向对照（appointment 3 / favorite 6），
确保「被收窄后的 1 条 / 2 条」不是「表里本就只有这么多」。

---

## 三、红/绿验证（变异实验，两次）

> 方法同 `DATASCOPE-AC6-MUTATION-EXPERIMENT.md`：**只移除一处条件 → 跑测试 → 恢复 → 复跑**，
> 且**不产生提交**。

### 变异 1 · `CounselingAppointmentDao.xml` 的 `counselorId` 条件

```text
改动范围：1 file changed, 5 deletions   （仅 XML，未动 Java / 测试 / 数据）
结果    ：Tests run: 7, Failures: 1, Errors: 0 · BUILD FAILURE

失败用例：appointment_counselorScope
          expected: 1   but was: 3
```

**归因**：`Errors=0` ⇒ 断言失败而非 SQL/启动错误；`1 → 3` 即
**所有咨询师的预约一次性全部可见**（越权数据重现）⇒ 该用例确实在观察这一条件。

### 变异 2 · `CounselorFavoriteDao.xml` 的 `counselorId` 条件

```text
改动范围：1 file changed, 5 deletions
结果    ：Tests run: 7, Failures: 2, Errors: 0 · BUILD FAILURE

失败用例：favorite_counselorScope        expected: 2   but was: 6
          counselorCannotWidenByParam    expected: 2   but was: 6
```

**附带收益**：变异同时被 **C2（提交他人 `counselorId` 不得放大）** 捕获 ⇒ 两类断言互补。

### 恢复与复验

```text
git checkout -- 两个 XML      → 无 M 记录（完全还原）
mvn test -Dtest=DataScopeC1CoverageTest  → 7/7 BUILD SUCCESS
mvn test（全量）                          → 156/156 BUILD SUCCESS
HEAD 未变 = bdde50b · 零提交
```

---

## 四、边界（严格未越界）

| 项 | 状态 |
| --- | :-: |
| 生产代码（Java / XML / SQL）| ✅ **0 改动** |
| C3 五个 no-op 调用 | ✅ 未动（继续保留）|
| C4 `Student` 咨询师范围 | ✅ 未动（**下一批仅审计**）|
| `DataScope` 实现 | ✅ 未动 |
| DB / 前端 / pom | ✅ 未动 |
| `counselor_message` 的变异实验 | ⏳ **未做**（不属本批；它已有 T17/T18 行为断言）|

---

## 五、本批证明了什么 / 没证明什么

### ✅ 已证明

| # | 结论 |
| :-: | --- |
| 1 | 两个模块的**行级范围真实生效**（学生维度、咨询师维度各自收窄）|
| 2 | 这两处范围**现已有测试保护**：删除 XML 条件会立刻变红 |
| 3 | 客户端提交他人 id **无法放大** 读范围 |
| 4 | 反向对照存在（admin 全量），排除「表里本就 1 条」的假象 |

### ❌ 未证明（不得外推）

| # | 未证明项 |
| :-: | --- |
| 1 | `counselor_message` 的范围断言是否也受同等保护 —— 有测试，**未做变异验证** |
| 2 | `DataScope` 整体契约是否成立 —— C2/C3/C4 仍未解决 |
| 3 | 新增 `/page` 会被拦住 —— 仍**无 M1 声明机制** |
| 4 | C4 `Student` 的咨询师范围 —— **仍是实际缺陷** |

---

## 六、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `bdde50b` / `bdde50b` · LEAD 0 |
| 改动 | **1 新增测试文件**（`DataScopeC1CoverageTest`，7 例）|
| 测试 | **156/156**（149 基线 + 7 新增）|
| 红/绿 | 变异 1 → `1 vs 3`；变异 2 → `2 vs 6`；恢复后全绿 |
| 生产代码 | **0 改动** ✓ |
| 提交 | ❌ **未 commit**（等复核）|

**等 staged diff 复核通过后 commit + push。**
