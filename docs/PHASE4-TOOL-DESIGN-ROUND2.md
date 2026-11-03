# Phase 4 · Round 2 设计：T5 测评趋势 / T6 我的收藏

> **性质**：📐 **设计（零代码 · 零 DB · 零依赖）**
> **基线**：`HEAD = origin = 3c8590d` · LEAD 0 · worktree CLEAN
> **上游**：Round 1 **CLOSED**（`3c8590d`）—— Query → Tool → 受限数据 → LLM 已闭环
> **本批定位**：能力从「**告诉你有什么数据**」推进到「**根据历史数据告诉你发生了什么变化**」

---

## 一、为什么必须先设计再写

Round 1 的 4 个 Tool 都是**事实查询**：返回什么由数据库决定，代码只做投影。
Round 2 出现一个本质变化：

```text
T5 的结论（上升 / 下降 / 持平）是【计算出来的】，不是数据库里现成的
```

若把原始分数丢给 LLM「让它自己看趋势」，会得到：
① 不稳定（同一数据两次回答可能不同）② 不可复核 ③ 可能把「不同满分的卷子」直接比大小。

因此本批**先把规则写死**，再实施。

---

## 二、取证（决定可行性与必须规避的坑）

| 事实 | 实测值 | 对设计的影响 |
| --- | --- | --- |
| `assessment_record` 记录数 | **student1 仅 1 条**（2022-03-29）| 🔴 **现有数据算不出趋势** ⇒ 测试必须用事务 fixture |
| 两张试卷满分 | `100` / `10` | 🔴 **原始分不可跨卷比较**，必须归一化 |
| 两张试卷状态 | 均 `is_deleted=1` | 历史记录仍须可解释（不能因试卷删除就丢历史）|
| `assessment_record` 字段 | `recordNo / studentId / paperId / totalScore / submittedAt` | 无「正确题数 / 用时」⇒ 趋势只能基于**得分率 + 时间** |
| `counselor_favorite` | 每生 2 条，`favorite_type` 均为 `1` | 字典仅 1 个取值（"收藏"）|
| 收藏目标 | `counselor`（含 `phone/email/username/password`）| 与 T4 同样必须**白名单 select** |

---

## 三、T5 `getMyAssessmentTrend` 规则设计

### 3.1 样本与窗口

```text
取样：本人记录，按 submitted_at 排序，取【最近 MAX_RECORDS = 10 条】
计算：按时间【升序】计算趋势
返回：按时间【升序】列出各点
total = 本人记录总数；truncated = total > items.size()
```

### 3.2 归一化（硬规则，不可省）

```text
scoreRate = totalScore / paper.totalScore      （0 ~ 1）
```

**为什么必须归一化**（这是本批最重要的正确性规则）：

| 场景 | 原始分 | 得分率 | 若按原始分会得到 |
| --- | :-: | :-: | --- |
| 第 1 次：100 分卷考 60 | 60 | 60% | — |
| 第 2 次：10 分卷考 8 | 8 | **80%** | ❌ 判成「下降」 |

⇒ **原始分跨卷比较是错的**。所有比较与趋势判定一律基于 `scoreRate`。

### 3.3 无效样本

| 情形 | 处置 |
| --- | --- |
| `paper` 不存在（被物理删除）| 该条 **excluded**，计入 `excludedCount`，不参与统计 |
| `paper.totalScore` 为空或 ≤ 0 | 同上（无法归一化）|
| `totalScore` 为空 | 同上 |

> 被排除的记录仍出现在 `items` 中并标记 `excluded=true`，
> **不静默丢弃**（否则模型会以为「我只考过 3 次」）。

### 3.4 趋势判定（确定性规则 · 代码内完成）

```text
n = 有效样本数
n < 2                      → trend = INSUFFICIENT
deltaRate = 最后一条有效得分率 − 第一条有效得分率
|deltaRate| < 5 个百分点    → trend = FLAT
deltaRate ≥ +5 个百分点     → trend = UP
deltaRate ≤ −5 个百分点     → trend = DOWN
```

**为什么不用线性回归**：样本上限 10、且用户真正想知道的是「最近比之前好了吗」；
首尾差 + 阈值**可解释、可复现、可测试**。
**已知局限（必须写进 Result 或 Prompt）**：忽略中间波动（如「先降后升」会被判为 UP）。

### 3.5 Result 契约

```java
record TrendToolResult(
    List<TrendPoint> items,     // 每点：date, paperName, score, maxScore, scoreRate, excluded
    TrendSummary summary,       // 见下
    long total,
    boolean truncated
) {}

record TrendSummary(
    int windowSize,        // 参与计算的条数（≤10）
    int validCount,        // 可归一化条数
    int excludedCount,     // 被排除条数
    Double firstRate,      // 第一个有效得分率；无效时为 null
    Double lastRate,       // 最后一个有效得分率
    Double deltaRate,      // lastRate - firstRate
    Double averageRate,    // 有效样本平均得分率
    String trend           // UP / DOWN / FLAT / INSUFFICIENT
) {}
```

**刻意不返回**：原始计算过程、每题的分数、任何试题内容。
**刻意不做**：预测下次分数、判断「是否严重」、给诊断结论。

---

