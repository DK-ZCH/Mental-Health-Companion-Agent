# Phase 2 总收尾报告（导航 + 挂账索引）

> **用途**：Phase 2 的**唯一入口式收尾文档** —— 21 个提交、6 个工作块、所有挂账项与下一阶段指针
> **状态**：Phase 2 主体 ✅ 完成（1 项收尾待办：Step 5b 类名重命名）
> **分支**：`phase2/step3-sensitive-field-governance`（**21 提交**，全部已推送；`master` 保持 `256f3e6` 未动）
> **回归**：**41/41 全绿**（`mvn clean package`）
> **纪律**：`docs/` 不纳入版本控制、不上传 GitHub（`AGENT-EXECUTION-RULES.md` §9.1）

---

## 1. Phase 2 全景（6 个工作块）

| # | 工作块 | 结论（一句话） | 提交 | 报告 |
| :-: | --- | --- | --- | --- |
| 1 | **Step 2 平台升级** | Spring Boot 2.2→**3.5.16** / JDK 8→**17** / MP 2.3→**3.5.8**；132 文件 | `42237d1` | [`PHASE2-STEP2-REPORT.md`](./PHASE2-STEP2-REPORT.md) |
| 2 | **SH-A 动态 SQL 下线** | 15 个动态 SQL 端点（8 个免鉴权）全部下线，**−1585 行** | `57f6f84` | [`PHASE2-SHA-REPORT.md`](./PHASE2-SHA-REPORT.md) |
| 3 | **Step 3 敏感字段治理** | `password` / 身份证号从**所有响应**消失：51 次真实调用 **0 命中** | `57f1781` | [`PHASE2-STEP3-REPORT.md`](./PHASE2-STEP3-REPORT.md) |
| 4 | **T-1 孤儿类清理** | 9 类隐式引用向量全为 0 → 删 26 类 / **−5270 行**（147→117 文件）| `7302cf8` | [`PHASE2-T1-REPORT.md`](./PHASE2-T1-REPORT.md) |
| 5 | **T-2 回归基线** | 建立自动化回归（**9 项 → 现 41 项**），**零新增依赖**，含反向对照 | `5e122b7` `f8cf62d` | [`PHASE2-T2-REPORT.md`](./PHASE2-T2-REPORT.md) |
| 6 | **Step 4 统一异常处理** | 取证（7 条链）→ A1–A4（新增 `GlobalExceptionHandler`）→ 契约固化；修掉 **P0「错误体被写成人 401」** | `6861eb4` `1bd738c` | [取证](./PHASE2-T4-EXCEPTION-AUDIT.md) / [交付](./PHASE2-T4-REPORT.md) |
| 7 | **Step 5 身份授权**（最大块，见 §2） | 从取证 → 设计 → 6 个子批次，闭合 **3 条实测越权链** + 2 类结构收敛 | 13 提交 | 见 §2 |

---

## 2. Step 5 逐批次（13 个提交）

| 批次 | 内容 | 提交 | 报告 |
| --- | --- | :-: | --- |
| 取证 | 10 向量扫描；身份根可信（唯一写入点）；实测发现 **P0-1 十五个 `/info/{id}` 越权**、**P0-2 十三个 `/list` 匿名枚举**、**P0-3 密码重置越权** | — | [`PHASE2-T5-IDENTITY-AUDIT.md`](./PHASE2-T5-IDENTITY-AUDIT.md) |
| 设计 | 三层原语 + fail-closed + 15 个 `/info/{id}` 逐接口分类 + 13 项回归矩阵 | — | [`PHASE2-T5B-DESIGN.md`](./PHASE2-T5B-DESIGN.md) |
| **批 1** | 8 个 `/info/{id}` 接入 `OwnershipGuard`；403 契约；**22/22**；反向对照 4/4 | `f922155` | [`PHASE2-T5B1-REPORT.md`](./PHASE2-T5B1-REPORT.md) |
| **批 2** | 2 个 `/resetPassword?id=` 限管理员（**DB 前后快照**断言）| `1a52a4c` | [`PHASE2-T5B2-REPORT.md`](./PHASE2-T5B2-REPORT.md) |
| **批 3A** | 写路径全量审计（**零代码**）：`/update` 9 处注释 = 缺口；管理端动态 `${this.table}/update` + 「请选择学生」→ **不可机械恢复** | — | [`PHASE2-T5B3A-WRITE-PATH-AUDIT.md`](./PHASE2-T5B3A-WRITE-PATH-AUDIT.md) |
| **批 3B** | 6 个 `/update` + 2 个 `/add`：`AdminWriteOperation` **具名操作登记**（非万能绕过）| `abb5bfc` | [`PHASE2-T5B3B-REPORT.md`](./PHASE2-T5B3B-REPORT.md) |
| **批 4-A** | `DataScope` **fail-closed**；12 处 `/page`；矩阵 3/11/12 | `311abeb` `5d87258` | [`PHASE2-T5B4A-REPORT.md`](./PHASE2-T5B4A-REPORT.md) |
| **D7** | 下线匿名 `/users/resetPass`（零调用面 + 运行期 404 + 对照）| `d6d8d03` | [`PHASE2-T5-D7-REPORT.md`](./PHASE2-T5-D7-REPORT.md) |
| **批 4-B** | `assertSelfOrAdmin` + 固定字段服务端恢复（Q1/Q2 落地）| `37ae1b1` `e477d57` | [`PHASE2-T5B4B-REPORT.md`](./PHASE2-T5B4B-REPORT.md) |
| **批 5** | 身份读取收敛：A1（role）/A2（userId 写）/A3（`/session`）/依赖反转/A4 审计 | `35f3024` `50aa5a2` `241e688` `6f843a9` `22d1f02` | [`PHASE2-T5B5-REPORT.md`](./PHASE2-T5B5-REPORT.md)、[A4 审计](./PHASE2-T5B5-A4-EXCEL-IDENTITY-AUDIT.md)、[分类](./PHASE2-T5B5-STEP1-CLASSIFICATION.md) |

