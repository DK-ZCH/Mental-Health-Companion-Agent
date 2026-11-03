# Phase 4 前置收尾盘点（四类）

> **性质**：🏁 **Phase 4 前置收尾 = CLOSED**（盘点 → 裁定 → C-1 实施 → 验证）
> **基线**：`HEAD = origin = f596ee3` · 收尾改动 **1 文件 +8/−1**（`InterceptorConfig`，已提交）
> **⚠️ 语义**：本文件 CLOSED **不等于** `DataScope` 审计 CLOSED（见 §六「语义区分」）
> **目标**：为 ③ 设置**出口**，避免「发现一个问题 → 再开一个专项 → 再设计一套机制」
> **不做什么**：不做全系统安全审计、不做 D4、不进入 Phase 4、不重构旧 Admin

---

## 零、盘点方法与边界（先说清楚没查什么）

| 项 | 做法 |
| --- | --- |
| 覆盖 | 16 个 Controller / 144 个端点 · 3 个前端（新 Vue / 旧 Admin / jQuery 前台）· 数据库读路径 |
| 方法 | 静态清点 + 真实 HTTP 只读探测（无写入） |
| **未做** | ❌ 逐端点鉴权矩阵（144×角色，成本过高）· ❌ 前端逐页面手工点击 · ❌ 渗透式测试 |
| 原则 | **只找"明显影响使用或明显越权"的问题**，不为理论完备继续深挖 |

---

## A. 旧接口遗留问题

### 事实

| 项 | 数值 |
| --- | :-: |
| Controller | **16** |
| `@RequestMapping` 端点 | **144** |
| 新 Vue 实际调用 | **20**（11 个 api 文件） |
| `@IgnoreAuth` 公开端点 | **7**（paper/list、question/list、counselor/list、knowledgeArticle/list、notification/list、sysConfig/list、file/download）|

### 关键结论

```text
✅ 未发现「旧实体名别名端点」残留
   —— 全部 Controller 根路径已统一为新命名
      （assessmentRecord / assessmentPaper / counselingAppointment …）
   ⇒ Step 5C 的接口改名已收口，不存在 examredetails / exampaper 之类的双份入口
```

### 待确认（成本已评估）

| 项 | 说明 | 建议 |
| --- | --- | --- |
| **无调用方的死端点** | 144 − 20（新 Vue）− ~25（旧 Admin 模式）− jQuery 前台 ≈ **仍有相当数量无法静态确认** | **不逐条核**。理由：死端点不构成越权（鉴权在端点内），删它属**清理**而非**收尾** |
| `/file/download` 免鉴权 | 公开可下载 `upload/` 下静态文件（图片/音频） | ✅ 与业务一致（头像/封面），**不列为问题** |

> **A 类结论：无明显遗留问题需修。** 唯一可做的是"死端点清理"，属技术债，不阻塞 Phase 4。

---

## B. 历史页面权限

### 三个前端的现状

| 前端 | 位置 | 是否可访问 | 调用后端 |
| --- | --- | :-: | --- |
| 新 Vue 3 | `frontend/`（dev `5173`） | ✅ | 20 个端点 |
| 旧 Admin | `/admin/dist/index.html` | ⚠️ **可加载，但接口 404 ⇒ 功能不可用**（见 C-2）| 调用的是**改名前的旧路径** |
| jQuery 前台 | `/front/index.html` | ✅ **HTTP 200** | 源码中**未发现**硬编码 API 路径（疑似动态拼接）|

### 🟡 复核：`UX-COUNSELOR-DETAIL-403` —— **当前无可复现触发路径（判定：过时/不适用）**

| 复核项 | 结论 |
| --- | --- |
| ① 旧 Admin 是否还有有效入口 | ❌ **没有** —— dist 调用旧路径（`/yonghu/login` 等）已 404；C-1 后 `/admin/**` 整体 404 |
| ② `/counselor/detail/{id}` 是否仍被前端调用 | ❌ 新 Vue **不调用**；旧 Admin dist 中 `detail` 出现 **0 次** |
| ③ 新 Vue 是否涉及该入口 | ❌ `CounselorDetailView` 用 `/counselor/list` + 前端按 id 匹配（G1 过渡策略），**不请求** `/counselor/info` 或 `/detail` |
| ④ 相关端点的现状 | `/counselor/info/{id}` = **admin-only**（非管理员 403，属**设计**）；`/counselor/detail/{id}` **无守卫**但返回 `PublicCounselorView` 8 字段（无 PII）|