## 四、T6 `getMyFavorites` 规则设计

| 项 | 内容 |
| --- | --- |
| 数据源 | `counselor_favorite` + `counselor`（**只取 id/name/expertise**）|
| 行范围 | `eq(student_id, 当前用户)` |
| 去重 | 按 `counselorId` 去重：同一老师多次收藏只出现一条，附 `favoriteCount` 与 `lastFavoritedAt` |
| 类型标签 | 字典 `counselor_favorite_type`（当前仅 `1=收藏`），代码内翻译 |
| 返回字段 | `counselorId` · `counselorName` · `expertise` · `favoriteTypeLabel` · `favoriteCount` · `lastFavoritedAt` |
| **不返回** | `phone` / `email` / `username` / `password` / `avatarUrl` / `studentId` |
| 空结果 | `items=[] , total=0`（正常结果）|

> **为什么不直接复用 T4**：T4 是「平台有哪些老师」，T6 是「我关注了哪些老师」；
> 一个是全量目录、一个是用户私有关系，数据来源与授权语义不同。

---

## 五、测试矩阵

### T5

| 组 | 用例 | 期望 |
| :-: | --- | --- |
| A | counselor / admin / 非法上下文 | 抛异常 |
| D | 0 条 / 1 条记录 | `trend=INSUFFICIENT`，**不得编造趋势** |
| **N（核心）** | 100 分卷 60 分 → 10 分卷 8 分 | 判 **UP**（60%→80%）；若实现用原始分会判 DOWN ⇒ 必红 |
| T | 构造 3 条：50% → 60% → 70% | `UP`，`deltaRate≈+0.20` |
| T | 构造 3 条：70% → 60% → 50% | `DOWN` |
| T | 构造 3 条：60% → 61% → 60% | `FLAT`（阈值 ±5pp）|
| X | 其中一条的试卷不存在 | 该条 `excluded=true`、`excludedCount=1`，其余照常统计 |
| E | 记录 > 10 条 | `items=10`、`total` 真实、`truncated=true` |
| C | 字段负向 | 不含 `studentId` / `answer` / `analysis` / 试题内容 |

### T6

| 组 | 用例 | 期望 |
| :-: | --- | --- |
| A | 域拒绝 ×3 | 抛异常 |
| B | 两学生各自调用 | 结果集不相交，`total` 与 DB 一致 |
| U | 同一老师收藏 2 次（fixture）| 1 条结果，`favoriteCount=2`，`lastFavoritedAt` 取最大 |
| C | 字段负向 | 不含 `phone/email/username/password/avatarUrl` |
| D | 无收藏 | `total=0`，不抛异常 |

### 变异验证（实施时必做）

| 变异 | 期望 |
| --- | --- |
| 去掉归一化（直接用 `totalScore` 比较）| **N 组转红** |
| 把 `INSUFFICIENT` 改成返回 `FLAT` | D 组转红（证明「样本不足」被真实断言）|
| T6 的 `select` 改回 `list()` 且 DTO 加 `phone` | C 组转红 |

---

## 六、实施顺序（若批准）

```text
1. TrendToolResult / TrendPoint / TrendSummary / FavoriteToolResult（先定契约）
2. T5 + 单测（含归一化负向 N 组）
3. T6 + 单测（去重 + PII 负向）
4. AgentConfig 注册（工具数 5 → 7）+ 更新「5 个工具」断言
5. AgentPrompts：趋势结论只能来自 Tool；样本不足必须说「数据不够」；不做预测/诊断
6. 全量回归（当前 216）
```

**不做**：不改 DB / 不加依赖 / 不动 T0–T4 行为 / 不做 Memory / RAG / 副作用 Tool。

---

## 七、待你裁定（5 项）

| # | 问题 | 选项 | 我的建议 |
| :-: | --- | --- | --- |
| **1** | 趋势判定算法 | A. 首尾差 + ±5pp 阈值<br>B. 线性回归斜率<br>C. 平均分前后半段对比 | **A**：可解释、可复现；B 在 ≤10 样本上不稳定 |
| **2** | 样本窗口 | A. 最近 10 条<br>B. 全部记录 | **A**：与 `MAX_RECORDS` 一致，避免长历史压垮 token |
| **3** | 试卷缺失时的处置 | A. 排除并计数<br>B. 用原始分兜底 | **A**：B 会重新引入「跨卷不可比」错误 |
| **4** | T6 去重 | A. 按老师去重 + `favoriteCount`<br>B. 逐条返回 | **A**：用户问「我关注了谁」时，重复条目没有信息量 |
| **5** | 本批范围 | A. T5 + T6 同批<br>B. 只做 T5，T6 后置 | **A**：T6 成本低（单表 + 去重），且与 T5 无耦合；若你更想控批次，选 B 也合理 |

---

## 八、状态

| 项 | 值 |
| --- | --- |
| 性质 | 📐 **设计（零代码 · 待裁定 5 项）** |
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** |
| 当前 `@Tool` | **5**（T0–T4）|
| 本批后预计 | **7**（+T5、T6）|
| 上游 | Round 1 **CLOSED**（`3c8590d`）|

**等你裁定 §七 后，进入「Round 2 实施清单 + 测试矩阵」（仍不写代码）。**
