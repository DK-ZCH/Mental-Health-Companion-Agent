# Phase 2 / Security Hotfix A（SH-A）交付报告｜下线 `CommonController` 动态 SQL 接口

> **状态**：✅ 已完成，**待用户确认后进入 Step 3**
> **分支**：`phase2/security-hotfix-a`（从 Step 2 提交点分出）
> **提交**：`57f6f84`（5 files changed, **−1585 行**）
> **编号说明**：**不在原 41 步路线内** —— Step 0 审计新发现的高危项，故以 `SH-A`（Security Hotfix A）命名，避免破坏 `Step 0/1/2/3…` 编号体系
> **依据**：`project-audit.md` §7.5 / §8.1 S3·S4

---

## 1. 范围

### 1.1 做什么

收敛 `CommonController` 的**动态表名 / 动态列名**接口：把「调用方控制标识符 → 字符串拼接 → SQL」这条链路**整体移除**。

### 1.2 不做什么（严格限定，未越界）

| 不做 | 说明 |
| --- | --- |
| ❌ 不重构 Token / `AuthorizationInterceptor` / Session / 权限模型 | 本次**一行未动** |
| ❌ 不引入 Spring Security | Shiro 已确认是纯死依赖；实际鉴权链是自建 `AuthorizationInterceptor` + `auth_token`，为「现代化」再引入一个框架＝制造第二次迁移成本且无安全收益 |
| ❌ 不做「参数过滤」式伪安全 | 只做**能力移除**，不做 blacklist 过滤 |
| ❌ 不动业务表结构 | 本 Step **零数据库结构与数据变更** |

---

## 2. 前置核对：调用方取证（**本 Step 的核心**）

> 用户要求：先确认「哪些表原本允许操作 / 哪些被业务实际使用 / **是否存在合法调用方** / 是否必须保留」。

### 2.1 静态暴露面（`CommonController` 共 15 个端点）

| 端点 | SQL 中的动态标识符 | 鉴权 | 真实调用方 | 当前可用性 |
| --- | --- | --- | --- | --- |
| `/option/{tableName}/{columnName}` | `${column}`、`${table}` | 🔴 **免鉴权** | **0** | ❌ 失效（读 `level`/`parent`，该列已不存在） |
| `/follow/{tableName}/{columnName}` | `${table}`、`${column}`（`SELECT *`） | 🔴 **免鉴权** | **0** | ❌ 失效 |
| `/sh/{tableName}` | `${table}`（**`UPDATE`**） | 需登录 | **0** | ❌ **必然报错**（写 `sfsh`，该列已不存在） |
| `/remind/{t}/{c}/{type}` | `${table}`、`${column}` | 🔴 **免鉴权** | **0** | ❌ 失效 |
| `/group/{tableName}` | `${table1}`、`${table2}`、`${column1}` | 🔴 **免鉴权** | **0** | ❌ 失效 |
| `/cal/{tableName}/{columnName}` | `sum/max/min/avg(${column})`、`${table}` | 🔴 **免鉴权** | **0** | ❌ 失效 |
| `/group/{tableName}/{columnName}` | `${column}`、`${table}` | 🔴 **免鉴权** | **0** | ❌ 失效 |
| `/value/{t}/{x}/{y}` | `${xColumn}`、`${yColumn}`、`${table}` | 🔴 **免鉴权** | **0** | ❌ 失效 |
| `/newSelectGroupSum` | `${tableName}`、`${groupColumn}`、`${sumColumn}` | 需登录 | 0（admin 中**已注释**） | ❌ 失效 |
| `/newSelectGroupCount` | 同上 | 需登录 | 0（admin 中**已注释**） | ❌ 失效 |
| `/newSelectDateGroupSum` | `${tableName}`、`${groupColumn}` | 需登录 | 0 | ❌ 失效 |
| `/newSelectDateGroupCount` | 同上 | 需登录 | 0 | ❌ 失效 |
| `/queryScore` | `${tableName}`、`${average}`、`${condition1}` | 🔴 **免鉴权** | **0** | ❌ 失效 |
| `/barCount` | `${thisTable.tableName}` 等 6 类标识符 | 需登录 | **0** | ❌ 失效 |
| `/barSum` | `${thisTable.tableName}` 等 6 类标识符 | 需登录 | 8 个 admin 模块 | ❌ **失效**（见 §2.2） |

