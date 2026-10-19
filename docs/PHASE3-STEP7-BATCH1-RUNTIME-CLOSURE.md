# Phase 3 / Step 7 · Batch 1 —— 真实运行时最小闭环记录

> **范围** ✓：`frontend/` 脚手架冒烟 + **登录 → 身份保存 → 首页 → 真实公开 API** 最小闭环
> **不在范围** ✗：18 条路由全量 · 旧 `front` 迁移 · 管理端 · `resetPass` · `adminUser/register` · Agent/RAG · 复杂权限矩阵
> **提交状态** ✗：**未提交**（`frontend/` 全部为未跟踪文件 ✓，待裁定后才提交）

---

## 1. 三层验收

### 第一层 · npm 安装 ✅（**不是"一次成功"** ✗）

```
added 114 packages in 1m
```

| 事实 | 记录 |
| --- | --- |
| 首次安装 | **残缺** ✗ —— `esbuild.exe` 仅 **611 KB** ✗（正常 ~10 MB ✗）→ `spawnSync … EFTYPE` ✗ |
| 根因 | tarball **截断** ✗（慢网络 ✗）+ npm **缓存污染** ✗ |
| 处置 1 | `npm cache verify`（**garbage-collected 282 项 / 118 MB** ✓）|
| 处置 2 | 定向修复残缺包 **3 个**：`@esbuild/win32-x64`（**9,913,856 B** ✓）· `vite`（`dist/node/cli.js` ✓）· `@rollup/rollup-win32-x64-msvc`（**2,666,496 B** ✓）|
| 处置 3 | **整体移出**残缺 `node_modules` → `%TEMP%\fe-nm-broken-000438` ✓（**非删除** ✓）→ 重新安装 |
| 结果 | `added 114 packages` ✓ · **`stderr` 空** ✓ · `package-lock.json` 已生成 ✓ · `pkgs=65` ✓ |

**`npm dedupe` 单独记录** ✓（**不构成依赖版本变更** ✓）：

```
added 6 packages, and removed 21 packages in 28s
```
* `@vue/shared@3.5.44` 由**嵌套**提升为**顶层** ✓（此前 `element-plus/es/**` 的裸说明符 `import "@vue/shared"` 无法解析 ✗ → `Could not resolve "@vue/shared"` ✗ ×5）
* **未改 `package.json`** ✓ · **未升级任何版本** ✗ · **未改 `vite.config.ts`** ✗
* 记为：**安装完成 + 依赖树整理完成** ✓

> `--force` / `cache verify` / 定向修复 / `dedupe` **全部只属于新 `frontend/` 的安装恢复** ✓，
> **与 Legacy Admin 的 Node 14 / lockfile 漂移问题完全分开** ✓（不同对象、不同结论 ✓）。

### 第二层 · Vite + Proxy ✅

```
VITE v5.4.21  ready in 882 ms
➜  Local:   http://localhost:5173/
```

| 项 | 证据 |
| --- | --- |
| 页面 | `http://localhost:5173/` → **200** ✓（`127.0.0.1:5173` 的 `000` 已证明是**探针地址错误** ✗：Vite 仅监听 **`[::1]:5173`** ✓，`localhost` → **200** ✓）|
| 模块编译 | `/src/main.ts` · `router/index.ts` · `stores/auth.ts` · `api/*.ts` · `views/*.vue` · `layouts/*.vue` → **全部 200** ✓ |
| 代理登录 | `5173/api/student/login` → `{"code":0,"role":"学生","userId":1,"token":"tk53…","username":"学生姓名1","tableName":"student"}` ✓ |
| 代理列表 | `5173/api/knowledgeArticle/list` → `data.total = 5` ✓（= DB `knowledge_article = 5` ✓）|

### 第三层 · 浏览器级最小闭环 ✅（Playwright + **系统 Chrome** ✓，免下载 197.7 MiB ✗）

| # | 检查项 | 结果 |
| :-: | --- | --- |
| 1 | `/login` 页面加载 | ✅ 标题 `心理健康陪伴系统` ✓ · `学生登录` / `用户名` / `密码` / `登录` ✓ |
| 2 | 真实登录 | ✅ 填 `a1` / `123456` → 点击 → **`code=0`** ✓ |
| 3 | **Pinia 认证状态** | ✅ `localStorage`：`mh_token=fly2…` ✓ · `mh_user_id=1` ✓ · `mh_role=学生` ✓ · `mh_username=学生姓名1` ✓ |
| 4 | **Router 跳转** | ✅ `/login` → **`/`** ✓（`document.location.pathname = "/"` ✓）|
| 5 | Axios 经 `/api` | ✅ 无直连 8080 的请求路径 ✓（`baseURL:'/api'` ✓）|
| 6 | Vite Proxy | ✅ 浏览器内直接导航 `5173/api/knowledgeArticle/list` 返回真实 JSON ✓ |
| 7 | **页面渲染真实 DB 数据** | ✅✅ **`共 5 条`** + **5 张卡片 `健康知识名称5…1`** + `2022-03-29 11:39:43` ✓（= `total:5` ✓，**非 Mock** ✓）|
| 8 | Console | ✅ **Errors 0 / Warnings 0** ✓（favicon 404 已消除 ✓）|
| 9 | 未登录访问受保护页面 | ⚠️ **本批未实现任何受保护路由** ✗ ⇒ **不适用/未验证** ✓（后续批次补 ✓）|
| 10 | Network「无失败请求」 | ⚠️ **本 CLI 版本无 `network` 子命令** ✗ ⇒ 仅以「数据渲染成功 + Console 0 error」**间接**证明 ✓，**未直接观测** ✗ |

