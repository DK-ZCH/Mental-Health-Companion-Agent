# Step 5b / Phase 1：类名重命名**只读审计**（零代码改动）

> **状态**：✅ 审计完成（**未改任何 Java / XML / 配置 / API**）
> **范围（审阅锁定 5 项）**：① 当前→建议类名 ② API 路径保持 ③ Entity/Mapper/Service/XML/Controller/Test 引用 ④ Spring/MyBatis-Plus 扫描与注入影响 ⑤ 无法安全重命名的例外
> **核心原则（审阅强调）**：**类名与表名对齐 ≠ 自动按表名批量重命名** —— 每项都必须单独核对全链路

---

## 1. 安全机制（**已实测确认**，这是重命名可行的前提）

| 机制 | 实测证据 | 含义 |
| --- | --- | --- |
| **API 前缀来自类级注解** | 16/16 Controller 的类级 `@RequestMapping` 值 = 前端调用的前缀（`/yonghu`、`/xinlilaoshiLiuyan`…，见 §2） | ✅ **改类名 + 保留注解值 ⇒ API 不变** |
| **Service 使用显式 Bean 名** | `@Service("…")` 带名 **16 处**；而唯一字符串式查找是 `DictionaryServletContextListener:37` 的 `getBean("dictionaryService")` | ✅ 改类名 **不改 Bean 名** ⇒ 字符串查找不破 |
| **Mapper 按包扫描** | `@MapperScan(basePackages = {"com.dao"})` + `mapper-locations: classpath*:mapper/*.xml` | ✅ 不依赖类名 |
| **XML 用全限定名** | `resultType/parameterType` 共 **15** 处，全部形如 `com.entity.view.XinlilaoshiView` | 🔴 **改 View/Entity 必须同步改 XML** |
| **反射式引用** | `Class.forName` = **0**；`@Qualifier` = **0** | ✅ 无反射依赖 |
| **type-aliases** | `mybatis-plus.type-aliases-package: com.entity`（**已配置**但 XML 未使用短名） | ✅ 当前无短名别名依赖 |
| **通配导入** | 各 Controller `import com.entity.*; com.entity.view.*; com.service.*;` | ✅ 同包改名**无需改 import**（仅测试类用显式 import：**14 处**）|

---

## 2. 全量类清单 + API 前缀（**16 个模块**）

| 模块前缀 | Entity → 表名 | View | Service/Impl | Dao(+xml) | Controller | **API 前缀（必须保持）** | 引用规模* |
| --- | --- | :-: | :-: | :-: | :-: | --- | ---: |
| `Yonghu` | `student` | ✓ | ✓ | ✓ | ✓ | `/yonghu` | 399 |
| `Xinlilaoshi` | `counselor` | ✓ | ✓ | ✓ | ✓ | `/xinlilaoshi` | 773* |
| `XinlilaoshiLiuyan` | `counselor_message` | ✓ | ✓ | ✓ | ✓ | `/xinlilaoshiLiuyan` | ↑含于上 |
| `XinlilaoshiCollection` | `counselor_favorite` | ✓ | ✓ | ✓ | ✓ | `/xinlilaoshiCollection` | ↑含于上 |
| `XinlilaoshiOrder` | `counseling_appointment` | ✓ | ✓ | ✓ | ✓ | `/xinlilaoshiOrder` | ↑含于上 |
| `Examrecord` | `assessment_record` | ✓ | ✓ | ✓ | ✓ | `/examrecord` | 195 |
| `Examredetails` | `assessment_answer` | ✓ | ✓ | ✓ | ✓ | `/examredetails` | 215 |
| `Examrewrongquestion` | `assessment_wrong_question` | ✓ | ✓ | ✓ | ✓ | `/examrewrongquestion` | 178 |
| `Exampaper` | `assessment_paper` | ✓ | ✓ | ✓ | ✓ | `/exampaper` | 317 |
| `Examquestion` | `assessment_question` | ✓ | ✓ | ✓ | ✓ | `/examquestion` | 308 |
| `Jiankangzhishi` | `knowledge_article` | ✓ | ✓ | ✓ | ✓ | `/jiankangzhishi` | 130 |
| `Tongzhi` | `notification` | ✓ | ✓ | ✓ | ✓ | `/tongzhi` | 130 |
| `Dictionary` | `sys_dict_item` | ✓ | ✓ | ✓ | ✓ | `/dictionary` | 300 |
| `Config` | `sys_config` | — | ✓ | ✓ | ✓ | `config` | 125 |
| `Users` | `admin_user` | — | ✓ | ✓ | ✓ | `users` | 92 |
| `Token` | `auth_token` | — | ✓ | ✓ | — | （无 Controller）| 318 |
| **其他** | `EIException`(无表) · `TypeEnum`(0 引用) · `FileController`(`file`) · 主类 `xinlijiankangxitongApplication` | | | | | | — |