**批 5 终态**：Controller 身份读取 `role` **0** / `userId` **13**（仅 Excel，按裁定保留）；代码级身份读取全仓 **15 = 13 Excel + 2 唯一实现** ✓

---

## 3. 挂账项总索引（**Phase 2 结束时仍未处理的一切**）

### 3.1 需要**业务规则 / 决策**（阻塞项）

| # | 项 | 归属 | 前置 |
| :-: | --- | --- | --- |
| P1 | **Excel 导入功能是否完成**（`/batchInsert` 13 处） | 批 5 / A4 | 业务规则：所有者是谁 / 模板列如何填 / 是否跨用户导入 |
| P2 | **「自助改密码」是否要做** | 批 4-B | 业务证据（现仅证明 `/update` 改不了密码，**不能**证明系统需要该功能）|
| P3 | **老师能否代改他人资料**（现 403，一处收紧） | 批 4-B | 业务规则（若无需求 → 维持收紧）|
| P4 | **管理员改「固定身份信息」是否需禁止** | 批 4-B | 业务规则（现保持现状）|
| P5 | **`phone` / `email` 是否纳入敏感字段治理** | Step 3 D2-① | 业务判定 |
| P6 | **免鉴权 `/list` 是否收敛**（匿名可枚举用户 PII） | Step 3 D2-② | 业务判定（与 D1 HTTP 401 迁移一并评估）|
| P7 | **教师侧读范围**（3 张测评表无 `counselor_id`，无判定依据） | 批 1 登记 | 业务规则 |

### 3.2 技术债 / 独立小项（**已有证据，可直接做**）

| # | 项 | 说明 |
| :-: | --- | --- |
| T1 | **空 `SET` 的 `updateById` → 500** ✅ **已关闭（2026-10-18）** | 取证 → 分类 ②（非法/不完整请求）→ 方向 A 实施：`UpdateGuard`（除主键外至少一个非空字段）+ `BadRequestException` → **HTTP 400 + code=400**；**15/15** 个 `/update` 接入（授权之后、DAO 之前）；新用例 `9e`（400 + 库内前后不变）；**47/47**；一次性反向对照命中（移除防护 → `expected 400 but was 500`）→ 见 [`PHASE2-T1-EMPTY-SET-AUDIT.md`](./PHASE2-T1-EMPTY-SET-AUDIT.md) §8bis |
| T2 | **Step 5b：Java 类名重命名** | Phase 2 计划内收尾项（**已完成并推送** ✓ —— 原表述「唯一未完成的收尾」已过期，2026-10-16 订正）；**只读审计 v2 + ①②③④ 补证 + 试点 + 批量 10/10 + **C 类全部 5 个（`SysDictItem`/`Counselor`/`AdminUser`/`SysConfig`/`AuthToken`）** + 主类改名（`MentalHealthCompanionApplication`）均已完成并推送 —— **Step 5b 全部 16 个模块完成**；；另完成 **小批次 A**（`/users/page`·`/users/list` 收紧为仅管理员）与 **小批次 B**（16 个 Bean 名统一对齐）；**Step 5c 已定与 Phase 3 合并**；另完成 **小批次 C**（`/users/list` 既有 500 修复：别名 + WHERE 拼接两处并存缺陷）；**D2-1 免鉴权 `/list` 只读最终审计已完成**（14 个端点；匿名可达 13/14；A=7 收敛 / B=4 公开 / C=2 暂缓，零代码）；**D2-2a 认证收口已完成**（A 组 7 个 `/list` 移除方法级 `@IgnoreAuth`；44/44 + 反向对照命中 + 端到端三组探针；B/C 组公开项未误伤）；**D2-2b Phase 1 只读勘察已完成**（共用 mapper 管道证明 + 逐端点 5 问表 + G1/G2/G3 分组；零代码零写库）；**D2-2b-G1 6/6 与 G2 均已完成并推送**（`e164d2f`…`99d6147` + `c879e51`）：G1 六模块逐模块独立提交/验收（含伪造 owner 参数在 SQL 层被中和、模块 3–6 临时对照数据后删除复原）；G2 一处 XML 缺口同时关闭 `/yonghu/list`（新增注入）与 `/yonghu/page`（既有注入恢复生效），并同步修订 1 个把缺口当正确行为的回归用例（**44/44**）；**G3 教师侧未收敛 → 挂账，暂不实施**（12 个模块改名；42/42；真实容器探针覆盖 11 个模块 + 字典链路；`artifactId` 与 API 基路径刻意不变） → [`PHASE2-T5B5-STEP5B-RENAME-AUDIT.md`](./PHASE2-T5B5-STEP5B-RENAME-AUDIT.md)：16 模块 + **API 前缀 16/16** + **94 类精确计数**（`ServiceImpl` 恒 **1**、`Controller` **1–2**）+ **16 个 `@Service` 值全枚举**（无碰撞；消费方字符串引用**仅 1 处**）+ **Dao↔XML 16/16** + 主类独立评估；**待选定试点后实施** |
| T3 | **13 个 `/save` 无调用方** | 登记「疑似废弃」，**不删** |
| T4 | **5 个无入口 `/batchInsert`** | 同 T3 |
| T5 | **模板文件 `*MuBan*.xls` 全仓缺失** | 影响「下载模板」可用性；需确认部署环境 |
| T6 | **`com/model/enums/TypeEnum.java` 0 外部引用** | T-1 清理的漏项（当时只扫 `entity/model`、`entity/vo`）|
| T7 | **`admin` 账号种子密码为 `admin`** | 弱口令；改凭据属部署决策 |
| T8 | **MP3 启动告警 13 条** | `@TableId` + `@TableField` 同字段告警（预存在）|
| T9 | **`FileController.download` 的 `HttpStatus.CREATED`(201)** | 端点无调用方 → 无「调用方误判」证据 → 仅登记（O7）|
| T10 | **`OwnershipGuard.currentUserId/currentRole` 是否最终移除** | 现为「签名保留 + 委托 Provider」；移除需评估 `DataScope` 等调用方 |
| T11 | **E2E 级「role 缺失」覆盖缺口** | 现为单元级契约；E2E 需构造空 role token |

