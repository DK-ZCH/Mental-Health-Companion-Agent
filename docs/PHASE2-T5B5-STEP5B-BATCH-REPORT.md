# Step 5b / 批量推进报告：10 个 B 类模块（10/10 完成）

> **状态**：✅ 完成并推送（10 个独立提交；试点 1 个另计）
> **范围**：审计 §3 的 **B 类 11 个模块**中，除试点（`Tongzhi`）外的 **10 个**
> **C 类 5 个模块**（`Xinlilaoshi` / `Users` / `Config` / `Token` / `Dictionary`）**未动** ✓

---

## 1. 交付清单

| # | 旧模块 | 新模块 | 表名（不变） | DICT_CODE 键 | 提交 | 文件 |
| :-: | --- | --- | --- | :-: | --- | :-: |
| — | `Tongzhi` | `Notification` | `notification` | `Notification.category` | `8ec38e0` | 10 |
| 1 | `Jiankangzhishi` | `KnowledgeArticle` | `knowledge_article` | `.category` | `eca56f9` | 9 |
| 2 | `Examrewrongquestion` | `AssessmentWrongQuestion` | `assessment_wrong_question` | 无 | `780c260` | 9 |
| 3 | `XinlilaoshiLiuyan` | `CounselorMessage` | `counselor_message` | 无 | `da02586` | 9 |
| 4 | `XinlilaoshiCollection` | `CounselorFavorite` | `counselor_favorite` | `.favoriteType` | `aebf60d` | 10 |
| 5 | `XinlilaoshiOrder` | `CounselingAppointment` | `counseling_appointment` | `.timeSlot` `.status` | `47e1ee6` | 10 |
| 6 | `Examrecord` | `AssessmentRecord` | `assessment_record` | 无 | `1785291` | 10 |
| 7 | `Examredetails` | `AssessmentAnswer` | `assessment_answer` | 无 | `15bd269` | 10 |
| 8 | `Exampaper` | `AssessmentPaper` | `assessment_paper` | `.status` | `9d2c7c4` | 12 |
| 9 | `Examquestion` | `AssessmentQuestion` | `assessment_question` | `.questionType` | `d454f7e` | 11 |
| 10 | `Yonghu` | `Student` | `student` | `.gender` | `d6c2155` | 21 |

**新类名 = 表名对齐**（PascalCase）：`knowledge_article` → `KnowledgeArticle` 等 —— 不是自由翻译 ✓

---

## 2. 可复制手法（每个模块 1 个提交）

```text
① git mv ×7（Entity / View / Service / Impl / Dao / Controller / Dao.xml）
② 全 src（含 src/test）一次【大小写敏感】前缀替换：Old → New
   · 天然覆盖：跨模块级联引用、测试类、CurrentUserProvider 等安全原语
   · 天然不改：小写 API 路径 / 表名 / Bean 名 / 局部变量 / 日志串（匹配不到）
   · 天然同步：DICT_CODE 键（其 key 本 = 实体类简单名）
③ 静态校验：旧名残留=0 + 4 条硬约束 + XML namespace
④ mvn -B clean test → 42/42
⑤ 独立提交（提交信息含：范围 / 硬约束实测值 / 字典键 / 验证结果）
```

**碰撞预检**（改名前）：新前缀在 `src` 内容中既有命中应为 **0**；`Yonghu` → `Student` 额外用精确类名模式
`Student(Entity|View|Service|Dao|Controller)` 预检（因为 `Student` 会作为词根出现在 `ownerStudentId` 等标识符中）→ 实测 **0** ✓

---

## 3. 🔴 实践教训：**重命名后必须 `clean`**

第一次跑模块 1 时用 `mvn -B test`（**未 clean**）→ **27+15 个测试全部 ERROR** ✗

根因（**非代码问题**）：

```text
target/classes/mapper/JiankangzhishiDao.xml   ← 旧副本仍残留（增量构建不删旧文件）
  → MyBatis 解析：Could not resolve type alias 'com.entity.view.JiankangzhishiView'
  → sqlSessionFactory 创建失败 → 上下文启动失败 → 全部用例 ERROR
```

