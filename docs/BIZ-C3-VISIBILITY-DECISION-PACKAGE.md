# C3 Business Visibility —— 决策包

> **性质**：📐 **只读取证 + 待业务裁定（零代码改动）**
> **基线**：`HEAD = origin = 36bb760` · LEAD 0 · worktree CLEAN
> **定位**：`DataScope` 审计主线当前**最高优先**项 —— 不解决则 `DataScope` 审计难以关闭
>
> **核心问题（必须逐模块回答）**：
>
> ```text
> 这个资源【本来就应该所有人看到】？
>         还是
> 其实存在用户 / 角色 / 发布态范围，只是目前【没有实现】？
> ```

---

## 一、为什么这是主线

`DataScope` 审计的收敛取决于：能否对每个模块给出「**应不应该有范围约束**」的结论。

```text
C1  已生效        → 已证明（3 个 C1 + 1 个 C4 的 M2 变异证据）
C2  由资源级拒绝覆盖 → 已明确边界
C4  缺失后已修复   → Student 已 CLOSED
C3  ❓ 未判定      → 卡住主线
```

> **C3 不解决，就无法回答「`DataScope` 的 19 个调用点里，究竟哪几个是真正的缺口」。**

---

## 二、逐模块取证（只读）

### 2.1 `notification`（🔴 最关键）

**数据模型**（`DESC notification`）：

```text
id · title · cover_url · category · published_at · content · created_at
```

- **无任何用户 / 角色 / 受众字段**
- 实际数据：`通知名称1…5` / `通知详情1…5`，`category` 取值 `{1, 2}`

**当前行为（实测）**：

| 身份 | 结果 |
| --- | :-: |
| 匿名 | **200**（可读 5 条）|
| student | 200 |
| counselor | 200 |
| admin | 200 |

**必须明确的 A/B**：

| 选项 | 含义 | 若为 B 的后果 |
| --- | :-: | --- |
| **A. 全站公告** | 所有人可见，现状正确 | 无需改动；可考虑移除 no-op 的 `DataScope` 调用并记录理由 |
| **B. 用户私有通知** | 每人只看自己的 | 🔴 **数据模型缺归属字段** —— 需加表字段 + 改查询，**远超 `DataScope` 范畴**，必须独立立项 |

> ⚠️ **现有证据只能证明「当前接口在测试条件下允许匿名读取」，不能证明它一定是全站公告。**
> 这正是本项不能由技术侧推断的原因。

### 2.2 `knowledge_article`

**数据模型**：与 notification 同形（`id · title · cover_url · category · published_at · content · created_at`），**无用户字段**。

**实际数据**：5 条，`content` 长度均为 **7 字符**（占位符「健康知识名称1」），`category ∈ {1,2}`。

| 选项 | 说明 |
| --- | :-: |
| A. 全站公开 | 倾向此选项（知识库通常公开）|
| B. 有草稿 / 审核态 | ⚠️ **当前表没有 `status` 字段** ⇒ 若业务需要草稿态，属**数据模型缺失**，独立立项 |

> 附带：`content` 为占位符 ⇒ 若未来做 RAG，需先做知识内容建设（已登记前置条件）。

### 2.3 `assessment_paper`（🔴 本轮新发现：存在维度但未强制）

**数据模型**：

```text
id · name · duration_minutes · total_score · status · is_deleted · created_at
```

⇒ **存在 `status` 与 `is_deleted` 两个可见性相关字段** —— 与其他 C3 模块不同！

**当前实现**（`AssessmentPaperDao.xml` L51–62）：

```xml
<if test="params.status != null and params.status != ''"> and a.status = #{params.status} </if>
<if test="params.isDeleted  != null and params.isDeleted  != ''"> and a.is_deleted = #{params.isDeleted} </if>
```

⇒ 它们是**客户端可选查询条件**，**不是服务端强制的可见性约束**。
⇒ 实测：两张试卷 `status=1`、`is_deleted=1`，`/page` 对匿名 / student 均返回 **2 条**。

**必须明确**：

| # | 问题 | 说明 |
| :-: | --- | --- |
| 1 | `is_deleted` 语义是「1=已删除」还是「1=正常」？ | 若是「1=已删除」，则**已删除试卷仍对匿名可见** ⇒ 独立缺口 |
| 2 | 是否应强制「只返回已发布（`status` 为某值）」？ | 若应强制，属**服务端可见性约束缺失**，需独立立项（不是 `DataScope` 的归属维度问题）|

### 2.4 `assessment_question`

**数据模型**：`id · paper_id · content · options · score · answer · analysis · question_type · sort_order · created_at`

