# Phase 3 / Step 6 —— 新 Vue 3 前端**设计版**（只设计，不编码）

> **输入**：Step 5c 已冻结的新 API 合同 ✓（`PHASE3-STEP5C-MAPPING.md` + `PHASE3-STEP5C-IMPLEMENTATION.md`）
> **硬约束** ✓：本阶段**不写业务代码** ✗；**不重新发明 API** ✗；**不假设目录结构** ✗（结构由真实业务推导 ✓）
> **旧资产定位** ✓：旧 `front`（**未入 Git** ✗）与旧 `admin/dist`（**历史构建** ✗）**只作为迁移参考** ✓；新 `frontend/` 才是**可版本控制、可重建的主线** ✓

---

## 0. 事实基线（本次清点实测，非假设 ✓）

### 0.1 旧前端规模

| 项 | 实测 |
| --- | --- |
| 页面目录 | **17** |
| HTML 页面 | **51** |
| 目录明细 | `home`1 · `login`1 · `yonghu`5 · `xinlilaoshi`5 · `xinlilaoshiOrder`4 · `exampaper`4 · `examquestion`3 · `examrecord`3 · `examredetails`3 · `examrewrongquestion`3 · `jiankangzhishi`3 · `tongzhi`3 · `xinlilaoshiCollection`3 · `xinlilaoshiLiuyan`3 · `dictionary`3 · **`chat`1** · **`recharge`1** |

### 0.2 后端真实端点面（16 个控制器）

| Controller | base（**实测**）| 映射数 | `@IgnoreAuth`（**公开**）|
| --- | --- | :-: | --- |
| `StudentController` | `/student` ✓ | 16 | `/login` `/register` **`/resetPass`** ✗ |
| `CounselorController` | `/counselor` ✓ | 16 | `/login` `/register` **`/resetPass`** ✗ `/list` ✓ |
| `CounselorMessageController` | `/counselorMessage` ✓ | 10 | — |
| `CounselorFavoriteController` | `/counselorFavorite` ✓ | 10 | — |
| `CounselingAppointmentController` | `/counselingAppointment` ✓ | 10 | — |
| `AssessmentPaperController` | `/assessmentPaper` ✓ | 10 | `/list` ✓ |
| `AssessmentQuestionController` | `/assessmentQuestion` ✓ | 13 | `/list` ✓ |
| `AssessmentRecordController` | `/assessmentRecord` ✓ | 12 | — |
| `AssessmentAnswerController` | `/assessmentAnswer` ✓ | 12 | — |
| `AssessmentWrongQuestionController` | `/assessmentWrongQuestion` ✓ | 10 | — |
| `KnowledgeArticleController` | `/knowledgeArticle` ✓ | 10 | `/list` ✓ |
| `NotificationController` | `/notification` ✓ | 10 | `/list` ✓ |
| `SysDictItemController` | `/sysDictItem` ✓ | 8 | **`/page`** ✓（白名单 ✓）|
| `SysConfigController` | **`/config`** ✗（**≠ `sysConfig`** ✗）| 9 | `/list` `/detail/{id}` ✓ |
| `AdminUserController` | `/adminUser` ✓ | 11 | `/login` **`/register`** ✗ |
| `FileController` | `/file` ✓（**按裁定不改名** ✓）| 3 | `/download` ✓ |

### 0.3 两处**合同差异**（必须显式登记，不擅自假设 ✓）

1. **`/config`（**已定案 ✓**）**：Step 5c 冻结合同此前误写 `config → sysConfig` ✗，但实际**从未执行**该 rename ✓（`SysConfigController` base = `/config` ✓、前端 API caller **0** ✓、B1–B6 均未涉及 ✓）⇒ **判定为"冻结清单文档错误"，非代码遗漏** ✓ ⇒ **正式修订合同为 `/config`** ✓，**不做 Batch 7** ✗。若未来希望语义统一为 `sysConfig`，须作为**新的 API 演进决策** ✓，不得伪装成 Step 5c 遗漏修复 ✗。
2. `chat` / `recharge` ✗：模板遗留（无对应业务控制器 ✓，`recharge` 无支付后端 ✓）⇒ **候选淘汰** ✓（见 §4）

---

## 1. 核心任务流（用户目标，而非页面清单 ✓）

### 流 A · 访客（未登录）

```text
首页（知识 / 通知 / 心理老师目录）
  ├─ 知识列表 → 知识详情
  ├─ 通知列表 → 通知详情
  ├─ 心理老师目录 → 老师详情
  └─ 登录 / 注册（学生、心理老师）
```

### 流 B · 学生（登录）

