# Phase 3 / 第一轮只读盘点（Discovery Round 1）

> **前提**：Phase 2 已 **Freeze**（`d3293b5` 已推送，远程顶端 `d3293b5` ✓）
> **本轮禁令**：❌ 不改任何代码 ❌ 不清理任何文件 ❌ 不开始 UI 设计 ❌ 不解冻 Phase 2
> **纪律**：盘点发现 → **只登记** ✓；影响 Phase 3 主线 → 单独裁定 ✓；否则 → backlog ✓
> **覆盖**：V1（前端纳管）· Step 5c（路径映射）· Step 6–10（技术落点）· **后端 API 契约冻结点**

---

## 1. V1 —— 前端资产：构成 / 体积 / Git 现状

### 1.1 实测构成

| 目录 | 文件数 | 体积 | Git 跟踪 |
| --- | :-: | :-: | :-: |
| `src/main/resources/front`（用户端）| 371 | 13.94 MB | **0** ✗ |
| `src/main/resources/admin`（管理端）| 283 | 21.05 MB | **82**（见 1.3 ✗）|
| `src/main/resources/static`（上传/媒体）| 19 | **14.78 MB** | **0** ✗ |
| `src/main/resources/img`（大图）| 3 | 2.47 MB | **0** ✗ |

**按扩展名**（front+admin）：`.svg` 162 · `.js` 157 · `.gif` 79 · **`.vue` 69** · `.html` 53 · `.css` 50 · `.png` 32 · **`.map` 16**

### 1.2 体积主要来源（前 8）

| 大小 | 文件 | 性质 |
| --- | --- | --- |
| 9.5 MB | `admin/admin/dist/js/chunk-vendors.*.js.map` | **构建产物**（source map）|
| 8.1 MB | `static/upload/config1.jpg` | **运行时媒体** |
| 4.2 MB | `admin/admin/src/assets/img/bg.jpg` | **源码资产** |
| 4.0 MB | `static/upload/music.mp3` | **运行时媒体** |
| 2.4 MB | `admin/admin/dist/js/app.*.js.map` | 构建产物 |
| 2.1 MB | `admin/admin/dist/js/chunk-vendors.*.js` | 构建产物 |
| 1.7 MB | `img/img/front-img-bg.jpg` | 源码/静态大图 |
| 1.1 MB | `front/.../modules/tinymce/tinymce.js` | **第三方库** |

> ⇒ 体积主要来自 **① source map/dist 构建产物（~14 MB）** + **② 媒体文件（~12 MB）** + **③ 第三方库** ✓ —— **不是**业务源码 ✓

### 1.3 🔴 与既有记录**不符**的两点（重要更正）

| # | 既有记录（V1）| 实测 | 结论 |
| :-: | --- | --- | --- |
| 1 | 「`admin/admin/src/` **未纳入 Git**」| **62 个 `src/` 文件已跟踪** ✓，且 **12 个 `dist/` 文件也被跟踪** ✗ | **纳管状态不一致** ✗：源码**部分**入库、构建产物**部分**入库 |
| 2 | 前端无独立 Git 历史 | **无 `.git`** ✓（扫描 = 0）| 一致 ✓ |

⚠️ **这两点必须在 Phase 3 的 V1 决策中一并处理**，否则「部分入库」会让后续 `git revert` 与体积治理都失效 ✓

### 1.4 工程文件（决定「源码 vs 产物」的边界）

* `admin/admin/package.json` ✓ + `admin/admin/vue.config.js` ✓ → **管理端是 Vue CLI 4 工程**（可重建 ✓）
* `admin/admin/1-install.bat` / `2-run.bat` / `3-build.bat` ✓ → 自带构建脚本 ✓
* 用户端**无** `package.json` ✗ → 是**静态页面集合**（HTML + 全局脚本 ✓），**不可构建重建** ✗ ⇒ 只能整体纳管或整体弃用 ✓

### 1.5 V1 建议（**待裁定，本轮不动手**）

| 类别 | 建议 |
| --- | --- |
| **必须保留** | `front/**`（用户端唯一实现 ✓，无构建链 ⇒ 不可再生 ✗）、`admin/admin/src/**`（源码 ✓）、`admin/admin/package.json`+`vue.config.js` ✓ |
| **不应入库** | `admin/admin/dist/**`（12 MB 产物 + map ✗，可由 `npm run build` 再生 ✓）· 各 `.map` ✗ |
| **需单独决定** | `static/upload/**`（12 MB 用户/种子媒体 ✓ —— 属**数据**而非代码 ✗：入库会让仓库膨胀 ✓；不入库则部署需另备 ✓）· `img/img/*.jpg` 2.5 MB ✓ |
| **可后续淘汰** | `front/.../modules/tinymce/**`（1 MB+ 富文本库 ✓ —— 新前端用现代编辑器 ✓）· `layui/**` ✓ · `elementui/**`（若新工程用 Vue3+新 UI ✓）|
| **不动** | 旧页面本身 ✓ —— **不因"文件多"而清理** ✓（Phase 3 完成后统一由新工程替代 ✓）|

