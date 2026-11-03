# Phase 2 / Step 5 批 3A：写路径身份语义审计（**仅取证，不改代码**）

> **阶段**：Step 5 第二阶段 批 3A · **状态**：⏳ **待审核**（审核通过后才进入批 3B 编码）
> **代码改动**：⛔ **零**
> **范围**：`/add`、`/update`（含 `/save` 作为对照）；**教师侧 `counselorId` 只记录 → 归属批 4**（不扩大批 3）

---

## 0. 结论摘要（先看这条）

| 发现 | 说明 |
| --- | --- |
| **写端点共 55 个**（12 模块 ×4 + Dictionary 3 + Config 2 + Users 2） | 见 §1 |
| **`/save` 身份覆盖多为生效** | 10 处生效（6 模块）—— 这部分**已经是对的** |
| **`/update` 的 9 处身份覆盖全部被注释** ← **本批真正缺口** | 见 §3 |
| 🔴 **`/update` 不能机械恢复** | **证据**：管理端以**动态 `${this.table}/update`** 调用所有模块，且其表单含**「请选择学生」选择器** → 恢复注释会把**管理员选定的目标学生覆盖成管理员自己的 id** → **B 类能力被误伤** |
| **用户端 0 处调用 `/update`** | 用户端只调 `/add`（25 处）→ `/update` 的实际调用方是**管理端** |
| `/add` 已有 3 处生效覆盖 | 与用户端 `localStorage.userId` 来源一致（A 类） |
| **`/save`（12 个）未发现任何前端调用方** | 疑似废弃（**不删**，登记待最终确认） |

---

## 1. 写端点全清单（55 个）

| Controller | `/save` | `/update` | `/add` | `/delete` | 归属字段 |
| --- | :-: | :-: | :-: | :-: | --- |
| Yonghu（学生） | ✓ L116 | ✓ L147 | ✓ L462 | ✓ L180 | 自身 id |
| Xinlilaoshi（老师） | ✓ L114 | ✓ L143 | ✓ L437 | ✓ L174 | 自身 id |
| XinlilaoshiOrder（预约） | ✓ L132 | ✓ L153 | ✓ L328 | ✓ L182 | studentId + counselorId |
| XinlilaoshiLiuyan（留言） | ✓ L132 | ✓ L153 | ✓ L309 | ✓ L183 | studentId + counselorId |
| XinlilaoshiCollection（收藏） | ✓ L132 | ✓ L165 | ✓ L321 | ✓ L197 | studentId + counselorId |
| Examrecord（测评记录） | ✓ L134 | ✓ L166 | ✓ L340 | ✓ L197 | studentId |
| Examredetails（答题明细） | ✓ L134 | ✓ L166 | ✓ L341 | ✓ L198 | studentId |
| Examrewrongquestion（错题） | ✓ L142 | ✓ L174 | ✓ L336 | ✓ L205 | studentId |
| Exampaper（试卷） | ✓ | ✓ | ✓ | ✓ | **无** |
| Examquestion（题目） | ✓ | ✓ | ✓ | ✓ | **无** |
| Jiankangzhishi（知识） | ✓ | ✓ | ✓ | ✓ | **无** |
| Tongzhi（通知） | ✓ | ✓ | ✓ | ✓ | **无** |
| Dictionary（字典） | ✓ | ✓ | — | ✓ | **无** |
| Config（配置） | — | ✓ | — | ✓ | **无** |
| Users（管理端账号） | — | ✓ | — | ✓ | **无**（管理端专用） |

> **有归属语义的只有 6 个模块**（与数据库实测一致：`student_id`/`counselor_id` 仅存在于 6 张表）+ 学生/老师自身的 2 个 → **共 8 个模块需要判定**。

---

## 2. 调用方证据（这是本批的核心）

### 2.1 用户端（Layui，60 个页面）

| verb | 调用情况 |
| --- | --- |
| `/add` | **25 处**（各模块 `add.html` 的提交） |
| `/update` | **0 处** ✅ |
| `/save` | **0 处** |

### 2.2 管理端（Vue，82 个文件）

| verb | 调用情况 |
| --- | --- |
| `/update` | **动态调用 ${this.table}/update（3 处）**，而每个模块的 `list.vue` 都设置了自己的 `tableName`（如 `'dictionary'`、`'jiankangzhishi'`…）→ **等价于对所有模块都调用 `/update`** |
| `/save`、`/add` | 未发现调用（`/save` 的调用方待最终确认） |

**关键证据（B 类的确定依据）** —— 管理端表单里存在**目标学生选择器**：

```text
examrecord/add-or-update.vue:65     <el-select v-model="ruleForm.studentId" placeholder="请选择学生">
examredetails/add-or-update.vue:119 同上
examrewrongquestion/add-or-update.vue:134 同上
```

→ 管理员**确实会以「代表某个目标学生」的身份**提交数据 → 这类调用是 **B 类**。

