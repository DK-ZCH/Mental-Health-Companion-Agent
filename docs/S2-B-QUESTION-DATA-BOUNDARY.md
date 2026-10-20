# S2-B —— 题目数据边界（DTO 投影）实现与验收

> 日期：2026-10-20 ｜ 基线：`09140cd`（S2-A 已推送，LEAD=0）
> 状态：**已实现 · 🚧 未提交** ✗ ｜ 改动 **3 文件**（2 新增 + 1 修改）
> 范围：只建立题目读取边界；**不改 DB / 不改 API 路径 / 不改认证规则 / 不修 CRUD / 不修 IDOR** ✓

---

## 1. 改动清单

| 文件 | 变更 | 要点 |
| --- | :-: | --- |
| `com/dto/QuestionPublicDTO.java` | **新增** | 仅 `id/paperId/content/options/score/questionType/sortOrder` ⇒ **结构上无 answer/analysis** |
| `com/utils/QuestionOptionSanitizer.java` | **新增** | options 安全投影（**不含角色判断** ✓）；codesOnly / 检测中性化两档；解析失败 → `[]` |
| `AssessmentQuestionController.java` | 修改 | `/list` `/detail` → Public(codesOnly)；`/page` `/info` → 管理员保留 `AssessmentQuestionView`，其余 Public(检测中性化) |

**角色判定**：`OwnershipGuard.currentRole(request)`（服务端身份），管理员 → 完整视图，否则 → Public（未知/缺失角色同样 Public，fail-closed）✓
**Admin 复用现有 `AssessmentQuestionView`**（未新建第 2 个 DTO）✓

---

## 2. 验收矩阵（8 项全部实测）

| # | 身份 | 端点 | 结果 | 判定 |
| :-: | --- | --- | --- | :-: |
| 1 | 匿名 | `/list?paperId=1` | 200 · **无 answer/analysis** · options=`选项A/B/C/D`（codesOnly）| ✅ |
| 2 | 学生 | `/page` | 200 · **无 answer/analysis** · `正确答案C`→`选项C`、`答案A` 保留（检测中性化）| ✅ |
| 3 | 学生 | `/info/1` | **200** · 无 answer/analysis | ✅（测试契约保持）|
| 4 | 学生 | `/detail/1` | 200 · 无 answer/analysis · options=codesOnly | ✅ |
| 5 | 管理员 | `/page` | 200 · **有 answer/analysis** · 原始 `正确答案` 保留 | ✅ |
| 6 | 管理员 | `/info/1` | 200 · **有 answer/analysis** | ✅ |
| 7 | — | `mvn -B test` | **47 / 47** · BUILD SUCCESS | ✅ |
| 8 | — | 旧页面 | `/list` 仍 200 且含 content | ✅ |

**认证契约未变**：`/list` 仍匿名、`/page` `/info` `/detail` 仍需登录（未登录 401）✓

---

## 3. 中性化行为

| 输入 text | 结果 |
| --- | --- |
| `正确答案C` | → `选项C` ✓（整条替换）|
| `标准答案为B` | → `选项B` ✓ |
| `本题应选C` | → `选项C` ✓ |
| `答案A`（普通标签）| **保留** ✓（不是泄露）|
| `这是一个关于答案的普通描述` | **保留** ✓（检测词不含裸 `答案`，无意义替换）|

检测词：`正确 | 标准答 | 正解 | 答案为 | 答案是 | 应选 | 本题 | 此题 | 选[A-D] | √ | ✔`
> 「普通描述不被误伤」为**按词表的逻辑验证**（当前 DB 无此类样本，未做运行时注入）；当前 4 题 5 处泄露全部命中 ✓

---

## 4. 兼容性（如实声明）

| 消费方 | 影响 |
| --- | --- |
| 旧学生答题页 `exampaper/exam.html` | 学生 `/page` 不再返回 `answer` ⇒ 本地判分失效（**其提交端点本就 404**，属预期破坏；R15 改服务端判分）|
| 管理端题目列表/编辑 | 不受影响（Admin 完整视图）✓ |
| 旧 `examquestion/list.html` | 不受影响（本就不渲染 options/answer）✓ |
| 新前端 R14 | 不调用题目接口 ⇒ 无影响 ✓ |
| `/assessmentAnswer`、`/assessmentWrongQuestion` | 未改动 ✓ |

---

## 5. 本轮新登记（不修）

| ID | 级别 | 事实 |
| --- | :-: | --- |
| **`SEC-ADMIN-DEFAULT-CREDENTIAL`** | 🔴 **P1 Security** | 管理员种子账号为 **`admin / admin`（明文，直接比较）** ✗ ⇒ 默认口令可被猜到（本次为完成验收按种子凭据正常登录，**未改库/未手造 token**）|
| `DEF-REGRESSION-TEST-DATA-MUTATION` | ⚪ TBD | 本轮 `mvn test` 再次把 `counselor_message.id=2.replied_at` 改为 **15:55:41**（**测试卫生，独立任务**，非 S2-B）|

---

## 6. 未做 / 明确不在本批 ✗

❌ DB 5 处历史 options · ❌ 题目 CRUD 404（`DEF-QUESTION-ADMIN-ENDPOINT-MISSING`）· ❌ `/detail` IDOR（S2-C）· ❌ “仅已提交才可看解析” · ❌ 答题/计分链路 · ❌ `assessmentAnswer`/`WrongQuestion` 语义 · ❌ 旧前端 · ❌ R15

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | S2-B 实现：`QuestionPublicDTO` + `QuestionOptionSanitizer` + Controller 4 端点；**8 项矩阵全通过**；Admin 复用 `AssessmentQuestionView`；未知角色 fail-closed；DB/API/认证均未变；新登记 `SEC-ADMIN-DEFAULT-CREDENTIAL` P1；测试卫生 `replied_at` 漂移再确认；**未提交** ✗ |
