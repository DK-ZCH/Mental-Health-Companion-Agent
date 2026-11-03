# Phase 2 / Step 3 交付报告｜API 数据边界治理（S1 敏感字段）

> **状态**：✅ **已完成并提交**
> **分支**：`phase2/step3-sensitive-field-governance`
> **提交**：`57f1781`（9 files changed, **+19 行**）
> **依据**：`project-audit.md` §8.1 S1｜`PROJECT2-MASTER-PLAN.md` §7（Step 3 三条硬约束）
> **本步唯一施工目标**：`DTO/VO + S1 敏感字段治理`。**26 个孤儿类已移出本步**（→ `T-1`）

---

## 1. 范围（严格限定）

| 做 | 不做 |
| --- | --- |
| 先做**真实 API 暴露面审计**，再据实际暴露面治理 | ❌ 不全量机械 `Entity → VO` |
| 剔除 `password` 与身份证号（含**关联/嵌套**字段） | ❌ **不重构认证授权体系**（`AuthorizationInterceptor` / Session / Token / 权限模型**一律未动**） |
| 以**实际 API 响应**为验收证据 | ❌ **不清理 26 个孤儿类**（→ `T-1`，须先取证） |

---

## 2. 审计：以实际 API 响应为证据

> 用户要求：**验收以实际 API 响应为最终证据，而不是以代码搜索结果为最终证据。**

### 2.1 审计方法

启动服务 → 三角色登录取 token → 对 **12 个业务控制器 × 4 类接口**（`/list`、`/page`、`/info/{id}`、`/detail/{id}`）+ 3 个 `/session` 发起 **51 次真实调用** → 对响应体做**子串匹配**（`(?i)password` / `(?i)idcard` / `(?i)phone` / `(?i)email`）。

### 2.2 审计结果（修复前）

| 泄露项 | 命中路径数 | 明细 |
| --- | --- | --- |
| 🔴 **`password`** | **10** | `yonghu/{list,page,info,detail,session}`、`xinlilaoshi/{list,page,info,detail,session}`、`users/{page,session}` |
| 🔴 **身份证号** | **28** | `yonghu/*`（4 路径）+ **6 类 View 接口**（预约 / 留言 / 收藏 / 测评记录 / 答题明细 / 错题）各自 4 路径 → 关联字段 `studentIdCardNo` |

**同时暴露的两个访问控制问题**（**本次未修**，见 §6）

| # | 问题 | 证据 |
| --- | --- | --- |
| A1 | `yonghu/list`、`xinlilaoshi/list` **免鉴权** | 带 `@IgnoreAuth`，无 token 即返回全量学生 / 老师数据 |
| A2 | `users/page` **学生 token 可访问** | 拦截器仅校验「是否登录」，不校验角色（审计 §8.1 S5 的实证） |

### 2.3 ⚠️ 审计过程中修正的一次**假阴性**

首轮审计用**精确名** `"idCardNo"` 匹配，判定「关联接口无泄露」；复核后发现关联字段名是 **`studentIdCardNo`**（前缀命名），故首轮结论**漏报了 24 条路径**。改用子串匹配后重扫，才看到完整暴露面。

> **教训**：审计响应体时不能用「精确字段名」匹配 —— 必须按**子串**匹配，否则关联/嵌套字段会漏报。

---

## 3. 改动清单

**9 个文件，全部为 getter 级 `@JsonIgnore`（新增 0 个类、0 行业务逻辑、0 处 SQL）**

| 文件 | 字段 | 说明 |
| --- | --- | --- |
| `com/entity/YonghuEntity.java` | `password`、`idCardNo` | 学生实体（亦被 `YonghuView` 继承） |
| `com/entity/XinlilaoshiEntity.java` | `password` | 心理老师实体（亦被 `XinlilaoshiView` 继承） |
| `com/entity/UsersEntity.java` | `password` | 管理员实体 |
| `com/entity/view/XinlilaoshiOrderView.java` | `studentIdCardNo` | 预约 |
| `com/entity/view/XinlilaoshiLiuyanView.java` | `studentIdCardNo` | 留言 |
| `com/entity/view/XinlilaoshiCollectionView.java` | `studentIdCardNo` | 收藏 |
| `com/entity/view/ExamrecordView.java` | `studentIdCardNo` | 测评记录 |
| `com/entity/view/ExamredetailsView.java` | `studentIdCardNo` | 答题明细 |
| `com/entity/view/ExamrewrongquestionView.java` | `studentIdCardNo` | 错题 |

### 3.1 为什么这里是「序列化边界排除」而不是新建 VO 类

**技术约束（本代码库的结构性事实）**：

```text
YonghuView  extends  YonghuEntity      ← View 继承 Entity
```

`password` / `idCardNo` 定义在 **Entity** 上，`View` 通过**继承**获得该 getter。因此：

- 无法「从 View 中移除」这两个字段（字段不在 View 里）；
- 若为该场景新建一个**继承 Entity** 的 VO，只会重复同样的问题；
- 唯一能**一次覆盖**「实体直出 + View + 关联拼装」三类的机制，就是在 **Jackson 序列化边界（getter）** 做结构性排除。