**处置**：**不修改任何代码**。原工单描述的链路（旧 Admin 菜单 → 详情 → 403）**已无法触发**，
若将来新增「咨询师入口」，需重新评估该规则，而不是沿用本条。

> 纪律保留：**不得为修 UI 而放开后端权限**。


### 未确认项（需人工）

| 项 | 为什么无法静态确认 | 建议 |
| --- | --- | --- |
| jQuery 前台页面是否调用已收口的接口 | 371 个文件，路径可能拼接 | **1 次浏览器抽查**（打开首页 + 1 个列表页，看 Network 是否 4xx/5xx）|

---

## C. 旧 Admin（**本次盘点最重要发现**）

### 🔴 C-1：整棵旧 Admin 源码工程被匿名 HTTP 暴露 —— ✅ **已修复（2026-10-27）**

> **修复**：`InterceptorConfig.addResourceHandlers` 移除 `.addResourceLocations("classpath:/admin/")`（1 行）。
> **前置核实（用户要求）**：`/admin/**` 的**唯一**解析来源是该 location
> （其余 `resources/ static/ img/ front/ public/` 下均无 `admin/` 前缀目录）；
> `/front/**` 由 `classpath:/front/` 提供，**与本次改动无关**。
> **运行时验证**：`/admin/package.json`、`/admin/src/main.js`、`/admin/node_modules/vue/package.json`、
> `/admin/vue.config.js`、`/admin/dist/index.html` **全部 404**；
> `/front/index.html` **200**；`/upload/tongzhi1.jpg` **200**（DB 中 `avatar_url`/`cover_url` 实际使用的 URL 形态）；
> `POST /student/login`、`/assessmentPaper/list`、`/counselor/list`、`/knowledgeArticle/list` **全部 200**。
> **回归**：`mvn test` **197/197**。**未提交**。
>
> **⚠️ 测试覆盖缺口（已确认，不在本批补）**：`src/test` 中**没有任何**针对 `/admin/**` 的断言
> ⇒ 197/197 只证明「现有测试未被破坏」，**不证明**「`/admin/**` 不再可读」这一新行为。
> 本批替代做法 = **运行时实测留证**（上表 5 条 404 + 4 条 200）；
> 是否补 1 条 `MockMvc` 断言（约 3 行）留作后续可选项，**不并入本批**（避免为一行动配置新建测试体系）。


| 证据 | 结果 |
| --- | --- |
| 资源映射 | `InterceptorConfig.addResourceHandlers` 把 **`classpath:/admin/`** 整体挂为静态资源（`/**`）|
| `/admin/package.json` | **200**（1,526 B）|
| `/admin/vue.config.js` | **200** |
| `/admin/babel.config.js` | **200** |
| `/admin/1-install.bat` | **200** |
| `/admin/public/index.html` | **200** |
| `/admin/src/main.js` | **200** |
| `/admin/src/views/login.vue` | **200**（13,848 B）|
| `/admin/node_modules/vue/package.json` | **200**（5,547 B）|
| 目录规模 | `resources/admin/` 共 **30,476 个文件**（含 `node_modules/`、`dist/`、`src/`）|

**定性**：这是**源码/构建配置的公开披露**，不是数据越权。
- 已核：源码中**未命中** `password=` / `apiKey` / `secretKey` 等凭据字面量；
- 影响：暴露全部接口清单、内部注释、构建脚本；为攻击者提供"地图"。

### C-2：旧 Admin **页面可加载，但功能已不可用**（🔴 更正上一版结论）

| 证据 | 结果 |
| --- | --- |
| `/admin/dist/index.html` | **200**（页面能打开） |
| dist 构建时间 | **2022-03-30**（`app.c0510d7c.js`）|
| dist 中 **旧** 模块名出现次数 | `yonghu` **506** · `xinlilaoshi` **600** · `examquestion` **430** · `exampaper` **290** |
| dist 中 **新** 模块名出现次数 | `student` **0** · `counselor` **0** · `assessmentQuestion` **0** · `assessmentPaper` **0** |
| 旧路径实测 | `POST /yonghu/login` → **404** · `POST /exampaper/list` → **404** |
| 新路径实测 | `POST /student/login` → **200** · `POST /assessmentPaper/list` → **200** |

