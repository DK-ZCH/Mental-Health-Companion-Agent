# S1 —— 安全根因盘点（只读归并）

> 日期：2026-10-20 ｜ 基线：`44c0177`（R14 已推送，LEAD=0）
> 约束：**零代码修改 ✗ · 零 DB 修改 ✗ · 零配置修改 ✗ · 零行为修改 ✗**
> 方法：静态清点（`@IgnoreAuth` / 守卫存在性）+ 只读 GET 实测 + 引用既有已验证结论
> **本文件只做归并和排序，不实施任何修复** ✗

---

## 0. 规模与证据强度

| 项 | 数量 |
| --- | --- |
| `@IgnoreAuth` 方法 | **17 处**（见 §2.4）|
| 含 `/delete` 端点的 Controller | **15 个** |
| 其中**文件内完全没有 `OwnershipGuard`** 的 | **6 个**（见 §2.1）|
| 本次实测的匿名/越权读取面 | **8 个端点** |
| 本次产生的业务写入 | **0** ✓（仅登录取 token，末尾删除）|
| 数据/配置变更 | **0** ✓ |

> **证据强度说明**：
> · `guard=N` = 该文件**完全没有** `OwnershipGuard` ⇒ 其 `/delete`、`/detail` **必然无归属校验**（静态可判定 ✓）
> · `guard=Y` **不代表** `/delete` 有校验（可能只用于 `/info`）⇒ 需逐方法审计 ⚠️
> · 未执行任何越权删除/写入（避免破坏数据）✗

---

## 1. 归并表（核心交付）

| # | 根因类别 | 涉及问题（ID） | 严重度 | 影响范围 | 最小修复方向 | 是否阻塞后续 |
| :-: | --- | --- | :-: | --- | --- | --- |
| **A** | **写操作缺归属/缺管理员校验** | `SEC-DETAIL-IDOR`(读) · `SEC-FAVORITE-DELETE-IDOR` · `SEC-APPOINTMENT-DELETE-IDOR` · `SEC-MESSAGE-DELETE-IDOR` · `SEC-PAPER-DELETE-IDOR` · `SEC-RECORD-DELETE-IDOR` · `SEC-RECORD-SCORE-UPDATE-IDOR` · **内容/配置表 `/delete` 全无守卫（新归并）** | 🔴 **P1 Security** | 任一登录用户可按 id 删/改**他人**数据；**知识/通知/试卷/题目/配置/字典**可被任意登录用户删除/改 | ① `/delete` 统一加归属或 `assertAdminOnly`（内容/配置类）② `/detail` 补 `assertOwnership`（与 `/info` 对齐）③ `examrecordId` 类入参改为服务端解析归属 | ✅ 阻塞“可信数据”表述 |
| **B** | **客户端可信字段（服务端不裁决）** | `SEC-APPOINTMENT-STATUS-SPOOF` · `SEC-MESSAGE-REPLY-SPOOF`（**实测可落库**）· `SEC-ANSWER-SCORE-SPOOF` · `SEC-RECORD-OWNER/SCORE`（`/add` 不强制 `studentId`）· `DEF-STUDENT-CREATEDAT-BINDABLE` | 🔴 **P1 Security** | 学生可伪造**审核状态 / 老师回复 / 得分 / 记录归属** | 服务端白名单 + 从 `CurrentUserProvider` 取值；`status/score/replyContent/createdAt` 不接受客户端值 | ✅ 阻塞 R15 |
| **C** | **匿名/低权限可读敏感数据** | `SEC-QUESTION-ANSWER-PUBLIC`（**新，4 个端点**）· `SEC-FILE-DOWNLOAD-TRAVERSAL`（**建议 P0**）· `SEC-FILE-UPLOAD-ANON` · `SEC-STUDENT-PAGE-POLICY`(P2) · `counselor/list` 匿名含 `phone/email` | 🔴 **P0×1 / P1×3** | 匿名即可取**答案键/服务器任意文件**；匿名可上传；匿名可取老师联系方式 | ① 题目 **DTO 投影**（见 §3，**只改一个接口无效**）② 下载路径规范化 + 鉴权 ③ 上传鉴权 + 类型白名单 ④ 目录字段投影 | ✅ 阻塞 R15 与“测评有效性” |
| **D** | **公共/演示/初始化端点仍开放** | `SEC-PASS-RESET`（学生/老师 `resetPass` 匿名）· `SEC-ADMIN-REG`（`adminUser/register` 匿名）· 学生/老师 `register` 匿名 | 🔴 **P1 Security** | 匿名可重置密码 / 注册管理账号 | **先做业务确认**（是否仍被真实调用）→ 关闭或改 `@IgnoreAuth` 白名单 + 管理员审批 | ⚠️ 需业务裁定 |
| **E** | **测试与数据卫生（非安全）** | `DEF-REGRESSION-TEST-DATA-MUTATION`（TBD）· `AUTO_INCREMENT` 漂移（环境状态） | ⚪ **TBD / 信息** | `mvn test` 会持久改写当前库业务数据；测试基线不可复现 | 测试隔离（事务回滚 / 独立库）；**不与安全项混批** | ❌ 不阻塞功能 |
| **F** | **功能阻塞（非安全）** | `DEF-ANSWER-ENDPOINT-MISSING`（旧前端调 `saveExamredetails` → **404**） | 🔴 **P1 Functional Blocker** | 答题提交链路不存在 | 后端提供正式提交端点（含服务端判分/计分）| ✅ 阻塞 R15 |