---

## 2. Step 5c —— 路径映射（**只出映射表，不做替换**）

### 2.1 后端：16 个控制器 / **154 个端点** / **18 个 `@IgnoreAuth`**

| 控制器 | 当前类级路径 | 端点 | 匿名 |
| --- | --- | :-: | :-: |
| `StudentController` | `/yonghu` | 15 | 3 |
| `CounselorController` | `/xinlilaoshi` | 15 | 4 |
| `CounselorMessageController` | `/xinlilaoshiLiuyan` | 9 | 0 |
| `CounselorFavoriteController` | `/xinlilaoshiCollection` | 9 | 0 |
| `CounselingAppointmentController` | `/xinlilaoshiOrder` | 9 | 0 |
| `AssessmentRecordController` | `/examrecord` | 11 | 0 |
| `AssessmentAnswerController` | `/examredetails` | 11 | 0 |
| `AssessmentWrongQuestionController` | `/examrewrongquestion` | 9 | 0 |
| `AssessmentPaperController` | `/exampaper` | 9 | 1 |
| `AssessmentQuestionController` | `/examquestion` | 12 | 1 |
| `KnowledgeArticleController` | `/jiankangzhishi` | 9 | 1 |
| `NotificationController` | `/tongzhi` | 9 | 1 |
| `SysDictItemController` | `/dictionary` | 7 | 1 |
| `SysConfigController` | `config`（⚠️ 无前导 `/`）| 8 | 2 |
| `AdminUserController` | `users`（⚠️ 无前导 `/`）| 10 | 3 |
| `FileController` | `file` | 2 | 1 |

### 2.2 候选映射表（**提案，未应用** ✗）

| 当前 | 候选新路径 | 依据 |
| --- | --- | --- |
| `/yonghu` | `/student` | 类名 `Student` ✓ |
| `/xinlilaoshi` | `/counselor` | 类名 `Counselor` ✓ |
| `/xinlilaoshiLiuyan` | `/counselorMessage` | 类名 ✓ |
| `/xinlilaoshiCollection` | `/counselorFavorite` | 类名 ✓ |
| `/xinlilaoshiOrder` | `/counselingAppointment` | 类名 ✓ |
| `/examrecord` | `/assessmentRecord` | 类名 ✓ |
| `/examredetails` | `/assessmentAnswer` | 类名 ✓ |
| `/examrewrongquestion` | `/assessmentWrongQuestion` | 类名 ✓ |
| `/exampaper` | `/assessmentPaper` | 类名 ✓ |
| `/examquestion` | `/assessmentQuestion` | 类名 ✓ |
| `/jiankangzhishi` | `/knowledgeArticle` | 类名 ✓ |
| `/tongzhi` | `/notification` | 类名 ✓ |
| `/dictionary` | `/sysDictItem` | 类名 ✓ |
| `config` | `/sysConfig` | 类名 ✓ |
| `users` | `/adminUser` | 类名 ✓ |
| `file` | **不改** | 语义已正确 ✓ |

> ⚠️ **命名大小写**（`/counselorMessage` vs `/counselor-message`）属 Step 5c Phase 2 设计决定 ✓ —— 本轮不选 ✓

### 2.3 前端引用量（**带两条口径警告** ⚠️）

| 前缀 | 命中 | 文件 |
| --- | :-: | :-: |
| `/xinlilaoshi` | 36 | 18 |
| `/dictionary` | 23 | 5 |
| `/file` | 16 | 15 |
| `/jiankangzhishi` | 12 | 7 |
| `/tongzhi` | 12 | 7 |
| `/xinlilaoshiOrder` | 12 | 7 |
| `/xinlilaoshiCollection` | 8 | 6 |
| `/yonghu` | 7 | 5 |
| `/exampaper` | 7 | 4 |
| `/examquestion` | 6 | 4 |
| `/examrewrongquestion` | 6 | 4 |
| `/xinlilaoshiLiuyan` | 6 | 4 |
| `/examredetails` | 5 | 3 |
| `/examrecord` | 4 | 3 |
| `/users` | 2 | 1 |
| `/config` | 101 | 51 |
| **`/config` 实际** | **≈ 0** ✓ | — |