\* 引用规模为**子串计数**（含 java+xml+yml），存在**高估**：`Xinlilaoshi` 包含 3 个子模块；`Config`/`Token`/`Dictionary` 会命中无关文本（如 `configuration`、`token` 字符串）→ **精确到类的计数留待 Phase 2**（见 §5）。

---

## 3. 分类（审阅给定的 A–D）

| 类 | 含义 | 本审计判定 |
| :-: | --- | --- |
| **A** | 可直接机械重命名 | **0 个模块** —— 每个模块都涉及 **8+ 文件**（Entity/View/Service/Impl/Dao/Dao.xml/Controller/测试），不存在"单类机械改名" |
| **B** | 需同步多个关联类（**可做，按模块成组**） | **11 个模块**：`Examrecord` / `Examredetails` / `Examrewrongquestion` / `Exampaper` / `Examquestion` / `Jiankangzhishi` / `Tongzhi` / `XinlilaoshiLiuyan` / `XinlilaoshiCollection` / `XinlilaoshiOrder` / `Yonghu`（前缀唯一、无语义歧义）|
| **C** | 存在语义/兼容风险，**暂缓** | **5 个**：`Xinlilaoshi`（**是 3 个子模块的前缀** → 盲目替换会污染子模块名）、`Users` / `Config` / `Token` / `Dictionary`（**常见英文子串**，批量替换易误伤无关代码）|
| **D** | 暂不应改名 | `CurrentUserProvider` / `OwnershipGuard` / `DataScope` / `R` / `PageUtils` / `MPUtil` / `SQLFilter` / `SpringContextUtils`（已是恰当英文名）；`TypeEnum`（0 引用，属 T-1 遗留清理项，另议）|

---

## 4. 建议的重命名顺序（**每模块独立提交**）

```text
单模块内的安全顺序（每步一次提交，41/41 兜底）：
  1) Entity（含 @TableName 值【不变】）
  2) View（同步改 XML 中的全限定名 —— 15 处之一）
  3) Service / ServiceImpl（@Service("…") 值【不变】）
  4) Dao（+ Dao.xml 文件名与 namespace）
  5) Controller（类级 @RequestMapping 值【不变】）
  6) 测试类中的显式 import（14 处）
```

**硬约束（每一步都要检查）**：
1. `@RequestMapping("/xxx")` 的值 **不变** ⇒ 前端零改动
2. `@TableName("...")` 的值 **不变** ⇒ 数据库零改动
3. `@Service("...")` 的值 **不变** ⇒ 字符串 Bean 查找不破
4. 🆕 **`DICT_CODE` 的键必须跟实体类简单名走**（`NotificationEntity` → `"Notification.category"`）—— 见 §13。
   这条**不属于「改类名」而属于「改字符串」**，编译与①②④补证都**不会**发现它；试点实测：漏改则字典翻译**静默失效**（接口仍 200 + code=0，只是 `xxxLabel` 变 null）

---

## 5. 遗留与未闭合项（**本审计未做的事**）

| # | 项 | 说明 |
| :-: | --- | --- |
| 1 | **精确到类的引用计数** | 现为子串计数（存在高估）→ Phase 2 需按**类名精确**统计（含 `src/test`、XML、yml）|
| 2 | **`@Service("…")` 的 16 个具体值未逐个列举** | 只确认"均带显式名"；实施前需确认**模块内每个 Service 的显式名**（避免两个类撞名）|
| 3 | **主类 `xinlijiankangxitongApplication` 是否改名** | 未评估 `pom.xml` 的 `finalName`/启动脚本耦合（本次命令未覆盖该检查）|
| 4 | **`Dao.xml` 的 namespace 与文件名** | 未逐条核对（若 namespace 与类全限定名一致，则改名必须同步）|
| 5 | **`TypeEnum`（0 引用）** | 属 T-1 遗留清理，与重命名无关，**另议** |
| 6 | **未做任何运行期验证** | 属只读审计：未改名、未构建、未发请求 |

---

## 7. 补证 ①：精确到类的引用计数（94 类，词边界匹配）