`mvn -B clean test` → **42/42** ✓；`target/classes/mapper` 旧 XML 残留 = **0** ✓
→ **已固化进每个提交的验证命令**（其余 9 个模块全程用 `clean test` ✓）

---

## 4. 总验收（批量完成后一次性复核）

### 4.1 静态

| 项 | 结果 |
| --- | --- |
| 11 个旧前缀在 `src`（java+xml）残留 | **全部 0** ✓ |
| 对照：`Xinlilaoshi`（C 类，应保留） | **85** 处 ✓ |
| `DICT_CODE` 9 个键 | **全部为新类名** ✓（`Student.gender` / `Xinlilaoshi.gender`〔C 类保留〕/ `AssessmentPaper.status` / `AssessmentQuestion.questionType` / `KnowledgeArticle.category` / `Notification.category` / `CounselorFavorite.favoriteType` / `CounselingAppointment.timeSlot` / `.status`）|
| 带名 `@Service` 总数 | **16**（未变 ✓ —— Bean 名一律保持原值）|
| 11 个控制器保留旧 API 前缀 | **11** ✓ |

### 4.2 构建与回归

`mvn -B clean package` → **BUILD SUCCESS**；**42/42** 全绿（27 + 15）✓（每个模块各跑一次 ✓）

### 4.3 端到端（真实容器，11 个 `/page` 探针）

| 模块 | code | total | 字典字段 |
| --- | :-: | :-: | --- |
| 通知 `Notification` | 0 | 5 | `categoryLabel` **5 字** ✓ |
| 知识 `KnowledgeArticle` | 0 | 5 | `categoryLabel` **7 字** ✓ |
| 学生 `Student` | 0 | 3 | `genderLabel` **1 字** ✓ |
| 量表 `AssessmentPaper` | 0 | 2 | `statusLabel` **2 字** ✓ |
| 题目 `AssessmentQuestion` | 0 | 6 | `questionTypeLabel` **3 字** ✓ |
| 预约 `CounselingAppointment` | 0 | 3 | `timeSlotLabel` **11 字** + `statusLabel` **2 字** ✓（**2 个键均生效**）|
| 留言 `CounselorMessage` | 0 | 2 | （无字典字段，符合预期）|
| 收藏 `CounselorFavorite` | 0 | 2 | `favoriteTypeLabel` **2 字** ✓ |
| 记录 `AssessmentRecord` / 明细 `AssessmentAnswer` / 错题 `AssessmentWrongQuestion` | 0 | 1/5/3 | （无字典字段，符合预期）|

→ **7 个带字典的模块全部完成回填** = 第 4 条硬约束在全批次**端到端验证通过** ✓✓
→ 数据库基线（`auth_token` = 3）不变 ✓

---

## 5. 刻意未动（边界）

| 项 | 原因 |
| --- | --- |
| **C 类 5 个模块**：`Xinlilaoshi` / `Users` / `Config` / `Token` / `Dictionary` | 审计判定「语义/兼容风险，暂缓」；需单独方案（前缀污染 / 常见词 / Bean 名不对应）|
| **Bean 名**（`tongzhiService`、`yonghuService`… 仍为拼音） | 硬约束 3：保持不动 ⇒ 字符串查找不破。**遗留不一致已登记**，如需清理须独立批次（`dictionaryService` 有真实 `getBean` 调用方）|
| 局部标识符（`tongzhiService` / `yonghuEntity` 等变量名） | 零耦合；改它们会稀释 diff 的可审查性 |
| 前端标识符（`dictionaryTongzhi` 等） | 前端自有模块名，**非类名引用**；且 `admin/admin/src` 未纳入版本控制 |
| API 路径 / 表名 / `@Service` 值 | 硬约束 1/2/3 |
| 主类 `xinlijiankangxitongApplication` + `pom.xml` `artifactId` | 审计 §10：单独立项；**建议不改 artifactId**（会改 jar 文件名）|
| `FileController` / `EIException` / `TypeEnum` | D 类（`TypeEnum` 属 T-1 遗留清理，另议）|