> 🔴 **更正**：上一版本把"页面返回 200"当成"UI 可用"，**这是错的**。
> dist 自 2022 年从未重建，Step 5c 改名后它调用的旧路径已全部消失
> ⇒ **旧 Admin 管理端实际不可用**（能打开登录页，但登录/列表接口均 404）。
> 这与历史记录一致：`PHASE3-STEP5C-IMPLEMENTATION.md` 判定「dist 重建不可达」、
> `PHASE3-STEP7-STATUS-INVENTORY.md` 记「Legacy Admin dist 陈旧」、
> 路线中把 **Step 10 管理端 UI** 挂为独立工单 **Legacy Admin Build Recovery（不阻塞主线）**。

### 处置选项（需你裁定）

| 选项 | 做法 | 代价 | 结果 |
| --- | --- | --- | --- |
| **A. 移除 `/admin/` 暴露** | 从 `addResourceHandlers` 去掉 `classpath:/admin/` | 低（1 处配置） | 源码不再可读；**承认当前无管理端**（与现状一致，因为已不可用）|
| **B. 只暴露 dist** | 改为只挂 `dist/` | 低 | 源码不可读，但保住的是**一个坏页面**（登录即 404）⇒ 收益仅剩"页面能打开" |
| **C. 重建 dist** | 走 **Legacy Admin Build Recovery**（Node14 / lockfile 漂移）| **高**（历史已判定不可达）| 恢复管理端；属 Phase 3 / Step 10 范围，**不属本收尾批次** |
| **D. 保持现状** | 不改 | 0 | 源码持续暴露 + 管理端仍不可用 |

> **我的建议：A**。
> 理由：既然 dist **已经不可用**，B 的"保留 UI"没有实际价值；
> C 成本高且属独立工单；A 同时消除源码披露并让"无管理端"这一事实**显性化**，
> 不再用一个 200 的登录页制造"系统还有后台"的错觉。


---

## D. 非 Agent 资源 / 数据的权限与可见性

### 已完成（有提交与证据）

| 对象 | 状态 | 节点 |
| --- | :-: | --- |
| `student` 列表（咨询师维度）| ✅ 已修 + 变异 | `36bb760` |
| `assessment_record` 咨询师范围 | ✅ C1 + 变异 | `e15f104` |
| `assessment_answer` / `wrong_question`（咨询师）| ✅ 一律 403 | `bdde50b` |
| `counseling_appointment` / `counselor_favorite` 行级范围 | ✅ C1 + 变异 | `9a1cb70` |
| `counselor_message` 行级范围 | ✅ C1 + 变异 | 本次审计 |
| `assessment_paper` / `question` 可见性 | ✅ 已修 + 变异 | `e15e0a2` |
| 六个 JOIN View 学生敏感字段 | ✅ 已删 | `d6db0b6` |
| `/student/detail` IDOR | ✅ 已修 | `d5b75b7` |

### 本次新核实：`DEF-COUNSELOR-LIST-PUBLIC-PII` ✅ **可以关闭**

匿名 `/counselor/list` 实际返回字段（实测）：

```text
id, name, avatarUrl, gender, genderLabel, expertise, resume, introduction
```

⇒ **无 `phone` / `email` / `idCardNo`**，与 S2-E-2-1 的 Public DTO 一致。
**建议状态：CLOSED（字段集已确认无 PII）** —— 无需再改代码。

### 剩余项（不阻塞）

| 项 | 定性 | 建议 |
| --- | --- | --- |
| `exampaperDeleteStart=1` 硬编码 | 管理端 `/page` 只显示"已删除"试卷（生成器遗留）| 登记技术债；**修它属功能修正，不属安全收尾** |
| `notification` 私有化 | 已裁定为全站公告 | **不登记、不实施** |
| 多学生数据分布隔离 | 属测试盲区（非缺陷）| 若将来改行级过滤再补 |

---

## 五、🔴 停止线（Stop Line）—— 明确不做什么

```text
❌ 不逐端点做 144×角色 鉴权矩阵
❌ 不做 D4 契约保护机制（已设计，登记待办，不实施）
❌ 不重构旧 Admin 为 Vue3
❌ 不修 exampaperDeleteStart 等"功能语义"问题
❌ 不为「理论完备」再开任何一个新专项
❌ 不对 jQuery 前台做全量页面审计（只做 1 次抽查）
```