> **口径纪律**：`P1 Functional Blocker`（F）**不是安全漏洞** ✗；`TBD`（E）**不预设等级** ✗。

---

## 2. 逐类证据

### 2.1 A 类：内容/配置表的 `/delete` **完全没有守卫**（本次静态新归并）

文件内 `OwnershipGuard` 命中数 = 0 ⇒ `/delete`、`/detail` 必然无归属校验：

| Controller | `/delete` | `/detail/{id}` | 守卫 | 风险 |
| --- | :-: | :-: | :-: | --- |
| `AssessmentPaperController` | ✓ | ✓ | **无** ✗ | 任意登录用户可**逻辑删除试卷**（`is_deleted=2`）|
| `AssessmentQuestionController` | ✓ | ✓ | **无** ✗ | 可删除**题目**（直接影响测评）|
| `KnowledgeArticleController` | ✓ | ✓ | **无** ✗ | 可删除**知识文章** |
| `NotificationController` | ✓ | ✓ | **无** ✗ | 可删除**通知** |
| `SysConfigController` | ✓ | ✓（**匿名**）| **无** ✗ | 可删除**站点配置** |
| `SysDictItemController` | ✓ | — | **无** ✗ | 可删除**字典项**（会影响时段/状态等下拉）|

> 另有 9 个 Controller 含 `/delete` 且文件内**出现过** `OwnershipGuard`，但**是否覆盖 `/delete` 未逐方法审计** ⚠️（不臆断为已修 ✓）。

### 2.2 B 类：客户端字段被采信（代码取证）

| 端点 | 事实 |
| --- | --- |
| `/counselingAppointment/add` `/update` | `status` 不限 ⇒ 可直造 `2 同意` ✗ |
| `/counselorMessage/add` `/update` `/save` | `replyContent` 客户端可控；**已实测落库** ✗ |
| `/assessmentAnswer/saveAssessmentAnswer` | `score` 客户端提交并累加；`if("学生id".equals(role))` 字面量 bug ⇒ `studentId` 不强制 ✗ |
| `/assessmentRecord/add` | 不强制 `studentId`、`totalScore` 客户端可控 ✗ |
| `/student/update` | `createdAt` 可绑定（P2）|

### 2.3 C 类：匿名可读面（本次实测）

| 端点 | 认证 | 结果 | 判定 |
| --- | --- | --- | --- |
| `/assessmentQuestion/list?paperId=1` | **匿名** | 200 · 含 `answer` / `analysis` | 🔴 答案键泄露 |
| `/assessmentQuestion/page` | 登录 | 200 · 含 `answer` / `analysis` | 🔴 同上（登录也不够）|
| `/assessmentQuestion/info/1` | 登录 | 200 · 含 `answer` / `analysis` | 🔴 同上 |
| `/assessmentQuestion/detail/1` | 登录 | 200 · 含 `answer` / `analysis` | 🔴 同上（且无守卫）|
| `/assessmentAnswer/list` | 登录(本人) | 含 `questionAnswer` / `questionAnalysis` + **`studentPhone` / `studentEmail`** | ⚠️ 本人可见答案（复习场景可接受，需业务确认）+ PII |
| `/assessmentWrongQuestion/list` | 登录(本人) | 同上 | ⚠️ 同上 |
| `/config/list` · `/config/detail/{id}` | **匿名** | 仅轮播图 `configKey/configValue` | ✅ 无敏感 ✓ |
| `/sysDictItem/page` | **匿名** | 字典项（类别/编码/名称） | ✅ 无 PII ✓ |
| `/file/download` | **匿名** | 既有实测：`?fileName=../pom.xml` → **201 / 5576 B** | 🔴 **P0**（引用既有结论，本轮未复现）|

### 2.4 D 类：`@IgnoreAuth` 全量（17 处）

```text
AdminUserController      /login · /register ·（1 处待定位）
AssessmentPaperController /list              ← 仅元信息 ✓
AssessmentQuestionController /list           ← 含 answer ✗
CounselorController      /login · /register · /resetPass · /list ← /list 含 phone/email ⚠️
FileController           /download           ← 穿越 P0 ✗
KnowledgeArticleController /list             ✓
NotificationController   /list               ✓
StudentController        /login · /register · /resetPass
SysConfigController      /list · /detail/{id} ← 仅轮播图 ✓
SysDictItemController    /page               ✓
```