---

## 6. 补充：C 类首个模块 + 主类（✅ 完成并推送）

### 6.1 `Dictionary` → `SysDictItem`（C 类首个，表名 `sys_dict_item`）

**前置取证（三项，全部通过后才动手）**：

| 取证项 | 结果 |
| --- | --- |
| 大写 `Dictionary` 标识符 | **闭集仅 8 种**：`DictionaryEntity`×38 / `DictionaryService`×32 / `DictionaryView`×15 / `DictionaryServletContextListener`×4 / `DictionaryDao`×4 / `DictionaryServiceImpl`×3 / `DictionaryController`×2 / `"Dictionary{"`×1 ✓ |
| 小写 `dictionary*`（不得动） | `dictionaryService`×80 · `dictionaryConvert`×56 · `dictionaryMap`×16 · `dictionary`×29 … **全为小写** ⇒ 大小写敏感替换**物理上碰不到** ✓ |
| `getBean("dictionaryService")` 调用方 | **仅 1 处**（`DictionaryServletContextListener:37`）⇒ **Bean 名【不同步】、保持 `dictionaryService`**（硬约束 3），调用方继续有效 ✓ |
| 管理端 9 个字典前端模块 | `menu.js` 的 `"tableName":"dictionaryXxx"` 是**前端模块标识符**（用于 `isAuth` 权限与路由）；后端**只有 `/dictionary`**、**无任何 `dictionaryXxx` 端点** ⇒ 重命名**不影响前端** ✓ |

**范围**：7 个 Java 类（含 `@WebListener` 监听器）+ 1 个 Mapper XML；连带 **21 个文件**（11 个控制器全部注入该 Service ⇒ 级联）✓

**硬约束与不可动项实测**：`RequestMapping("/dictionary")`=1 ✓ / `@Service("dictionaryService")`=1（**不同步**）✓ / `@TableName("sys_dict_item")` 保持 ✓ / XML `namespace=com.dao.SysDictItemDao` ✓ /
`dictionaryMap` 属性 ✓ / `dictionaryConvert` 方法（56 处）✓ / 拦截器白名单 `"/dictionary/page"` ✓ / `getBean("dictionaryService")` 调用方 1 处 ✓ —— **全部为小写，物理上未被触碰** ✓

**注册机制说明**：主类上有 `@ServletComponentScan(value = "com.ServletContextListener")` ⇒ 监听器按**包**注册、与类名无关 ⇒ 改类名安全 ✓

**验证**：`mvn clean test` → **42/42** ✓；真实容器探针：

| 探针 | 结果 |
| --- | --- |
| `/dictionary/page`（免鉴权；重命名后的控制器） | `code=0` **total=25** —— 与库中 `sys_dict_item` **25 行完全一致** ✓ |
| `/tongzhi/page`（字典翻译经 `SysDictItemService`） | `code=0` `categoryLabel` **5 字** ✓ |
| `/yonghu/page` | `code=0` `genderLabel` **1 字** ✓ |
| `/jiankangzhishi/page` | `code=0` `categoryLabel` **7 字** ✓ |

⇒ 字典服务是全站承重件，**重命名后翻译链路完整 ✓**

### 6.2 主类：`xinlijiankangxitongApplication` → `MentalHealthCompanionApplication`（独立小项）

| 项 | 值 |
| --- | --- |
| 变更 | 文件名 + 类名 + **仅 3 处**自身引用（`public class` / `SpringApplication.run` / `applicationBuilder.sources`）|
| 前置取证 | 全仓 13 处命中中 **10 处在 docs**，代码中仅类自身 3 处 ⇒ **零外部代码引用** ✓ |
| **刻意不改** | `pom.xml` 的 `artifactId`/`name`、`application.yml` 的 `context-path` ⇒ **jar 名与 API 基路径完全不变** ✓ |
| 替换手法 | 只替换**完整类名** ⇒ 不会误碰裸 `xinlijiankangxitong`（`context-path: /xinlijiankangxitong`）✓ |
| 验证 | `mvn clean test` → **42/42** ✓（`@SpringBootTest` 按注解自动定位主类，不依赖类名 ✓）|