**取证方法与范围**

| 取证对象 | 方法 | 结果 |
| --- | --- | --- |
| 用户端 Layui（49 页 + `js/`） | 全量正则检索 15 个端点名与路径前缀 | **0 处**（此前匹配到的均在 `elementui.js`/`tinymce.js` 等第三方压缩库内，属误报） |
| 管理端 Vue 源码（`admin/admin/src`） | 同上 | 仅 `barSum` **8 处**；`newSelectGroupCount` **已注释**；其余 **0** |
| 后端内部（`src/main/java`） | 检索 `CommonService` / `CommonDao` / 各方法名 | **自包含**：仅 `CommonController → CommonService → CommonServiceImpl → CommonDao`，**无其他调用方** |
| 管理端已编译产物（`admin/dist`） | 检索 | 仅图表相关，且该产物因字段改名已整体失效（D2 已知接受） |

### 2.2 关键判断：「存在调用点」≠「存在合法调用方」

`/barSum` 是唯一在源码中**有调用点**的端点（8 个 admin 列表页的「统计」弹窗）。但逐一核对调用参数后发现其**参数本身非法**：

```javascript
// admin/src/views/modules/yonghu/list.vue:296-303
thisTable : {
    tableName : 'yonghu',            // ← 旧表名（现为 student）
    sumColum  : 'yonghu_number',     // ← 该列在当前库中【从未存在过】
    date      : 'insert_time',       // ← 该列在 student 表中不存在
}
```

实测 `student` 表的列为：`id / username / password / name / phone / id_card_no / avatar_url / gender / email / created_at` —— **无 `yonghu_number`、无 `insert_time`**。

> 该调用参数属**代码生成器遗留的占位配置**，因此 `/barSum` 的功能**自改造前即已失效**（与 `db-naming-convention.md` §10.3 的记录一致）。
>
> **结论：15 个端点中，「存在合法调用方」的数量为 0。**

### 2.3 方案取舍：为什么是「移除」而不是「白名单」

| 方案 | 评价 |
| --- | --- |
| **A. 删除动态能力**（✅ 采用） | 对**零个可用功能**免维护；符合「业务语义 → 明确 API → 明确 Service → 明确 Mapper」 |
| B. 表白名单 + 列白名单 + 固定模板 | 需为 16 表维护列集合、为 `types` 参数维护别名白名单（`LEFT JOIN sys_dict_item ${alias}` 存在**别名注入**面），成本高而**收益为零**（无任何可用调用方） |
| C. 参数过滤 / blacklist | ❌ 伪安全，不采用 |

**能力去向**：管理端图表属 **Phase 3 / Step 10（管理端 Dashboard）** 已规划范围，届时按业务语义定义**显式统计 API**（如 `/admin/stats/assessment-trend`），不再接受调用方传入表名/列名。

---

## 3. 改动清单

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `src/main/java/com/controller/CommonController.java` | **删除** | 15 个动态 SQL 端点的唯一载体 |
| `src/main/java/com/service/CommonService.java` | **删除** | 随其失去用途 |
| `src/main/java/com/service/impl/CommonServiceImpl.java` | **删除** | 同上 |
| `src/main/java/com/dao/CommonDao.java` | **删除** | 同上（含未被 HTTP 暴露的 `plusCloumNumber`/`reduceCloumNumber`/`updateCloumValue`/`chartOne`/`getFollowByOption2`） |
| `src/main/resources/mapper/CommonDao.xml` | **删除** | 含 `${table}`/`${column}` 拼接的 23 条语句 |

**合计：5 个文件删除，−1585 行。新增 0 行。** 未修改任何业务代码、配置或数据库。

> 顺带消除的越权面：`CommonDao` 中 `plusCloumNumber` / `reduceCloumNumber` / `updateCloumValue` 三个 `UPDATE ${tableName}` 方法此前虽未被 HTTP 暴露，但仍是可被后续代码误用的定时炸弹。

---

## 4. 测试证据

### 4.1 构建