* ⚠️ **口径 1（重叠计数）**：`/xinlilaoshi` 的 36 **包含**其子前缀（`/xinlilaoshiOrder` 等）✗ ⇒ 正式映射必须**最长前缀优先**匹配 ✓
* ⚠️ **口径 2（假阳性）**：`/config` 的 101 命中**全部是 `<script src=".../js/config.js">`** ✗（**文件路径**，非 API ✓）⇒ `config` 前缀**几乎没有前端引用** ✓

### 2.4 🔴 动态拼接（**Step 5c 的真正风险**）

| 形态 | 次数 |
| --- | :-: |
| `${this.$storage.get("sessionTable")}/session` | 16 |
| `${vue.detailTable}/detail` | 3 |
| `${table}/session` | 2 |
| `${this.$storage.get("sessionTable")}/update` | 2 |
| `${this.tableName}/login` | 1 |
| `${this.table}/update` / `${table}/update` | 1 / 1 |

**关键事实**：`login.html:259` 写入的是 **`localStorage.setItem('sessionTable', data.role)`** ✗ —— 即**中文角色**（学生/心理老师/管理员），**不是**表名；`detail.html:466` 又用 `localStorage.getItem('userTable')` ✓。

⇒ **路径前缀在运行时部分由登录数据推导** ✓ ⇒ Step 5c **不能只做字符串替换** ✓：必须同时核对
① `login.html` 的存储语义 ✓ ② 登录响应中的 `tableName`/`role` 字段 ✓ ③ 各页面 `userTable`/`tableName` 的来源 ✓

### 2.5 拦截器**硬编码**路径白名单（必须同步）

```text
AuthorizationInterceptor: "/dictionary/page" · "/file/upload" · "/yonghu/register"
```

⇒ 其中 **`/dictionary/page`** 与 **`/yonghu/register`** 会随 Step 5c 改名 ✓（**漏改 = 白名单失效 → 这些端点退化为需鉴权** ✓）

---

## 3. 后端 API 契约**冻结点**（Phase 3 的护栏）

### 3.1 盘点表结构（为每个端点建立一行；Phase 3 期间**只读**）

| 列 | 用途 |
| --- | --- |
| Controller / HTTP Method / **Path** | 稳定性 ✓ |
| **参数**（query / path / body）| 前端适配依据 ✓ |
| **响应结构**（`{code,msg,data}` 的外层 ✓ + `data` 关键字段 ✓）| 前端字段绑定 ✓ |
| **鉴权要求**（`@IgnoreAuth` / 角色 / 403 契约）| 前端登录与降级 ✓ |
| **DataScope / Ownership** | 读范围与归属（G1/G2 已收敛 ✓）|
| **前端调用方** | 一一对应 ✓ |

### 3.2 三类必须区分（Phase 3 的解释权边界）

| 类别 | 定义 | Phase 3 允许的动作 |
| --- | --- | --- |
| **A · 既有 API（冻结）** | 当前 154 个端点中的绝大多数 ✓ | **只适配，不改行为/路径** ✓ |
| **B · Step 5c 批准的路径变更**（15 个前缀 ✓）| 见 §2.2 提案 ✓ | 改名 + 前端同步 ✓（分批 + E2E ✓）|
| **C · 未来 Agent API**（Phase 4+ ✓）| `/agent/**`、Tool 端点等 ✓ | **不得混入 A/B** ✗（新命名空间 ✓）|

> 原则：**Phase 2 已 Freeze ⇒ A 类端点不得因前端不方便而改后端** ✓；若确实需要变更 → 单独裁定 ✓，不夹带 ✓

---

## 4. Step 6–10 —— 技术落点（**只定方向，不设计 UI**）

### 4.1 现状技术栈（实测）

| 端 | 技术栈 | 可重建？ |
| --- | --- | :-: |
| 管理端 | **Vue 2.6.10 + Element UI 2.13 + Vue CLI 4 + axios + echarts 4**（`package.json` ✓）| ✅ 可（`npm run build` ✓）|
| 用户端 | **静态 HTML + Vue 2 + Element UI + layui + 自研 `http` 模块**（无 `package.json` ✗）| ❌ **不可重建**（无构建链 ✗）|
| 管理端模块 | `views/modules/` 下 **23 个目录**（16 业务 + 7 字典子模块 ✓）| — |
| 用户端页面 | `pages/<module>/{list,add,detail,confirm,exam,home,login,center,register,chat,…}.html`（代码生成器风格 ✓）| — |

