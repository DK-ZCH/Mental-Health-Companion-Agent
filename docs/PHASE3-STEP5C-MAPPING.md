# Phase 3 / Step 5c —— **可执行映射表**（只读产出，**未做任何替换**）

> **前提**：Phase 2 Freeze（`d3293b5` 已推送）· 本轮**只读** ✓ · ⛔ **不改 `/yonghu → /student`** ✓
> **验收标准（本轮）**：不是"看起来能改"，而是**每一个待改前缀都能定位到实际调用者** ✓
> **方法**：**最长前缀优先**（`/xinlilaoshiCollection` 等先于 `/xinlilaoshi` ✓）· **行号级** ✓ · **语义链**（赋值→存储→读取→拼接→HTTP ✓）

---

## 0. 对 Round 1 的**两处更正**（重要）

| # | Round 1 表述 | 复核结果 |
| :-: | --- | --- |
| 1 | `@IgnoreAuth` **18** 个 | ✅ 实际 **16** 个（Round 1 多计 ✗）—— 清单见 §3 |
| 2 | `sessionTable` 是「真实风险」⇒ 前缀由登录数据推导 | ⚠️ **只对一半**：**管理端**一致（= 菜单 `tableName` = API 前缀 ✓）；**用户端**存的是**中文角色** ✗ ⇒ 用户端那些 `/session` 调用**拼不出合法路径**，属**既有缺陷**（不是 Step 5c 阻断点 ✓）|

---

## 1. 可执行映射表（**逐前缀**）

> 「前端调用点」列为**字面量**命中（最长前缀 ✓，已排除 `*.min.js`/`dist`/`tinymce`/`layui`/`elementui`）；
> **动态**路径不在此列（见 §2 语义链 ✓）；`非API` 已剔除（如 `config.js` 脚本引用 ✓）。

| 旧路径 | 候选新路径 | 前端字面量调用点（文件:行）| 形成方式 | 鉴权（匿名端点）| 白名单/特殊逻辑 | 可直接改？ |
| --- | --- | --- | --- | --- | --- | :-: |
| `/yonghu` | `/student` | `front/.../pages/yonghu/*.html`(4 处)、`admin/.../modules/yonghu/*.vue` | 字面量 ✓ + **`tableName` 菜单驱动**（管理端 ✓）| 15 端点 / **3 匿名**：`login` `register` `resetPass` | 🔴 **拦截器白名单 `/yonghu/register`** ✓ | ⚠️ 需同步白名单 + 菜单 |
| `/xinlilaoshi` | `/counselor` | 9 处（5 文件 ✓）| 字面量 ✓ + `tableName` | 15 / **4 匿名**：`login` `register` `resetPass` `list` | — | ✅ 可直接改（**重名风险以外**）|
| `/xinlilaoshiLiuyan` | `/counselorMessage` | 3 处（2 文件 ✓）| 字面量 ✓ | 9 / 0 | — | ✅ |
| `/xinlilaoshiCollection` | `/counselorFavorite` | 5 处（4 文件 ✓）| 字面量 ✓ | 9 / 0 | — | ✅ |
| `/xinlilaoshiOrder` | `/counselingAppointment` | 9 处（5 文件 ✓）| 字面量 ✓ | 9 / 0 | — | ✅ |
| `/examrecord` | `/assessmentRecord` | 4 处（3 文件 ✓）| 字面量 ✓ | 11 / 0 | — | ✅ |
| `/examredetails` | `/assessmentAnswer` | 3 处（2 文件 ✓）| 字面量 ✓ | 11 / 0 | — | ✅ |
| `/examrewrongquestion` | `/assessmentWrongQuestion` | 4 处（3 文件 ✓）| 字面量 ✓ | 9 / 0 | — | ✅ |
| `/exampaper` | `/assessmentPaper` | 5 处（3 文件 ✓）| 字面量 ✓ | 9 / **1 匿名**：`list` | B 组公开 ✓ | ✅ |
| `/examquestion` | `/assessmentQuestion` | 4 处（3 文件 ✓）| 字面量 ✓ | 12 / **1 匿名**：`list` | B 组公开 ✓ | ✅ |
| `/jiankangzhishi` | `/knowledgeArticle` | 9 处（5 文件 ✓）| 字面量 ✓ | 9 / **1 匿名**：`list` | B 组公开 ✓ | ✅ |
| `/tongzhi` | `/notification` | 9 处（5 文件 ✓）| 字面量 ✓ | 9 / **1 匿名**：`list` | B 组公开 ✓ | ✅ |
| `/dictionary` | `/sysDictItem` | 4 处（3 文件 ✓）| 字面量 ✓ | 7 / **1 匿名** | 🔴 **拦截器白名单 `/dictionary/page`** ✓ | ⚠️ 需同步白名单 |
| `config`（**无前导 `/`**）| **`/config`**（**合同修订 2026-10-19：原写 `/sysConfig` 属文档错误，实际未执行该 rename** ✓）| **0 处**（101 命中全为 `config.js` 假阳性 ✓）| — | 8 / **2 匿名**：`list` `detail/{id}` | 前缀写法需统一 ✓ | ✅ 可直接改（**无前端调用** ✓）|
| `users`（**无前导 `/`**）| `/adminUser` | 2 处（1 文件 ✓）| 字面量 ✓ | 10 / **2 匿名**：`login` `register` | 管理端登录在 `login.vue:151` ✓ | ✅ |
| `file` | **不改** ✓ | 15 处（3 文件 ✓）| 字面量 ✓ | 2 / **1 匿名**：`download` | 🔴 **拦截器白名单 `/file/upload`** ✓ | ✅ 不动 |

