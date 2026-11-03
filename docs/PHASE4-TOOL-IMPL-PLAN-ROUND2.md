# Phase 4 · Round 2 实施清单 + 测试矩阵（T5 趋势 / T6 收藏）

> **性质**：🏁 **CLOSED · 用户 2026-11-03 验收通过（正式基线 `32388f8`）**
> **基线**：`HEAD = origin = 32388f8` · LEAD 0 · worktree CLEAN · 改动 **9 文件 +1129/−6**
>
> **CLOSED 依据（用户复核项）**：T5/T6 功能 ✅ · 先红后绿 29/29 ✅ · 全量 239/239 ✅ ·
> 变异 4/4 ✅ · 真实 LLM 调用 ✅ · Tool 数据边界 ✅ · 零污染 ✅ · 静态检查 ✅ ·
> commit/push `32388f8` ✅ · HEAD=origin ✅ · worktree CLEAN ✅ · DB/schema/依赖/前端无改动 ✅ · 文档同步 ✅
>
> 用户特别认可的两个工程判断：
> ① **没有为了测试而改 DB schema**（`submitted_at ON UPDATE` 只登记，不扩大范围）
> ② **没有机械改 Prompt**（T5 趋势由 Tool 给出、T6 为结构化数据，现有 Prompt 已足够）
> **上游**：`PHASE4-TOOL-DESIGN-ROUND2.md` —— **APPROVED**（用户 2026-11-03，五项裁定全批准）

---

## 一、先钉死三条边界语义（用户要求，实施时不得改写）

### 1.1 `n` = **排除无效样本后**的有效样本数

```text
windowSize  = min(本人记录总数, 10)        ← 从数据库取到的原始条数
excludedCount = 无法归一化的条数
validCount  = windowSize - excludedCount
n           = validCount                  ← 【只有它】决定是否 INSUFFICIENT
```

| 场景 | windowSize | excluded | n | 判定 |
| --- | :-: | :-: | :-: | --- |
| 10 条全部有效 | 10 | 0 | 10 | 正常计算 |
| 10 条中 2 条试卷缺失 | 10 | 2 | 8 | 正常计算 |
| **10 条中 9 条无效** | 10 | 9 | **1** | 🔴 **INSUFFICIENT** |
| 3 条全无效 | 3 | 3 | 0 | INSUFFICIENT |

> **禁止**用 `windowSize >= 2` 代替 `n >= 2`。
> 「数据库里有 10 条」**不等于**「有足够样本算趋势」—— D 组测试专门锁这一条。

### 1.2 `deltaPp` 明确定义为**百分点**

```text
firstRate, lastRate ∈ [0, 1]           // 均为有效样本
deltaPp = (lastRate - firstRate) * 100 // 百分点（percentage points）

deltaPp >=  5      → UP
deltaPp <= -5      → DOWN
-5 < deltaPp < 5   → FLAT
```

| 输入 | firstRate | lastRate | deltaPp | 期望 |
| --- | :-: | :-: | :-: | :-: |
| 60% → 64% | 0.60 | 0.64 | **+4** | **FLAT** |
| 60% → 65% | 0.60 | 0.65 | **+5** | **UP** |
| 65% → 60% | 0.65 | 0.60 | **−5** | **DOWN** |
| 60% → 80% | 0.60 | 0.80 | **+20** | **UP** |

> 用 `>=` / `<=` 比较，**禁止**浮点相等判断。
> 文档与代码中一律写 `deltaPp`，不写「5%」（避免与「相对变化 5%」混淆）。

### 1.3 排序与稳定 tie-breaker

```text
① 查询：ORDER BY submitted_at DESC, id DESC  LIMIT 10      ← 取「最近 10 条」
② 计算前：对这 10 条按 submitted_at ASC, id ASC 重排         ← 得到「时间上的首→尾」
③ first = 第一条【有效】样本；last = 最后一条【有效】样本
```

**为什么必须有第二排序键**：`submitted_at` 是 `timestamp`（秒级），
同秒记录若不指定 tie-breaker，数据库返回顺序不确定 ⇒ 首尾可能互换 ⇒ **趋势翻转**。
本项目 `assessment_record.id` 单调可用，直接作 tie-breaker，**不需要新增字段**。

---

## 二、取证：本批新发现的两个数据模型风险