**截图**：`step7-home.png`（顶栏 `学生姓名1` ✓ · `健康知识` `共 5 条` ✓ · 5 张真实卡片 ✓）

---

## 2. 本批发现的**真实前后端契约缺陷**（必须保留在案 ✓）

> 这两个不是环境问题 ✗，而是 Step 7 骨架**最初对响应契约理解不准确** ✗ —— 即使重装/换机也必须记住 ✓。

| # | 缺陷 | 实际契约 | 修正 |
| :-: | --- | --- | --- |
| **C1** | `content.ts` 把 `/knowledgeArticle/list` 的 `data` **当数组** ✗ | 实际是**分页对象** `data:{total,pageSize,totalPage,currPage,list:[…]}` ✓ | 兼容**数组 + 分页对象** ✓（`toArray()` ✓）|
| **C2** | `auth.ts` 用 `unwrap()` 只取 `data` ✗ → `r.token` 抛 `Cannot read properties of undefined (reading 'token')` ✗ | `/student/login` 把 **token/role/userId/tableName 放在响应顶层** ✓（**无 `data` 包裹** ✗）| 两种形状都兼容 ✓ |

> **C1/C2 说明后端响应形状不统一** ✓（登录在顶层、列表在 `data`）—— 本批**只做兼容** ✓，
> **不在 Step 7 内统一返回模型** ✗；是否统一留待正式整理 API 层时决策 ✓。

**附带修正**（非运行时 ✗）：`index.html` 增加 `<link rel="icon" href="data:," />` ✓ → 消除 `favicon.ico` **404** ✓（原为 Console 唯一 error ✓）。

---

## 3. 本批的**测量错误**（非代码 ✗，如实登记）

| # | 现象 | 根因 |
| :-: | --- | --- |
| M1 | `curl 127.0.0.1:5173` = `000` ✗ | Vite 监听 **`[::1]`** ✓，应使用 `localhost` ✓ |
| M2 | 登录点击后无反应 ✗ | 使用了**首屏旧 ref** `e13/e20/e21` ✗（实际需 `f2e13/…` ✓，每页 refs 带前缀 ✓）|
| M3 | `playwright-cli eval` 报 `input is not defined` ✗ / `Invalid regular expression flags` ✗ | PowerShell → 原生程序时**双引号被剥离** ✗ |
| M4 | `snapshot --filename=…` 文件为空 ✗ / `network` 命令不存在 ✗ | 该 CLI 版本差异 ✗ |

---

## 4. 结论与措辞

✅ **可以表述为**：

> **Vue 3 新前端完成真实运行时最小闭环：浏览器 → Vue Router / Pinia → Axios → Vite Proxy → Spring Boot → MySQL → 页面真实数据渲染。**

❌ **不得表述为**："新前端已经完成" ✗ / "Step 7 已完成" ✗

**Step 7 Batch 1 状态**：**运行层闭环已达成** ✓ · **尚未提交** ✗ · **尚未宣布批次关闭** ✗（待裁定 ✓）

---

## 5. 明确未验证（explicit gaps ✗）

| # | 项 | 说明 |
| :-: | --- | --- |
| G1 | 受保护路由的未登录行为 | 本批仅有 `/`（公开）与 `/login` ✓ |
| G2 | Network 层「无失败请求」直接观测 | CLI 无 `network` ✓，仅间接证明 ✓ |
| G3 | `npm run build` | **未执行** ✗ ⇒ 不宣称生产构建可复现 ✗ |
| G4 | `admin/dist` | 仍为旧构建 ✗（Batch 6 统一重建 ✓）|
| G5 | 其余 16 条路由 / 旧 `front` 迁移 | 后续批次 ✓ |

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-20 | Step 7 Batch 1 运行层闭环记录：三层验收（npm 安装**非一次成功** ✓ / Vite Proxy ✓ / **浏览器级 5 条真实数据** ✓）；**C1/C2 两处真实契约缺陷**保留在案 ✓；M1–M4 测量错误 ✓；**G1–G5 明确未验证** ✓；**未提交** ✗ |