```text
mvn -B -DskipTests clean package → BUILD SUCCESS，编译错误 0
```

### 4.2 A) 核心业务回归（必须全部正常）

| 用例 | 结果 |
| --- | --- |
| 学生登录 | ✅ OK |
| 知识列表 | ✅ `total=5` |
| 试卷列表 | ✅ `total=2` |
| 带 Token 分页 | ✅ `total=3，返回 2 条` |
| 心理老师列表 | ✅ `total=3` |
| 启动日志 ERROR/Exception | ✅ **无** |

### 4.3 B) 已下线端点（必须全部 404）

```text
/option/sys_dict_item/dict_code   404     /newSelectGroupSum        404
/follow/student/id                404     /newSelectGroupCount      404
/sh/assessment_paper              404     /newSelectDateGroupSum    404
/cal/student/id                   404     /newSelectDateGroupCount  404
/value/student/id/name            404     /barSum                   404
/queryScore                       404     /barCount                 404
/group/student                    404
/group/student/id                 404
/remind/student/id/1              404
```

**15 / 15 全部 404** ✅ —— 攻击面清零。

---

## 5. 回滚方式

> **准确表述**：**SH-A 的代码变更可通过 Git 提交回滚；本 Step 未修改数据库结构与数据，因此不存在本 Step 独立产生的数据库回滚负担。** 此结论仅限本 Step。

```bash
git revert 57f6f84          # 单独回退 SH-A
# 或
git checkout master         # 回到更早基线（会一并丢失 Step 2）
```

---

## 6. 遗留问题 / 待确认项

| # | 项 | 说明 | 归属 |
| --- | --- | --- | --- |
| S1 | **管理端图表能力被移除** | `barSum` 从 500/参数错误 变为 404。由于该功能**自改造前即失效**，不构成「可用功能回退」；但**能力位空缺需在 Phase 3 补回** | **Phase 3 / Step 10** |
| S2 | `admin/dist` 已编译产物未同步 | 属构建产物，不应手改；D2 已知接受 | Phase 3 |
| S3 | 🔴 **敏感字段外泄（`password` / 身份证号）** | 本次实测**依然存在**（来自 Entity 直出），与 SH-A 无关 | **Step 3** |
| S4 | 密码明文存储 | 需先出数据迁移方案 | Step 3 / Phase 9 |
| S5 | 最小回归测试基线（`T-1`） | 本次验证靠脚本化 curl，**尚未固化为测试用例** | **Step 3 之后** |

### 6.1 本次沉淀的两条技术结论（面试可讲）

1. **「有调用点」不等于「有合法调用方」**。做安全收敛时，只看「谁调用了这个接口」会得出「必须保留」的错误结论；必须进一步核对**调用参数是否成立**。本例中 8 个调用点的参数引用了**从未存在过的列**，因此真实合法调用方为 0 —— 这直接决定了「删除」而非「白名单」的方案。
2. **删除是最彻底的收敛，也是最省维护的**。给一个零可用调用方的动态 SQL 接口加白名单，等于用新代码去保护一段死代码；而当调用方确实存在时，才应升级为「业务语义 API + 固定模板」。

---

## 7. 是否请求进入 Step 3

**请求进入 Step 3：DTO / VO 重构 + 敏感字段治理**（`project-audit.md` §8.1 S1）。

| 项 | 内容 |
| --- | --- |
| 目标 | `Response VO` 层化，阻断 Entity 直出；**强制剔除 `password` / 身份证号 / 其他敏感字段** |
| 为什么它也是 Agent 安全边界 | Phase 4 起将是 `Agent → Tool → Service → DTO/VO`。若底层 API 习惯原样返回 Entity，Tool 会把该问题**放大进 LLM 上下文** |
| 与 Execution Rules 一致 | 落实 **「LLM 永远不能直接接触 Entity」** |
| 附带清理 | `entity/model`(13) + `entity/vo`(13) 共 **26 个孤儿类**（`PHASE1` §8.3） |
| 回滚 | ✅ 可分接口逐步回滚 |

按 `PROJECT2-MASTER-PLAN.md` 的校准顺序，Step 3 之后是 **`T-1`（最小回归测试基线）**，再进入 Step 4。