**方法**：对 `com.entity` / `com.entity.view` / `com.service` / `com.service.impl` / `com.dao` / `com.controller` 下全部 **94 个类名**，用 `\b类名\b` 统计，并按 **Java 代码 / import / 注释 / XML / Java 字符串行** 分类。
**范围**：`src/` 下 java+xml+yml+properties（**docs 不含在内**）。对比 Phase 1 的子串计数，本表**无高估**。

### 7.1 按类**类型**汇总（这是重命名成本的真实结构）

| 类类型 | 真实引用（典型） | 拆分 | 含义 |
| --- | :-: | --- | --- |
| **`XxxServiceImpl`** | **1** | 代码 1 · import 0 · XML 0 | 🔴 **16/16 全部只有 1 处**（= 自身类声明）。**没有任何代码按类名引用 Impl**（都按接口注入）→ 改 Impl ≈ 改文件名 + 类名 |
| **`XxxController`** | **1–2** | 代码 1–2 · import 0 | 🔴 **无外部代码引用**（`FileController` 的 1 处是注释）→ 改 Controller 不动任何调用方 |
| **`XxxDao`** | **4** | 代码 2 · import 1 · XML 1 | import 来自 ServiceImpl；XML 1 处 = `namespace` |
| **`XxxView`** | **20** | 代码 17 · import 2 · XML 1 | XML 1 处 = `resultType` 全限定名 |
| **`XxxEntity`** | **42–92** | 代码 29–78 · import 4–6 · 注释 5–12 | 最重（Controller/Service/Impl/Dao/View 都用）|
| **`XxxService`（接口）** | **18–112** | 代码 17–97 · **import 1–17** | 成本主要在**消费方的 import** |

**两个此前被低估的事实（§3 分类因此修正）**：
1. **`ServiceImpl` 与 `Controller` 几乎不受外部耦合** → 「单类机械重命名」**是存在的**（Phase 1 判定「A=0」不准确 ✗，见 §11 修正）
2. **成本差异极大**：`Impl` 1 处 vs `Entity` 92 处 —— 所以「按模块成组改名」时，**同一条链内部的成本并不均匀**，`Entity` 是主要工作量与风险来源

### 7.2 逐模块真实规模（六类相加，去掉 import/注释后）

| 模块 | Entity | View | Service | ServiceImpl | Dao | Controller | **合计（代码级）** | 唯一异常点 |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| **Tongzhi** | 35 | 17 | 17 | 1 | 2 | 2 | **74** | — |
| **Jiankangzhishi** | 35 | 17 | 17 | 1 | 2 | 2 | **74** | — |
| Examrewrongquestion | 41 | 17 | 22 | 1 | 2 | 2 | 85 | — |
| XinlilaoshiOrder | 29 | 17 | 17 | 1 | 2 | 2 | 68 | — |
| Examrecord | 44 | 17 | 26 | 1 | 2 | 2 | 92 | 有字符串行（见下）|
| Dictionary | 41 | 12 | **97** | 1 | 2 | 2 | 155 | **接口被 15 个文件 import** |
| XinlilaoshiLiuyan | 31 | 17 | 23 | 1 | 2 | 2 | 76 | 有字符串行 |
| XinlilaoshiCollection | 39 | 17 | 23 | 1 | 2 | 2 | 83 | 有字符串行 |
| Exampaper | 61 | 17 | 37 | 1 | 2 | 2 | 120 | — |
| Examquestion | 59 | 17 | 34 | 1 | 2 | 2 | 114 | — |
| Examredetails | 56 | 17 | 25 | 1 | 2 | 2 | 102 | 有字符串行 |
| Yonghu | 78 | 19 | 75 | 1 | 2 | 2 | 177 | — |
| **Xinlilaoshi** | 67 | 19 | 63 | 1 | 2 | 2 | 154 | **是 3 个子模块前缀** |
| Config | 29 | — | 17 | 1 | 2 | 1 | 50 | 常见词 |
| Users | 37 | — | 18 | 1 | 2 | 1 | 59 | 常见词（且 `@Service("userService")` 与类名不对应）|
| Token | 52 | — | 45 | 1 | 2 | — | 100 | **TokenEntity 有 6 处字符串行**（见 §8.3）|

> **「有字符串行」= 该模块的 XML 或 Java 里以字符串形式出现其类名所在字段**（如 `XinlilaoshiLiuyan` 出现在前端/参数名中）→ 成组改名时**必须逐条确认这些字符串不是类名引用**（§8.3 已列清单）。

---

## 8. 补证 ②：16 个 `@Service("...")` 的具体值（**必须保持不动**）

### 8.1 全部 16 条（类 → Bean 名）