- **无用户 / 受众字段**
- 试题依附于试卷（`paper_id`）⇒ **可见性应跟随试卷**

⇒ 裁定与 `assessment_paper` **绑定**：若试卷需要强制发布态过滤，试题同样需要（按 `paper_id` 关联）。

> 🔴 注意：试题含 `answer` / `analysis` —— **答案与解析外泄**会直接影响测评有效性。

### 2.5 `counselor`

**数据模型**：`id · username · password · name · phone · email · gender · avatar_url · expertise · resume · introduction · created_at`

- **无用户归属语义** —— 它是「咨询师名录」，不是「某人的数据」
- 当前：`/list` 匿名可读（8 字段 `PublicCounselorView`）；`/page`、`/info` **admin-only**

| 选项 | 说明 |
| --- | :-: |
| A. 公开目录 | 现状基本合理；但 `DEF-COUNSELOR-LIST-PUBLIC-PII` 仍挂账（需确认 8 字段是否仍含 PII）|
| B. 需按角色/范围收敛 | 与业务不符（学生需要看到咨询师才能选择预约）|

⇒ 倾向 A，但**须与既有挂账 `DEF-COUNSELOR-LIST-PUBLIC-PII` 合并裁定**。

---

## 三、汇总：五个模块的裁定项

| # | 模块 | 归属字段 | 其他可界定属性 | 需业务回答的问题 | 倾向 |
| :-: | --- | :-: | --- | --- | --- |
| 1 | `notification` | ❌ 无 | `category`（分类，非受众）| **全站公告 A / 用户私有通知 B** | 🔴 必须裁定 |
| 2 | `knowledge_article` | ❌ 无 | `category` | 全站公开 A / 需草稿审核态 B | 倾向 A |
| 3 | **`assessment_paper`** | ❌ 无 | **`status` · `is_deleted`** | `is_deleted` 语义？是否强制「仅已发布」？ | 🔴 必须裁定 |
| 4 | `assessment_question` | ❌ 无 | 随 `paper_id` | 与试卷绑定裁定 | 随 3 |
| 5 | `counselor` | ❌ 无 | 是名录非某人数据 | 公开目录字段集是否够？与 `DEF-COUNSELOR-LIST-PUBLIC-PII` 合并 | 倾向 A |

---

## 四、🔴 本轮的关键新发现（与既有认知不同）

此前 C3 被统一描述为「无归属维度」。本轮取证显示**它内部并不一致**：

```text
notification / knowledge_article / counselor
      → 确实没有任何用户维度字段

assessment_paper / assessment_question
      → 【存在】status / is_deleted 可见性字段
         但当前只是客户端可选过滤条件，没有服务端强制约束
```

⇒ **不得把 C3 五个模块当成同一类问题处理**。
试卷/试题的「是否强制仅已发布」是**独立于 `DataScope` 归属维度**的另一类可见性约束。

---

## 五、边界

```text
❌ 本文件不修改任何代码 / SQL / 权限 / 前端
❌ 不替业务做决定（所有 A/B 均标注为「待裁定」）
❌ 不把「当前无字段」当作「永远不需要约束」的证据
❌ 不把「存在其他控制层（@IgnoreAuth / assertAdminOnly）」当作授权正确的证据
```

---

## 六、待你裁定（4 项）

| # | 问题 | 选项 |
| :-: | --- | --- |
| **1** | `notification` 是全站公告还是用户私有通知？ | A 公告 / B 私有（B 需改表，独立立项）|
| **2** | `is_deleted` 语义（1=已删除 还是 1=正常）？ | 需确认；若为「已删除」→ 已删除试卷对匿名可见，属独立缺口 |
| **3** | 试卷/试题是否应**服务端强制**「仅返回已发布」？ | A 不需要 / B 需要（B 独立立项）|
| **4** | `knowledge_article` 是否需要草稿/审核态？ | A 不需要 / B 需要（B 需加字段，独立立项）|

> 裁定完成后，即可更新 C3 分类：哪些转「确认公开」（no-op 调用可移除并记录理由），
> 哪些转「独立立项」（可见性约束缺失）。

---

## 七、状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `36bb760` / `36bb760` · LEAD 0 |
| worktree | **CLEAN**（零代码改动）|
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** ✓ |
| 上游 | `DataScope` 审计：`AUDIT / M2 PROVEN FOR 3 C1 CASES + 1 C4 CASE (Student)` |
| T13 | ✅ PASS；**T13b 决定不做** |
| Student 修复批次 | ✅ 保持 **CLOSED**（不因 C3 工作重开）|

**等你裁定 §六 四项。**