**提交**：`b0166ea`（SysDictItem，21 文件）+ `25dd305`（主类，1 文件）

---

### 6.3 `Xinlilaoshi` → `Counselor`（C 类第 2 个，表名 `counselor`）—— 提交 `32232f5`

**前缀污染风险已消除** ✓：它的 3 个子模块（`XinlilaoshiLiuyan`/`Collection`/`Order`）在批量阶段**已先改名** ⇒ 现在 `Xinlilaoshi` 只指老师模块本身 ✓

**三项前置取证**：

| 项 | 结果 |
| --- | --- |
| 大写 `Xinlilaoshi` 替换面 | **闭集 8 种**：`Entity`×58 / `View`×22 / `Service`×18 / `Dao`×4 / `Controller`×2 / `ServiceImpl`×1 + `"Xinlilaoshi{"`（toString）+ `"Xinlilaoshi.gender"`（**字典键**）✓ |
| 小写（不得动） | `xinlilaoshi`×101 · `xinlilaoshiLiuyan`×59 · `xinlilaoshiService`×47 · `xinlilaoshiIdNotNull`（3 个 XML 的查询参数）… **全小写** ⇒ 天然不受影响 ✓（改后复核：`xinlilaoshiService` **仍 47 处** ✓、XML 参数 **9 处** ✓）|
| 碰撞预检 | 已有 `Counselor(Entity\|View\|Service\|ServiceImpl\|Dao\|Controller)` = **0** ✓ |
| 模块自身 `getBean` 调用方 | **0** ✓ |

**⚠️ 额外排查到 2 个「中段标识符」**（非纯类名前缀，是本次唯一超出闭集的东西）：

| 标识符 | 性质 | 处置 |
| --- | --- | --- |
| `getXinlilaoshiNewMoney` | `CounselingAppointmentController:336` 的**注释掉的行**（死代码）| 随替换同步（无害）✓ |
| `getCurrXinlilaoshi` | `CounselorController:365` 的**活方法**（`/session` 端点的方法体）—— **全仓零外部引用**、按 `@RequestMapping` 映射不依赖方法名 | 随替换同步 ✓ |
| `params.xinlilaoshiIdNotNull` | XML 中的**查询参数名**（小写）| **未触碰** ✓ |

**硬约束实测**：`RequestMapping("/xinlilaoshi")`=1 ✓ / `@Service("xinlilaoshiService")`=1（**不同步**）✓ / `@TableName("counselor")`=2 ✓ / XML `namespace=com.dao.CounselorDao` ✓
**`DICT_CODE`**：`Counselor.gender`=1、旧键残留 **0** ⇒ **全部 9 个字典键自此均为英文** ✓
**范围**：21 文件（11 控制器级联 + 测试）✓；`mvn clean test` → **42/42** ✓

**真实容器探针**：

| 探针 | 结果 |
| --- | --- |
| `/xinlilaoshi/page`（旧 API 路径） | `code=0` total=3 · `genderLabel` **1 字** ✓ |
| `/xinlilaoshi/list`（免鉴权） | `code=0` total=3 · `genderLabel` ✓ |
| `/xinlilaoshiLiuyan/page`（子模块） | `code=0` total=2 ✓ |
| `/xinlilaoshiOrder/page`（子模块） | `code=0` total=3 · `timeSlotLabel` **11 字** + `statusLabel` **2 字** ✓ |

---

### 6.4 C 类剩余 3 个模块：`Users` / `Config` / `Token`（✅ 全部完成并推送）

> **共同结论：三个模块都【不能用裸前缀替换】** —— 取证逐条证明了原因（见下表）

