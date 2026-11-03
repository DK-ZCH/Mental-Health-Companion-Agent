# Phase 2 / Step 5 批 5 · A4：Excel `/batchInsert` 身份语义审计（**只读，零代码**）

> **状态**：✅ 审计完成（13/13 逐条取证，**无推定项**）
> **代码改动**：⛔ **零**
> **前置裁定**：`13 个 /batchInsert` 不进 A1–A3，须先做本审计（审阅锁定）
> **验收标准（审阅）**：**13/13 逐条得到证据链，不接受「和其他 12 个长得一样所以推定相同」**

---

## 0. 结论摘要（先看这条）

| 发现 | 说明 |
| --- | --- |
| 🔴 **`studentId` 是死变量** | **13/13** 处「身份赋值使用 = 0」—— 读出来后**从未使用** |
| 🔴 **整块实体赋值被注释** | 13/13 的 `entity.setXxx(data.get(n))` **全部处于注释状态**，且**索引统一写成 `data.get(0)`**（连索引都没填），标注为「**要改的**」→ 是**未完成的生成器骨架** |
| 🟠 **入口分化：8 有 / 5 无** | 管理端**全源仅 8 个** `batchInsert?fileName=` 调用点；**5 个测评模块无任何入口** |
| 🟠 **模板文件缺失** | 前端指向 `/upload/xxxMuBan.xls`，但**仓库内未找到任何 `*MuBan*.xls`**（`static/upload/` 目录存在） |
| ⇒ **本批不替换** | 语义**尚未定义**（既不是操作人、也不是已实现的所有者）→ **不做机械替换** |

---

## 1. 逐条证据：13/13（八项要求逐项）

| 模块 | ①会话读取 | ②身份赋值使用 | ③落库方式 | ④模板意图含身份列 | ⑤管理端入口 | ⑥发起者 | ⑦⑧ | 分类 |
| --- | :-: | :-: | --- | :-: | :-: | --- | --- | :-: |
| Dictionary | L226 | **0** | `saveBatch` | —（无身份字段） | ✅ | 管理员 | — | **D-变体** |
| Exampaper | L196 | **0** | `saveBatch` | —（无身份字段） | **❌** | — | — | **D** |
| Examquestion | L199 | **0** | `saveBatch` | —（无身份字段） | **❌** | — | — | **D** |
| Examrecord | L212 | **0** | `saveBatch` | ✅ `setStudentId(data.get(0))`（注释） | **❌** | — | — | **D** |
| Examredetails | L213 | **0** | `saveBatch` | ✅ 同上（注释） | **❌** | — | — | **D** |
| Examrewrongquestion | L220 | **0** | `saveBatch` | ✅ 同上（注释） | **❌** | — | — | **D** |
| Jiankangzhishi | L183 | **0** | `saveBatch` | —（无身份字段） | ✅ | 管理员 | — | **D-变体** |
| Tongzhi | L183 | **0** | `saveBatch` | —（无身份字段） | ✅ | 管理员 | — | **D-变体** |
| XinlilaoshiCollection | L212 | **0** | `saveBatch` | ✅ `setCounselorId/setStudentId(data.get(0))`（注释） | ✅ | 管理员 | — | **D-变体** |
| Xinlilaoshi | L192 | **0** | `saveBatch` | ✅ 自身字段（注释） | ✅ | 管理员 | — | **D-变体** |
| XinlilaoshiLiuyan | L198 | **0** | `saveBatch` | ✅ 双身份列（注释） | ✅ | 管理员 | — | **D-变体** |
| XinlilaoshiOrder | L197 | **0** | `saveBatch` | ✅ 双身份列（注释） | ✅ | 管理员 | — | **D-变体** |
| Yonghu | L203 | **0** | `saveBatch` | ✅ 自身字段（注释） | ✅ | 管理员 | — | **D-变体** |

**「D-变体」**＝入口真实存在（管理员导入），但**身份处理是未完成的骨架**；**「D」**＝连入口都不存在。

### 1.1 关键原始证据（节选，均为现状原文）

```text
ExamrecordController:212  Integer studentId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
ExamrecordController:237  // examrecordEntity.setStudentId(Integer.valueOf(data.get(0))); //考试学生 要改的
ExamrecordController:267  examrecordService.saveBatch(examrecordList);

yonghu/list.vue:46   href="http://localhost:8080/xinlijiankangxitong/upload/yonghuMuBan.xls"   ← 模板下载
yonghu/list.vue:52   action="xinlijiankangxitong/file/upload"                                   ← 上传
yonghu/list.vue:751  url: "yonghu/batchInsert?fileName=" + data.file                            ← 触发导入
```

### 1.2 入口分化（排除「文件夹命名导致的假阴性」）