**关键性质**：`@TableField` / `@TableId` 与持久化映射**完全不受影响**，MyBatis 仍正常读写这些列，**只是不再进入 JSON 响应** —— 因此这是**结构性契约变更**，不是「字段黑名单过滤」。

### 3.2 遗留的架构分叉（供 `T-1` 决策，不在本步处理）

`com.entity.vo.*`（13 个类）与 `com.entity.model.*`（13 个类）被判定为**孤儿类**。但本次迁移发现：**`com.entity.vo.*` 本身就是「字段逐拷贝」的 VO 形态**（且同样含 `password` / `idCardNo`，见 §3.1 首轮 grep 证据）。

因此 `T-1` 面对的不是「删 26 个类」这一个选项，而是：

| 选项 | 说明 |
| --- | --- |
| A. 全部删除（26） | 按原计划；VO 契约层留到 Phase 3/4 另建 |
| B. 删 13（model）+ **改造** 13（vo）为真正的响应 VO | 减少新建成本，但需先确证 13 个 VO 无隐式引用，且要解决「VO 是否仍继承 Entity」 |

> 该分叉正是用户要求 `T-1` **先取证再删**的原因，故本步不触碰。

---

## 4. 验证证据（构建 + 真实响应）

### 4.1 构建

```text
mvn -B -DskipTests clean package → BUILD SUCCESS
```

### 4.2 带「服务就绪守卫」的复测

> ⚠️ 首次复测曾出现「0 命中」的**无效结论** —— 因为当时构建失败、服务未启动，51 次调用全部失败。
> 已在验证脚本中加入两条守卫：**① 构建必须成功，否则中止；② 三角色登录必须成功，否则中止**。下表为守卫通过后的结果。

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| `password` 命中 | 10 条路径 | **0** ✅ |
| 身份证号 命中 | 28 条路径 | **0** ✅ |
| 调用次数 | 51 | 51 |

### 4.3 核心业务回归（必须全部正常）

| 用例 | 结果 |
| --- | --- |
| 学生 / 管理员 / 老师 登录 | ✅ 全部成功 |
| 知识列表 | ✅ `total=5` |
| 试卷列表 | ✅ `total=2` |
| 学生分页 | ✅ `total=3，返回 2 条` |

---

## 5. 回滚方式

> **准确表述**：**Step 3 的代码变更可通过 Git 提交回滚；本 Step 未修改数据库结构与数据，因此不存在本 Step 独立产生的数据库回滚负担。** 仅限本 Step。

```bash
git revert <step3-commit>
```

---

## 6. 未纳入本步的项（需用户决策）

| # | 项 | 现状证据 | 说明 |
| --- | --- | --- | --- |
| **D1** | `phone` / `email` 仍在响应中 | 51 次调用中 **34 次命中** | **不在本次验收范围**（用户验收只列了 `password` 与身份证号）。是否治理需明确：`phone`/`email` 属 PII，但也是业务字段（管理员需联系学生、老师列表需展示） |
| **D2** | 免鉴权 `/list` 端点 | `yonghu/list`、`xinlilaoshi/list` 等 13 个 `/list` 无 token 可访问 | 属**访问控制**决策；`xinlilaoshi/list` 可能被公开首页使用，**改动会影响未登录页面**，故必须由用户决定，不能由实现者自行收紧 |
| **D3** | 26 个孤儿类 | `entity/model` 13 + `entity/vo` 13 | 已移出本步 → **`T-1`**（须先取证：Spring 扫描 / 反射 / XML / `Class.forName` / 注解 / Servlet·Listener / 前端字符串 / 配置） |
| **D4** | `T-2` 最小回归测试基线 | 本次验证仍依赖脚本化 curl | 尚未固化为测试用例 |

---

## 7. 提交记录

| 项 | 内容 |
| --- | --- |
| 分支 | `phase2/step3-sensitive-field-governance` |
| 提交 | `57f1781`（9 files changed，**+19 行**） |
| 改动性质 | 仅 9 处 getter 注解 + 3 处 import —— **新增 0 个类、0 处业务逻辑、0 处 SQL** |

> **过程记录（可追溯）**：本次提交过程中，`git checkout -b` / `git commit` 曾多次返回
> `Execution Cancelled: Permission request timed out`（工具明确标注**非用户拒绝**），
> 而 `git status` / `git add` 正常；重试后提交成功。不影响代码与验证结论。

---

## 8. 是否请求进入下一步

按 `PROJECT2-MASTER-PLAN.md` 校准顺序，Step 3 之后是 **`T-1`（26 个孤儿类取证与清理）**，然后 `T-2`（最小回归测试基线）。

但**建议先回答 §6 的 D1 / D2**：它们与 Step 3 同属「API 数据暴露」，若确定要一并治理，可顺势并入本步的第二阶段，避免 Phase 3 重建前端后又要回头改契约。