| 模块 | 表名 | 新名 | 提交 | 🔴 裸前缀会误伤的东西（取证实测） |
| --- | --- | --- | --- | --- |
| `Users` | `admin_user` | `AdminUser` | `0cf4bb9` | `PoiUtil` 中的 **`"C:/Users/Administrator/Desktop/工作1.xls"` ×3**（会变成 `C:/AdminUser/…`）|
| `Config` | `sys_config` | `SysConfig` | `36a6fcd`+`70f9436` | **`@Configuration` 注解** · `AutoConfigureMockMvc` · `InterceptorConfig` / `MybatisPlusConfig` · 实体属性 `configValue`/`configKey`（改则属性名变、XML 别名错配）|
| `Token` | `auth_token` | `AuthToken` | `3ed238a` | **`LOGIN_TOKEN_KEY = "Token"`（HTTP 头名常量）** · CORS `Access-Control-Allow-Headers` 列表 · `.header("Token", …)` 调用 · 实体属性 `getToken`/`setToken` · 测试变量 `studentToken`/`adminToken`… |

**替换方式**：只用**精确类名模式**（5 个类名 / 3 个类名），或**单次通过正则** `[regex]::Replace`（见下）

**不得误伤实测（改后与取证逐项吻合）**：
`C:/Users/` 路径仍 3 ✓ · `@Configuration` 仍 2 ✓ · `AutoConfigureMockMvc` 5 ✓ · `InterceptorConfig` 2 ✓ · `MybatisPlusConfig` 1 ✓ · **纯 `"Token"` 字符串仍 6 ✓ · 测试 token 变量仍 71 ✓ · `getToken`/`setToken` 仍 3 ✓ · `LOGIN_TOKEN_KEY` 完好 ✓**

---

### 6.5 ⚠️ 过程事故与修正：`Config` 批的**替换级联**（已修复，如实登记）

`Config → SysConfig` 我最初用了**顺序多模式替换**（长名优先），仍发生级联：

```text
ConfigServiceImpl →(模式1)→ SysConfigServiceImpl
                  →(模式2: ConfigService→SysConfigService)→ SysSysConfigServiceImpl   ✗✗
```

**根因**：替换的**输出本身包含下一个模式**（`SysConfigServiceImpl` 含 `ConfigService`）。
**教训**：顺序替换只在「输出不含任何输入模式」时安全 —— `Users` 批恰好满足（`AdminUserServiceImpl` 不含 `UsersService`）✓，`Config` 批不满足 ✗。

| 项 | 处置 |
| --- | --- |
| 损伤范围 | **仅 1 行**（`SysConfigServiceImpl` 的类声明）✓ |
| 修法 | `[regex]::Replace(text, 'SysSysConfig(ServiceImpl\|Service)', 'SysConfig$1')` → 复原 ✓（提交 `70f9436`）|
| 校验口径修正 | 改用**负向后顾** `(?<![A-Za-z])Config(…)` 判残留 —— 否则新名 `SysConfigService` 会被旧模式误判为残留 ✗ |
| 流程缺陷 | 我的命令**缺少 BUILD FAILURE 守卫** ⇒ 编译不过的状态被提交 ✗（已在本批后续命令中全部加上守卫）|
| `Token` 批的预防 | 因 `AuthTokenServiceImpl` **含** `TokenService` ⇒ 直接改用**单次通过** `[regex]::Replace` ⇒ 级联残留 `AuthAuthToken` = **0** ✓ |

### 6.6 🔑 `Token` 批的"假绿"防线（本批最有价值的一条）

若用裸前缀替换 `Token`，会**同时**改掉：服务端读头的常量 `"Token"` **和**测试里的 `.header("Token", …)` ⇒ **MockMvc 42/42 仍全绿** ✗，而**真实前端（发 `Token` 头）会全线 401** ✗✗。

**实测双重防线**：

| 验证 | 结果 |
| --- | --- |
| 真实容器用**真 HTTP 头 `Token`** 访问 6 个端点 | 全部 `code=0` ✓（含字典字段）|
| **反向对照**：不带 `Token` 头 | `code=401 msg=请先登录` ✓ |

⇒ 正反成对，证明头名**未被改动** ✓✓

### 6.7 ⚠️ 新观察项（**非本批引入**，待裁定）

