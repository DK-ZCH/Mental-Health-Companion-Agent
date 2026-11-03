# 授权缺口清单（Phase 4 前置 · 按数据类型逐一确认）

> **性质**：调研取证（📐 **零代码改动 · 待用户裁定规则**）
> **基线**：`HEAD = origin = e15f104` · LEAD 0 · worktree CLEAN
> **上游**：`STEP13-IMPL.md`（③ CLOSED）· 登记项 `DATA-SCOPE-COUNSELOR-OTHER-TABLES`
> **本批定位**：把「其他表也有问题」这句模糊判断，变成**逐表、逐角色、有实测证据**的清单

---

## 一、结论摘要

| 层级 | 数量 | 说明 |
| --- | :-: | --- |
| 🔴 **确认缺口（counselor 全量可见）** | **3** | `student` / `assessment_answer` / `assessment_wrong_question` |
| 🟡 **语义待定** | **1** | `notification`（无归属字段，可能是「公告」而非「个人通知」）|
| ✅ 已有双字段过滤 | 3 | `assessment_record`（③ 已修）· `counseling_appointment` · `counselor_message` · `counselor_favorite` |
| ⚪ 公共数据（本就不应按角色过滤） | 4 | `assessment_paper` · `assessment_question` · `knowledge_article` · `counselor` |

> **最重要的结论**：`student` 表对 counselor **全量可见，且含姓名 / 手机 / 身份证号 / 邮箱**
> ⇒ 这是本清单中**敏感度最高**的一项，高于已修的测评记录。

---

## 二、🔴 自我纠错：一处误判及其原因

### 2.1 我最初的判断（错误）

首轮实测：

```text
counselor a1 → assessmentAnswer       列表 5 · 全表 5   ⇒ 判为「counselor 缺口」
student  a1 → assessmentAnswer       列表 5 · 全表 5   ⇒ 判为「student 也越权」❌
student  a1 → assessmentWrongQuestion 列表 3 · 全表 3   ⇒ 判为「student 也越权」❌
```

### 2.2 核对数据后纠正

```text
assessment_answer         student_id 分布：1 → 5 条（全部属于 student 1）
assessment_wrong_question student_id 分布：1 → 3 条（全部属于 student 1）
```

⇒ **学生 a1 看到「5 条」是正确的**（本来就全是他的），不是越权。

**误判根因**：**样本量太小** —— 全表恰好只有 student 1 的数据，
「过滤生效」与「没有过滤」在当前数据下**表现相同**。

> 这与 ③ 中「counselor 看到 1 条 = 全表 1 条」的陷阱是**同一类错误**。
> 记录在此，避免重复。

### 2.3 🔴 由此产生的证据强度限制（必须声明）

```text
当前数据无法证伪学生侧是否真的按 student_id 过滤：
  · answer / wrong_question 全表都属于 student 1
  · 若过滤失效，结果依然相同

⇒ 结论只能是：counselor 侧可见全表（数据归属可区分，成立）
⇒ 不能是：学生侧过滤已验证正确（数据无法区分）
```

**若要验证学生侧**，需临时构造 student 2 的答题明细（事务回滚方式），属后续实现批次的事。

---

## 三、逐表矩阵（代码条件 × 实测行为）

### 3.1 XML 是否真实存在过滤条件

| 表 | `params.counselorId` | `params.studentId` | 判定 |
| --- | :-: | :-: | --- |
| `assessment_record` | **2** | 4 | ✅ ③ 已修 |
| `counseling_appointment` | **2** | 4 | ✅ 双字段 |
| `counselor_message` | **2** | 4 | ✅ 双字段 |
| `counselor_favorite` | **2** | 4 | ✅ 双字段 |
| **`assessment_answer`** | **0** | 4 | 🔴 **缺 counselor 条件** |
| **`assessment_wrong_question`** | **0** | 4 | 🔴 **缺 counselor 条件** |
| **`student`** | **0** | 3 | 🔴 **缺 counselor 条件** |
| `notification` | 0 | 0 | 🟡 无任何归属字段 |
| `assessment_paper` | 0 | 0 | ⚪ 试卷定义（公共）|
| `assessment_question` | 0 | 0 | ⚪ 题目（公共）|
| `knowledge_article` | 0 | 0 | ⚪ 知识文章（公共）|
| `counselor` | 0 | 0 | ⚪ 咨询师资料（公共）|

### 3.2 实测（counselor a1 访问 `/xxx/page`）

| 端点 | 返回 | 全表 | 判定 |
| --- | :-: | :-: | --- |
| `/assessmentAnswer/page` | **5** | 5 | 🔴 **全量** |
| `/assessmentWrongQuestion/page` | **3** | 3 | 🔴 **全量** |
| `/student/page` | **3** | 3 | 🔴 **全量（含 PII）** |
| `/notification/page` | **5** | 5 | 🟡 公告表？需确认 |

### 3.3 对照：student a1（本人范围）

| 端点 | 返回 | 全表 | 能否判定正常 |
| --- | :-: | :-: | :-: |
| `/assessmentAnswer/page` | 5 | 5 | ❌ **无法判定**（全表都属他）|
| `/assessmentWrongQuestion/page` | 3 | 3 | ❌ **无法判定** |
| `/student/page` | **1** | 3 | ✅ **正常**（字段存在且生效）|
| `/notification/page` | 5 | 5 | 🟡 无归属字段 |

---

## 四、按敏感度分级

