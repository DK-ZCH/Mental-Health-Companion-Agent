# Phase 2 / Step 2 交付报告｜后端平台升级（Spring Boot 3.5.16 + JDK 17 + MyBatis-Plus 3.5.8）

> **状态**：✅ 已完成，**待用户确认后进入 Step 2b**
> **分支**：`phase2/step2-boot3-upgrade`（结构性改动，按规则分支开发）
> **提交**：`42237d1`（132 files changed, +848 / −1051）
> **依据**：`PROJECT2-MASTER-PLAN.md` ADR-001.1 + §6 + §7｜`PHASE1-SCOPE-AND-BOUNDARY.md` §8.5

---

## 1. 范围

### 1.1 做什么

- 后端平台栈升级：**Spring Boot 2.2.2 → 3.5.16**、**Java 8 → 17**
- 数据访问栈替换：`mybatis-spring-boot-starter 2.1.1` + `mybatis-plus 2.3` → **`mybatis-plus-spring-boot3-starter 3.5.8`**
- `javax.*` → `jakarta.*` 全量迁移
- MyBatis-Plus **2.x → 3.5.x API 迁移**
- 配置文件迁移到 Boot 3 / MP 3.x 的配置结构
- 清理**死依赖**与**死代码**

### 1.2 不做什么（明确划出边界）

| 不做 | 归属 |
| --- | --- |
| Java 类名重命名（`Yonghu*`→`Student*` 等） | **Step 5b** |
| 接口路径重命名（`/yonghu/*`→`/student/*`） | **Step 5c** |
| DTO/VO 重构、剔除 `password`/身份证号（🔴 S1） | **Step 3** |
| 统一异常处理 | **Step 4** |
| 统一身份 `CurrentUserProvider` | **Step 5** |
| 引入 Spring AI、建 Agent/Tool 分层包 | **Phase 4** |
| 前端（管理端 / Layui 用户端）适配 | **Phase 3**（D2 已知接受） |

> **关于「分层」验收**：本 Step 的分层目标是**确认并保持**既有依赖方向 —— 实测 **0 个 Controller 导入 `com.dao`**，`Controller → Service → DAO` 方向成立，无越层。`Application/Agent` 与 `Tool` 两层属 Phase 4 新建，本 Step 不预建空包。

---

## 2. 前置核对

| 前置依赖 | 状态 |
| --- | --- |
| Phase 1 产品边界关闭（4 项决策） | ✅ |
| ADR-001.1 目标栈定稿（Boot 3.5.x + JDK17 + Spring AI 1.1.6） | ✅ |
| 分支 `phase2/step2-boot3-upgrade` | ✅ 已建 |
| 目标版本在 Maven Central 实际存在 | ✅ 已核验（Boot 3.5.x 最新 = 3.5.16；MP Boot3 starter 最新 = 3.5.17） |
| 与真实代码一致性 | ✅ 迁移点全部由**编译器**枚举，非凭经验猜测 |

---

## 3. 改动清单