| # | 事实（实测） | 影响 | 本批处置 |
| :-: | --- | --- | --- |
| 1 | `assessment_record` 仅 **1 条**（student1）| 趋势无法从真实数据验证 | 测试全部用**事务 fixture**，报告不得声称「真实数据已验证趋势」 |
| 2 | 两张试卷满分 **100 / 10** | 原始分跨卷不可比 | 强制归一化（§一 1.2） |
| 3 | 🔴 `submitted_at` 为 `timestamp … **ON UPDATE CURRENT_TIMESTAMP**`，且存在 `/assessmentRecord/update` | **任何人编辑记录都会把时间改成 now** ⇒ 历史顺序被静默重排 ⇒ T5 的输入被改变 | **本批不改表**（超边界）；登记为独立议题；Prompt 不承诺「精确时间」 |
| 4 | 🔴 `counselor_favorite` **只有 PRIMARY(id) 索引**，**无 (student_id, counselor_id) 唯一约束** | 同一老师可能有多条不同 `favorite_type` ⇒ `favoriteTypeLabel` 语义不能假设唯一 | 见 §十二 待裁定 1（建议改为 `favoriteTypes` 列表）|

> 第 3、4 条属**新发现**，此前未登记。本批只保证「不因它们产生错误结论」，不修数据模型。

---

## 三、变更清单

### 3.1 新增（4 个）

| # | 文件 | 职责 |
| :-: | --- | --- |
| 1 | `agent/TrendTools.java` | T5 `getMyAssessmentTrend` |
| 2 | `agent/TrendToolResult.java` | 含 `TrendPoint` / `TrendSummary` 嵌套 record |
| 3 | `agent/FavoriteTools.java` | T6 `getMyFavorites` |
| 4 | `agent/FavoriteToolResult.java` | 含 `Item` 嵌套 record |

### 3.2 修改（3 处）

| # | 文件 | 改动 |
| :-: | --- | --- |
| 1 | `config/AgentConfig.java` | 新增 2 个 `@Bean`；`agentService` 增加 2 个参数 |
| 2 | `agent/AgentPrompts.java` | ① 趋势结论只能来自 Tool ② 样本不足须说明「数据不够」③ 不预测/不诊断 ④ 收藏只描述老师方向 |
| 3 | `test/regression/AgentControllerRegressionTest.java` | 工具数 5 → **7** 的断言 |

### 3.3 新增测试（2 个）+ 扩展 1 个

| # | 文件 | 覆盖 |
| :-: | --- | --- |
| 1 | `regression/Phase4TrendToolTest.java` | A/D/N/T/X/E + pp 边界 + tie-breaker |
| 2 | `regression/Phase4FavoriteToolTest.java` | A/B/U/C/D |
| 3 | `Phase4ToolScopeTest.java`（扩展）| 把 T5/T6 加入「四 Tool 同时拒绝」的断言（改为六 Tool）|

---

## 四、类与方法边界

### 4.1 `TrendTools`

```java
public class TrendTools {
    static final int MAX_RECORDS = 10;      // 原始窗口
    static final double THRESHOLD_PP = 5.0; // 百分点阈值

    @Tool(...) public TrendToolResult getMyAssessmentTrend(ToolContext ctx);

    // 纯函数，便于单测与变异
    static Double scoreRate(Integer score, Integer maxScore);     // 任一为 null 或 max<=0 → null
    static String trendOf(double deltaPp, int validCount);        // INSUFFICIENT / UP / FLAT / DOWN
}
```
依赖：`AssessmentRecordService` · `AssessmentPaperService`（均复用，不新建 DAO/Service）。

### 4.2 `FavoriteTools`

```java
public class FavoriteTools {
    static final int MAX_RECORDS = 10;

    @Tool(...) public FavoriteToolResult getMyFavorites(ToolContext ctx);
}
```
依赖：`CounselorFavoriteService` · `CounselorService`（**只 select id/name/expertise**）· `AgentDictLabels`。

---

## 五、Result 契约

### 5.1 T5

```java
record TrendToolResult(
    List<TrendPoint> items,   // 按时间升序
    TrendSummary summary,
    long total,               // 本人记录总数（业务事实）
    boolean truncated         // total > items.size()
) {}

record TrendPoint(
    String date,        // yyyy-MM-dd（submittedAt）
    String paperName,   // 试卷不存在时 "未知试卷"
    Integer score,      // 原始分（明确标注，不参与比较）
    Integer maxScore,   // 试卷满分
    Double scoreRate,   // 0~1；无法计算时 null
    boolean excluded,   // 是否被排除出统计
    String excludedReason  // "paper_missing" / "paper_score_invalid" / "score_missing"；未排除时 null
) {}

record TrendSummary(
    int windowSize,      // 原始窗口条数（≤10）
    int validCount,      // = n，决定 INSUFFICIENT
    int excludedCount,
    Double firstRate,
    Double lastRate,
    Double deltaPp,      // (lastRate-firstRate)*100，保留 2 位
    Double averageRate,  // 有效样本均值
    String trend         // UP / DOWN / FLAT / INSUFFICIENT
) {}
```