```text
登录 → 首页
  ├─ 浏览知识 / 通知 / 老师
  ├─ 老师详情 → 收藏 / 预约 / 留言
  ├─ 我的收藏 · 我的预约 · 我的留言（仅本人）
  ├─ 个人中心（资料 / 头像）
  └─ 心理测评
        ├─ 选择试卷 → 答题 → 提交
        └─ 我的测评记录 → 记录详情 → 我的错题
```

### 流 C · 心理老师（登录）

```text
登录 → 我的资料
  ├─ 我的留言（收到/回复）
  ├─ 我的预约（学生预约列表）
  └─ 被收藏情况（只读）
```

### 流 D · 管理员（在**管理端**，非本前端主线）

```text
管理端（旧 Vue2 admin）→ 见 §8 退役策略
```

> **注** ✓：Step 6 的新 `frontend/` **只覆盖访客 + 学生 + 心理老师** ✓；管理端**不在本次重建范围** ✓（旧 `admin` 继续作为管理入口 ✓，其 `dist` 重建见 §9 P1-1）。

---

## 2. 目录职责（**由 §1 的业务域推导** ✓，非模板照搬 ✗）

```text
frontend/
├─ public/                    静态资源（favicon 等）
├─ src/
│  ├─ api/                    ★ 按【业务域】而非按后端控制器机械划分
│  │   ├─ http.ts             统一 axios 实例（Token 头、错误归一）
│  │   ├─ auth.ts             student / counselor / adminUser 的 login|register|session
│  │   ├─ content.ts          knowledgeArticle · notification · counselor(list/info)
│  │   ├─ counselor.ts        message · favorite · appointment
│  │   ├─ assessment.ts       paper · question · record · answer · wrongQuestion
│  │   ├─ profile.ts          student/counselor 个人中心（session/update）
│  │   ├─ dict.ts             sysDictItem/page（下拉数据）
│  │   └─ file.ts             file/upload · file/download
│  ├─ assets/                 图片、样式变量、图标
│  ├─ components/             通用无业务组件（分页、空态、上传、富文本壳）
│  ├─ composables/            组合式逻辑（useAuth、usePagination、useOwnership）
│  ├─ layouts/                PublicLayout / UserLayout / (AdminLayout 预留)
│  ├─ router/                 routes.ts · guards.ts
│  ├─ stores/                 pinia：auth（token/role/userId）、dict 缓存
│  ├─ views/                  ★ 按【任务流】组织，见 §3
│  ├─ types/                  与后端 View 对齐的 TS 类型（只读投影）
│  └─ utils/                  时间/格式化/权限判定
├─ .env.development / .env.production
└─ vite.config.ts
```

**为什么这样分** ✓（每条都有依据 ✓）：

| 决策 | 依据 |
| --- | --- |
| `api/` 按**业务域**而非 16 个控制器一一对应 | 旧 16 控制器里 `counselorMessage/Favorite/Appointment` 在业务上同属"与老师的互动" ✓；新前端 1 个页面会调多个控制器 ✓ |
| `composables/useOwnership` | D2-2b 已证明**归属由服务端强制** ✓ ⇒ 前端只做 UI 提示 ✓，不做权限判定 ✓ |
| `stores/dict` | `sysDictItem/page` 是**公开**端点 ✓ ⇒ 适合**一次性加载 + 缓存** ✓ |
| `types/` 只放**只读投影** | 后端 View 含 `*Label` 等展示字段 ✓，前端**不参与写入结构** ✓ |

---

## 3. 路由表（51 HTML → **约 14 条路由** ✓）

### 3.1 路由定义