| 类 | `@Service` 值 | 类 | `@Service` 值 |
| --- | --- | --- | --- |
| `ConfigServiceImpl` | `configService` | `TokenServiceImpl` | `tokenService` |
| `DictionaryServiceImpl` | `dictionaryService` | `TongzhiServiceImpl` | `tongzhiService` |
| `ExampaperServiceImpl` | `exampaperService` | **`UsersServiceImpl`** | **`userService`** ⚠️ |
| `ExamquestionServiceImpl` | `examquestionService` | `XinlilaoshiCollectionServiceImpl` | `xinlilaoshiCollectionService` |
| `ExamrecordServiceImpl` | `examrecordService` | `XinlilaoshiLiuyanServiceImpl` | `xinlilaoshiLiuyanService` |
| `ExamredetailsServiceImpl` | `examredetailsService` | `XinlilaoshiOrderServiceImpl` | `xinlilaoshiOrderService` |
| `ExamrewrongquestionServiceImpl` | `examrewrongquestionService` | `XinlilaoshiServiceImpl` | `xinlilaoshiService` |
| `JiankangzhishiServiceImpl` | `jiankangzhishiService` | `YonghuServiceImpl` | `yonghuService` |

### 8.2 碰撞与硬编码检查

| 检查 | 结果 |
| --- | --- |
| Bean 名唯一性 | **16 条 / 去重后 16** → **无碰撞** ✅ |
| 消费方字符串引用 | **仅 1 处**：`DictionaryServletContextListener:37` → `getBean("dictionaryService")` ⚠️ |
| `@Resource` / `@Qualifier` | **0 / 0** ✅ |
| `getBean(` 全仓 | 5 处 = 1 处真实消费（同上）+ 4 处 `SpringContextUtils` 的静态方法定义 ✅ |

> 说明：脚本曾把 16 条注解**自身**的字符串也标为「字符串引用」——那些是**声明**不是**消费**；**消费方只有 `dictionaryService` 一处** ✅
> ⚠️ **`UsersServiceImpl` 的 Bean 名是 `userService`（不是 `usersService`）** → 与类名**不对应**，成组改名时极易被「按类名推 Bean 名」的机械替换误伤 ✅ 已登记

### 8.3 各模块「字符串行」清单（成组改名时逐条确认）

| 类 | 字符串行数 | 需确认的点 |
| --- | :-: | --- |
| `TokenEntity` | 6 | Token 相关字符串（如 token 字段/键名）—— 非类名引用 |
| `UsersEntity` | 2 | 同上 |
| `EIException` | 2 | 同上 |

（其余模块的「字符串行」为 0 或 1，多为前端参数名/表名，**不是类名引用**。）

---

## 9. 补证 ④：Dao ↔ XML ↔ namespace 逐条核对

### 9.1 结论：**16/16 完美一一对应** ✅

| 项 | 实测 |
| --- | --- |
| `src/main/java/com/dao/*.java` | **16 个** |
| `src/main/resources/mapper/*.xml` | **16 个**（**无多余、无缺失** ✅） |
| 文件名配对 | 每个 `XxxDao.java` 都有同名 `XxxDao.xml` ✅ |
| `namespace` | 全部为 `com.dao.XxxDao`（与接口全限定名一致）✅ |
| XML 内全限定类名 | 共 **15 处**，每 XML 至多 1 处（`ConfigDao.xml` 为 0）✅ |

### 9.2 XML 内**全部**全限定类名（改名必须同步的清单）

```text
com.entity.TokenEntity                        ×1
com.entity.UsersEntity                        ×1
com.entity.view.DictionaryView                ×1
com.entity.view.ExampaperView                 ×1
com.entity.view.ExamquestionView              ×1
com.entity.view.ExamrecordView                ×1
com.entity.view.ExamredetailsView             ×1
com.entity.view.ExamrewrongquestionView       ×1
com.entity.view.JiankangzhishiView            ×1
com.entity.view.TongzhiView                   ×1
com.entity.view.XinlilaoshiCollectionView     ×1
com.entity.view.XinlilaoshiLiuyanView         ×1
com.entity.view.XinlilaoshiOrderView          ×1
com.entity.view.XinlilaoshiView               ×1
com.entity.view.YonghuView                    ×1
```

### 9.3 关键结论：**XML 文件名是「表面」，namespace 才是「绑定」**

`application.yml` 配置 `mapper-locations: classpath*:mapper/*.xml` → **加载的是目录下全部 XML**，与文件名无关。
因此：
- **必须改**：XML 内的 `namespace`（绑定到 DAO 接口全限定名）+ XML 内的 `com.entity.*` 全限定名
- **可改可不改**：XML **文件名**（为了可读性建议同步改，但不改也不会坏）
- 每个 `XxxDao` 的真实耦合面 = **接口文件 + 1 处 ServiceImpl import + 1 处 namespace** = **4 处**（见 §7.1）

