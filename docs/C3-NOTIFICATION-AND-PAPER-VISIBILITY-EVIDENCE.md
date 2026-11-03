# C3 后续取证：Notification 结构 + Paper/Question 可见性影响面

> **性质**：🔍 **只读取证（零代码改动）**
> **基线**：`HEAD = origin = 36bb760` · LEAD 0 · worktree CLEAN
> **上游业务裁定**（用户 2026-10-27）：
> · `notification` = **全站通知 + 用户私有通知并存** → 需先确认现有模型能否区分
> · `is_deleted = 1` = **已删除** → 匿名可见已删除试卷 = **已确认违反业务规则**
> · `assessment_paper/question` = **普通用户/匿名不得访问未发布/不可用内容** → 独立资源可见性缺口
> · `knowledge_article` = **不新增草稿/审核态** → 保持现状，不立项

---

## 一、Notification 结构取证（用户要求 5 点，逐项回答）

| # | 要求取证 | 结果 |
| :-: | --- | --- |
| 1 | Entity / 表字段 | `id · title · coverUrl · category · publishedAt · content · createdAt` —— **无任何用户 / 角色 / 受众字段** |
| 2 | 创建通知的接口 | `POST /notification/save`、`/notification/add`（标准 CRUD）；`/list` 标 `@IgnoreAuth` |
| 3 | 管理员发布全站通知的路径 | 旧 Admin `views/modules/tongzhi/{list,add-or-update}.vue` —— 纯管理端录入，**无目标用户选择器** |
| 4 | **「用户私有通知」当前是否已存在业务路径** | 🔴 **不存在** —— 全仓检索：仅 `NotificationController/Dao/Entity/View/Service/ServiceImpl` 自身引用该实体，**没有任何代码按 userId/studentId 写通知** |
| 5 | 查询接口如何决定用户看到哪些通知 | **没有任何按用户过滤** —— 前端直接 `GET /notification/list`，返回全表 |

### 1.1 前端消费方式（关键佐证）

```ts
// frontend/src/api/content.ts:33-36
/** 通知列表：GET /notification/list（@IgnoreAuth 公开 ✓） */
export async function listNotifications(): Promise<NotificationItem[]> {
  const data = await unwrap<unknown>(http.get('/notification/list'))
  return pickList<NotificationItem>(data).list
}
```

- `NotificationListView.vue` / `HomeView.vue` 均直接展示该列表
- 页面注释明确写「**公开接口**」
- **不存在**「我的通知」「未读」「按用户过滤」等任何调用

### 1.2 结论（按用户的方法论要求）

```text
「用户私有通知」在当前系统中：
   · 无法【表达】：数据模型没有归属字段
   · 无法【写入】：没有按用户创建通知的代码路径
   · 无法【查询】：列表接口无任何用户维度过滤

⇒ 这不是「已存在但权限缺失」，而是【功能尚未存在】
⇒ 不得凭业务设想推导成【现存漏洞】
⇒ 记录为【未来需求】：若要做私有通知，需
     ① 加归属字段（user_id / 受众类型）
     ② 增加按用户写入的路径
     ③ 查询按用户过滤
   —— 属【数据模型变更】，独立立项，不并入 DataScope
```

> ⚠️ 仍然成立的一条事实：**当前匿名可读全部通知**。在「全站公告」语义下这是正确的；
> 只有在未来私有通知上线后，这一现状才会变成问题。**现在不构成缺陷。**

---

## 二、AssessmentPaper / Question 影响面

### 2.1 已确认的缺陷事实（业务裁定后）

| 事实 | 状态 |
| --- | --- |
| 两张试卷 `status=1`、`is_deleted=1`（= 已删除）| 实测 |
| `/assessmentPaper/page` 与 `/list` 对**匿名**返回 2 条 | 实测 |
| `is_deleted` 仅作为**客户端可选过滤参数**（`params.isDeleted`）| `AssessmentPaperDao.xml:60-62` |
| **服务端不强制**「仅已发布 / 未删除」 | 代码确认 |

⇒ 依业务裁定：**匿名可读已删除试卷 = 已确认违反可见性规则**（不再需要业务确认）。

### 2.2 同一根因下的既有挂账

```text
SEC-QUESTION-ANSWER-PUBLIC  🔴 P1（Identified / Unfixed）
   /assessmentQuestion/list 匿名返回 answer / analysis（正确答案与解析）
```

- 新 Vue 已**主动规避**调用该端点（`frontend/src/api/assessment.ts:113-118` 有明确注释与理由）
- 但它仍**对外可访问** ⇒ 与本项是**同源**（资源状态/字段可见性治理），但是**不同问题**：
  - 本题 = **状态可见性**（未发布/已删除不得访问）
  - 既有挂账 = **字段外泄**（答案/解析不应返回）

### 2.3 消费方清单（决定修复不能"一刀切"）