**汇总**：16 个前缀 / **154 端点** / **16 匿名** / 前端**字面量命中 89 行**（剔除 `/config` 的 99 条假阳性 ✓）

---

## 2. 语义链（五个动态符号的**完整追踪**）

### 2.1 `sessionTable` —— **两端行为不同**（本轮最重要结论）

| 端 | 赋值 | 存储 | 读取 | 拼接 | HTTP |
| --- | --- | --- | --- | --- | --- |
| **管理端** ✅ | `login.vue:147` `this.tableName = menus[i].tableName`（**菜单表** ✓）| `login.vue:158` `$storage.set("sessionTable", this.tableName)` ✓ | `IndexHeader.vue:44` `$storage.get("sessionTable")` ✓ | `IndexHeader.vue:46` `sessionTable + '/session'` ✓ | `$http({url: …})` ✓ → **合法**（=`/yonghu/session` ✓）|
| **用户端** ❌ | `login.html:259` `setItem('sessionTable', data.role)` ✗（**中文角色**）| 同上 ✓ | `recharge.html:122` 读的是 **`userTable`** ✗ | `recharge.html:123` `${table}/session` ✗ | → **`/学生/session`** ⇒ **既有缺陷**（拼不出合法路径 ✓）|

⇒ **结论**：Step 5c 的**真实耦合面 = 管理端菜单表** ✓；用户端那批 `/session` 调用属**既有缺陷**（登记 ✓，**不在 Step 5c 修复** ✓）。

### 2.2 `userTable`（用户端）

```text
赋值：login.html:258  localStorage.setItem('userTable', data.role)   ← 中文角色 ✗
读取：add.html:251/261/270/284/289/317/327、exam.html:225、recharge.html:122 ✓
拼接：config.js:33   '../' + localStorage.getItem('userTable') + '/center.html'   ← 页面路径（非 API）✗
      recharge.html:123  `${table}/session`                                        ← API ✗
```

### 2.3 `tableName`（**两套菜单表**）