探针中我**预判**「学生访问 `/users/page` 应 403/401」，**实测 `code=0 total=1`** ✗ —— 即学生可列出**管理端账号**。
经核对：本批**只改类型名**，未动 URL / `@RequestMapping` / 权限逻辑 ⇒ **这是既有行为**，不是本次引入 ✓。
（`DataScope` 对学生只注入 `studentId` 参数，而 `admin_user` 表无该列 ⇒ 过滤失效。）
→ **建议独立小批次**核对该端点的预期可见性（与 D7 同类处理方式）。

---

### 6.8 小批次 A：`/users/page`、`/users/list` 收紧为**仅管理员**（`d168727`）

**来源**：Step 5b 探针发现 —— 学生访问 `/users/page` 返回 `code=0 total=1` ✗（**既有行为，非 5b 引入**：5b 只改类型名）
**风险升级**：`AdminUserEntity` 含 **`password`** 字段（库中为明文）⇒ 任意已登录用户可读到管理端账号口令 ✗

| 步骤 | 内容 |
| --- | --- |
| 取证 | 学生端 `front/` **完全不调用** `users/*` ✓；管理端仅 `users/list.vue:386` 调用 `users/page`（dist 命中 1 处）✓；`/users/info/{id}` **批 1 已限管理员**（测试 12/13 断言学生/老师被拒）⇒ 本批采用**同一策略**保持一致性 ✓ |
| 实现 | 两个方法各加 `HttpServletRequest` 参数 + `OwnershipGuard.assertAdminOnly(request)`（复用批 1 既有原语，不新增契约）|
| 测试 | 新增用例 **21**：正面管理员访问 `/users/page` 允许（防空跑）；反面 学生/老师 × 2 端点 = **4 组合均 403** ✓ |
| 验证 | `mvn clean test` → **43/43** ✓；真实容器：学生 `/users/page` → **403** ✓ |

### 6.9 小批次 B：16 个 Bean 名统一对齐（`c678789`）

**范围**：16 个 `@Service("...")` 值 + **1 处** `getBean` 调用方；**只替换带引号的字符串** ⇒ 同名字段/局部标识符一律不动 ✓

| 旧 → 新（16 组）|
| --- |
| `configService`→`sysConfigService` · `dictionaryService`→`sysDictItemService` · `exampaperService`→`assessmentPaperService` · `examquestionService`→`assessmentQuestionService` · `examrecordService`→`assessmentRecordService` · `examredetailsService`→`assessmentAnswerService` · `examrewrongquestionService`→`assessmentWrongQuestionService` · `jiankangzhishiService`→`knowledgeArticleService` · `tokenService`→`authTokenService` · `tongzhiService`→`notificationService` · `userService`→`adminUserService` · `xinlilaoshiCollectionService`→`counselorFavoriteService` · `xinlilaoshiLiuyanService`→`counselorMessageService` · `xinlilaoshiOrderService`→`counselingAppointmentService` · `xinlilaoshiService`→`counselorService` · `yonghuService`→`studentService` |

**前置取证**：消费方字符串引用**仅** `dictionaryService`（1 处 `getBean`，已同步）✓；其余 15 个仅自身声明 ✓；`@Qualifier`=0 / `@Resource`=0 ⇒ **全部按类型注入，改 Bean 名不可能破坏注入** ✓

**校验**：带名 `@Service` 仍 **16** ✓；16 个新名各就位（旧名残留 **0**）✓；`getBean("sysDictItemService")` **1** 处 ✓；无级联重复前缀 ✓
**直接证据**：字段标识符**未被触碰** —— `private NotificationService tongzhiService;` ✓（类型已换、字段名保留 ✓）；`tongzhiService` 在控制器中仍 **13** 处 ✓

> ⚠️ 过程记录（**我的检查口径缺陷，非代码问题**）：我原想用"裸标识符计数不变"作不变量，但基线把**引号内**的出现也算进去了 ✗ ⇒ 替换后每个旧名恰好掉 1（`dictionaryService` 掉 2，因它有 2 处引号）⇒ **掉落总数 1×15+2 = 17 = 实际替换数** ✓ 完全自洽。