### 4.2 三个待裁定项（**A/B/C**）

**A. 新工程目录**

| 选项 | 说明 |
| --- | --- |
| `frontend/`（推荐 ✓）| 与 Master Plan Step 6 的措辞一致 ✓（"新 `frontend/` 工程"）|
| `front-v3/` | 更醒目，但与计划文档不一致 ✗ |

**B. 技术栈（只定选型，不写组件）**

| 层 | 推荐 | 依据 |
| --- | --- | --- |
| 框架 | **Vue 3 + `<script setup>` + TS** | 与 Vue2 共存期短 ✓；生态完整 ✓ |
| 构建 | **Vite** | 替代 Vue CLI 4（已停更 ✓）|
| 路由 | **Vue Router 4** | — |
| 状态 | **Pinia** | 替代 Vuex ✓ |
| UI | **Element Plus**（或自建 Design System + 少量组件）| 与旧 Element 语义最接近 ✓，迁移成本最低 ✓ |
| HTTP | **axios**（薄封装 + 统一 401/403/400/500 处理 ✓）| 与既有 `{code,msg,data}` 契约对齐 ✓ |

**C. 必须迁移的业务流程**（以**流程**为单位，不是页面数 ✓）

```text
登录/注册 → 首页 → 健康知识（浏览/详情）→ 心理老师（列表/详情/留言/收藏）
        → 预约（下单/我的预约/状态）→ 心理测评（试卷/答题/结果/错题）
        → 个人中心（资料/密码/充值）→ 通知
```

> 该流程与 Step 6 的「用户目标驱动」信息架构一一对应 ✓ —— **具体页面设计属 Step 7+，本轮不做** ✓

---

## 5. 本轮「只登记」的发现（**不处理** ✓）

| # | 发现 | 归属 |
| :-: | --- | --- |
| 1 | `admin` 的 Git 状态**不一致**（62 src + **12 dist** 已入库 ✗）| **V1**（Phase 3）✓ |
| 2 | 体积主要来自 `dist`+`.map`（~14 MB）与媒体（~12 MB）✗ | V1 ✓ |
| 3 | `sessionTable` 写入的是**中文角色** ✗；前缀部分由登录数据推导 ✓ | **Step 5c**（必须先核对语义 ✓）|
| 4 | `/config` 101 命中是**假阳性**（`config.js` 文件路径 ✓）；真实 API 引用 ≈ 0 ✓ | Step 5c ✓ |
| 5 | 拦截器硬编码 3 个路径（`/dictionary/page`、`/file/upload`、`/yonghu/register`）| Step 5c ✓（必须同步 ✓）|
| 6 | `SysConfig`/`AdminUser` 的类级路径**无前导 `/`**（`config`、`users` ✓）| Step 5c ✓（改名时统一 ✓）|
| 7 | 用户端**无构建链** ✗（不可重建 ⇒ 只能整体纳管或整体弃用 ✓）| V1 ✓ |
| 8 | 16 个控制器 / **154 端点** / 18 匿名 ✓ | 契约冻结点 ✓ |

---

## 6. 下一轮建议（**仍只读**）

1. **Step 5c 精确映射**：按「**最长前缀优先**」重算引用量 ✓ + 逐文件列出**需修改的行号** ✓（形成可执行映射表 ✓）+ 核对 `sessionTable`/`userTable`/`tableName` 的完整语义链 ✓
2. **V1 纳管方案**：给出 3 个候选（全量纳管源码 / 仅纳管 `src`+配置 / 保持现状+补 `.gitignore` ✓）与**体积估算** ✓，以及 `static/upload` 的处置建议 ✓
3. **Step 6 骨架**：只定**目录结构 + 路由表 + API 封装层**（不写业务页面 ✓），作为 Step 7 的前置 ✓

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-18 | Phase 3 第一轮只读盘点（**零代码改动**）：V1 构成/体积/Git 现状（**更正两处既有记录**：admin 部分入库 ✗、dist 已入库 ✗）；Step 5c 全量 API 盘点（16 控制器/154 端点/18 匿名）+ 候选映射表 + 前端引用量（含两条口径警告）+ **动态拼接 8 种形态** + 拦截器硬编码 3 路径；API 契约冻结点 8 列结构 + **A/B/C 三类边界**；Step 6–10 技术落点（现状栈 + 3 个待裁定项 + 流程清单）；登记 8 项发现（全部只登记 ✓）|