| 类别 | 文件 | 改动 |
| --- | --- | --- |
| 平台依赖 | `pom.xml` | Boot `2.2.2.RELEASE`→`3.5.16`；`java.version` `1.8`→`17`；新增 `spring-boot-starter-validation`；`mysql-connector-java`→`com.mysql:mysql-connector-j`；MP→`mybatis-plus-spring-boot3-starter 3.5.8`；POI `3.9`→`5.2.5`（仅保留 `poi-ooxml`）；hutool `4.0.12`→`5.8.25`；fastjson `1.2.8`→`1.2.83` |
| **删除死依赖** | `pom.xml` | `shiro-spring 1.3.2`（**全仓 0 引用**）、`spring-boot-starter-thymeleaf`（无模板资源）、`mybatis-spring-boot-starter`、`mybatis-plus 2.3`、`mybatisplus-spring-boot-starter 1.0.5`、重复声明的 `commons-io`（2.5 与 2.2）、`poi-examples`/`poi-excelant`/`poi-ooxml-schemas`/`poi-scratchpad` |
| 配置 | `application.yml` | MP 配置迁移到 `global-config.db-config.*`（`id-type: input`、`insert/update-strategy: not_null`、`table-underline: true`）；移除 `field-strategy`/`refresh-mapper`/`sql-injector`；`spring.resources`→`spring.web.resources`；`driverClassName`→`driver-class-name` |
| Jakarta 迁移 | `src/main/java/**` | `javax.servlet` / `javax.validation` / `javax.annotation` → `jakarta.*` |
| MP 导入迁移 | `src/main/java/**` | `EntityWrapper`→`QueryWrapper`；`mapper.Wrapper`→`core.conditions.query.QueryWrapper`；`mapper.BaseMapper`→`core.mapper.BaseMapper`；`service.IService`/`service.impl.ServiceImpl`→`extension.service.*`；`plugins.Page`→`extension.plugins.pagination.Page`；`enums.*`/`annotations.*`→`annotation.*`；`plugins.pagination.Pagination`→`core.metadata.IPage` |
| MP API 迁移 | 131 个 java 文件 | `IService` 方法改名 **204 处**（`selectList`→`list`、`selectOne`→`getOne`、`selectById`→`getById`、`selectCount`→`count`、`insert`→`save`、`deleteById`→`removeById`、`delete`→`remove`、`selectPage`→`page`、`deleteBatchIds`→`removeByIds`、`selectBatchIds`→`listByIds`、`insertBatch`→`saveBatch`） |
| MP 语义改写 | 8 个 Controller | `.andNew()` → `.and(w -> w…)` **11 处**，保持「整体 AND + 嵌套 OR 分组」语义 |
| MP 分页 | `MybatisPlusConfig`、17 个 Dao、`Query`、`PageUtils` | `PaginationInterceptor`→`MybatisPlusInterceptor`+`PaginationInnerInterceptor(DbType.MYSQL)`；`selectListView(Pagination)`→`selectListView(IPage<T>)`；`setOrderByField`/`setAsc`→`addOrder(OrderItem)`；`getSize`/`getCurrent` 返回值 `long` 显式收窄 |
| 工具库 | 26 个 `entity`/`view` 文件 | `commons-beanutils` → Spring `BeanUtils`（**参数顺序互换**：`copyProperties(this, t)`→`copyProperties(t, this)`），并移除因改为非受检异常而失效的 `try/catch` |
| Excel | `PoiUtil.java` | `Cell.CELL_TYPE_STRING` → `CellType.STRING`（POI 4+ 移除常量） |
| **删除死代码** | `com/config/MyMetaObjectHandler.java` | 未被任何 Bean 注册或引用，且填充字段 `ctime` 在当前库中不存在（`project-audit.md` §10 已列为 Phase 2 处理项） |

**总计：132 个文件，+848 / −1051 行**（净减 203 行）。

---

## 4. 测试证据

### 4.1 构建

```text
mvn -B -DskipTests clean package
→ BUILD SUCCESS
→ Compiling 147 source files with javac [debug parameters release 17]
→ Total time: 8.085 s
→ Building jar: target/xinlijiankangxitong-0.0.1-SNAPSHOT.jar
```

### 4.2 启动与端到端回归（全部实测）

| # | 用例 | 结果 |
| --- | --- | --- |
| — | 启动 | ✅ `Started xinlijiankangxitongApplication in 10.007 seconds`；Tomcat 8080，context `/xinlijiankangxitong` |
| 1 | 学生登录 `a1` | ✅ `{"code":0,"role":"学生","userId":1,"tableName":"student","token":"…"}` |
| 2 | 知识列表 | ✅ `total=5` |
| 3 | 试卷列表 | ✅ `total=2` |
| 4 | **带 Token 分页**（验证 MP3 分页插件 + `IPage` 迁移） | ✅ `total=3, pageSize=2, totalPage=2, 返回 2 条` |
| 5 | 管理端静态页 | ✅ `HTTP=200` |
| 6 | 启动日志 ERROR/Exception | ✅ **无** |

### 4.3 迁移彻底性自检（全部为 0）

```text
javax.servlet / javax.validation / javax.annotation          0
com.baomidou.mybatisplus.mapper. / .service. / .enums        0
com.baomidou.mybatisplus.plugins.Page                        0
EntityWrapper / andNew() / PaginationInterceptor             0
org.apache.commons.beanutils / CELL_TYPE_STRING              0
Controller 导入 com.dao 的文件数                              0   ← 分层方向成立
```

---

## 5. 回滚方式

> **准确表述（2026-10-05 修正）**：
> **Step 2 的代码变更可通过 Git 分支/提交回滚；本 Step 未修改数据库结构，因此不存在本 Step 独立产生的数据库回滚负担。**
>
> ⚠️ 此结论**仅限本 Step**，不可外推为「整个项目任何情况下都可完全回滚」。后续一旦涉及数据库结构迁移，回滚约束将改变，届时须重新评估并单独说明。

```bash
git checkout master                                  # 回到升级前基线
git branch -D phase2/step2-boot3-upgrade             # 丢弃整个分支
```