| 级别 | 表 | 数据性质 | 依据 |
| :-: | --- | --- | --- |
| 🔴 **P1** | **`student`** | 姓名 · 手机 · **身份证号** · 邮箱 | 强 PII，可用于身份冒用 |
| 🔴 **P1** | **`assessment_answer`** | 学生在心理测评中的**逐题作答** | 心理状态原始数据，比总分更敏感 |
| 🟠 **P2** | **`assessment_wrong_question`** | 错题明细 | 可反推测评结果，敏感度略低于原始作答 |
| 🟡 **P3** | `notification` | 标题 · 内容 · 分类 | 需先确认是「公告」还是「个人通知」 |
| ✅ | `assessment_record` | 总分 + 试卷 | ③ 已按 C1 收敛 |
| ⚪ | paper / question / knowledge / counselor | 公共资料 | 不应按角色过滤 |

> **重要**：`student` 表的 PII 敏感度**高于**已修的测评记录。
> ③ 修的是「谁看了我的分数」，而这里涉及「谁看了我的身份证号」。

---

## 五、待裁定规则（4 项，逐表独立）

> **🔒 核心原则（用户 2026-10-25 明确）**：
> **不得**因为 C1 存在就自动套用到其他表。
> 每类数据必须**单独**确认访问规则与必要性。

### #1 `student` 表 —— counselor 能看哪些学生的 PII？（P1）

| 选项 | 含义 | 代价 |
| --- | --- | --- |
| A | 保持全量（现状）| 0，但咨询师可检索全部学生身份证/手机 |
| B | 仅「已同意预约」的学生（复用 C1）| 低（可复用现有关系判定）|
| C | **B + 字段脱敏**（列表不返回身份证/手机，详情按需）| 中（需改 View / SQL select）|
| D | 仅基本字段（姓名/头像），敏感字段仅管理员可见 | 中高 |

> **注**：`student` 是「主体表」，`DataScope.apply(..., "studentId", "counselorId")` 对主体表的语义本身就不清晰
> （`studentId` 过滤的是「学生的 id」，而不是「归属」）⇒ 需专门设计。

### #2 `assessment_answer` —— 是否沿用 C1？（P1）

| 选项 | 含义 |
| --- | --- |
| A | **沿用 C1**（与 `assessment_record` 同一条规则：已同意预约即可）|
| B | 更严：仅「已完成的预约」或「当前正在咨询」|
| C | 不可见（答题原始数据仅学生本人与管理员）|

> **理由**：`answer` 是 `record` 的下级明细（同一次测评），
> 沿用 C1 有较强业务一致性 —— 但**仍须用户确认**，不自动继承。

### #3 `assessment_wrong_question` —— 同上（P2）

选项同 #2。

### #4 `notification` —— 是公告还是个人通知？（P3）

| 选项 | 含义 |
| --- | --- |
| A | **公告**（当前形态）⇒ 全量可见是**正确的**，不需要过滤 |
| B | 应为**个人通知** ⇒ 需新增 `student_id` 字段 + 按人过滤（属于**功能缺失**，不是越权）|

> 从字段 `title / cover_url / category / published_at / content` 看，
> **更像「公告/资讯」**而非「个人通知」⇒ 倾向 A，但需用户确认业务意图。

---

## 六、建议实施顺序（待用户裁定后）

```text
① student 表（P1 · PII 最高）
      · 先裁定 #1（A/B/C/D）
      · 若选 B/C/D，需要「主体表」专门设计 —— 不能直接套 DataScope
        ↓
② assessment_answer（P1 · 心理原始数据）
      · 裁定 #2；若沿用 C1，可直接复用 RelationChecker
        ↓
③ assessment_wrong_question（P2）
      · 与 ② 同批或紧随
        ↓
④ notification（P3）
      · 先确认业务意图（公告 or 个人通知）
        ↓
⑤ 全部完成后再评估 Phase 4 多 Tool
```

> **不建议**把 ①②③ 合并成一批做 —— 每张表的规则来源不同，
> 合并会导致「一处规则同时解释三张表」，一旦裁定有误影响面扩大。

---

## 七、本批不做什么

| 不做 | 原因 |
| --- | --- |
| ❌ 修改任何代码 / SQL | 本批为调研 |
| ❌ 自动把 C1 套到 answer / wrong_question | 用户明确禁止自动继承 |
| ❌ 设计 `student` 表脱敏方案 | 需先裁定 #1 |
| ❌ 给 `notification` 加字段 | 需先确认业务意图 |
| ❌ 修改 DB / 前端 / pom | 零改动 |

---

## 八、证据强度声明

| 结论 | 强度 |
| --- | :-: |
| counselor 对 3 张表可见全表 | ✅ **实测 + 数据归属可区分** |
| student 侧过滤正确 | ❌ **无法判定**（全表都属于 student 1）|
| `notification` 是公告表 | 🟡 **字段推断**，非业务确认 |
| `student` 含身份证等 PII | ✅ 实测取值 |
| 全表行数 | ✅ 实测 |

---

## 九、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `e15f104` / `e15f104` · LEAD 0 |
| worktree | **CLEAN** |
| 代码 / DB / 前端 / pom | **0 改动** |
| 实测方式 | 仅 HTTP 只读 + SQL SELECT，**无写入** |
| Step 13 | 🏁 CLOSED |
| 本批 | 📐 **仅调研，待裁定 #1–#4** |

**未写任何代码，等你裁定 §五 的 4 项规则。**
