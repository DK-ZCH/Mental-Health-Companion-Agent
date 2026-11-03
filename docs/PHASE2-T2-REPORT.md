# Phase 2 / T-2 交付报告｜最小回归测试基线

> **状态**：✅ 已完成并提交（含 1 次补充提交）
> **提交**：`5e122b7`（+241 行）+ **`f8cf62d`**（+46 行，补充鉴权用例）
> **依据**：`PROJECT2-MASTER-PLAN.md` §7（T-2 定义）｜用户 2026-10-05 下达的 T-2 四条范围约束
> **性质**：🟡 测试基建（**独立提交**，与 Step 3 / T-1 分离）

---

## 1. 范围

### 1.1 本次只做 4 件事

1. **测试现状取证**（不预设项目已有 JUnit / Boot Test 配置）
2. **建立最小核心回归集**（覆盖 Step 2 / SH-A / Step 3 / T-1 中已验证过的行为）
3. **把安全回归固定下来**（15 个已删除端点 + 敏感字段保护 → 自动化断言）
4. **不碰尚未决策的东西**

### 1.2 明确不做

| ❌ 不做 | 说明 |
| --- | --- |
| 处理 `phone` / `email` | 属待决策 D1 |
| 修改匿名 `/list` 权限 | 属待决策 D2 |
| 引入 Spring Security | 已明确不为「现代化」制造第二次迁移 |
| 引入 Spring AI | 属 Phase 4 |
| DTO 大重构 | 属后续 Step |
| 继续清理「看起来没用」的代码 | 属后续 `T-*` |
| 修改数据库结构 | 全程未动 |
| **新增任何测试依赖** | 见 §2.3 |

### 1.3 原则

> **测试的是「当前已经确认正确的行为」，不是「我们希望未来变成什么样」。**

目的：后续 `Step 5b/5c` 批量重命名、Phase 3 前端重构、Phase 4 引入 Agent 时，能明确区分
**「新改动破坏了旧系统」** 与 **「原本就存在问题」**。

---

## 2. 第 1 步：测试现状取证

### 2.1 取证结果

| # | 项 | 结果 |
| --- | --- | --- |
| 1 | `src/test` | ❌ **不存在**；测试类 **0** 个 |
| 2 | `pom.xml` 测试依赖 | 仅 `spring-boot-starter-test`（test scope）；**无** surefire/failsafe/junit/mockito 显式声明 |
| 3 | 测试框架 | **JUnit 5 + surefire 3.5.6**（由 Boot parent 管理） |
| 4 | `mvn test` 基线（取证时） | ✅ `No tests to run.` → `BUILD SUCCESS` |
| 5 | 启动类装配 | `@SpringBootApplication` + `@ServletComponentScan("com.ServletContextListener")` + `@MapperScan("com.dao")` + `SpringBootServletInitializer` |
| 6 | **测试上下文前置条件** | ⚠️ **需要可用 MySQL** —— `DictionaryServletContextListener`（`@WebListener`）在 **context 初始化时查 `sys_dict_item`**；DB 不可用则上下文启动失败 |
| 7 | 本地 Maven 仓库 | ⚠️ **自定义路径** `D:\app\idea\maven\factory`（`conf/settings.xml` 指定，非默认 `~/.m2/repository`；镜像 aliyun） |

### 2.2 测试栈就绪性（按 Boot 3.5.16 BOM 核对）

| 库 | BOM 要求版本 | 本地是否就绪 |
| --- | --- | --- |
| JUnit Jupiter | 5.12.2 | ✅ |
| Mockito | 5.17.0 | ✅ |
| AssertJ | 3.27.7 | ✅ |
| spring-test | 6.2.19 | ✅ |
| spring-boot-test | 3.5.16 | ✅ |

### 2.3 关键结论：**零新增依赖**

`spring-boot-starter-test` 已**传递提供** JUnit 5 / AssertJ / spring-test / MockMvc（以及 Mockito）。
本次**未新增任何依赖**，也未引入 Spring Security 等框架 —— 符合「不为测试提前引入大块基础设施」的约束。

> 注：测试**不需要 mock 任何东西**（Mockito 虽在依赖树中，但未被使用）—— 因为测的是真实链路。

---

## 3. 第 2 步：最小回归测试集

### 3.1 形态选择：方案 A