---

## 10. 补证 ③：主类（**单独评估，不混入模块重命名**）

### 10.1 证据

```text
文件      src/main/java/com/xinlijiankangxitongApplication.java      ← 直接在 com 包下（非子包）
L10       @SpringBootApplication
L12       @MapperScan(basePackages = {"com.dao"})
L13       public class xinlijiankangxitongApplication extends SpringBootServletInitializer
L15       public static void main(String[] args) {
L16       SpringApplication.run(xinlijiankangxitongApplication.class, args);
L21       return applicationBuilder.sources(xinlijiankangxitongApplication.class);   ← WAR 部署钩子
```

| 检查 | 结果 |
| --- | --- |
| 外部代码引用 | **0**（全仓 13 处命中中，**3 处在类自身**，其余 10 处全在 docs） ✅ |
| `pom.xml` | `<artifactId>xinlijiankangxitong</artifactId>`、`<name>xinlijiankangxitong</name>`；**未配置 `finalName`** |
| 构建产物 | `xinlijiankangxitong-0.0.1-SNAPSHOT.jar`（由 artifactId+version 决定） |
| pom 内注释 | L9 已注明「artifactId 暂不改名（随 Step 5b 的 Java 命名统一处理）」 |

### 10.2 建议（与审阅意见一致）

| 项 | 建议 |
| --- | --- |
| **Java 类名** | 可改（零外部引用；只需同步文件名 + 3 行 `class` 引用）—— **但作为独立小项**，不与任何模块批次混合 |
| **`pom.xml` 的 `artifactId`/`<name>`** | ⛔ **不改** —— 会改变 **jar 文件名**，牵动构建/部署/脚本耦合，而收益只是「名字好看」→ 不符合「不为英文规范化强行改」 |
| 归属 | 本项**不属于** `Entity → View → Service → Dao → Controller` 链，**单独立项** |

---

## 11. 分类修正（**Phase 1 的「A = 0」不准确，予以更正**）

Phase 1 我判定「**A（可直接机械重命名）= 0 个模块**，因为每个模块都涉及 8+ 文件」。**按类精确计数后，该结论需要修正**：

| 原判 | 修正后 |
| --- | --- |
| A = 0（无单类机械改名） | ⚠️ **不准确**：`ServiceImpl`（**1 处引用**）与 `Controller`（**1–2 处引用**）**本身就是可直接机械重命名的单类** ✅ |
| —— | ✅ **更准确的表述**：**模块必须「成组」改名（因 `Entity`↔`View`↔`Service` 互相引用），但组内每个类的成本/风险差异极大** |

### 11.1 按类风险分级（**修正版**）

| 级别 | 类 | 依据 | 处置 |
| --- | --- | --- | --- |
| **A · 可直接机械重命名** | `XxxServiceImpl`（16） | 引用 **1** 处、无外部按类引用 | 改文件名 + 类名；**`@Service("…")` 值不动** |
| **A · 可直接机械重命名** | `XxxController`（16） | 引用 **1–2** 处、无外部代码引用 | 改文件名 + 类名；**类级 `@RequestMapping` 值不动** |
| **B · 需同步少量关联点** | `XxxDao`（16） | 4 处 = 接口 + 1 import + 1 namespace | 同步 XML `namespace`（文件名可选） |
| **B** | `XxxView`（13） | 20 处 = 类 + 17 代码 + 2 import + 1 XML | 同步 XML `resultType` |
| **C · 高耦合，需逐条核对** | `XxxService`（16，接口） | 18–112；`DictionaryService` 被 **15 个文件** import | 逐模块统计消费方 import |
| **C** | `XxxEntity`（17） | 42–92，含注释与字符串行 | 逐模块核对 |
| **D · 暂不改** | `Xinlilaoshi` / `Users` / `Config` / `Token` / `Dictionary` **模块整体** | 前缀污染 / 常见词 / Bean 名不对应 | 见 Phase 1 §3 |

### 11.2 试点候选的实际成本（**用真实数据，不用 130 这个子串数**）

| 候选 | 六类代码级合计 | Entity | Service 接口 import | DAO | 结论 |
| --- | :-: | :-: | :-: | :-: | --- |
| **`Tongzhi`** | **74** | 35 | **1** | 4 | ✅ 最小之一、前缀唯一、无字符串行 |
| `Jiankangzhishi` | **74** | 35 | **1** | 4 | ✅ 与 Tongzhi 并列；`knowledge_article` 语义更贴切 |