| # | 路由 | 视图 | 认证 | 说明 |
| :-: | --- | --- | --- | --- |
| R1 | `/` | `home/HomeView` | 公开 | 首页：知识/通知/老师 三块 ✓ |
| R2 | `/login` | `auth/LoginView` | 公开 | 学生/老师 二选一 ✓（管理员走旧 admin ✓）|
| R3 | `/register` | `auth/RegisterView` | 公开 | 学生/老师注册 ✓ |
| R4 | `/knowledge` | `content/KnowledgeListView` | 公开 | 用 **`/list`** ✓ |
| R5 | `/knowledge/:id` | `content/KnowledgeDetailView` | 公开 | |
| R6 | `/notifications` | `content/NotificationListView` | 公开 | 用 **`/list`** ✓ |
| R7 | `/notifications/:id` | `content/NotificationDetailView` | 公开 | |
| R8 | `/counselors` | `counselor/CounselorListView` | 公开 | 用 **`/list`** ✓ |
| R9 | `/counselors/:id` | `counselor/CounselorDetailView` | 公开 | 收藏/预约/留言入口（需登录 ✓）|
| R10 | `/me` | `profile/ProfileView` | 学生/老师 | 资料 + 头像上传 ✓ |
| R11 | `/me/favorites` | `counselor/FavoriteListView` | 学生 | 仅本人 ✓ |
| R12 | `/me/appointments` | `counselor/AppointmentListView` | 学生 | 仅本人 ✓ |
| R13 | `/me/messages` | `counselor/MessageView` | 学生/老师 | 学生看自己发起 ✓；老师看收到 ✓ |
| R14 | `/assessment` | `assessment/BankView` | 学生 | 试卷列表 ✓ |
| R15 | `/assessment/:paperId/take` | `assessment/TakeView` | 学生 | 答题 + 提交 ✓ |
| R16 | `/me/records` | `assessment/RecordListView` | 学生 | 仅本人 ✓ |
| R17 | `/me/records/:recordNo` | `assessment/RecordDetailView` | 学生 | 含答题明细 ✓ |
| R18 | `/me/wrong-questions` | `assessment/WrongView` | 学生 | 仅本人 ✓ |

> 计数：**18 条路由**（含详情页 ✓）。其中访客 9 条 ✓、登录后 9 条 ✓。

### 3.2 旧 51 页的处置（**保留 / 合并 / 重构 / 淘汰** ✓）

| 旧目录（页数）| 处置 | 去向 |
| --- | --- | --- |
| `home`(1) | **重构** ✓ | R1 |
| `login`(1) | **重构** ✓ | R2 |
| `yonghu`(5) | **重构** ✓ | R2/R3/R10 + 头像上传 |
| `xinlilaoshi`(5) | **重构** ✓ | R8/R9/R10（老师侧）+ R3 |
| `xinlilaoshiOrder`(4) | **合并** ✓ | R12/R13（预约）|
| `xinlilaoshiCollection`(3) | **合并** ✓ | R11（含原 detail ✓）|
| `xinlilaoshiLiuyan`(3) | **合并** ✓ | R13（留言）|
| `jiankangzhishi`(3) | **合并** ✓ | R4/R5 |
| `tongzhi`(3) | **合并** ✓ | R6/R7 |
| `exampaper`(4) + `examquestion`(3) | **合并** ✓ | R14/R15（原 add/list/detail/exam → 2 视图 ✓）|
| `examrecord`(3) + `examredetails`(3) + `examrewrongquestion`(3) | **合并** ✓ | R16/R17/R18 |
| `dictionary`(3) | **淘汰** ✓ | 数据由 `sysDictItem/page` 提供 ✓（前端不再有字典管理页 ✓）|
| **`chat`(1)** | **淘汰** ✗ | **无对应后端控制器** ✓（实测 16 个控制器中无 chat ✓）|
| **`recharge`(1)** | **淘汰** ✗ | **无支付后端** ✓；模板遗留 ✓ |

**净效果** ✓：**51 → 18 条路由**（页面级合并率 **≈65%** ✓）。

---

## 4. API 模块（**以 Step 5c 冻结合同为准** ✓，不新增 ✗）

| 前端模块 | 使用的 frozen 前缀 | 备注 |
| --- | --- | --- |
| `api/auth.ts` | `/student` · `/counselor` · `/adminUser` | `/login` `/register` `/session` ✓ |
| `api/content.ts` | `/knowledgeArticle` · `/notification` · `/counselor` | 公开读用 **`/list`** ✓ |
| `api/counselor.ts` | `/counselorMessage` · `/counselorFavorite` · `/counselingAppointment` | 全部需登录 ✓ |
| `api/assessment.ts` | `/assessmentPaper` · `/assessmentQuestion` · `/assessmentRecord` · `/assessmentAnswer` · `/assessmentWrongQuestion` | |
| `api/profile.ts` | `/student` · `/counselor` | `/session` `/update` `/resetPass`✗ 见 §6 |
| `api/dict.ts` | `/sysDictItem` | 仅用 `page` ✓（公开 ✓）|
| `api/file.ts` | `/file` | `upload` ✓ `download` ✓ |
| **`api/config.ts`** | **`/config`（实测 ✓）** ✗ vs 冻结清单 `sysConfig` ✗ | **P0-1 未决** ✗ |

---

## 5. 页面 → API → 权限契约（**本 Step 核心产物** ✓）

> 列含义 ✓：**「前端需要」**= 该页面完成任务所需能力 ✓；**「旧 API 实际」**= 代码实测的端点与公开性 ✓；**「差距」**= 二者的差 ✓