```text
管理端菜单：admin/src/…/config.js:82   {"menu":"学生管理","tableName":"yonghu"} …   ← 硬编码 API 前缀 ✓
            login.vue:151  `${this.tableName}/login?…`                              ← 由菜单驱动 ✓
            IndexAsideStatic.vue / IndexMain.vue  menuHandler(child.tableName)      ← 前端模块路由 ✓
用户端菜单：front/…/js/menu.js:18/30/42/58 … "tableName":"dictionaryJiankangzhishi" …  ← 同样硬编码 ✓
```

⇒ **改名必须同步两份菜单表** ✓（否则管理端菜单点进去会 404 ✓），**且菜单 `tableName` 同时是前端模块目录名** ✓（`views/modules/<tableName>/` ✓）

### 2.4 `${table}/update` / `${table}/session`（模板字符串，8 处）

| 形态 | 次数 | 值来源 | 判定 |
| --- | :-: | --- | :-: |
| `${this.$storage.get("sessionTable")}/session` | 16 | 管理端 = 菜单前缀 ✓ / 用户端 = 中文角色 ✗ | **管理端有效** ✓ |
| `${vue.detailTable}/detail` | 3 | 运行时 `detailTable` ✓ | 需逐页确认 ✓ |
| `${table}/session` | 2 | `userTable` ✗ | **用户端缺陷** ✓ |
| `${this.$storage.get("sessionTable")}/update` | 2 | 同上 ✓ | 管理端有效 ✓ |
| `${this.tableName}/login` | 1 | **菜单表** ✓ | 有效 ✓ |
| `${this.table}/update` / `${table}/update` | 1 / 1 | 同上 ✓ | 有效 ✓ |

### 2.5 `localStorage` 键名全集（20 个）—— **不是 API 前缀，不得误改** ✗

`adminName` · `buyNumber` · `counselorId` · `dictionaryId` · `examredetailsId` · `examrewrongquestionId` · `iframeUrl` · `jiankangzhishiId` · `paperId` · `questionId` · `role` · `sessionTable` · `studentId` · `Token` · `tongzhiId` · `userId` · `userTable` · `xinlilaoshiCollectionId` · `xinlilaoshiLiuyanId` · `xinlilaoshiOrderId`

> ⚠️ 其中 `examredetailsId` / `xinlilaoshi*Id` 等是**详情页参数键** ✓ —— 改名与否是**命名选择**，与 API 路径**无关** ✓（⛔ 不得因 Step 5c 而批量替换 ✓）

---

## 3. 匿名端点全清单（**16 条**，改名时最易打破）

| # | 端点 | 说明 |
| :-: | --- | --- |
| 1–2 | `users/login` · `users/register` | 管理端登录/注册 ✓ |
| 3–4 | `/exampaper/list` · `/examquestion/list` | B 组公开 ✓ |
| 5–8 | `/xinlilaoshi/login` · `register` · `resetPass` · `list` | ⚠️ 含 **PII**（已登记于 D2）✓ |
| 9 | `file/download` | 白名单同名 ✓ |
| 10–11 | `/jiankangzhishi/list` · `/tongzhi/list` | B 组公开 ✓ |
| 12–14 | `/yonghu/login` · `register` · `resetPass` | 🔴 `register` 在拦截器白名单 ✓ |
| 15–16 | `config/list` · `config/detail/{id}` | C 组（配置公开）✓ |

---

## 4. 端点 vs 前端调用者（**"有调用者"的边界**）

* **154 个端点** vs **89 行前端字面量命中** ⇒ **大量端点无前端调用者** ✓
* 已知无调用方家族（与既有登记一致 ✓）：`/save` ×2 · `/batchInsert`（无前端 ✓）· 部分 `/delete` ✓
* ⇒ Step 5c **只应改「有调用者」的前缀 + 白名单 + 菜单表** ✓；**无调用者的端点改名属"顺手"** ✗ —— 建议**同步改路径但不做行为变更** ✓（保持一致性 ✓），或**单列**（待定 ✓）