> 二者规模**并列最小**，且都无子模块污染、无高碰撞词、无 Bean 名异常。
> 按审阅倾向 **先 `Tongzhi`**（候选建议，**非批准改名**）。

---

## 12. 首批试点结果：`Tongzhi` → `Notification`（✅ 完成，未推送）

> 详细证据见 [`PHASE2-T5B5-PILOT-TONGZHI-REPORT.md`](./PHASE2-T5B5-PILOT-TONGZHI-REPORT.md)

| 项 | 结果 |
| --- | --- |
| 范围 | 6 个 Java 类 + 1 个 Mapper XML + `DICT_CODE` 键（**仅类型名与文件名**）|
| 手法 | 文件名 `git mv`；每文件**一次大小写敏感** `Tongzhi` → `Notification` 替换 |
| 强性质 | 小写的 API 路径 / 表名 / Bean 名 / 局部变量 / 日志串**物理上不可能被误改**（替换匹配不到）|
| 四条硬约束 | `RequestMapping("/tongzhi")`=1 ✓ `@Service("tongzhiService")`=1 ✓ `@TableName("notification")`=2 ✓ `DICT_CODE` 新键=1、旧键残留=0 ✓ |
| 旧类名残留 | `src/main` + `mapper` + `test` 中 **0** ✓ |
| 回归 | **42/42**（27 + 15）✓；数据库基线不变 ✓ |
| 端到端（真实容器） | `/tongzhi/page` → `code=0 total=5`，`categoryLabel` 与字典值**逐字符一致 5/5** ✓；`/tongzhi/info/1` ✓ |
| 对照组 | 未改名模块 `/jiankangzhishi/page` → **3/3** ✓（证明机制本身正常）|
| 反向对照 | 字典键改回 `"Tongzhi.category"` → 新用例**失败**并打印完整 `DICT_CODE` + 缺失键 ✓ |

### 12.1 🔴 试点最重要的产出：第 4 类耦合（**①②④ 补证都未覆盖**）

`DictionaryServiceImpl.dictionaryConvert()` 在**运行期**按 `declaringClass.getSimpleName()`（去 `Entity` 后缀）拼 key 查 `DICT_CODE`：

```text
NotificationEntity.category  →  "Notification.category"  →  "notification_category"
```

**纯机械改类名 → 字典翻译静默失效**（接口仍 `200 + code=0`，只是 `xxxLabel` 变 `null`）。
它不是编译错误，也不是 ①（引用计数）②（Bean 名）④（XML）能发现的 —— 因为它是一处**没有「引用」外观的字符串**。

**全仓扫描确认这是唯一一处**：`getSimpleName()` 1 处、`endsWith("Entity")` 1 处、`replace("Entity"/"View")` **0**、`getAnnotation(`/`TableName.class`/`TableInfoHelper` **0/0/0**。

**批量推进时的字典键清单**（每个模块改名必须同步）：

| 模块 | `DICT_CODE` 键 | 分类 |
| --- | --- | --- |
| `Yonghu` | `Yonghu.gender` | B |
| `Exampaper` | `Exampaper.status` | B |
| `Examquestion` | `Examquestion.questionType` | B |
| `Jiankangzhishi` | `Jiankangzhishi.category` | B |
| `XinlilaoshiOrder` / `XinlilaoshiCollection` | `.timeSlot` / `.status` / `.favoriteType` | B |
| `Xinlilaoshi` | `Xinlilaoshi.gender` | **C（暂缓）** |

### 12.2 过程自我纠正（2 处**测量**错误，非代码问题）

| # | 错误 | 如何识破 |
| :-: | --- | --- |
| 1 | 先改代码、后加基线用例 → 首次运行即失败，**无法判定真断链还是环境问题** | 追查为 MockMvc **不初始化 `@WebListener`** → `dictionaryMap` 为 null → 改断言为「key 契约」+ 端到端交真实容器 |
| 2 | PowerShell 对无 `charset` 头的 JSON 按 **Latin-1** 解码 → 逐字符比对全部 MISMATCH | hex 显示 `E9 80 9A…` = UTF-8 字节被按 Latin-1 解；**决定性证据是未改名的对照组出现同样伪影** → 改 `curl` + 显式 UTF-8 → 5/5 ✓ |

> 经验：**对照用例是区分「真的改坏了」与「测错了」的唯一可靠工具**。本批两处纠正都靠它。

### 12.3 已知瑕疵（如实登记）