或保留分支、仅回退本次提交：`git revert 42237d1`。

> 回滚后 `application.yml` 回到 MP2 配置结构，与 master 的 Boot 2.2.2 匹配；两个版本共用同一套表，无需数据迁移。

---

## 6. 遗留问题 / 待确认项

| # | 项 | 说明 | 建议归属 |
| --- | --- | --- | --- |
| L1 | `fastjson 1.2.83` 保留 | 15 个文件使用 `com.alibaba.fastjson.JSONObject`；选 1.2.83 是为**零代码改动**完成安全补丁。1.2.x 已 EOL | Phase 2 收尾或 Phase 9，迁移 fastjson2 / Jackson |
| L2 | `hutool` 升 5.8.25、`POI` 升 5.2.5 | 仅 `MPUtil`/`PoiUtil` 各 1 个文件使用，**未做深度回归**（相关接口未被前端调用） | 需要时补测 |
| L3 | 26 个孤儿类（`entity/model` 13 + `entity/vo` 13） | 仅自引用，无消费者（`PHASE1` §8.3 已记录） | **Step 3** |
| L4 | `MyThreadMethod`（空循环线程） | 仍被 `DictionaryServletContextListener` 启动，审计列为死代码，本 Step 未处理 | Step 2b / Step 3 |
| L5 | `admin/dist` 与 Layui 用户端未适配 | 字段改名后前端渲染异常，属 **D2 已接受代价** | **Phase 3** |
| L6 | 🔴 S1 敏感字段外泄（`password`/身份证） | 本次实测**依然存在**（来自 Entity 直出），与平台升级无关 | **Step 3（提前修复）** |
| L7 | `spring-boot-devtools` 保留 | 仅开发期使用，可接受 | — |

### 6.1 本次沉淀的四个关键技术结论（面试可讲）

1. **MyBatis-Plus 3.5.9+ 不只拆模块，还改了包名**：`IService`/`ServiceImpl` 迁移到 `com.baomidou.mybatisplus.spring.service.*`，分页能力移入 `mybatis-plus-jsqlparser`（且为 `optional`）。本项目选用 **3.5.8**（模块细分前最后一个版本）以保证经典包布局与社区文档一致 —— 这是一个**用版本选择换迁移风险**的取舍。
2. **`IService` 的方法名是破坏性变更**：`selectXxx/insert/deleteXxx` 全部改名，但 **`BaseMapper` 保留旧名**。因此不能全局替换，必须按**接收者**（`xxxService` vs `xxxDao`）区分 —— 否则会静默改错。
3. **`Wrapper` 在 3.x 是抽象基类**，条件方法（`eq/like/ge/orderBy…`）在其子类上。原代码大量以 `Wrapper<T>` 声明变量，导致 `eq()` 找不到符号；需统一改为 `QueryWrapper<T>`。
4. **逻辑删除实际从未生效**：配置了 `logic-delete-value: -1` 与 `LogicSqlInjector`，但 **16 个实体无一处 `@TableLogic`**，因此该机制一直是空转（已由「`isDeleted=1` 的试卷仍正常列出」实测佐证）。迁移时按**实际行为**而非**配置意图**处理。

---

## 7. 是否请求进入 Security Hotfix A

**请求进入 `Security Hotfix A（SH-A）`：收敛 `CommonController` 动态表名接口**（`project-audit.md` §8.1 S3/S4）。

> **编号说明（2026-10-05 调整）**：此前口头称「Step 2b」，现正式改名为 **`Security Hotfix A`**。
> 理由：它**不在原 41 步路线内**，而是 Step 0 审计新发现的高危项；用 `2b` 会破坏 `Step 0/1/2/3…` 的编号体系。
> 它与 Step 2 是**两个独立提交**，不混在一起。

理由：该风险（**未鉴权 + `${table}`/`${column}` 拼接 + `/sh/{tableName}` 可 UPDATE 任意表**）涉及**任意业务表写操作**，风险面比 DTO/VO 的敏感字段暴露更直接，故优先级更高。

**SH-A 的边界（严格限定，不得扩散）**

| 做 | 不做 |
| --- | --- |
| 收敛 `CommonController` 的动态表名/动态列接口 | ❌ 不重构 Token / Interceptor / Session / 权限模型 |
| 先取证调用方，再决定「白名单化」还是「删除无调用方接口」 | ❌ 不引入 Spring Security（Shiro 已确认是纯死依赖，无需替代品） |
| 动态能力若必须保留 → 表白名单 + 列白名单 + 固定 SQL 模板 | ❌ 不做「参数过滤」式的伪安全 |