**真实容器验证（关键）**：`dictionaryMap` 由 `getBean("sysDictItemService")` 构建 ⇒ 探针 `通知 categoryLabel` ✓ / `学生·老师 genderLabel` ✓ / `量表 statusLabel` ✓ / `字典 total=25` ✓ ⇒ **改 Bean 名后全链路正常** ✓

### 6.10 ✅ 新观察项 2 已修复：`/users/list` 对管理员返回 500（小批次 C，`8c538bf`）

> **重要过程记录**：我先按「1 词修复」把别名 `"user"` 改成 `"u"` ⇒ **测试仍然 500** ✗ ⇒
> 于是取**真实 SQL** 才看到**真正原因是 WHERE 拼接** ⇒ 实际是**两处并存缺陷，均已修** ✓
> （别名不一致确实存在 ✓ 是缺陷之一；但只修它不够 —— 这一点如实登记）

**真实 SQL 证据（修复前的失败现场）**：

```sql
SELECT u.* FROM admin_user u WHERE 1=1 (u.id IS NULL AND u.password IS NULL AND u.username = ? AND u.role IS NULL AND u.created_at IS NULL)
                                       ↑ 1=1 后缺 AND ⇒ MySQL 1064 语法错误
```

| # | 改动 | 依据（实证）|
| :-: | --- | --- |
| 1 | XML：`<where> 1=1 ${ew.sqlSegment}</where>` → **`${ew.customSqlSegment}`** | MP 的 `sqlSegment` **既不含 WHERE 也不含前导 AND** ✗；`customSqlSegment` 自带 WHERE，且条件为空时整段消失 ✓（规范用法）|
| 2 | 控制器：`allEq(map)` → **`allEq(map, false)`** | 实测 SQL 含 `u.id IS NULL AND u.password IS NULL …`（`null2IsNull` 默认 true ✗）⇒ 即使语法修好也**永远查不到行** ✗；`false` = 跳过 null ⇒ 无过滤=查全部、有过滤=按过滤 ✓ |

**测试升级**：用例 21 的管理员断言从「放行（200）」升级为「**必须真正取到数据**」（`?username=admin` 后 `data` 非空）—— 否则「修好 500 但查不到东西」会被漏掉 ✓

**端到端验证（真实容器）**：

| 探针 | 结果 |
| --- | --- |
| 管理员 `?username=admin` | `code=0` **1 条**，`username=admin` ✓ |
| 管理员 无过滤 | `code=0` **1 条**（skip-null 生效 ✓）|
| 学生 | **403** ✓（小批次 A 的授权保持 ✓）|

**回归**：`mvn clean test` → **43/43** ✓；提交 `8c538bf`（3 文件）已推送 ✓

---

#### 6.10.1 历史记录（修复前的判定过程，保留备查）

| 项 | 证据 |
| --- | --- |
| 现象 | 真实容器 + MockMvc 均为 `code=500` |
| 根因 | `BadSqlGrammarException`：`AdminUserDao.xml` 别名是 `u`，而控制器传 `"user"` 前缀 ⇒ SQL 别名不匹配 |
| 是否本批引入 | **否** —— `git` 对比证明方法体**一字未改**（本批仅加参数与通过性守卫）✓；且守卫对管理员**必然放行** ⇒ 逻辑上不可能由本批产生 ✓ |
| 影响面 | 该端点**无任何真实 API 调用方**（学生端 0 处；管理端唯一命中是 `router-static.js` 的模块导入路径 `@/views/modules/users/list`，**不是 API 调用**）⇒ 所以从未被发现 ✓ |
| 处置 | ✅ **已由小批次 C 修复**（`8c538bf`，见上）—— 规模从预估的「1 行级」**升级为「2 处」**（别名 + WHERE 拼接），过程已如实登记 ✓ |

---

## 7. 待裁定

1. **C 类 5 个模块**是否推进（建议：`Dictionary` → `SysDictItem` 需先核对前端「字典」相关模块与 `getBean("dictionaryService")` 调用方）
2. 主类是否改名（建议：类名可改、`artifactId` 不改，作为独立小项）
