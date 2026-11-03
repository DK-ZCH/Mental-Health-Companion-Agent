# Phase 3 / Step 5c —— **实施台账**（逐批执行 · 每批独立提交）

> 依据：`PHASE3-STEP5C-MAPPING.md`（Round 2 映射已通过 ✓）
> 方案：**A**（改旧 API + 同步旧前端调用）✓ · 不做后端双路径兼容层 ✓
> 批次顺序（审阅裁定）：**Batch 1 → 2 → 3 → 4(`/yonghu` 最高风险) → 5(`users`) → 6(`dictionary`) → 全量残留审计 → Final E2E → Step 6**

## 0. 全批通用规则（先定死，避免每批重议）

### 0.1 **不改的东西（身份键 ≠ API 前缀）**

| 类别 | 示例 | 处置 |
| --- | --- | --- |
| 前端参数键 | `exampaperId` · `examredetailsId` · `studentId` | ❌ **不改** |
| admin 组件/目录身份 | `@/views/modules/exampaper/list` · `path:'/dictionaryExampaper` | ❌ **不改**（本轮只改 API 路径）|
| **`menu.js` 的 `tableName`** | 若某批**必须**改它（如 B4/B5 的登录/会话链）| ✅ **必须同步改 `isAuth(...)` 权限键** ✓（见 §4.2：`utils/utils.js:17` 为**严格键相等匹配** ✓，不同改会导致管理端按钮权限恒 false ✗）|
| **`isAuth(tableName, …)` 第一参数** | — | ✅ **与 `menu.js` 条目的 `tableName` 强配对** ✓（`main.js:61 Vue.prototype.isAuth = isAuth` ✓）|
| front 页面路径 | `./pages/exampaper/list.html` · `vue.jump("../exampaper/list.html")` | ❌ **不改**（`front` 未纳管，移动文件风险高）|
| `/file/*` | `/file/upload` | ❌ **不改名**（仅验证仍可用 ✓）|
| 数据库 | — | ❌ **不写库**；baseline 必须不变 ✓ |

### 0.2 **只改的两种字面量**