---

## 5. 两种实施方案（**证据仍不足以选定** ✓）

| 方案 | 内容 | 优点 | 风险 | 需要的额外证据 |
| --- | --- | :-: | --- | --- |
| **A · 改旧系统** | 改 16 个 Controller 前缀 + 旧前端 89 行 + 2 份菜单表 + 3 条白名单 ✓ | 一次性彻底 ✓；旧系统也语义统一 ✓ | 旧前端**无构建链** ✗ ⇒ 只能手改 371 个文件中的相关行 ✓；回归面大 ✗ | 旧前端是否会随 Phase 3 被替换 ✗ / 用户端 `userTable` 缺陷是否一并修 ✗ |
| **B · 新前端规范化 + 兼容层** | 后端**保留旧路径** ✓，新 `frontend/` 直接调用新路径（后端加 `/student/**` 别名或新版 Controller ✓）| 旧系统零回归 ✓；新前端从一开始就干净 ✓ | 需**双路径并存**期 ✓；后端出现两套入口 ✓ | 兼容层保留多久 ✓ / 是否允许后端新增别名（**Phase 2 已 Freeze** ⇒ 需单独裁定 ✓）|

> ⛔ 本轮**不选** ✓ —— 需先回答：**旧 `front/` 是否会在 Phase 3 退役** ✗（决定 A 是否有意义 ✓）

---

## 6. 本轮登记（**只登记，未处理** ✓）

| # | 项 | 归属 |
| :-: | --- | --- |
| 1 | `@IgnoreAuth` 实为 **16**（Round 1 多计 18 ✗）| 已更正 ✓ |
| 2 | **用户端** `sessionTable`/`userTable` 存中文角色 ✗ ⇒ `/session`、`center.html` 路径不合法 ✓ | **既有缺陷**（登记，不在 Step 5c 修 ✓）|
| 3 | 管理端菜单表 `config.js:82` + 用户端 `menu.js` **硬编码 16 个前缀** ✓ | Step 5c **必须同步** ✓ |
| 4 | 拦截器白名单 3 条（`/dictionary/page`、`/file/upload`、**`/yonghu/register`**）| Step 5c **必须同步** ✓ |
| 5 | `config` / `users` 类级路径**无前导 `/`** ✓ | Step 5c 统一写法 ✓ |
| 6 | 20 个 `localStorage` 键中含旧模块名 ✗（`xinlilaoshi*Id` 等）| **命名选择**，与路径无关 ✓ |
| 7 | `/config` 101 命中全为假阳性 ✓；真实前端引用 ≈ 0 ✓ | 已澄清 ✓ |
| 8 | 154 端点 vs 89 行调用 ⇒ 大量端点无前端调用者 ✓ | 与 T3 一致 ✓ |

---

## 7. 下一轮（Round 3）

1. **V1 Git 纳管落地**（按方案 ② 倾向：`admin/src` + 配置纳管、`front` 暂不纳管、`dist` 退出 ✓）—— **本轮不动 Git** ✓
2. **Step 5c 方案 A/B 的裁决所需证据**：旧 `front/` 的退役计划 ✓ + 是否允许后端双路径 ✓
3. 之后才是 **Step 6 骨架设计**（目录 / 路由（用户目标→页面→状态→API→权限）/ API 封装层 ✓ —— **不写 axios 实现** ✓）

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-18 | Step 5c 可执行映射表（**只读**）：16 前缀逐条映射（含调用点/形式/鉴权/白名单/可否直接改 ✓）；**更正 Round 1 两处**（`@IgnoreAuth` 16 非 18 ✓、`sessionTable` 两端语义不同 ✓）；五符号语义链（**管理端一致 vs 用户端缺陷** ✓）；16 条匿名端点全清单；154 端点 vs 89 行调用者；两种实施方案与**缺失证据**；登记 8 项（未处理 ✓）|