### 3.3 已定案但**可能未来迁移**（非阻塞）

| # | 项 | 现状 |
| :-: | --- | --- |
| D-1 | **未登录契约 = `HTTP 200 + body.code=401`** | 🔒 已定案保持（D1）；改标准 401 需同步改 **2 文件 4 处前端跳转**（取证已完成）|
| D-2 | **参数校验未启用** | Step 4 D4 定案「单独立项」（属**新增拒绝行为**）|
| D-3 | **`code` 语义未收敛（`511` ×163）** | Step 4 D3 定案「先分类再做」 |
| D-4 | **`/yonghu/resetPass`、`/xinlilaoshi/resetPass` 仍 `@IgnoreAuth`** | 属「忘记密码」流程，D3 定案不动 |

### 3.4 版本控制边界（**已知缺口，非缺陷**）

| # | 项 |
| :-: | --- |
| V1 | `front/`、`static/`、`img/`、`admin/admin/src/` **未纳入 Git** → 其改动无法 `git revert`（Phase 3 前端重建后再定）|

---

## 4. 工作原则（本阶段形成的、应被继承的规则）

1. **先取证，再改** —— 每个安全动作前先确定调用面/语义；`103 → 54 → 50` 的校准即由此产生。
2. **一个变更解决一个问题，独立提交、独立可回滚** —— D7 / 4-B / A1a / A1b / A2 / A3 / 依赖反转 各自独立。
3. **不凭空发明业务规则** —— 无证据的语义（教师侧写授权、Excel 身份、老师代表目标用户）一律**登记不猜**。
4. **不用「测试绿了」充当等价性证明** —— 等价性由结构化检查/逐字比对给出（B1 的处理方式）。
5. **必须指明拒绝层** —— 「失败 ≠ 被授权系统拒绝」（空 `SET` 的 500 案例）。
6. **按表名查库前先从 `@TableName` 确认表名** —— 两次空跑证据的教训（`users` vs `admin_user`）。
7. **反向对照** —— 每条新断言都要有「禁用后必须失败」的对照。
8. **主动更正自己的结论** —— 本阶段发生 **3 次**（`idCardNo` 依据 / 「失败≠被拒绝」/ fail-open 降级）。

---

## 5. 下一阶段指针

| 阶段 | 内容 | 依赖 |
| --- | --- | --- |
| **Step 5b（本阶段收尾）** | Java 类名重命名（与表名对齐；**API 路径不变**）| 无（纯重构）|
| Phase 3 | 前端 UI/UX 重构（Step 6–10）| Master Plan §5（ADR-002）|
| Phase 4 | Agent 基础能力（Step 11–15）| Master Plan §4、§7 |
| Phase 5 | **RAG（核心）**（Step 16–20）| Master Plan §5（ADR-003）|

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-10 | Phase 2 总收尾：6 个工作块 + Step 5 的 13 提交索引；**挂账项总索引**（阻塞 7 / 技术债 11 / 已定案 4 / 版本控制 1）；8 条工作原则；下一阶段指针 |