本地提交 `adc2cd3` 实际包含**代码 + 测试共 10 文件**，但提交信息只描述了「测试」部分
（命令转义出错导致代码提交失败，随后的测试提交把全部改动一并提交）。
按规则**未擅自 `--amend` / `reset`** 改写历史 → **待裁定**（未推送）。

---

### 12.4 批量推进：10 个 B 类模块（✅ 10/10 完成并推送）

> 详细清单与验收见 [`PHASE2-T5B5-STEP5B-BATCH-REPORT.md`](./PHASE2-T5B5-STEP5B-BATCH-REPORT.md)

| 旧 | 新 | 表名（不变）| 提交 |
| --- | --- | --- | --- |
| `Jiankangzhishi` | `KnowledgeArticle` | `knowledge_article` | `eca56f9` |
| `Examrewrongquestion` | `AssessmentWrongQuestion` | `assessment_wrong_question` | `780c260` |
| `XinlilaoshiLiuyan` | `CounselorMessage` | `counselor_message` | `da02586` |
| `XinlilaoshiCollection` | `CounselorFavorite` | `counselor_favorite` | `aebf60d` |
| `XinlilaoshiOrder` | `CounselingAppointment` | `counseling_appointment` | `47e1ee6` |
| `Examrecord` | `AssessmentRecord` | `assessment_record` | `1785291` |
| `Examredetails` | `AssessmentAnswer` | `assessment_answer` | `15bd269` |
| `Exampaper` | `AssessmentPaper` | `assessment_paper` | `9d2c7c4` |
| `Examquestion` | `AssessmentQuestion` | `assessment_question` | `d454f7e` |
| `Yonghu` | `Student` | `student` | `d6c2155` |

**手法**：`git mv` ×7 → **全 src（含 `src/test`）一次大小写敏感前缀替换** → 静态校验（旧名残留 0 + 4 条硬约束 + XML `namespace`）→ `mvn -B clean test` → 独立提交。
该手法的强性质：**跨模块级联引用 / 测试类 / 安全原语自动同步**，而**小写 API 路径 / 表名 / Bean 名 / 局部变量 / 日志串天然不被匹配** ✓

**总验收**：11 个旧前缀残留 **全 0** ✓；`DICT_CODE` **9 个键全部为新类名** ✓；带名 `@Service` **16 个未变** ✓；11 个控制器保留旧 API 前缀 ✓；**42/42** ✓；真实容器 **11 个 `/page` 探针 code=0** 且 **7 个带字典模块全部回填**（含预约的 2 个键）✓；数据库基线不变 ✓

**🔴 实践教训（非代码）**：重命名后**必须 `clean`** —— 首次用 `mvn test`（未 clean）时 `target/classes/mapper/` 残留旧 XML（含旧全限定类名）→ MyBatis 解析失败 → **全部用例 ERROR**。`clean test` 后 42/42 ✓

**未动**：C 类 5 个模块（`Xinlilaoshi`/`Users`/`Config`/`Token`/`Dictionary`）· Bean 名（仍为拼音，硬约束 3）· 局部标识符 · 前端标识符 · 主类与 `artifactId`

---

### 12.5 C 类首个模块 + 主类（✅ 完成并推送）

| 项 | 旧 → 新 | 提交 | 证据要点 |
| --- | --- | --- | --- |
| 字典模块（**C 类首个**） | `Dictionary` → `SysDictItem`（表 `sys_dict_item`）| `b0166ea` | 三项前置取证通过：大写字面量是**闭集 8 种** / 小写 `dictionary*` 全不受影响 / `getBean("dictionaryService")` **仅 1 调用方** ⇒ **Bean 名不同步（硬约束 3）**；管理端 `dictionaryXxx` 是**前端模块标识符**、后端无同名端点 ⇒ 前端不受影响；21 文件（11 控制器级联）；探针 `/dictionary/page` **total=25 = 库中 25 行** ✓ |
| 心理老师模块（**C 类第 2 个**） | `Xinlilaoshi` → `Counselor`（表 `counselor`）| `32232f5` | **前缀污染风险已消除**（3 个子模块已先改名）；三项取证通过；⚠️ 额外排查出 2 个**中段标识符**（`getXinlilaoshiNewMoney` 死注释 ✓ / `getCurrXinlilaoshi` 活方法但**零外部引用** ✓）⇒ 全前缀替换安全；小写 `xinlilaoshiService` **仍 47 处**、XML 参数 9 处未动 ✓；探针 `/xinlilaoshi/page` → `genderLabel` ✓ ⇒ **DICT_CODE 9 键自此全为英文** ✓ |
| **主类**（独立小项） | `xinlijiankangxitongApplication` → `MentalHealthCompanionApplication` | `25dd305` | 代码中仅**类自身 3 处**引用；**`artifactId` / `context-path` 刻意不改** ⇒ jar 名与 API 基路径不变；`@ServletComponentScan` 按包注册监听器 ⇒ 类名无关 ✓ |