| 消费方 | 端点 | 需要看到什么 |
| --- | --- | --- |
| 新 Vue `AssessmentPaperListView.vue` → `api/assessment.ts` | `/assessmentPaper/list`（公开）| **仅已发布、未删除**；且**不调** question |
| 旧 Admin `exampaper/list.vue`、`add-or-update.vue`、`exam.vue` | `/assessmentPaper/page`、`/info`、`/assessmentQuestion/*` | **必须看到未发布/已删除**，否则无法管理 |
| 旧 Admin `examquestion/list.vue`、`add-or-update.vue` | `/assessmentQuestion/*` | 同上 |
| 旧 Admin `examrecord` / `examredetails` / `examrewrongquestion` 表单 | 引用试卷/试题 | 依记录回溯，需可见 |

### 2.4 🔴 由此得出的关键约束

```text
❌ 不能只在 selectListView 上加 `and is_deleted = 0 and status = 已发布`
   —— 那会让管理端也看不到自己管理的未发布/已删除试卷，直接破坏管理功能

✅ 必须区分【视角】：
     管理端（admin）      → 全量（含未发布 / 已删除）
     学生 / 匿名          → 仅已发布 + 未删除
```

这与 `DataScope` 的一个关键区别必须写清：

```text
DataScope ownership（归属维度：这是谁的数据）
        ≠
resource visibility（状态维度：这条数据当前是否对外可用）
```

⇒ **本项不属于 `DataScope Contract Audit`**，是独立的资源可见性治理。

---

## 三、C3 正式拆分（业务裁定后）

```text
C3（原「无归属维度」合并类）→ 已分裂为四类
│
├─ knowledge_article
│    └─ 🟢 保持现状（不新增草稿/审核态）
│         可移除无效 DataScope 调用并记录理由（后续小项）
│
├─ counselor
│    └─ 🟢 名录型资源，不按用户所有权限制
│         但须与既有挂账 DEF-COUNSELOR-LIST-PUBLIC-PII 合并裁定字段集
│
├─ assessment_paper
│    └─ 🔴 资源可见性缺口（独立批次，不并入 DataScope）
│         · 未发布不得访问
│         · 已删除不得访问
│         · 必须区分管理端 / 用户端视角
│
├─ assessment_question
│    └─ 🔴 随试卷状态受可见性约束
│         且已存在 SEC-QUESTION-ANSWER-PUBLIC（答案/解析匿名可读）
│
└─ notification
     └─ ⚠️ 私有语义【当前无法表达】⇒ 记为未来需求
          现状（匿名可读全站公告）不构成缺陷
```

---

## 四、待裁定（3 项）

| # | 问题 | 选项 | 说明 |
| :-: | --- | --- | --- |
| **1** | Paper/Question 可见性如何区分视角 | **A. 在 Controller 按角色注入状态过滤参数**（admin 不注入 / 其余注入「已发布+未删除」）<br>**B. 拆分两个端点**（管理端 `/page` 全量；公开端 `/published`）<br>**C. 在 XML 内按角色判断** | **建议 A**：改动最小、复用 `DataScope` 已有的「参数注入」模式；但需新增**状态常量**避免 SQL 写死。<br>⚠️ B 更干净但属接口契约变更；C 会把角色判断塞进 SQL，不推荐 |
| **2** | 是否将 Paper/Question 立项为**独立批次**（与 DataScope 解耦） | A. 是<br>B. 并入 DataScope | **A**：性质是资源可见性，不是归属维度 |
| **3** | `notification` 私有通知是否登记为**未来需求** | A. 登记<br>B. 不登记 | **A**：登记「需加归属字段 + 写入路径 + 查询过滤」，不实施 |

> `SEC-QUESTION-ANSWER-PUBLIC`（字段外泄）与本项（状态可见性）**同源不同问题**，
> 建议**同批次处理但分别验收**，避免把两件事混成一个「修复完成」的结论。

---

## 五、边界

```text
❌ 未修改任何代码 / SQL / 权限 / 前端
❌ 未把「当前无字段」当作「永远不需要约束」
❌ 未把「通知现在匿名可读」直接判成漏洞（私有通知功能尚不存在）
❌ 未把 paper/question 问题归入 DataScope Contract Audit
```

---

## 六、状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `36bb760` / `36bb760` · LEAD 0 |
| worktree | **CLEAN**（零代码改动）|
| 代码 / SQL / DB / 前端 / pom | **全部 0 改动** ✓ |
| C3 Business Visibility | 🟢 **业务裁定完成 → 已拆分为 4 类**；Paper/Question 转独立立项 |
| `DataScope` 审计 | 🟡 `AUDIT / M2 PROVEN FOR 3 C1 CASES + 1 C4 CASE (Student)` —— **不因 C3 拆分而关闭** |
| Student 修复批次 | ✅ 保持 CLOSED |

**等你裁定 §四 三项。**