```text
① Controller 类级 @RequestMapping("旧前缀")
② 引号后紧跟前缀的 API 字面量：  (['"`])旧前缀/   →   $1新前缀/
```

> ⚠️ **已发现的陷阱**：`exampaper/` 也出现在**页面路径**（`./pages/exampaper/list.html`、`../exampaper/list.html`）⇒ **朴素全局替换会打断页面跳转** ✗
> ⇒ 必须用「**引号后紧跟**」的锚定正则 ✓（页面路径以 `/` 或 `.` 开头 ⇒ 天然排除 ✓）

### 0.3 每批固定验收模板（A–F）

| 项 | 内容 |
| --- | --- |
| **A 静态** | 旧路径残留扫描（java/front/admin）· 新路径扫描 · 菜单/`tableName` 扫描 · 白名单扫描 |
| **B API** | 旧路径不再作为正式入口 · 新路径正常响应（`200` + `code=0`）|
| **C 权限** | 按模块实际存在的角色验证（**不为无业务意义的角色硬造测试** ✓）|
| **D 回归** | `mvn clean test` → **47/47** ✓ |
| **E 数据库** | baseline **不变** ✓（路径变更不写库 ✓）|
| **F Git** | **一个独立提交** ✓（Batch 3 出问题不需回滚 Batch 1/2 ✓）|

---

## 1. Batch 1：低耦合静态前缀（4 个）

| # | 旧前缀 | 新前缀 | Controller（类名已是新名 ✓）| 类级映射位置 |
| :-: | --- | --- | --- | --- |
| 1 | `/exampaper` | `/assessmentPaper` | `AssessmentPaperController` | L44 |
| 2 | `/examquestion` | `/assessmentQuestion` | `AssessmentQuestionController` | L44 |
| 3 | `/jiankangzhishi` | `/knowledgeArticle` | `KnowledgeArticleController` | L44 |
| 4 | `/tongzhi` | `/notification` | `NotificationController` | L44 |

### 1.1 只读清点结果（已完成 ✓）

| 前缀 | java 路径字面量 | front API | admin API | admin import（不改 ✓）| front `前缀Id`（不改 ✓）|
| --- | :-: | :-: | :-: | :-: | :-: |
| `exampaper` | **1**（控制器类级）| **6** | **11** | 2 | 0 |
| `examquestion` | **1** | **6** | **9** | 1 | 0 |
| `jiankangzhishi` | **1** | **12** | **5** | 1 | **2** |
| `tongzhi` | **1** | **12** | **5** | 1 | **2** |
| **合计** | **4 行** | **36** | **30** | 4 | 4 |

**关键事实** ✓：
* java 侧**每个控制器只需改 1 行**（方法级映射均为相对路径 ✓；`RequestMapping` 总数 10–13 但路径字面量仅 1 ✓）
* **`menu` 表不存在** ✓ ⇒ 菜单为**静态文件**（`admin/src/menu.js` + `router-static.js`）⇒ 本批**不需要任何数据库写入** ✓✓（第 E 条自动满足 ✓）
* admin 侧调用形如 `url: "exampaper/page"` / `` url: `exampaper/info/${id}` `` ✓（均为**引号后紧跟前缀** ✓）

### 1.2 执行计划（下一步）

```text
1) 4 个 Controller：@RequestMapping("旧") → @RequestMapping("新")      （4 行）
2) 正则替换（front + admin，各 1 遍）：
     (['"`])exampaper/       → $1assessmentPaper/
     (['"`])examquestion/    → $1assessmentQuestion/
     (['"`])jiankangzhishi/  → $1knowledgeArticle/
     (['"`])tongzhi/         → $1notification/
3) 编译 + mvn clean test（47/47）
4) A–F 验收（含 front/admin 旧路径残留 = 0、新路径 E2E）
5) 单独提交
```

### 1.3 实施与验收结果（2026-10-18 ✅ **完成**）

**提交 `79d7062`** —— 18 files changed · **+47 / −47** ✓

| 面 | 改动 |
| --- | --- |
| Controller | **4 行**（各 1 行类级映射 ✓）|
| front | **18 处 / 13 文件** ✓（⚠️ **未入库** ✗ —— `front/` 未纳管 ✓）|
| admin src | **30 处 / 12 文件** ✓ |
| 回归测试 | **16 处 / 2 文件** ✓（`IdentityAuthorization` 4 + `Phase2Core` 12，含 javadoc ✓）|

> **计划修正（如实登记）**：§1.1 表中 front「36」是 `前缀/` 的**全量**匹配，其中 **18 处是页面路径**（`./pages/exampaper/...` ✓）⇒ **真实 API 仅 18 处** ✓。这正是锚定规则生效的证明 ✓。

#### A–F 验收实测

| 项 | 结果 |
| --- | --- |
| **A 静态** | 旧 API 前缀残留 **java=0 / front=0 / admin=0 / test=0** ✓✓；新前缀 front **18** / admin **30** ✓；**身份键全保留**：`exampaperId` 1 · `examquestionId` 1 · `jiankangzhishiId` 2 · `tongzhiId` 2 ✓ · imports `modules/…` 2/1/1/1 ✓ · `pages/…` 1/1/1 ✓ · `dictionaryXxx` 10/11/11 ✓ |
| **B API** | 新 `/assessmentPaper|assessmentQuestion|knowledgeArticle|notification` 的 `/list` → **全部 `http=200 code=0`** ✓，条数 **2 / 6 / 5 / 5** 与 DB **精确一致** ✓✓；旧 4 前缀 `/list` → **全部 `http=404`** ✓✓ |
| **C 权限** | `/list` 保持 **public**（匿名 `200` ✓，与 `@IgnoreAuth` 契约一致 ✓）；`/page` 匿名 **401**（既有契约不变 ✓）；**学生** `200/code=0` ✓；**admin** 4 个 `/page` 全 `200/code=0`，条数 2/6/5/5 ✓ |
| **D 回归** | 首次构建 **5 失败** ✗（测试写死旧路径 ✓）→ 同步 16 处后 **BUILD SUCCESS · 47/47** ✓✓ |
| **E 数据库** | baseline **零变化** ✓：`auth_token=3` / `assessment_paper=2` / `assessment_question=6` / `knowledge_article=5` ✓（全程零写入 ✓）|
| **F Git** | **单一提交 `79d7062`** ✓（未进入 Batch 2 ✓）|

#### 1.5 版本化状态（审阅要求 · **必须保留**）

> **`79d7062` 的实际内容 = `Java(4 控制器) + admin/src(30 处) + tests(16 处)`** ✓
> ❌ **不含** `front` 的 **18 处**修改（`front/` 未纳管 ✗；仅存在于工作区 + `%TEMP%\b1-front-backup` 25 文件备份 ✓）
> ⇒ **后续任何报告不得表述为「Batch 1 全部前端修改均已版本化」** ✗
> ⇒ 可迁移的提交是 **API 后端 + 管理端源码 + 测试契约** ✓；前端同步需随 `front/` 纳管策略另行决定 ✓

#### 明确登记（未完成/未验证）

| # | 事项 | 影响 |
| :-: | --- | --- |
| 1 | **`front/` 未纳入版本控制** ⇒ 18 处前端改动**仅存在于工作区** ✗ | 本提交**不含**前端同步 ✓；换机/重克隆需重做 ✓ |
| 2 | **`admin/admin/dist` 仍是旧构建** ✗（Round 3 已退出索引 ✓）| **管理端这 4 个模块的已构建 UI 在 `npm run build` 之前仍调用旧路径** ✗ ⇒ **Step 5c 收尾必须重建 dist** ✓ |
| 3 | **未做浏览器级 E2E**（`front` 静态页）| 以「静态替换 + API 层探针」替代 ✓ |
| 4 | 未执行 Batch 2–6 ✓ | 按裁定逐批推进 ✓ |

---

## 2. 待做批次（占位，逐批填充）

| Batch | 内容 | 风险 | 状态 |
| :-: | --- | --- | --- |
| **1** | `/exampaper`→`/assessmentPaper` · `/examquestion`→`/assessmentQuestion` · `/jiankangzhishi`→`/knowledgeArticle` · `/tongzhi`→`/notification` | 低 | ✅ **完成 `79d7062`**（47/47 ✓ · 旧路径 404 ✓ · 新路径条数与 DB 一致 ✓）|
| 2 | `/examrecord` → `/assessmentRecord` · `/examredetails` → `/assessmentAnswer` · `/examrewrongquestion` → `/assessmentWrongQuestion` | 中（与 DataScope/owner 参数相邻，**参数键不得改** ✗）| ✅ **完成 `b887ad3`**（13 files · +32/−32 · **A=1/B=0 天然判别** ✓）|
| 3 | `/xinlilaoshi` → `/counselor` · `…Liuyan` → `/counselorMessage` · `…Collection` → `/counselorFavorite` · `…Order` → `/counselingAppointment` | 中高（**前缀包含关系**，最长前缀优先 ✓；公开接口 ✓；不改 Phase 2 权限语义 ✓）| ✅ **完成 `2013580`**（14 files · +81/−81 · **c1/c2 双教师 scope 未破坏** ✓）|
| 4 | `/yonghu` → `/student` | **最高**（`tableName`/`sessionTable`/`userTable`/`views/modules/yonghu`/白名单 `/yonghu/register`/登录/个人中心）| ✅ **完成 `e35a40a`**（15 files · +63/−63 · 白名单+菜单/登录链+前端 role 方案 A ✓）|
| 5 | `/users` → `/adminUser` | 中（管理端登录身份链 `/users/login` `/users/session`）| ✅ **完成 `80765c7`**（7 files · +44/−44 · `menu.tableName`+`isAuth` 同批 ✓ · `user_type=admin_user` 实测未变 ✓）|
| 6 | `/dictionary` → `/sysDictItem` | 中（`/dictionary/page` 在拦截器白名单 ⇒ 必须同步 ✓）| ✅ **完成 `e0f96ca`**（36 files · +65/−65 · **白名单契约已迁移** ✓：新 `/sysDictItem/page` 匿名 200/total=25 而旧 `/dictionary/page` 404 ✓）|
| — | `/file/*` | **不改名** ✓（仅验证 `/file/upload` 可用 ✓）|

### 2.1 Batch 2 实施与验收（✅ 完成 `b887ad3`）

**改动**：3 控制器各 1 行 ✓ + front **14 处 / 8 文件** ✓ + admin **15 处 / 12 文件** ✓ + 测试 **15 处 / 2 文件** ✓（13 files in commit ✓）

| 项 | 结果 |
| --- | --- |
| **A 静态** | 旧 API 残留 **java=0 front=0 admin=0 test=0** ✓✓；新前缀 front `5/6/3` · admin `5/6/4` ✓；**7 项身份键与清点逐一相同** ✓✓：`examrecordId` 6 · `examredetailsId` 2 · `examrewrongquestionId` 2 · `record_no` 12 · `recordNo` 68 · `studentId` 223 · `counselorId` 89 |
| **B API** | 旧 3 前缀 `/list` → **404** ✓✓；新前缀 `/list` → **200/code=0** ✓ |
| **C 权限** | 匿名 `/list` → **`code=401`** ✓（D2-2a 契约不变 ✓）；**A(s1)** record **1** / answer **5** / wrong **3** = DB ✓✓；**B(s2)** = **0/0/0** ✓✓（**A≠B 天然判别** ✓，无需对照数据 ✓）；A 伪造 `?studentId=2` 仍 **s1** ✓；B 伪造 `?studentId=1` 仍 **0** ✓；admin **1/5/3** 全量 ✓；**teacher 实际全量（G3 挂账，非本批结论）** ✗ |
| **D 回归** | 首次构建 **4 失败** ✗ → 判定为**测试契约需随 rename 同步**（非业务回归 ✓）→ 同步 15 处 → **BUILD SUCCESS · 47/47** ✓✓ |
| **E 数据库** | baseline 不变 ✓：`auth_token` 清理后 **3** ✓ · `assessment_record` **1** · `assessment_answer` **5** · `assessment_wrong_question` **3** ✓（零写入 ✓）|
| **F Git** | **单一提交 `b887ad3`** ✓ · 未进入 Batch 3 ✓ |

**流程纠正 #4**（测量问题）：探针里 `"$B$ep?username=..."` 被 PowerShell 解析为变量 `$ep?` ✗ → 4 个登录 URL 失效 ⇒ 令牌 null ⇒ 学生/教师探针全 **401** ✗（**非代码问题** ✓）。改为 `$B + $path + '?...'` 拼接后 **全部通过** ✓。有效部分（匿名 401 / 旧路径 404 / admin 1-5-3 / DB 基线）在首跑即成立 ✓。

**如实登记**：`front` 14 处改动**不在提交内** ✗（未纳管 ✓）；`admin/dist` 仍陈旧 ✗（Batch 6 重建 ✓）；教师未收敛 = **Phase 2 G3 既定挂账** ✓，本批未新增字段/SQL ✓。

---

### 3.1 Batch 3 实施与验收（✅ 完成 `2013580`）

**改动**：4 控制器各 1 行 ✓ + front **30 处 / 12 文件** ✓ + admin **28 处 / 8 文件** ✓ + 测试 **90 处 / 2 文件** ✓（14 files in commit ✓）

| 项 | 结果 |
| --- | --- |
| **A 静态** | 旧 API 残留 **java=0 front=0 admin=0 test=0** ✓✓；新前缀 front **30** / admin **28** / test **90** ✓；**7 项身份键逐一不变** ✓✓：`xinlilaoshiId` **0** · `counselorId` **89** · `studentId` **223** · `counselor_id` **14** · `modules/xinlilaoshi/` 1 · `pages/xinlilaoshi/` 1 · `dictionaryXinlilaoshi` **18**；java **标识符**（`xinlilaoshiService` / `xinlilaoshiOrderService` / `xinlilaoshiLiuyan` 变量 / `xinlilaoshi.getId()`）未动 ✓ |
| **B API** | 旧 4 前缀 `/list` → **404** ✓✓；新前缀正常响应 ✓ |
| **C 权限** | 匿名 `/counselor/list` → **200/code=0 且 3 条** ✓✓（**半公开策略不变** ✓）；匿名 `counselorMessage/counselorFavorite/counselingAppointment` `/list` → **401** ✓（D2-2a 契约 ✓）；学生 A **2/2/3** ✓（= G1 ✓）；**教师 c1 = 3/2/1 且归属仅 c1** ✓ · **教师 c2 = 2/3/1 且归属仅 c2** ✓✓ ⇒ **`counselor_id` DataScope 在 rename 后仍生效** ✓✓；admin **6/6/3** ✓ |
| **D 回归** | **BUILD SUCCESS · 47/47（首次即绿 ✓** —— 测试契约**预先**同步 ✓）|
| **E 数据库** | 不变 ✓：`auth_token=3` · `counselor=3` · `counselor_message=6` · `counselor_favorite=6` · `counseling_appointment=3` ✓（零写入 ✓）|
| **F Git** | **单一提交 `2013580`** ✓ · 未进入 Batch 4 ✓ |

**流程纠正 #5**（测量问题）：前缀 rename **同时迁移了登录端点**（`/xinlilaoshi/login` → **`/counselor/login`** ✓）；首跑探针仍用旧登录路径 ✗ ⇒ 教师令牌 null ⇒ 假 **401** ✗（**非代码回归** ✓）。改用新端点后 c1/c2 **全部通过** ✓。

**前端残留口径澄清**：`front` 仍有 **125 处** `xinlilaoshi` **字符串** ✓ —— 全部是**页面路径/变量名/menu url**（`config.js: './pages/xinlilaoshi/list.html'` ✓、`xinlilaoshiList` ✓、`jump('../xinlilaoshi/...')` ✓）⇒ **API 字面量 = 0** ✓✓（符合"残留=0 **仅指 API**"的口径 ✓）。

**如实登记**：front 30 处**不在提交内** ✗（备份 `%TEMP%\b3-front-backup` ✓）；`admin/dist` 仍陈旧 ✗（Batch 6 统一重建 ✓）；教师登录端点已迁移 ✓，前端**无**该 API 调用（静态可证 ✓）。

---

### 4.1 Batch 4 实施与验收（✅ 完成 `e35a40a`）· **最高风险批次**

**`tableName` 运行链（本次实测确认）** ✓：

```text
utils/menu.js  tableName:"yonghu"
  ├─→ IndexMain.vue:27        name: menu.tableName      → 动态组件名（需 router-static 注册名一致）
  ├─→ login.vue:146-147       this.tableName = menus[i].tableName
  │      ├─→ L151  url: `${tableName}/login`             → /student/login      （真实 API ✗✗）
  │      └─→ L158  $storage.set("sessionTable", tableName)
  │             └─→ IndexHeader.vue:46  sessionTable + '/session' → /student/session （真实 API ✗✗）
  └─→ login.vue:34  register('yonghu') → loginTable → /student/register （真实 API ✗✗）
```

⇒ 因此 `tableName: "yonghu" → "student"` **是唯一同时修正 login/session/register 三处真实 API 的位置** ✓（**不是**机械替换 ✓）

**改动（15 files in commit）**：

| 面 | 内容 |
| --- | --- |
| Controller | `StudentController` L46 → `/student` ✓ |
| **白名单** | `AuthorizationInterceptor` L40：`/yonghu/register` → **`/student/register`** ✓ |
| admin | API 字面量 **18 处 / 12 文件** ✓（含跨模块学生下拉：`examrecord`/`examredetails`/`examrewrongquestion`/`xinlilaoshi*`）· `utils/menu.js` **`tableName` ×3** ✓ · `router/router-static.js` import 别名+`component` ✓（**目录不动** ✓、`path:'/yonghu'` 保留 ✓）· `views/login.vue` `register('student')` ✓ |
| front（不入库 ✗）| API 字面量 **5 处** ✓ · `login.html` **`data.role='student'`** ✓ · `js/config.js` **`tableName` ×2 → student** ✓（**方案 A** 与 `role` 配对 ✓）|
| 测试 | **37 处** ✓ |

| 项 | 结果 |
| --- | --- |
| **A 静态** | 旧 API 残留 **java=0 front=0 admin=0 test=0** ✓✓；新前缀 front **5** / admin **18** / test **43** ✓；**`"tableName":"yonghu"` = 0** ✓；`yonghuId` **3** ✓ 与 `#yonghuPhotoUpload` **2** ✓ 不变；`sessionTable` **71** / `userTable` **31** 机制未改 ✓ |
| **B API** | 旧 `/yonghu/login`·`/yonghu/list`·`/yonghu/register` → **404** ✓✓；新 `/student/login` = **`code=0`（响应含 `tableName:"student"`）** ✓；`GET /student/register` = **405** ✓（**已过白名单到控制器 ✓ 且未创建任何数据 ✓**）|
| **C 权限/身份链** | 学生 A：`/student/list` 与 `/student/page` = **`code=0` · total=1 · id1** ✓✓；伪造 `?studentId=2`/`?userId=2` 仍 **1** ✓；匿名 = **401** ✓；admin = **3** ✓；teacher = **3**（**G3 既定挂账未变** ✓）；**A `/student/session` = `code=0` 本人数据** ✓；admin `/student/session` = **511**（admin 无学生行 ✓ **预期**）|
| **D 回归** | **BUILD SUCCESS · 47/47** ✓ |
| **E 数据库** | `auth_token=3` / `student=3` ✓（登录仅**重签发** ✓ 无新增行 ✓；零业务写入 ✓）|
| **F Git** | **单一提交 `e35a40a`** ✓ · 未进入 Batch 5 ✓ |

**流程纠正 #6（探针/测量，非代码）**：首轮 E2E 出现学生 `/student/list` 401 与 `/student/session` 401 ✗；以**同一令牌流程**复测**不可复现** ✓（复测均 `code=0`；`511` 属 admin 无学生行的**预期**行为 ✓）。**根因未完全定位** ✗ ⇒ 按"探针侧问题"登记 ✓，**不计为代码回归** ✗，也**不宣称已修复** ✗。

**如实登记**：front 改动（5 API + `data.role` + `config tableName`）**不在提交内** ✗（备份 `%TEMP%\b4-front-backup` ✓）；`admin/dist` 仍陈旧 ✗（Batch 6 重建 ✓）；用户端 `/学生/session` 缺陷**仅登记未修** ✓；`/student/update` 安全逻辑**未动** ✓。

---

### 4.2 b4.1：Batch 4 遗留缺陷修复（✅ 完成 `3bb8e51`）

**缺陷（Batch 4 引入）** ✗：

| 项 | 事实 |
| --- | --- |
| Batch 4 改动 | `utils/menu.js` `"tableName":"yonghu"` → `"student"`（✓ 必要：驱动 login/session）|
| 未同步 | `views/modules/yonghu/list.vue` 仍为 `isAuth('yonghu', …)` **×12** ✗ |
| `isAuth` 语义 | `utils/utils.js:7` `export function isAuth(tableName,key)` · `main.js:61 Vue.prototype.isAuth = isAuth` · **`utils/utils.js:17` `if(tableName == menus[i].backMenu[j].child[k].tableName)`** ⇒ **严格键相等匹配** ✓ |
| 后果 | `'yonghu'` ✗ vs `'student'` ✓ ⇒ **恒 `return false`** ✗ ⇒ 管理端「学生管理」新增/修改/删除按钮**权限判定失败** ✗（**后端不受影响** ✓，仅按钮显示 ✗）|

**修复** ✓：`isAuth('yonghu', …)` → `isAuth('student', …)` **×12**（单文件 `views/modules/yonghu/list.vue` ✓）· 提交 **`3bb8e51`**（1 file · **+12/−12** ✓）

| 验收 | 结果 |
| --- | --- |
| `isAuth('yonghu'` | **0** ✓✓ |
| `isAuth('student'` | **12** ✓✓ |
| `menu.js` `"tableName":"student"` | **3**（L90/L258/L456 ✓）⇒ 键匹配恢复 ✓ |
| 回归 | **BUILD SUCCESS · 47/47** ✓ |
| 未改 | `/student` API ✓ · 后端 ✓ · `menu.js` ✓ · `usersService`/`usersCrossAddOrUpdateFlag` ✓ · `auth_token` ✓ · DB ✓ |
| 浏览器级按钮显示 | ⏳ **待 `dist` 重建后验证**（Batch 6 ✓）—— 本提交以「静态键匹配 + `isAuth` 源码语义 + 47/47」为证据链 ✓ |

> **由此固化为通用规则** ✓：**任何批次若改 `menu.js` 的 `tableName`，必须同批改 `isAuth(...)` 第一参数** ✓（Batch 5 的 `users → adminUser` 同样适用 ✓：`isAuth('users')` **16 处** ✗ 必须同改 ✓）。

---

### 5.1 Batch 5 实施与验收（✅ 完成 `80765c7`）

**改动（7 files in commit）**：

| 面 | 内容 |
| --- | --- |
| Controller | `AdminUserController` L33 `users` → **`adminUser`** ✓（原写法**无前导斜杠** ✓）|
| admin API | **4 处 / 2 文件** ✓：`users/info/` · `users/{save\|update}` · `users/page` · `users/delete` |
| `menu.js` | `"tableName":"users"` → **`"adminUser"`** ✓（驱动 login + session 两条动态 API ✓）|
| **`isAuth`** | `isAuth('users',…)` → **`isAuth('adminUser',…)` 16 处** ✓（**与 `menu.js` 同批** ✓，遵守 §4.2 新规则 ✓）|
| `router-static.js` | import 别名 + `component` 同步 ✓（**`path:'/users'` 保留** ✓）|
| 测试 | **20 处** ✓ |

| 项 | 结果 |
| --- | --- |
| **A 静态** | 旧残留 **admin=0 / java=0 / test=0** ✓✓；新前缀 admin **4** / test **20** ✓；**保留项全部不变** ✓✓：`usersService` **14** · `usersCrossAddOrUpdateFlag` **2** · `modules/users/` **1** · **`path:'/users'` 1** ✓；`isAuth('users')` **0 → `'adminUser'` 16** ✓；`tableName:"users"` **0 → `"adminUser"` 1** ✓ |
| **B API** | 旧 `/users/list` · `/users/page` · `POST /users/login` → **全部 404** ✓✓；新 `POST /adminUser/login` → **`code=0`**（role=管理员 · userId=6）✓ |
| **C 权限/身份链** | `/adminUser/session` **`code=0`** ✓；`/adminUser/list` **`code=0`** ✓；`/adminUser/page` **`code=0` · total=1** ✓；匿名 `/adminUser/list` → **401** ✓；`GET /adminUser/register` → **405** ✓（`@IgnoreAuth` 生效 ✓、零写入 ✓）|
| **D 回归** | **BUILD SUCCESS · 47/47** ✓ |
| **E 数据库** | `auth_token=3` · `admin_user=1` ✓；**`auth_token.user_type` 实测仍为 `admin_user`** ✓✓（集合 `admin_user,counselor,student` 未变 ✓）⇒ **`tableName=adminUser` 与 `user_type=admin_user` 并存，二者语义独立** ✓✓ |
| **F Git** | **单一提交 `80765c7`** ✓ · 未进入 Batch 6 ✓ |

**如实登记**：`admin/dist` 仍陈旧 ✗（Batch 6 收尾统一重建 ✓）；**浏览器级按钮权限**验证待 `dist` 重建 ✓ —— 本批以「`isAuth` 键与 `menu.tableName` 静态一致 + 47/47」为证据 ✓。

---

### 6.1 Batch 6 实施与验收（✅ 完成 `e0f96ca`）· **最后一批**

**改动（36 files in commit）**：

| 面 | 内容 |
| --- | --- |
| Controller | `SysDictItemController` L43 `/dictionary` → **`/sysDictItem`** ✓（**无 `DictionaryController`** ✓，Step 5b 已改名 ✓）|
| **白名单** | `AuthorizationInterceptor` L40：`/dictionary/page` → **`/sysDictItem/page`** ✓✓（**认证边界契约迁移** ✓）|
| front（不入库 ✗）| **27 处 / 21 文件** ✓ |
| admin | **61 处 / 33 文件** ✓（含各 `dictionaryXxx` 模块页内的 `/dictionary/...` 调用 ✓）|
| 测试 | **2 处** ✓（含 javadoc 引用 ✓）|

| 项 | 结果 |
| --- | --- |
| **A 静态** | 旧残留 **admin=0 / front=0 / java=0 / test=0** ✓✓；新前缀 admin **61** / front **27** / test **2** ✓；**排除项逐一不变** ✓✓：`dictionaryExampaper` 9 · `Examquestion` 9 · `Xinlilaoshi` 18 · `Jiankangzhishi` 10 · `Tongzhi` 10 · `Shijianduan` 10 · **`dictionaryMap` 14** · **`isAuth('dictionary')` 12** · `tableName:"dictionary"` **0** |
| **B API（契约迁移核心）** | **新 `/sysDictItem/page` 匿名 → `200 / code=0 / total=25`** ✓✓（与 DB `sys_dict_item=25` **精确一致** ✓）；**旧 `/dictionary/page` 匿名 → 404** ✓✓；匿名 `/sysDictItem/info/1` → **401** ✓（非白名单端点仍受保护 ✓）|
| **C 权限** | admin `/sysDictItem/page` → `200/code=0` total=25 ✓；`/sysDictItem/list` 与旧 `/dictionary/list` → **404** ✓（字典控制器**本无 `/list`** ✓ 属预期 ✓）|
| **D 回归** | **BUILD SUCCESS · 47/47** ✓ |
| **E 数据库** | `auth_token=3` · **`sys_dict_item=25`** ✓ · 零写入 ✓ |
| **F Git** | **单一提交 `e0f96ca`** ✓ |

**如实登记**：front 27 处**不在提交内** ✗（备份 `%TEMP%\b6-front-backup` ✓）；`admin/dist` 仍陈旧 ✗（收尾统一重建 ✓）；**`isAuth('dictionary')` 12 处为既有"死键"** ✗（`menu.js` 无对应 `tableName` ✓）—— **pre-existing** ✓，本次**不改** ✗ 仅登记 ✓。

---

## 7. Step 5c 收尾：`dist` 重建调查结论（❌ **不可达** ✗ · 2026-10-19）

### 7.1 五次安装尝试与真实失败原因（逐次取证 ✓）

| # | 命令 | 失败阶段 | 错误 |
| :-: | --- | --- | --- |
| 1 | `npm install` | 依赖下载 | `CERT_HAS_EXPIRED`（`registry.npm.taobao.org` 证书过期 ✗）→ npm 崩溃 `Exit handler never called!` |
| 2 | `+ --replace-registry-host=always`（npmjs）| 依赖下载 | **E404**（仅换 host ✗，保留 taobao `/download/` 路径 ✗）|
| 3 | 同上用 npmmirror | 依赖下载 | **E422**（`?cache=0&sync_timestamp=...` 被当作版本号 ✗）|
| 4 | 临时重写 `resolved`（**含我自己的 scoped 文件名 bug** ✗ → 已修正 ✓）| 依赖下载 | E404（scoped 包文件名需去 scope ✗）|
| 5 | URL 已修正为标准 npmjs 形态 ✓ | **native 编译** ✗ | `canvas@2.9.3`：预编译 `node-v127` **404** ✗ → 源码编译 **`gyp ERR! find VS`**（`not looking for VS2017 as it is only supported up to Node.js 21` ✗）|

**URL 层全过程零漂移** ✓：改写前后 **`version`/`integrity`/`requires` 差异 = 0** ✓✓（947=947 ✓，键集合一致 ✓）；每次失败后均**从备份恢复**，SHA256 均回到基线 `C28BD1…F51AA` ✓✓。

### 7.2 决定性发现：**`package-lock.json` 结构性不完整** ✗✗

| 项 | 数据 |
| --- | --- |
| 根 `dependencies` 键 | **947**（**全为传递依赖** ✓，如 `@babel/*`）|
| `package.json` 直接依赖 | **24** |
| lockfile **覆盖** | **10** ✓（`@vue/cli-service`/`vue`/`core-js` …）|
| **缺失** | **14** ✗：`axios` `echarts` `element-ui` **`vue-qr`** `vue-router` `js-md5` `print-js` `vue-quill-editor` `vue-amap` `vue-json-excel` `babel-plugin-component` **`node-sass`** `sass-loader` `svg-sprite-loader` |

⇒ 这 14 项**及其整棵子树**必被 npm **现场解析** ✗ ⇒ **版本漂移不可控** ✗ ⇒ **即使构建成功，产物也不等价于原始 `dist`** ✗

### 7.3 `canvas` 依赖来源（`npm explain` 实测 ✓）

```text
canvas@2.9.3
node_modules/canvas
  canvas@"2.9" from vue-qr@3.2.4         ← vue-qr 的【普通】依赖（非 optional ✗）
  node_modules/vue-qr
    vue-qr@"^3.2.2" from the root project
```

* `vue-qr@3.2.4` 的 `package.json`：**`dependencies: { canvas: "2.9" }`** ✓（**非 optionalDependencies** ✗）
* `vue-qr` 源码 `src/lib/awesome-qr.js:10`：**`import { createCanvas } from "canvas";`** ✗（**模块顶层硬引用** ✗）
* lockfile 中 **`"canvas"` 出现 0 次** ✗、**`vue-qr` 条目也不存在** ✗ ⇒ 由 npm 10 现场解析 ✓

### 7.4 方案判定

| 方案 | 判定 | 依据 |
| :-: | --- | --- |
| **A** `--omit=optional` | ❌ **不成立** ✗ | `canvas` 是 `vue-qr` 的**普通依赖** ✗ + 源码**顶层 import** ✗ |
| **B** 切 Node 14 | ⚠️ **可安装但非重建** ✗ | canvas 2.9 有 node-v83 预编译 ✓，但 **14 个直依赖重新解析** ✗ ⇒ 漂移 ✗ + 环境变更 ✗ |
| **C** 放弃 `dist` 重建（**采纳** ✓）| ✅ **唯一不歪曲事实** ✓ | 旧 `dist` 保留 ✓；该证据层**如实登记为不可达** ✗ |

### 7.5 Step 5c 证据边界（**交付口径** ✓）

| 层 | 状态 |
| --- | --- |
| ① Java Controller / 新 API path | ✅ 已证实（**14 前缀残留 = 0** ✓）|
| ② `admin/src` 源码（API / `menu.tableName` / `isAuth` / router）| ✅ 已证实（B1–B6 + b4.1 ✓）|
| ③ `front` 源码 | ✅ 已证实（**未入库** ✗，仅工作区 ✓）|
| ④ 测试契约 + 回归 | ✅ **47/47** ✓ |
| ⑤ **API 级 E2E**（新 200 / 旧 404 / 权限契约）| ✅ 逐批已证实 ✓ |
| ⑥ **`dist` 构建产物** | ❌ **不可达** ✗（lockfile 缺 14/24 直依赖 ✗ + `vue-qr→canvas@2.9` 在 Node 22 不可构建 ✗）|
| ⑦ **浏览器级 Final E2E（新路径）** | ❌ **不可达** ✗（依赖 ⑥）|

### 7.5b **Step 5c 关闭裁定（审阅原文口径 ✓）**

> **Step 5c：关闭，状态 = 完成但存在明确证据边界。**
> `dist` 重建与浏览器级 Final E2E 两项 **不得写成"未完成"而无原因**，必须记录为：
> **"由于原项目 `package-lock.json` 对 24 个直接依赖仅覆盖 10 个，无法形成零漂移重建；同时 `vue-qr@3.2.4` 的普通依赖 `canvas@2.9.3` 在当前 Node 22 环境下既无可用预编译二进制，又无法完成源码编译，因此本环境无法对既有管理端 `dist` 进行可信的等价重建。"** ✓
> **Step 5c 目标边界** ✓：完成旧系统 API 路径重命名及调用链同步 ✓，**不是**把缺失完整锁定依赖树的老前端重新构建出来 ✗。
> **方案 B 暂不批准** ✗，保留为独立工单 **"Legacy Admin Build Recovery"** ✓（目标重定义为：在受控旧 Node 环境下恢复管理端构建能力，并评估依赖重新解析造成的版本漂移 ✓；届时需重验 `canvas`/`node-sass`/`sass-loader`/Vue CLI 4/其他 native 依赖/新旧产物行为差异 ✓）。

### 7.6 必须登记的运行期后果 ✗

* **当前 `dist` 仍是旧构建** ✗ ⇒ **管理端 UI 仍在调用旧 API 路径** ✗ ⇒ **管理端在改名后不可用** ✗
* 恢复路径 ✓：在具备 **Node 14（或可为 canvas 提供预编译的环境）** + **重建 lockfile** 的前提下重新构建 ✓ —— **超出 Step 5c 范围** ✓，**单独立项** ✓
* 环境现状 ✓：`node_modules` 为**部分安装** ✗（ignored ✓，无 git 影响 ✓）⇒ **`npm run build` 当前会失败** ✗（须先解决 7.2/7.4）

---

## 3. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| **v9** | 2026-10-19 | **Step 5c 收尾：`dist` 重建判定为 ❌ 不可达**（见 §7）：5 次安装尝试的失败原因逐次取证（taobao 证书过期 ✗ → `/download/` 路径 ✗ → query 串 ✗ → scoped 文件名 ✗ → **`canvas@2.9.3` 在 Node 22 无预编译且 gyp 拒绝 VS2017** ✗）；**URL 层零漂移已证明** ✓（version/integrity/requires 差异 0 ✓，每次失败后 SHA256 均回到基线 ✓）；**决定性发现：lockfile 仅覆盖 10/24 直接依赖（缺 14 项含 `vue-qr`/`node-sass`/`element-ui`）** ✗ ⇒ 零漂移重建机制上不成立 ✗；`npm explain` 证明 `canvas@"2.9"` 是 **`vue-qr@3.2.4` 的普通依赖**（非 optional ✗）且源码顶层 import ✗ ⇒ **方案 A 不成立** ✗；采纳 **C**：保留旧 `dist` ✓、证据边界 ①–⑦ 明确登记 ✓、**运行期后果（管理端 UI 仍调旧路径）单独立项** ✓ |
| v8 | 2026-10-18 | **Batch 6 完成（`e0f96ca`）· Step 5c 六批全部完成 6/6**：`/dictionary` → `/sysDictItem`（36 files · **+65/−65**）；**白名单认证边界契约迁移** ✓（新 `/sysDictItem/page` 匿名 **200/code=0/total=25 = DB** ✓、旧 `/dictionary/page` **404** ✓、匿名 `/info/1` **401** ✓）；排除项逐一不变 ✓（`dictionaryXxx` 9/9/18/10/10/10 · `dictionaryMap` 14 · `isAuth('dictionary')` 12 = pre-existing 死键 ✓）；**全量残留审计：14/14 前缀 java/admin/front/test 全 0** ✓✓（`/file/upload` 9 与 `/config` 2 未误改 ✓）；47/47 ✓ · DB 零写入 ✓ |
| v7 | 2026-10-18 | **Batch 5 完成（`80765c7`）**：`/users` → `/adminUser`；7 files · **+44/−44**；Controller 1 行 + admin API **4** + **`menu.tableName`** + **`isAuth` 16 同批** ✓（遵守 §4.2 规则 ✓）+ router 别名/component（**`path:'/users'` 保留** ✓）+ 测试 **20** ✓；**A–F 全过**（旧残留 admin/java/test 全 0 ✓ · 旧 3 端点 **404** ✓ · `POST /adminUser/login` `code=0` ✓ · `/session`·`/list`·`/page` 正常 ✓ · 匿名 **401** ✓ · `GET /register` **405** ✓ · **47/47** ✓ · DB 零写入 ✓）；**`auth_token.user_type` 实测仍 `admin_user`** ✓✓（硬编码链未受 rename 影响 ✓，与 `tableName=adminUser` 语义独立 ✓）；保留 `usersService` 14 / `usersCrossAddOrUpdateFlag` 2 / `modules/users/` 1 / `path:'/users'` 1 ✓ |
| v6 | 2026-10-18 | **b4.1 完成（`3bb8e51`）· Batch 4 遗留缺陷修复**：Batch 4 改 `menu.js` `tableName` 却未同步 `isAuth` 键 ⇒ `utils/utils.js:17` 严格键相等匹配导致学生管理按钮权限恒 false ✗；修复 `isAuth('yonghu')` **×12 → `'student'`** ✓（`isAuth('yonghu')`=0 / `'student'`=12 / `menu.js`=3 ✓ · 47/47 ✓ · 后端零改动 ✓）；**固化为通用规则**：改 `menu.tableName` 必须同改 `isAuth` 键 ✓（直接约束 Batch 5：`isAuth('users')` **16 处** ✗ 必须同改 ✓）；浏览器级按钮验证待 `dist` 重建（B6 ✓）|
| v5 | 2026-10-18 | **Batch 4 完成（`e35a40a`）· 最高风险批次**：`/yonghu`→`/student`；**实测确认 `menu.js tableName` 同时驱动「动态组件名 + `{tableName}/login` + `sessionTable→/{t}/session` + 注册页」** ⇒ `tableName` 改动是唯一同时修正 3 处真实 API 的位置 ✓；**白名单 `/yonghu/register` → `/student/register`** ✓（`GET` = **405** 证明过白名单且零写入 ✓）；admin 18 处（含跨模块学生下拉）+ 菜单/路由注册 + 登录注册入口 ✓；front 5 处 + `data.role` + `config tableName`（**方案 A**）✓；测试 37 处 ✓；**A：`/student/list`·`/page` total=1 id1** ✓✓、伪造无效 ✓、匿名 401 ✓、admin 3 ✓、teacher 3（G3 未变 ✓）、旧路径全 404 ✓、**47/47** ✓、DB 零写入 ✓；**流程纠正 #6**（首轮 401 复测不可复现 ⇒ 探针侧问题，不计回归、不宣称修复 ✗）|
| v4 | 2026-10-18 | **Batch 3 完成（`2013580`）**：4 前缀（counselor 系）+ front 30 / admin 28 / 测试 90 处同步（14 files · **+81/−81** ✓）；**A–F 全过**（旧残留全 0 ✓ · 旧路径 404 ✓ · **匿名 `/counselor/list` 200/3 条 = 半公开不变** ✓ · 另 3 个 401 ✓ · 学生 2/2/3 ✓ · **教师 c1=3/2/1 仅 c1 与 c2=2/3/1 仅 c2** ✓✓ 证明 `counselor_id` DataScope 未被破坏 ✓ · **47/47 首次即绿** ✓ · DB 零写入 ✓ · 单提交 ✓）；7 项身份键逐一不变 ✓；**流程纠正 #5**（rename 同时迁移登录端点 → 首跑假 401 ✓）；**口径澄清**：front 余 125 处 `xinlilaoshi` 均为页面/变量/menu，**API 字面量 = 0** ✓ |
| v3 | 2026-10-18 | **Batch 2 完成（`b887ad3`）**：3 控制器各 1 行 + front 14 / admin 15 / 测试 15 处同步（13 files · **+32/−32** ✓）；**A–F 全过**（旧残留全 0 ✓ · 旧路径 **404** ✓ · **A(s1)=1/5/3 = DB** 与 **B(s2)=0/0/0** 构成**天然判别** ✓✓ · 双向伪造均无效 ✓ · admin 1/5/3 ✓ · **47/47** ✓ · DB 零写入 ✓ · 单提交 ✓）；**7 项身份键计数与清点逐一相同** ✓（`recordNo` 68 / `studentId` 223 / `counselorId` 89 等）；首次构建 4 失败判定为**测试契约**（非业务回归 ✓）；登记 **teacher 全量 = G3 既定挂账** ✓、front 未入库 ✗、`dist` 陈旧 ✗；**流程纠正 #4**（PS 变量解析导致登录 URL 失效 → 令牌 null → 401 假象 ✓）|
| v2 | 2026-10-18 | **Batch 1 完成（`79d7062`）**：4 控制器各 1 行 + front 18 / admin 30 / 测试 16 处同步（**+47/−47** ✓）；**A–F 全过**（旧 API 残留 java/front/admin/test 全 0 ✓ · 新路径条数 **2/6/5/5 与 DB 精确一致** ✓ · 旧路径 **404** ✓ · `/list` public 与 `/page` 401 契约不变 ✓ · **47/47** ✓ · DB 零写入 ✓ · 单提交 ✓）；**修正计划口径**：front「36」含 18 处页面路径 ⇒ 真实 API 仅 18 ✓；登记 **front 改动未入库** ✗、**`dist` 陈旧需 rebuild** ✗、**未做浏览器 E2E** ✓ |
| v1 | 2026-10-18 | Step 5c 实施台账开篇：0.1 身份键不改清单（参数键/组件身份/页面路径/`/file`/数据库）· 0.2 只改两类字面量 + **锚定正则规避页面路径陷阱** · 0.3 A–F 验收模板 · **Batch 1 只读清点完成**（4 控制器各 1 行 / front 36 / admin 30 / import 4 与参数键 4 不改 / `menu` 表不存在 ⇒ 零写库 ✓）|