| 项 | 选择 |
| --- | --- |
| 形态 | **`@SpringBootTest` + `@AutoConfigureMockMvc`** |
| 理由 | 不发真实端口（快、稳），但**走完整 MVC + 拦截器链**，可直接对 JSON 做断言 |
| 不做的 | **不对任何环节做 mock** —— 登录、`AuthorizationInterceptor`、MyBatis、分页插件全部真实执行 |

### 3.2 用例清单（9 个）

| # | 用例 | 断言 |
| --- | --- | --- |
| 1 | 学生登录 | `code=0`、`token` 非空、`role=学生`、`tableName=student` |
| 2 | 心理健康知识列表 | `code=0`、`data.total=5`（基线） |
| 3 | 测评试卷列表 | `code=0`、`data.total=2`（基线） |
| 4 | **MP3 分页** | `data.total=3`、`pageSize=2`、`totalPage=2`、`list.size=2` |
| 5 | 心理老师列表 | `code=0`、`data.total=3`（基线） |
| 6a | **敏感字段（分页/列表）** | 14 个端点：`code=0`、**`total>0`（防空跑）**、响应体不含 `password` / `idCardNo`（含关联字段） |
| 6b | **敏感字段（详情）** | `/yonghu/info/1`：响应体不含 `password` / `idCardNo`，且长度 > 50（防空跑） |
| 7 | **SH-A 安全回归** | **15 个已删除动态 SQL 端点全部 404** |
| 8 | **鉴权：无 Token → 拒绝**（`f8cf62d` 补充） | 10 个受保护接口：`body.code=401` 且 `msg` 含「请先登录」。**只断言 `body.code`，刻意不断言 HTTP 状态**（见 §3.3） |

### 3.3 三个设计要点

1. **防空跑**：敏感字段断言前先校验 `total > 0` / 响应长度 —— 否则「空响应」会让断言**永远通过**。
2. **基线常量 + 来源注释**：`5 / 2 / 3 / 3` 写为显式常量并注明数据来源（种子数据 SQL）。按「测当前已确认正确的行为」原则，若日后数据变更导致失败，**失败本身就是「数据变了」的记录**。
3. **只断言 `body.code`，不断言 HTTP 状态**（鉴权用例）：实测发现 `AuthorizationInterceptor` 只把 `{"code":401,"msg":"请先登录"}` 写进**响应体**，**并不设置 HTTP 状态码** → 无 Token 访问受保护接口实际是 **HTTP 200 + body code=401**。
   刻意不锁定状态码，是为了**将来把它修正为标准 401 时不会误伤测试**。
   同一批接口在【带 Token】时可通过（由用例 2/3/4/5/6a 覆盖）—— 两者成对，才能证明 401 来自「缺少 Token」而非「接口本身坏掉」。

---

## 4. 验证证据

### 4.1 `mvn test`

```text
# 首次（用例 1–7，提交 5e122b7）
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11.26 s

# 补充鉴权用例后（用例 1–8，提交 f8cf62d）
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11.37 s
   -- in com.regression.Phase2CoreRegressionTest
```

### 4.2 ⭐ 反向对照（证明测试**有牙齿**）

> 一个「永远不会失败」的测试是没有价值的。因此做了一次刻意破坏验证：

| 步骤 | 操作 | 结果 |
| --- | --- | --- |
| 1 | 临时移除 `YonghuEntity.getPassword()` 上的 `@JsonIgnore` | — |
| 2 | 运行 `mvn test` | ✅ **如预期失败**：`Tests run: 8, Failures: 2`，且失败点**精确命中**两个敏感字段用例（`学生详情不应包含 password`） |
| 3 | 精确恢复 | ✅ `git diff` **为空**（字节级一致） |
| 4 | 重新运行 `mvn test` | ✅ `Tests run: 8, Failures: 0` |

**结论：安全断言确实会因真实泄露而失败，不是装饰。**

### 4.3 启动 + 关键接口冒烟（验收第 2 步）

| 项 | 结果 |
| --- | --- |
| 打包 | ✅ `BUILD SUCCESS` |
| 启动 | ✅ Spring Boot **3.5.16** / Tomcat **10.1.55**，端口 8080 |
| 登录 | ✅ OK |
| MP3 分页 | ✅ `total=3, pageSize=2, totalPage=2, 返回 2 条` |
| `yonghu/list` 敏感字段 | ✅ 无 `password`、无身份证 |
| SH-A 端点抽查 `/barSum` | ✅ `HTTP=404` |