**C 类 5 个模块全部完成** ✓（`SysDictItem` / `Counselor` / `AdminUser` / `SysConfig` / `AuthToken`）：

| 模块 | 旧 → 新 | 提交 | 🔴 裸前缀会误伤（取证实测） |
| --- | --- | --- | --- |
| `Users` | `Users*` → `AdminUser*`（表 `admin_user`）| `0cf4bb9` | `PoiUtil` 的 `"C:/Users/Administrator/…"` ×3 |
| `Config` | `Config*` → `SysConfig*`（表 `sys_config`）| `36a6fcd` + **修正** `70f9436` | **`@Configuration`** · `AutoConfigureMockMvc` · `InterceptorConfig`/`MybatisPlusConfig` · 实体属性名 |
| `Token` | `Token*` → `AuthToken*`（表 `auth_token`）| `3ed238a` | **`LOGIN_TOKEN_KEY = "Token"`（HTTP 头名）** · `.header("Token", …)` · 实体属性名 · 测试变量 |

**⚠️ 过程事故（已修复并登记）**：`Config` 批用**顺序多模式替换**时发生**级联** —— `ConfigServiceImpl → SysConfigServiceImpl` 后，第二个模式 `ConfigService` 又命中新名子串 ⇒ `SysSysConfigServiceImpl` ✗（仅 1 行，编译失败被提交 ✗）。
**教训**：顺序替换仅在「**输出不含任何输入模式**」时安全（`Users` 批满足 ✓、`Config` 批不满足 ✗）。
**修正手法**：改用 `[regex]::Replace` **单次通过**（`Token` 批级联残留 `AuthAuthToken` = **0** ✓）；判残留改用**负向后顾** `(?<![A-Za-z])`（否则新名会被旧模式误判 ✗）。

**🔑 假绿防线（Token 批）**：裸替换 `Token` 会同时改服务端读头常量与测试头名 ⇒ MockMvc 42/42 仍绿 ✗ 而真实前端全线 401 ✗✗。
实测：真实容器带**真 `Token` 头** → 6 个端点全 `code=0` ✓；**不带该头** → `code=401 请先登录` ✓ ⇒ 正反成对证明头名未变 ✓✓

---

## 13. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-10 | Step 5b / Phase 1 只读审计（**零改动**）：16 模块全量类清单 + 表名 + **API 前缀 16/16**；安全机制 7 项实测（含 XML 全限定名 15 处、显式 Bean 名 16 处、反射 0）；分类 **A=0 / B=11 / C=5 / D=8 基础类**；给出单模块 6 步安全顺序与 3 条硬约束；登记 6 项未闭合 |
| v2 | 2026-10-10 | **补证 ①②④（零改动）**：① **94 类**精确计数（词边界，按代码/import/注释/XML/字符串分类）→ 发现 **`ServiceImpl` 引用恒为 1、`Controller` 为 1–2**；② **16 个 `@Service` 值全部枚举**（无碰撞；消费方字符串引用**仅 `dictionaryService` 1 处**；`UsersServiceImpl` 的 Bean 名是 `userService` ⚠️）；④ **Dao↔XML 16/16 一一对应**、`namespace` 全为全限定名、XML 内 **15 处**全限定类名已列清单、**文件名可改可不改（`mapper-locations` 与文件名无关）**；③ 主类单独评估（**零外部引用**、建议**不改 `artifactId`**）；**更正 Phase 1 的「A=0」**（`ServiceImpl`/`Controller` 本身就是单类机械改名）→ 见 §11 |
| v3 | 2026-10-10 | **首批试点（`Tongzhi` → `Notification`，本批含代码改动）**：发现并封堵**第 4 条硬约束**——`DICT_CODE` 键必须跟**实体类简单名**走（运行期按 `getSimpleName()` 匹配，机械改名会**静默**打断字典翻译；全仓扫描仅此 **1** 处）；7 文件重命名；**42/42** + 真实容器端到端**逐字符一致 5/5** + 对照组 3/3 + 反向对照有效 + 库基线不变；登记 **2 处测量错误**（MockMvc 无 WebListener / Latin-1 解码）与 1 处提交信息瑕疵 → 见 §12 |