管理端 `views/modules/` 下 **13 个模块目录全部存在**（含 5 个测评模块）→ 复核后确认：
管理端**全源仅 8 个** `batchInsert?fileName=` 调用点 = dictionary / jiankangzhishi / tongzhi /
xinlilaoshi / xinlilaoshiCollection / xinlilaoshiLiuyan / xinlilaoshiOrder / yonghu
→ **exampaper / examquestion / examrecord / examredetails / examrewrongquestion 确无任何入口** ✅（非假阴性）

---

## 2. 分类判定（按审阅给定的 A–E 五类，**不预设只有一种**）

| 类 | 含义 | 判定结果 |
| :-: | --- | --- |
| **A** | `userId` = 当前操作人 → 可收敛 | **0 处** ❌ —— 没有任何一处把操作人 id 写入实体 |
| **B** | `userId` = 业务所有者 | **0 处** ❌ —— 所有者来源（Excel 列）**被注释、未实现** |
| **C** | 两者兼有 | **0 处** |
| **D** | 历史遗留 / 无实际调用 | **5 处**（测评模块，无入口）+ **8 处「D-变体」**（入口在，身份逻辑未完成）|
| **E** | 证据不足 | **1 项**：`⑦⑧`（导入数据后续被当「谁的记录」、是否存在跨用户导入场景）**无业务文档可依** |

> **为什么不能判为 A**：`studentId` 变量**从未被使用** —— 没有任何一行把它写进实体；
> 注释里的 `setStudentId(data.get(0))` 说明**原始意图是从 Excel 取身份**（即 B 的形态），
> 但该行被注释且标注「要改的」= **功能未完成的占位**。
> ⇒ 当前真实状态是：**导入行不携带由本代码决定的身份**。这是**未完成的骨架**，不是「操作人语义」。

---

## 3. 处置建议（**提案，需业务裁定**）

| 选项 | 内容 | 前置 |
| --- | :-: | --- |
| **(a) 保留现状 + 登记** ✅ **建议** | 13 处原样保留，登记为「Excel 导入身份语义未定义」；**不做任何替换** | 无（符合「无业务证据不凭空发明」）|
| (b) 删除死变量 + 标注功能未完成 | 同 A1b 的做法（删死变量）＋在端点注释写明「导入功能未完成」 | 需确认「导入功能是否仍计划完成」 |
| (c) 完成导入功能（B 类设计） | 按「管理员代导入」设计：`operatorId` 与「数据业务所有者」分离 | **需业务规则**（谁是所有者？模板列怎么填？）|

**明确不做**：把 13 处机械替换为 `CurrentUserProvider.currentUserId()` ❌ —— 那会把**未定义的语义**
偷偷定成「操作人」，属于**在重构中偷偷做产品决策**（与 A1 的 B1 处理原则相反）。

---

## 4. 独立发现（不并入 A4，单独登记）

| # | 发现 | 说明 |
| :-: | --- | --- |
| 1 | **模板文件缺失** | 前端指向 `/upload/xxxMuBan.xls`，仓库内**未找到任何 `*MuBan*.xls``（`static/upload/` 目录存在）→ 若部署环境同样缺失，则「下载模板」链接 404、导入功能**实际不可用** → 需确认模板是否随部署环境提供 |
| 2 | **5 个无入口模块** | `exampaper` / `examquestion` / `examrecord` / `examredetails` / `examrewrongquestion` 的 `/batchInsert` **无任何前端调用** → 与「13 个 `/save` 无调用方」同类 → 登记「疑似废弃」，**不删** |
| 3 | **生成器骨架污染面** | 「要改的」注释块共 **13 处方法 × 平均 5–13 行** → 说明该项目的 Excel 导入功能**整体处于未完成状态**（不是个别遗漏）→ 建议作为**独立功能项**立项 |

---

## 5. 审计边界（未做的事，明确说明）

| 项 | 说明 |
| --- | --- |
| 未做运行期验证 | 本次为**只读静态审计**：未实际发起导入请求、未检查导入行的实际落库值（`student_id` 是否为空）→ 如需，应另立**运行期取证**（需构造 Excel 文件）|
| 未解析 `.xls` 模板内容 | 模板文件在仓库内缺失（见 §4-1）→ 其列定义**无法从仓库证实**，只能由注释推断「预期含身份列」 |
| 未判 `⑦⑧` | 导入数据的后续归属语义与跨用户导入场景**无业务文档** → 按裁定**归入 E（证据不足），不猜** |

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-10 | A4 只读审计（零代码）：13/13 逐条取证；判定 `studentId` **13/13 为死变量**、实体赋值块**整体注释且索引未填**；入口 **8 有 / 5 无**（已排除命名假阴性）；模板文件**缺失**；A–E 分类结果为 **A/B/C = 0、D = 5、D-变体 = 8、E = 1**；建议**保留现状不替换**并立项 |