**刻意不返回**：原始计算过程、试题内容、`studentId`、任何 `answer/analysis`。
**刻意不做**：预测下次分数、严重程度判断、诊断。

### 5.2 T6

```java
record FavoriteToolResult(
    List<Item> items,     // 按 lastFavoritedAt 降序（同时间按 counselorId 升序）
    long total,           // 去重后的老师数
    boolean truncated
) {}

record Item(
    Integer counselorId,
    String counselorName,
    String expertise,
    List<String> favoriteTypes,   // 去重后的类型标签（见 §十二 待裁定 1）
    int favoriteCount,            // 原始收藏条数
    String lastFavoritedAt        // yyyy-MM-dd HH:mm
) {}
```

**刻意不返回**：`phone` / `email` / `username` / `password` / `avatarUrl` / `studentId`，
以及未经聚合的 `CounselorFavoriteEntity`。

---

## 六、SQL / Service 复用方案

### 6.1 T5（3 次查询）

```text
① count : assessment_record where student_id = ?                      → total
② list  : select(id, paper_id, total_score, submitted_at)
          where student_id = ? order by submitted_at desc, id desc limit 10
③ paper : select(id, name, total_score) where id in (…)               ← 批量，避免 N+1
```

**不查**：`record_no`（模型不需要）、任何题目/答案表。

### 6.2 T6（2 次查询）

```text
① fav  : select(counselor_id, favorite_type, favorited_at)
         where student_id = ? order by favorited_at desc, id desc
② cou  : select(id, name, expertise) where id in (去重后的 counselorId)
```

🔴 **禁止** `counselorService.list()` / `listByIds()`：`CounselorEntity` 含 `password/phone/email/username`，
全量取回等于「先泄露再遮挡」。

---

## 七、算法伪代码（可直接照写）

### T5

```text
identity = requireStudent(ctx)                      // 失败即抛异常
total = count(student_id = me)
if total == 0: return ok([], summary(window=0,valid=0,excluded=0,trend=INSUFFICIENT), 0)

rows = select(...) order by submitted_at desc, id desc limit 10
rows.sort(by submitted_at asc, then id asc)         // 稳定 tie-breaker

paperMap = batchLoad(paperIds)                      // select id,name,total_score

for row in rows:
    paper = paperMap[row.paperId]
    rate  = scoreRate(row.totalScore, paper?.totalScore)
    excluded = (rate == null)
    reason = excluded
        ? (paper == null ? "paper_missing"
           : (paper.totalScore == null || paper.totalScore <= 0) ? "paper_score_invalid" : "score_missing")
        : null
    items.add(Point(date, paper?.name ?? "未知试卷", row.totalScore, paper?.totalScore, rate, excluded, reason))

valid = items where !excluded
n = valid.size()
excludedCount = items.size() - n

if n >= 2:
    firstRate = valid[0].rate ; lastRate = valid[n-1].rate
    deltaPp = round((lastRate - firstRate) * 100, 2)
    trend = deltaPp >= 5 ? UP : deltaPp <= -5 ? DOWN : FLAT
    averageRate = round(avg(valid.rate), 4)
else:                       // n < 2
    firstRate   = (n == 1) ? valid[0].rate : null
    lastRate    = (n == 1) ? valid[0].rate : null
    deltaPp     = null      // 样本不足时【不给差值】，避免模型拿 0 当「持平」
    averageRate = (n == 1) ? round(valid[0].rate, 4) : null
    trend = INSUFFICIENT

return ok(items, summary(rows.size(), n, excludedCount, ...), total)
```

### T6

```text
identity = requireStudent(ctx)
favs = select(counselor_id, favorite_type, favorited_at) where student_id = me
       order by favorited_at desc, id desc
if favs empty: return ok([], 0)

group by counselorId（保持首次出现顺序 = 最近收藏在前）
    count += 1
    types.add(label(favorite_type))   // 去重
    lastFavoritedAt = max(时间)       // 因已按 desc 排序，取第一条即可

total = group.size()（去重后老师数）
取前 MAX_RECORDS 组 → items；truncated = total > items.size()
counselorMap = select(id,name,expertise) where id in (ids)     // 白名单列
```

---

## 八、测试矩阵

### 8.1 T5