> **判据**：一个问题的修复，若**不能**用「明显影响正常使用」或「明显越权/敏感数据暴露」两条之一解释，
> 就**不进本阶段**，只登记技术债。

---

## 六、Phase 4 前置收尾门槛（Gate）

| # | 项目 | 完成标准 | 现状 |
| :-: | --- | --- | :-: |
| 1 | 旧接口 | 无旧别名端点残留；死端点登记即可 | ✅ **达标** |
| 2 | 历史页面 | 已知前后端不一致已复核并定性 | ✅ **达标**（`UX-COUNSELOR-DETAIL-403` 判定**过时/不适用**，无可复现路径）|
| 3 | 旧 Admin | C-1 源码暴露移除（✅ 已完成）+ C-2 管理端不可用挂 Step 10 | ✅ **C-1 达标** · 🟡 C-2 已挂账 |
| 4 | 非 Agent 数据 | 已量化的越权/敏感暴露全部处理 | ✅ **达标**（`DEF-…-PII` 可关闭）|

```text
四项均已有结论 ⇒ ③ 核心收尾范围已完成，进入 Phase 4
（C-2 管理端重建仍为主计划 Step 10 独立工单，不阻塞）
```

### 🔴 语义区分（用户 2026-10-27 要求，不得省略）

```text
③ Gate CLOSED  ≠  DataScope CLOSED
```

| 表述 | 含义 | 状态 |
| --- | --- | :-: |
| **Phase 4 前置收尾（③）** | 核心收尾范围完成：旧接口无残留 · 历史页面已定性 · 源码暴露已移除 · 非 Agent 数据已处理 | 🏁 **CLOSED** |
| **`DataScope` Contract Audit** | 契约是否被「防未来失效」的机制保护 | 🟡 **仍为 `AUDIT / M2 PROVEN FOR 4 C1 + 1 C4`** |
| **D4 契约保护机制** | 自动化检测 | ❌ **不实施 · 技术债** |

> 二者不矛盾：**Gate 通过 = 当前实际问题已处理完**；
> **审计未 CLOSED = 系统仍缺「防止同类问题再次静默出现」的机制**。
> 今后禁止写成「DataScope 已全部 CLOSED」。

---

## 七、裁定结果（用户 2026-10-27 · 已定案）

| # | 问题 | 裁定 | 执行情况 |
| :-: | --- | --- | --- |
| 1 | 旧 Admin 处置 | ✅ **A（修正含义）**：移除 `/admin/` 整体源码暴露；**不把 2022 年 dist 当作可用管理端保留** | ✅ **已完成**（前置核实 → 改 1 行 → 运行时验证 → 197/197）|
| 2 | `UX-COUNSELOR-DETAIL-403` | ✅ **先复核，不直接修** | ✅ **已复核**：无可复现路径 ⇒ 判**过时/不适用**，零代码 |
| 3 | jQuery 前台 `/front` | — | ⏳ 未做（不影响 Gate；`/front/index.html` 已实测 200）|

> **C（重建 dist）明确不做**：属主计划 **Step 10 / Legacy Admin Build Recovery** 独立工单，
> 不并入收尾批次 —— 避免「为收尾一个问题又重新打开一个大型工作流」。

---

## 八、原始待裁定记录（已由 §七 取代）

| # | 问题 | 选项 | 我的建议 |
| :-: | --- | --- | --- |
| **1** | 旧 Admin 处置 | **A. 移除 `/admin/` 暴露**（承认当前无管理端）<br>B. 只暴露 dist（保住坏页面）<br>C. 重建 dist（Legacy Admin Build Recovery）<br>D. 保持现状 | **A** —— dist **已不可用**（旧路径 404），"保留 UI"无实际价值；源码暴露应立即消除；重建属独立工单 |
| **2** | `UX-COUNSELOR-DETAIL-403` | A. 本批修（隐藏旧 Admin 菜单）<br>B. 登记不修 | **A** —— 属"明显影响正常使用"，且改动在前端，风险低 |
| **3** | jQuery 前台 | A. 做 1 次浏览器抽查<br>B. 不管 | **A** —— 它是可访问入口，只需确认没有明显报错页 |

> 三项若按建议执行：**预计 1 个小批次**（1 处资源映射 + 1 处菜单 + 1 次抽查）即可关闭 ③。

**本文档未改任何代码。等你裁定 §七。**