### 2.3 身份字段的三种来源语义（你要求严格区分）

| 来源 | 证据 | 语义 |
| --- | --- | --- |
| **`localStorage.userId`** | 用户端 `add.html`：`data["studentId"] = localStorage.getItem("userId")` | **当前登录用户** → A 类 |
| **页面选择的目标对象** | `xinlilaoshi/detail.html`：`counselorId: vue.detail.id`；`yonghu/detail.html`：`studentId: _this.detail.id` | **目标对象** → C 类（关系字段） |
| **URL 参数** ⚠️ | `examrewrongquestion/list.html`：`http.getParam("userId")`（**2 处**） | **客户端可控** → D 类（第一阶段 P1-5 已登记） |
| **管理端选择器** | `el-select v-model="ruleForm.studentId"` | **管理员代表目标学生** → B 类 |

---

## 3. 身份赋值现状：生效 vs 注释（逐处）

| Controller | `/save` | `/add` | `/update` |
| --- | --- | --- | --- |
| Examrecord | L142 **生效** | L375 **生效** | **L174 注释** ✗ |
| Examredetails | L142 **生效** | L378 **生效** | **L174 注释** ✗ |
| Examrewrongquestion | L145 **生效** | （无） | **L177 注释** ✗ |
| XinlilaoshiOrder | L140/L142 **生效** | L343 **生效**（`setStudentId(userId)` 无条件） | **L161/L163 注释** ✗ |
| XinlilaoshiLiuyan | L142 **生效** | （无） | **L161/L163 注释** ✗ |
| XinlilaoshiCollection | L135/L137 **生效** | （无） | **L168/L170 注释** ✗ |
| **合计** | **10 处生效** | **3 处生效** | **9 处注释** ← 缺口 |

另外还有 **6 处 Excel 批量导入分支内的注释**（如 `// entity.setStudentId(Integer.valueOf(data.get(0)))` + 注释「要改的」）→ 属同类问题（导入路径的归属也采信客户端上传的列），**本批仅登记，不处理**。

---

## 4. A / B / C / D 分类矩阵

> 分类只针对**有归属语义的 8 个模块**；其余 7 个（试卷/题目/知识/通知/字典/配置/管理端账号）**无归属字段** → 不适用 A/B（内容与配置类，保持现状）。

| # | 模块 | verb | 身份字段 | 当前来源 | 实际调用方 | 业务角色 | **分类** | 处置（建议） |
| :-: | --- | --- | --- | --- | --- | --- | :-: | --- |
| 1 | xinlilaoshiOrder | `/add` | studentId | **Session（L343 无条件覆盖）** | 用户端（学生下单） | 学生 | **A** | ✅ 已正确 → 批 4 统一为 `stampOwner` |
| 2 | xinlilaoshiOrder | `/add` | counselorId | 客户端（`vue.detail.id`，页面选择） | 用户端 | 学生选老师 | **C** | ✅ 保留（目标老师，需存在性校验，现已有） |
| 3 | xinlilaoshiOrder | `/update` | studentId+counselorId | **客户端（覆盖被注释）** | **管理端**（动态 tableName） | 管理员代表目标学生 | **B** | 🔴 **需 `authorizedTargetUser`（管理员放行；学生则 `stampOwner`）** |
| 4 | xinlilaoshiLiuyan | `/add` | studentId | 客户端（localStorage） | 用户端 | 学生 | **A** | 🔴 需补 `stampOwner`（与 #1 对齐） |
| 5 | xinlilaoshiLiuyan | `/update` | studentId+counselorId | **客户端（注释）** | 管理端 | 管理员 | **B** | 🔴 同上（按 role 分支） |
| 6 | xinlilaoshiCollection | `/add` | studentId | 客户端（localStorage） | 用户端 | 学生 | **A** | 🔴 需补 `stampOwner` |
| 7 | xinlilaoshiCollection | `/update` | studentId+counselorId | **客户端（注释）** | 管理端 | 管理员 | **B** | 🔴 同上 |
| 8 | examrecord | `/add` | studentId | **Session（L375 生效）** | 用户端 / **管理端亦有表单** | 学生 / 管理员 | **A + B** | ⚠️ **混合**：学生 → 覆盖；管理员 → 保留其选择 → **必须 role 分支** |
| 9 | examrecord | `/update` | studentId | **客户端（注释）** | 管理端 | 管理员 | **B** | 🔴 同上（**不可机械恢复**） |
| 10 | examredetails | `/add`、`/update` | studentId | 同 #8 | 同上 | 同上 | **A + B** | 🔴 同上 |
| 11 | examrewrongquestion | `/update` | studentId | **客户端（注释）** | 管理端 | 管理员 | **B** | 🔴 同上 |
| 12 | examrewrongquestion | （查询） | studentId | **URL 参数 `getParam("userId")`** | 用户端 | — | **D** ⚠️ | 已登记（P1-5）；**当前被服务端 `/page` 覆盖保护**，建议在批 3B 一并清理为 `localStorage`/不传 |
| 13 | yonghu | `/add`、`/update` | 自身 id | 客户端（表单） | 用户端（学生改自己）/ 管理端 | 学生 / 管理员 | **A + B** | ⏸ **需进一步业务证据**（学生是否可自助改资料？管理端表单是否含目标学生？） |
| 14 | xinlilaoshi | `/add`、`/update` | 自身 id | 客户端 | 管理端（老师资料维护） | 管理员 | **B** | ⏸ 同上 |
| 15 | 6 个模块的 `/save` | — | studentId 等 | **Session（生效）** | **未发现调用方** | — | **A**（但无人调用） | ⏸ 登记为「疑似废弃」，**不删**，待最终确认 |