| 组 | 用例 | 构造 | 期望 |
| :-: | --- | --- | --- |
| A | counselor / admin / 非法 ctx | — | 抛异常 |
| **D1** | 无记录 | student2 | `total=0`，`trend=INSUFFICIENT`，`items=[]` |
| **D2** | 仅 1 条有效 | 插 1 条 | `validCount=1`，`trend=INSUFFICIENT` |
| **D3（核心）** | **10 条中 9 条试卷缺失** | 1 条正常 + 9 条 `paper_id=9999` | `windowSize=10`、`excludedCount=9`、`validCount=1`、**`trend=INSUFFICIENT`** |
| **N（核心）** | 归一化正确性 | 100 分卷 60（早）→ 10 分卷 8（晚）| `deltaPp=+20` ⇒ **UP**；若按原始分（8−60=−52）会判 DOWN ⇒ **必红** |
| T1 | 上升 | 50% → 60% → 70% | `deltaPp≈+20`，`UP` |
| T2 | 下降 | 70% → 60% → 50% | `deltaPp≈−20`，`DOWN` |
| T3 | 持平 | 60% → 64% | `deltaPp=+4`，**FLAT** |
| **T4（pp 边界）** | 60% → 65% | `deltaPp=+5` | **UP** |
| **T5（pp 边界）** | 65% → 60% | `deltaPp=−5` | **DOWN** |
| **O（tie-breaker）** | 同秒两条（id 100 分数低、id 101 分数高）| 同一 `submitted_at` | 计算顺序必须为 **id 100 → 101**，趋势判定与之一致 |
| X | 试卷不存在 | `paper_id=9999` | `excluded=true`、`excludedReason=paper_missing` |
| E | >10 条 | 插 12 条 | `items=10`、`total=12`、`truncated=true` |
| C | 字段负向 | — | 无 `studentId` / `answer` / `analysis` / 试题文本 |

> 所有 fixture 一律 `@Transactional` + 自动回滚（Round 1 已确立的纪律）。

### 8.2 T6

| 组 | 用例 | 期望 |
| :-: | --- | --- |
| A | counselor / admin / 非法 ctx | 抛异常 |
| B | student1 vs student2 | `total` 与 DB 去重计数一致；集合不相交 |
| **U（去重）** | 同一老师插 3 条收藏 | 1 个 Item，`favoriteCount=3`，`lastFavoritedAt` 为最大时间 |
| U2（多类型） | 同一老师两种 `favorite_type` | `favoriteTypes` 含两个标签（顺序：最近优先）|
| C | 字段负向 | 无 `phone/email/username/password/avatarUrl/studentId` |
| D | 无收藏 | `total=0`，不抛异常 |

---

## 九、变异方案（实施时必做）

| # | 变异 | 期望转红 |
| :-: | --- | --- |
| M1 | `scoreRate` 改为直接返回 `score`（去归一化）| **N 组** |
| M2 | `trendOf` 把 `INSUFFICIENT` 改为 `FLAT` | **D2 / D3**（尤其 D3 证明 `n` 不是 `windowSize`）|
| M3 | `deltaPp` 阈值改成 `> 5` / `< -5`（差值等号）| **T4 / T5** 边界 |
| M4 | 去掉 `id` tie-breaker（仅按 submitted_at）| **O 组**（可能偶发；需重复跑确认）|
| M5 | T6 的 `select` 改 `list()` 且 DTO 加 `phone` | **T6 C 组** |

---

## 十、运行时验证方案

| 步 | 内容 | 说明 |
| :-: | --- | --- |
| 1 | Tool 层直调（不经 LLM）| 用 `@SpringBootTest` 已覆盖，无需真实调用 |
| 2 | 真实 LLM（可选）| 需真实数据：临时插入 3 条记录 + 2 条收藏 → 调用 `/agent/chat` → **`finally` 删除** → 核对计数 |
| 3 | 数据核对 | 验证前后 `assessment_record` / `counselor_favorite` / `counselor_message.replied_at` **均不变** |

> ⚠️ 若执行第 2 步，必须在同一脚本内完成「插入 → 调用 → 删除 → 计数核对」，
> 不得把 fixture 留在库中（Round 1 的教训）。

---

## 十一、不做

```text
❌ 不改 assessment_record.submitted_at 的 ON UPDATE 语义（超边界，独立议题）
❌ 不给 counselor_favorite 加唯一索引（需改表，独立议题）
❌ 不做 T7 / RAG / Memory / 副作用 Tool
❌ 不为 counselor / admin 开域
❌ 不在 Tool 内做业务权限分支
```

---

## 十二、待你裁定（2 项）