---

## 3. `SEC-QUESTION-ANSWER-PUBLIC` 完整暴露面（专项）

**结论：泄露不是单点，4 个端点共用同一 `AssessmentQuestionView`，只改 `/list` 无效** ✗

```text
/assessmentQuestion
├── /list        (匿名)  ← answer + analysis
├── /page        (登录)  ← answer + analysis
├── /info/{id}   (登录)  ← answer + analysis（无归属校验）
└── /detail/{id} (登录)  ← answer + analysis（无归属校验）
```

连带暴露：

| 端点 | 字段 | 说明 |
| --- | --- | --- |
| `/assessmentAnswer/list` | `questionAnswer` · `questionAnalysis` | 本人记录的“正确答案/解析” |
| `/assessmentWrongQuestion/list` | 同上 | 错题本带答案（复习合理，但需业务确认）|
| `options` 文本 | 直接写「正确答案B」 | **改 DTO 也必须清洗选项文案** ⚠️ |

**最小修复方向（待设计，不预批准）**：

```text
① 服务端按场景投影：
   PublicQuestionDTO  = id, paperId, content, options(清洗后), score?, questionType, sortOrder
   StudentQuestionDTO = 同上（答题中不含 answer/analysis）
   AdminQuestionDTO   = 全字段（仅管理端）
② 4 个端点统一改用对应 DTO；**不允许**再把 Entity/View 直接序列化
③ `options` 文案清洗（去掉“正确答案”字样）
④ 回归：匿名与登录均**不得**在响应中出现 answer/analysis（答题结束后是否可看解析，需业务裁定）
```

⚠️ **还需核对**：`/assessmentAnswer/*`、`/assessmentWrongQuestion/*` 是否也应剥离 `questionAnswer/questionAnalysis`（取决于“提交后能否看解析”这一业务规则）。

---

## 4. 修复优先级建议（**待裁定，不实施** ✗）

| 批次 | 内容 | 理由 |
| :-: | --- | --- |
| **S1-A** | `SEC-FILE-DOWNLOAD-TRAVERSAL`（P0）| 匿名读服务器任意文件，风险最高、修复面最小（路径规范化 + 鉴权）|
| **S1-B** | `SEC-QUESTION-ANSWER-PUBLIC`（DTO 投影，§3）| 影响测评有效性；**是 R15 的前置安全条件** |
| **S1-C** | A 类 `/delete` 收口（6 个无守卫文件 + 其余逐方法审计）| 破坏性写操作，面最广 |
| **S1-D** | B 类客户端可信字段（status/score/replyContent/studentId）| 影响数据真实性与业务信任 |
| **S1-E** | D 类匿名 register/resetPass（**先业务确认**）| 账号安全；但不能凭“看起来危险”直接删 ✗ |

> 每批仍需：**单独裁定范围 → 实现 → 验收 → staged 审计 → 再 commit/push** ✓

---

## 5. 未验证 / 边界（明确）

| # | 项 | 原因 |
| :-: | --- | --- |
| 1 | 各 `/delete` 的**实际越权删除** | 未执行（破坏数据）✗ |
| 2 | 9 个 `guard=Y` 文件的 `/delete` 是否已覆盖 | **未逐方法审计** ⚠️（不臆断）|
| 3 | 文件穿越复现 | 引用既有 P0 结论，本轮**未重复执行** ✗ |
| 4 | `register` / `resetPass` 是否仍被前端调用 | 未做调用面统计 ✗ |
| 5 | 管理端 `dist` 的调用面 | 未评估 ✗ |
| 6 | `options` 文案清洗对旧页面的影响 | 未评估 ✗ |

---

## 6. R15 挂账措辞（按审阅修正）

> **R15 暂缓，待服务端答题/计分权威链路完成后再实施。**
> 前置条件（缺一不可）：题目展示安全 → 身份由服务端确定 → 记录归属由服务端确定 → 分值由服务端确定 → 答案由服务端判定 → `totalScore` 由服务端计算 → record 仅本人可改 → 提交过程具备合理一致性。

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | S1 只读归并：**6 类根因**（A 写操作缺归属 / B 客户端可信字段 / C 匿名可读 / D 公共端点 / E 测试卫生 / F 功能阻塞）；新归并 **6 个内容/配置控制器 `/delete` 完全无守卫**；`SEC-QUESTION-ANSWER-PUBLIC` 确认 **4 个端点 + options 文案**均泄露；优先级 S1-A→E；**零代码 / 零 DB / 零配置 / 零行为变更** ✓ |
