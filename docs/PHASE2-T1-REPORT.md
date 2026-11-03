# Phase 2 / T-1 交付报告｜26 个孤儿类取证与清理

> **状态**：✅ 已完成并提交
> **提交**：`7302cf8`（26 files changed, **−5270 行**）
> **依据**：`PHASE1-SCOPE-AND-BOUNDARY.md` §8.3｜`PROJECT2-MASTER-PLAN.md` §7（T-1 定义）
> **性质**：🟡 **代码清理 / 技术债**（与 Step 3 的 🔴 安全修复**分属两个独立提交**）

---

## 1. 范围

| 做 | 不做 |
| --- | --- |
| **先取证**，再删除 | ❌ 不与 Step 3 的 S1 修复混提交 |
| 只删 `entity/model`(13) + `entity/vo`(13) | ❌ 不删 `entity/view`（在用的响应契约） |
| 覆盖用户指定的全部 9 类隐式引用向量 | ❌ 不改业务逻辑、不改表结构、不改前端 |

---

## 2. 取证：9 类隐式引用向量（全部为 0）

> 用户要求：**「没有 Controller 引用」≠「可以删除」**，必须排除隐式引用。

| # | 向量 | 取证方法 | 结果 |
| --- | --- | --- | --- |
| 1 | **Java 源码引用** | 26 个类名与 `entity.model` / `entity.vo` 包名，全量扫描 `src/main/java`（排除这两个包自身） | **0** |
| 2 | **XML / Mapper 引用** | 扫描 `src/main/resources/**/*.xml`（含 `resultType` / `typeAliases` / `parameterType`） | **0** |
| 3 | **配置文件引用** | 扫描 `application.yml`（含 `type-aliases-package`） | **0** |
| 4 | **Spring 组件扫描** | 在两个包内检索 `@Component` / `@Service` / `@Repository` / `@Controller` / `@RestController` / `@Configuration` / `@Bean` | **0** |
| 5 | **反射 / 动态加载** | 全量检索 `Class.forName` / `newInstance(` / `getDeclaredConstructor` / `ClassLoader` / `loadClass` | 13 处命中，**逐条核对后全部是** `getClass().getClassLoader().getResource("static/upload/" + fileName)` —— 取上传目录路径，**与类加载无关** |
| 6 | **继承关系** | 检查 26 个类的声明 | 全部为 `public class XxxModel/VO implements Serializable`，**独立类，不继承 Entity**，删除不会连带破坏父类 |
| 7 | **前端（含管理端）字符串引用** | 扫描 `front/` + `admin/` 的 `*.html` / `*.js` / `*.vue`，检索 26 个类名 | **0** |
| 8 | **Servlet / Listener / 线程 / 注解驱动** | 扫描 `com/ServletContextListener`、`com/thread`、`com/config` | **0** |
| 9 | **仓库根 / 脚本 / 说明文件** | 扫描根目录 `*.txt` / `*.bat` / `*.sh` / `*.cmd` / `*.xml` | **0** |

### 2.1 对照验证（关键）

同一次取证中，对**在用的** `com.entity.view` 做同样扫描：

```text
实体类外部引用                           26 个类 -> 0 处
entity/view 引用                          13 个类 -> 52 处   ✅ 在用，保留
```

> 这个对照很重要：它证明扫描方法**本身有效**（能扫出真实引用），而不是「扫不到任何东西」的假阴性。

### 2.2 取证结论

> **26 个类无任何隐式引用，可安全删除。**

---

## 3. 改动清单

| 目录 | 文件数 | 处理 |
| --- | --- | --- |
| `src/main/java/com/entity/model/` | 13 | **删除整个目录** |
| `src/main/java/com/entity/vo/` | 13 | **删除整个目录** |
| `src/main/java/com/entity/view/` | 13 | **保留**（在用，52 处引用） |

**合计：26 files changed，−5270 行，新增 0 行。**

---

## 4. 验证证据

| 项 | 结果 |
| --- | --- |
| 构建 | ✅ `BUILD SUCCESS`，编译错误 **0** |
| **源文件数** | **147 → 117**（SH-A 删 4 + T-1 删 26） |
| 应用启动 | ✅ Spring Boot **3.5.16** / Tomcat **10.1.55**，无 ERROR |
| 学生登录 | ✅ 正常 |
| 知识列表 | ✅ `total=5` |
| 试卷列表 | ✅ `total=2` |
| 学生分页（MP3 分页插件） | ✅ `total=3，返回 2 条` |
| Step 3 修复保持生效 | ✅ `yonghu/list` 响应仍**无** `password` / 身份证号 |

---

## 5. 回滚方式

> **准确表述**：**T-1 的代码变更可通过 Git 提交回滚；本步未修改数据库结构与数据，因此不存在本步独立产生的数据库回滚负担。** 仅限本步。

```bash
git revert 7302cf8          # 单独回退 T-1（恢复 26 个类）
```

---

## 6. 关于「复用 VO 类」这一分叉的裁决

`PHASE1-SCOPE-AND-BOUNDARY.md` §8.3 曾提出：`entity/vo` 的 13 个类**本身就是字段逐拷贝的 VO 形态**，是否可改造成真正的响应 VO（而非删除）？

**裁决：不复用，直接删除。** 依据：

| 理由 | 说明 |
| --- | --- |
| 它们是**字段拷贝**，不是**契约设计** | 内容与 Entity 完全一致（**同样含 `password` / `idCardNo`**，Step 3 取证时已实测确认）——沿用等于把问题搬个位置 |
| Step 3 已解决同一问题且更彻底 | `@JsonIgnore` 在序列化边界**一次覆盖**「实体直出 + View + 关联拼装」，无需另建平行类 |
| Phase 4 需要的是**领域 DTO**，不是字段镜像 | Tool 返回给 LLM 的形状应按「Tool 需要什么」重新设计（如 `AssessmentSummary`），而非按表结构生成 |

> 因此 `T-1` 按原计划删除 26 个类；VO / DTO 契约层留待 **Phase 4** 按实际领域需求新建。

---

## 7. 提交记录

| 项 | 内容 |
| --- | --- |
| 提交 | `7302cf8`（26 files changed，**−5270 行**） |
| 分支 | `phase2/step3-sensitive-field-governance` |

> **⚠️ 分支说明**：T-1 的提交落在 Step 3 的分支上（因为创建新分支的命令当时被审批超时中断，而提交需要尽快落库）。
> **提交本身是独立的**（`57f1781` Step 3 与 `7302cf8` T-1 是两个分离的提交，满足「一个独立问题 → 一个独立提交」）。
> 但**分支名与内容不完全对应**。若需要严格的分支隔离，可在 Phase 2 收尾时统一整理（如合并到 `master` 后按步骤重新打标签）。

---

## 8. 下一步

按校准顺序，T-1 之后是 **`T-2`（最小回归测试基线）**，然后是 **`Step 4`（统一异常处理）**。

**仍未决策的两项**（来自 Step 3 报告 §6，与 T-1 无关）：

| # | 项 | 说明 |
| --- | --- | --- |
| D1 | `phone` / `email` 是否纳入治理 | 51 次调用中 34 次命中；它们是业务字段，建议留到 Phase 9 用身份感知的字段可见性统一处理 |
| D2 | 免鉴权 `/list` 是否收敛 | 13 个 `/list` 可匿名访问，能枚举全量学生/老师 PII；`xinlilaoshi/list` 可能被公开首页使用，收紧会影响未登录页面 —— 属访问控制决策 |