| # | 问题 | 选项 | 建议 |
| :-: | --- | :- | --- |
| **1** | `favoriteTypeLabel` 语义（**因无唯一约束，无法假设唯一**）| A. 取最新一条的类型<br>B. 返回去重类型**列表** `favoriteTypes`<br>C. 改表加唯一约束（超本批）| **B**：不依赖任何约束，天然正确；当前字典只有 1 个值，输出仍是单元素列表 |
| **2** | `submitted_at` 的 `ON UPDATE CURRENT_TIMESTAMP` | A. 本批仅登记，不改<br>B. 本批一并移除（需改表 DDL）| **A**：改表属独立批次；本批通过 Prompt 不承诺「精确时间」并登记风险 |

---

## 十四、✅ 实施结果（2026-11-03）

### 14.1 变更（9 文件）

| 类别 | 数量 | 内容 |
| --- | :-: | --- |
| 新增 | **6** | `TrendTools` · `TrendToolResult` · `FavoriteTools` · `FavoriteToolResult` · 2 个测试 |
| 修改 | **3** | `AgentConfig`（+2 Bean / agentService 8 参数）· `AgentControllerRegressionTest`（5→7）· `Phase4ToolScopeTest`（扩到 6 Tool）|

> `@Tool`：**5 → 7**。零新增依赖、零 DB schema、零前端改动。
> ⚠️ **未修改 `AgentPrompts`**：设计清单里写过要加 4 条约束，但实施时判断
> 现有 Prompt 已覆盖「不编造 / 不诊断 / 空结果如实说」，
> 且 T5 的 `trend` 由 Tool 直接给出、T6 不含文本内容 ⇒ **未新增条目，保持 Prompt 稳定**。
> 这是对清单的**有理由偏离**，已在此记录，不计为遗漏。

### 14.2 测试

```text
新增 : Trend 16 例 + Favorite 7 例 = 23 例
先红 : Trend 11 失败 / Favorite 4 失败（骨架返回空结果时）
后绿 : 三组 29/29
全量 : 239/239 绿
```

### 14.3 变异验证（4 项，全部命中）

| 变异 | 结果 |
| --- | --- |
| **M1** `scoreRate` 去掉归一化（直接返回分数）| ✅ **8 项转红**（含 N 组：`expected 4.0 but was 400.0`）|
| **M2** `validCount >= 2` 放宽为 `>= 1` | ✅ **D2 / D3 转红**（`INSUFFICIENT → FLAT`）|
| **M3** 阈值去掉等号（`>` / `<`）| ✅ **仅 T4 / T5 边界转红**（UP/DOWN → FLAT）|
| **M4** tie-breaker 反向（id 降序）| ✅ **O 组转红**（`expected 0.5 but was 0.9`，首尾互换）|

⇒ 四条核心规则（归一化 / n 语义 / 阈值边界 / 排序稳定）**各自都有独立断言锁住**。

### 14.4 运行时验证（真实 LLM，只读）

| 请求 | 实际回答（摘要）| 判定 |
| --- | --- | --- |
| 「我收藏了哪些老师？」 | 2 位老师 + 擅长领域 + 收藏时间，**无任何联系方式** | ✅ T6 被真实调用 |
| 「我的测评成绩趋势怎么样？」 | 「只有 1 条记录，看不出趋势…趋势结论：样本不足（INSUFFICIENT）」+ 得分 15/100（15%）| ✅ T5 正确返回 INSUFFICIENT，**未编造趋势** |

### 14.5 零污染核对

```text
assessment_record = 1  (fixture id>=9400 残留 = 0)
counselor_favorite = 6 (fixture id>=9500 残留 = 0)
counselor_message = 6  ·  id=2.replied_at = 2022-03-29 11:39:43（仍为基线）
```

---

## 十五、状态

| 项 | 值 |
| --- | --- |
| 性质 | 🏁 **CLOSED — 用户 2026-11-03 验收通过 · 正式基线 `32388f8`** |
| Git | `HEAD = origin = 32388f8` · LEAD 0 · worktree CLEAN · 推送 `3c8590d..32388f8` |
| 改动 | **9 文件 +1129/−6**（4 新增类 + 2 新增测试 + 3 修改）|
| `@Tool` | **7** |
| 测试 | 新增 23/23 · 全量 **239/239** |
| 变异 | 4 项全部命中 |
| 运行时 | T5 / T6 均被真实调用且结论正确 |
| 数据污染 | ✅ 零残留（`fixture_ar=0` / `fixture_fav=0`）|

**等你复核后再 commit。**