| 页面 | 前端需要 | 旧 API 实际 | 认证/范围 | 差距 |
| --- | --- | --- | --- | --- |
| 首页 | 知识/通知/老师 **公开列表** | `/knowledgeArticle/list` ✓ `/notification/list` ✓ `/counselor/list` ✓ **均 `@IgnoreAuth`** ✓ | 公开 | ✅ 无 |
| 知识/通知详情 | 单条详情 | 详情端点**需登录** ✗（仅 `/list` 公开 ✓）| — | ⚠️ **G1** ✗：公开详情需 `/list` 客户端筛选 ✓ 或后端补公开 `info` ✗ |
| 老师目录 | 公开列表 | `/counselor/list` ✓ 公开 ✓ | 公开 | ✅ |
| 老师详情 | 单条 + 是否已收藏 | `/counselor/info/{id}` **需登录** ✗；`/counselor/list` 公开 ✓ | 登录 | ⚠️ **G1** ✗ 同上 |
| 登录 | 三端登录 | `/student|/counselor|/adminUser` `/login` ✓ 全公开 ✓ | 公开 | ✅ |
| 注册 | 学生/老师注册 | `/student/register` ✓ `/counselor/register` ✓ 公开 ✓ | 公开 | ✅（**`/adminUser/register` 亦公开 ✗ → 前端不得暴露 ✗**）|
| 我的收藏 | 本人收藏列表 + 删除 | `/counselorFavorite/list`·`/page`·`/delete` ✓ | 学生·**服务端强制 `student_id`** ✓ | ✅（G1/D2-2b 已证 ✓）|
| 我的预约 | 本人预约列表 + 新增/取消 | `/counselingAppointment/*` ✓ | 学生·**服务端强制** ✓ | ✅ |
| 我的留言 | 本人留言 + 新增 | `/counselorMessage/*` ✓ | 学生·**服务端强制** ✓ | ✅ |
| 老师-收到的留言 | 按 `counselor_id` 过滤 | `/counselorMessage/*` ✓ **有 `counselor_id` 且 XML 支持** ✓ | 老师·`counselor_id` ✓ | ✅（B3 已用 c1/c2 双教师证明 ✓）|
| 测评列表 | 试卷列表 | `/assessmentPaper/list` ✓ 公开 ✓ | 公开 | ✅ |
| 答题 | 题目列表 + 提交 | `/assessmentQuestion/list` ✓ 公开 ✓；`/assessmentRecord/save`·`/assessmentAnswer/save` ✓ | 学生 | ⚠️ **G2** ✗：**无"提交整卷"原子端点** ✗ ⇒ 前端需多次调用 ✓（一致性风险 ✗，登记不改造 ✗）|
| 我的记录 | 本人记录 | `/assessmentRecord/list`·`/page` ✓ | 学生·服务端强制 ✓ | ✅（A=1/B=0 已证 ✓）|
| 记录详情 | 某 `recordNo` 的答题明细 | `/assessmentAnswer/list?recordNo=` ✓ | 学生·服务端强制 ✓ | ✅ |
| 我的错题 | 本人错题 | `/assessmentWrongQuestion/list` ✓ | 学生·服务端强制 ✓ | ✅ |
| 个人中心 | 我的资料 + 改资料 + 头像 | `/student/session` ✓ `/student/update` ✓ `/file/upload` ✓ | 本人 | ✅ |
| 修改密码 | — | **`/student/resetPass` 与 `/counselor/resetPass` 为 `@IgnoreAuth` 公开** ✗✗ | **无认证** ✗ | 🔴 **G3：高危** ✗ —— 新前端**禁止暴露** ✗；后端风险**单独立项** ✓（与 D7 同类 ✓）|
| 下拉字典 | 字典项 | `/sysDictItem/page` ✓ 公开 ✓（白名单 ✓）| 公开 | ✅ |
| 站点配置 | 轮播图等 | `/config/list` ✓ `/config/detail/{id}` ✓ 公开 ✓ | 公开 | ⚠️ **P0-1**：路径与冻结清单 `sysConfig` 不一致 ✗ |

**G1–G3 汇总** ✓：
* **G1** ✗：**公开详情页缺端点**（`/info/{id}` 需登录 ✓，`/list` 公开 ✓）⇒ 方案 A：前端用 `/list` + 客户端筛选 ✓（**可行但低效** ✓）；方案 B：后端补公开详情 ✓（**超出 Step 6** ✗）
* **G2** ✗：**答题提交流程非原子** ✗ ⇒ 前端需容忍部分失败 ✓（登记风险 ✓）
* **G3** 🔴：**公开 `resetPass`** ✗（学生/老师）⇒ **前端绝不暴露** ✗；**后端修复单独立项** ✓