**一个值得记录的证据**：启动日志显示 SQL 仍然 `SELECT a.password as password, a.id_card_no as idCardNo`，
但**响应 JSON 中已不含这两个字段** —— 正好印证 Step 3 的设计：**持久化映射未动，只在序列化边界排除**。

---

## 5. 回滚方式

> **准确表述**：**T-2 的代码变更可通过 Git 提交回滚；本步未修改数据库结构与数据，因此不存在本步独立产生的数据库回滚负担。** 仅限本步。

```bash
git revert 5e122b7     # 删除测试类，回到无测试状态
```

---

## 6. 遗留问题 / 观察项

| # | 项 | 说明 | 归属 |
| --- | --- | --- | --- |
| O1 | **测试依赖 MySQL** | `@SpringBootTest` 需要可用 DB（`@WebListener` 在 context init 查表）。本轮在本机通过；若未来接入 CI，需先准备 DB 或改为 `@MockBean` 隔离（但那会偏离「测真实链路」的原则） | 将来建 CI 时决策 |
| O2 | **基线数值的数据依赖性** | `5/2/3/3` 依赖种子数据；数据变更会失败 | 已接受（失败即数据变更记录） |
| O3 | ✅ **「无 Token → 401」用例已补充** | 用户 2026-10-05 追加要求；已按实测清单加入 **10 个受保护接口**的断言（提交 `f8cf62d`） | 已完成 |
| O4 | MP3 启动告警 | `This "id" is the table primary key by @TableId annotation ... So @TableField annotation will not work!`（13 条，预存在） | 后续 `T-*` 可清理 |
| O5 | 未覆盖测评答题链路 | 交卷判分/错题生成等复杂链路未纳入最小集 | 需要时扩展 |
| **O6** | **鉴权失败返回 `HTTP 200 + body code=401`** | `AuthorizationInterceptor` 只写响应体、**不设置 HTTP 状态码**（代码见其 `preHandle` 的 `PrintWriter` 分支）。这是**既有设计**——前端依赖 `body.code` 判断，改成标准 401 属**接口契约变更**，会影响前端错误处理与后续 Agent 调用 | **T-2 未修改**，仅记录；建议在 Step 4（统一异常处理）或 Phase 9 一并处理 |

### 6.1 过程记录（可追溯）

本次 T-2 执行中出现并已修复的一个**我的操作事故**：

在执行「反向对照」的**恢复**步骤时，PowerShell 的 `-replace` 参数拼接被解析为 4 个参数（语法错误），
导致 `$new` 为空值，**`YonghuEntity.java` 被写为空文件**。随后通过 `git show HEAD:<path>` 重建内容，
并补齐文件末尾换行使 `git diff` 归零，**恢复后与提交版本字节级一致**，`mvn test` 回到 8/8 通过。

> **教训**：用脚本改文件时，**必须在写入前校验替换结果非空**；同时这也再次印证了「每步独立提交」的价值 ——
> 任何一步被改坏都能从 Git 精确还原，而不是靠记忆重建。

---

## 7. 提交记录

| 项 | 内容 |
| --- | --- |
| 提交 1 | `5e122b7`（**+241 行**，新建测试类，用例 1–7） |
| 提交 2 | `f8cf62d`（**+46 行**，补充鉴权用例，用例 8） |
| 分支 | `phase2/step3-sensitive-field-governance`（与 T-1 相同；分支名将在 Phase 2 收尾时统一整理） |
| 新增文件 | `src/test/java/com/regression/Phase2CoreRegressionTest.java` |

---

## 8. 下一步

按校准顺序，`T-2` 之后是 **`Step 4`（统一异常处理：`BusinessException` + `GlobalExceptionHandler`）**。

> **顺序优势**：现在已有回归测试基线，Step 4 改动 `R` / 异常返回结构后，可直接用 `mvn test` 判断是否破坏了既有行为 —— 这正是 T-2 放在 Step 4 之前的价值。

**仍未决策 2 项**（来自 Step 3 报告 §6，与 T-2 无关）：D1（`phone`/`email` 是否治理）、D2（免鉴权 `/list` 是否收敛）。