---

## 5. 实施清单（批 3B 的候选范围）

| 处置 | 条目 |
| --- | --- |
| 🔴 **必须改** | #3/#5/#7/#9/#10/#11 —— 6 个模块的 `/update` 9 处注释 → 改为 **role 分支**：<br>`学生 → stampOwner(覆盖)` / `管理员（或老师） → authorizedTargetUser(保留目标)` / `其他 → 拒绝` |
| 🔴 **建议同批补齐** | #4/#6 —— `xinlilaoshiLiuyan`、`xinlilaoshiCollection` 的 `/add` 目前**采信客户端 localStorage**（虽与 Session 值一致，但属「应由服务端决定」）→ 补 `stampOwner` |
| ⏸ **暂不改** | #2（C 类目标老师，保留）；13 个 `/save`（无调用方，待最终确认后再定） |
| 📌 **并入批 4** | 所有 `counselorId` 相关规则（含 U1/U2 教师侧归属） |
| 📌 **独立安全小批次** | **D7：`/users/resetPass` 下线**（调用面核查已完成 → 见 §6，**不塞进批 3**） |
| ⏸ **需进一步业务证据** | #13 `yonghu`、#14 `xinlilaoshi` 的 `/add`、`/update`（学生/老师能否自助修改？） |
| ⏸ **登记不处理** | 6 处 Excel 导入路径的注释覆盖 |

---

## 6. 附带完成：D7 `/users/resetPass` 最终调用面核查（结果供你决策）

| 核查区域 | 结果 |
| --- | --- |
| Java 源码 | 仅 **6 行**（3 个端点各自的 2 行定义：`/users/resetPass`、`/yonghu/resetPass`、`/xinlilaoshi/resetPass`）→ **无任何内部调用** |
| 用户端 `front/`（60 文件） | **0 处** |
| 管理端 `admin/admin/src`（82 文件） | **0 处** |
| 管理端 `dist`（已构建产物） | **0 处**（早前误判为 1 处，实为 `resetPassword` 的**前缀误匹配**） |
| `scripts/` `bin/` `deploy/` `docker/` | **目录均不存在** → 无运维脚本调用 |
| 配置 / HTTP 路由引用 | 无 |
| 业务说明 | 仅方法上方注释「密码重置」，**无「忘记密码」入口、无前端页面** |
| docs | 33 处（均为本项目的审计记录，非调用） |

→ **结论：`/users/resetPass` 在全部可执行区域零调用 + 无业务说明** ✓（符合你设定的下线标准）
→ 但按你的要求，它应作为**独立安全小批次**，**不并入批 3** —— 等你决定是否立项。

---

## 7. 待你审核（批 3B 前置）

| # | 审核点 | 我的建议 |
| --- | --- | --- |
| 1 | `/update` 的处置方案：**按 role 分支**（学生 `stampOwner` / 管理员 `authorizedTargetUser` / 其他拒绝）是否认可？ | 认可则据此实现 |
| 2 | 是否同批补齐 `xinlilaoshiLiuyan`、`xinlilaoshiCollection` 的 `/add` 覆盖 | 建议同批（同类问题，改动极小） |
| 3 | `/save`（13 个）无调用方 → 是否登记为「疑似废弃」（**不删**） | 建议登记 |
| 4 | `yonghu`、`xinlilaoshi` 的 `/add`、`/update`（#13/#14） | 需你给业务规则，否则本批**不改**（fail-closed 会破坏管理端编辑） |
| 5 | Excel 导入路径的 6 处注释覆盖 | 建议单独登记，不在批 3 处理 |
| 6 | **D7 是否立项**为独立安全小批次 | 建议立项（证据已齐） |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-05 | 批 3A 写路径身份语义审计（**零代码**）：55 个写端点全清单；`/save` 10 处生效 / `/add` 3 处生效 / **`/update` 9 处注释（缺口）**；调用方证据（用户端只调 `/add`、0× `/update`；管理端动态 `${this.table}/update` + 「请选择学生」选择器 → **B 类确证**）；身份字段三种来源语义区分；A/B/C/D 矩阵 15 行；实施清单；**附带完成 D7 调用面核查（全区域零调用）** |