---

## 6. 认证与会话（沿用现状 ✓，不改造 ✗）

| 项 | 现状（实测）| 新前端做法 |
| --- | --- | --- |
| Token 传输 | 请求头 **`Token`** ✓（`LOGIN_TOKEN_KEY`）| axios 拦截器统一注入 ✓ |
| 登录响应 | `{ code, role, userId, token, tableName? }` ✓ | 写入 pinia + 持久化 ✓ |
| 会话校验 | `/{tableName}/session` ✓ | 启动时校验一次 ✓ |
| `auth_token.user_type` | `student` / `counselor` / **`admin_user`** ✓（硬编码 ✓，与 `tableName` 语义独立 ✓）| **前端不使用** ✓ |
| Token 过期 | 服务端 `expired_at` ✓；**无 refresh token** ✗（未发现 ✓）| 401 → 跳登录 ✓（登记 ✗）|

---

## 7. 迁移与退役策略（遵守既有裁定 ✓）

```text
Phase 3 现状
  旧 front/         → 迁移参考 ✓（未入 Git ✗，最终退役条件待业务流迁移完成后决定 ✓）
  旧 admin/src      → 已入 Git ✓（管理端继续使用 ✓）
  旧 admin/dist     → 历史构建 ✗（重建能力见 §9 P1-1 独立工单 ✓）

Step 6/7 之后
  新 frontend/      → ★ 主线 ✓（可版本控制 ✓、可构建 ✓）
  旧 front/         → 仅作行为参照 ✓，逐业务流验证新前端后可退役 ✓（不现在删 ✗）
```

---

## 8. 未决事项与审批点

| ID | 事项 | 影响 | 建议 |
| :-: | --- | --- | --- |
| **P0-1** | ~~`/config ≠ sysConfig`~~ | — | ✅ **已定案（2026-10-19）**：**修订冻结合同保留 `/config`** ✓，**不做 Batch 7** ✗（属**文档错误** ✓，非代码遗漏 ✓）|
| **SEC-PASS-RESET** 🔴 | **公开 `resetPass`**（`/student/resetPass` ✓ `/counselor/resetPass` ✓ 均 `@IgnoreAuth` ✗）| 认证边界与敏感操作不匹配 ✓ | **独立安全工单** ✓：先做 **caller + 业务证据审计** ✓；**不预判修复方式** ✗（改登录后/校旧密码/管理员重置/验证码 均需分别调查 ✓）；**不并入 Step 7** ✗ |
| **SEC-ADMIN-REG** 🔴 | **`/adminUser/register` 公开** ✗ | 同上 ✓ | **独立安全工单** ✓；**是否"应删除的后门"还是"历史设计入口"，凭 `@IgnoreAuth` 不足以判定** ✓ ⇒ 先审计 ✓；**不并入 Step 7** ✗ |
| **P1-1** | **`dist` 重建能力** ✗（lockfile 缺 14/24 直依赖 ✗ + `vue-qr→canvas@2.9` Node22 不可构建 ✗）| 管理端可用性 ✗ | 独立工单 **"Legacy Admin Build Recovery"** ✓（目标重定义为"受控旧 Node 环境恢复构建 + 评估漂移" ✓）|
| **P1-2** | **公开详情页缺端点**（G1）✗ | 知识/通知/老师详情 ✓ | 新前端先用 `/list` 客户端筛选 ✓；后端补端点另议 ✓ |
| **P1-3** | **答题提交非原子**（G2）✗ | 一致性 ✓ | 前端补偿 + 登记 ✓ |
| **P2-1** | `chat` / `recharge` 淘汰 ✗ | 范围收敛 ✓ | 确认无业务需求后淘汰 ✓ |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-19 | **Step 6 设计版首版（零代码 ✓）**：清点旧 front **17 目录 / 51 HTML** 与 **16 控制器真实端点面 + 公开端点精确清单** ✓；核心任务流 A–D ✓；目录职责按业务域推导 ✓；**51 → 18 条路由**（合并率 ≈65% ✓，`dictionary`/`chat`/`recharge` 淘汰 ✓）；API 模块严格对齐冻结合同 ✓；**页面→API→权限契约表**（含"前端需要 vs 旧 API 实际" ✓）与差距 **G1/G2/G3** ✓；登记 **P0-1 `/config`≠`sysConfig`** ✗、**公开 `resetPass`** 🔴、**`adminUser/register` 公开** ✗、**`dist` 重建独立工单** ✓ |
