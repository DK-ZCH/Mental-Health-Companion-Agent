# Phase 4 / Step 11 · Spring AI 最小 Agent 闭环 —— 前置调研报告

> **性质**：调研（📐 **零代码 · 零 pom 改动 · 零类创建**）
> **基线**：`HEAD = origin = d30cceb` · worktree CLEAN · DB 7/7 BCrypt
> **目的**：在写设计前，先把「版本能不能用、项目能复用什么、第一个 Tool 选什么、身份怎么传」四个问题钉死在**事实**上
> **结论摘要**：🔴 **发现一处硬冲突，必须先裁定再设计**

---

## 零、结论摘要（先看这个）

| # | 调研项 | 结论 |
| :-: | --- | --- |
| **①** | 版本 | 🔴 **Spring AI 2.0.x 与 Spring Boot 3.5.16 不兼容**（官方原文：2.0.x 支持 Boot **4.0.x / 4.1.x**）。可用组合是 **Boot 3.5.16 + Spring AI 1.1.8** |
| **②** | 可复用 | ✅ `CurrentUserProvider` / `GlobalExceptionHandler` / `R` / `MockMvc` 测试基座 / 拦截器全拦 / `AssessmentRecordService` 均可复用，**无需新造 UserContext** |
| **③** | 第一个 Tool | ✅ 建议 `get_my_assessment_records`（纯查询 · 权限边界清晰 · 有真实种子数据）|
| **④** | 调用链 | ✅ `ChatClient + @Tool` 可闭环；身份用 **ToolContext**（官方保证不发给模型）传入，**不让 LLM 决定 userId** |

**因此**：Step 11 设计应以 **Spring AI 1.1.8** 为基线，而非 2.0.x。请裁定（见 §6）。

---

## 一、① 版本与依赖

### 1.1 关键冲突（官方原文）

| Spring AI 版本 | 官方声明支持的 Spring Boot | 与本项目（3.5.16）|
| --- | --- | :-: |
| **2.0.x**（含 2.0.0 / 2.0.1 / 2.0.2）| **4.0.x / 4.1.x** | ❌ **不兼容** |
| **1.1.x**（最新 1.1.8）| **3.4.x / 3.5.x** | ✅ **兼容** |

> 来源：官方 Getting Started
> - 2.0 分支原文：**“Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x.”**
> - 1.1 分支原文：**“Spring AI supports Spring Boot 3.4.x and 3.5.x.”**

**这不是「文档建议」而是「世代划分」**：
- Spring AI 1.x → Spring Framework 6.x / Boot 3.x
- Spring AI 2.x → Spring Framework 7.x / Boot 4.x

### 1.2 Maven Central 实测（非文档推断）

```text
org.springframework.ai:spring-ai-bom
  1.1.x 末位 = 1.1.8        ✅ 存在
  2.0.x      = 2.0.0 / 2.0.1 / 2.0.2   ✅ 存在
  最新       = 2.1.0-RC1

org.springframework.ai:spring-ai-starter-model-deepseek
  1.0.0 → 1.1.8   ✅ 存在（1.1.8 可用）
  2.0.0 → 2.1.0-RC1 ✅ 存在
```

> 实测命令：`repo1.maven.org/maven2/.../maven-metadata.xml`
> （注意：`search.maven.org` 的 solr 索引对 1.1+/2.x 覆盖不全，**不可作依据**）

### 1.3 若采用 1.1.8 的坐标

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>1.1.8</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-deepseek</artifactId>
</dependency>
```

**预计传递引入**：Spring Framework 6.2.x（与 Boot 3.5.16 同代）、Jackson、`spring-webflux`（DeepSeek 客户端用 `WebClient`/`Flux`）。

> ⚠️ **待验证**：`spring-webflux` 会否与现有 `spring-boot-starter-web`（Tomcat/Servlet）共存造成启动冲突。
> 通常不会（WebFlux 仅在存在 `WebClient` 时作为库使用），但**必须实测启动**。

### 1.4 DeepSeek 配置项（1.1.8 文档原文）

| 配置 | 默认 |
| --- | --- |
| `spring.ai.deepseek.api-key` | 无（必填）|
| `spring.ai.deepseek.base-url` | `https://api.deepseek.com` |
| `spring.ai.deepseek.chat.options.model` | `deepseek-chat`（可选 `deepseek-reasoner`）|
| `spring.ai.deepseek.chat.enabled` | `true` |

> 1.1.8 用 `chat.options.model`；2.0.x 改为 `chat.model`。**版本差异点**。

### 1.5 1.1.x 的 Tool Calling 机制（与 2.0 不同）

| 维度 | 1.1.x（本项目可用）| 2.0.x（需 Boot 4）|
| --- | --- | --- |
| 默认循环位置 | **`ChatModel` 内部**（`ToolCallingManager`）| `ChatClient` 的 `ToolCallingAdvisor` |
| 开关 | `ToolCallingChatOptions.internalToolExecutionEnabled`（默认 `true`）| `spring.ai.chat.client.tool-calling.enabled` |
| ChatClient 角色 | 把工具转成 `ToolCallback` 塞进 `Prompt`，**循环仍由 ChatModel 执行** | 循环上移到 advisor 链 |
| 直接调 ChatModel | ✅ **仍会自动执行工具** | ❌ **不再自动执行** |
| Advisor 方式 | `ToolCallAdvisor`（1.1 新增）| `ToolCallingAdvisor`（默认自动注册）|

> **对本项目的影响**：Step 11 只需要「一个 Tool 的同步闭环」，
> 1.1.x 的默认框架托管模式（ChatModel 内部循环）**完全够用**，
> 且不需要理解 advisor 链 —— 反而比 2.0 更简单。

---

## 二、② 项目内可复用资产（已逐项取证）

### 2.1 `pom.xml` 现状

| 项 | 事实 |
| --- | --- |
| Spring Boot | **3.5.16**（parent）|
| JDK | 17 |
| MyBatis-Plus | 3.5.8（`mybatis-plus-spring-boot3-starter`）|
| Hutool | 5.8.25 |
| **Spring AI** | ❌ **完全没有**（无 BOM、无 starter）|
| **AI 相关配置** | ❌ `application.yml` 无任何 `spring.ai.*` |

### 2.2 🔴 身份链现状（**最关键的复用点**）

```java
public final class CurrentUserProvider {           // com.security
    public static String  currentRole(HttpServletRequest request);
    public static Integer currentUserIdOrNull(HttpServletRequest request);
    public static String  currentTableName(HttpServletRequest request);
    public static Integer requireCurrentUserId(HttpServletRequest request);  // 缺失 → ForbiddenException(403)
}
```

| 事实 | 影响 |
| --- | --- |
| **静态方法 + 必须传 `HttpServletRequest`** | ⚠️ **Tool 方法内拿不到 request**（除非 `RequestContextHolder`）|
| 身份写入源唯一 | `AuthorizationInterceptor` 把 `auth_token` 的 4 个属性写入 Session |
| `requireCurrentUserId` 缺失即 403 | ✅ **天然 fail-closed**，Tool 可直接复用此语义 |

### 2.3 拦截器：`/agent/**` 自动受保护（无需额外配置）

```java
registry.addInterceptor(...)
        .addPathPatterns("/**")
        .excludePathPatterns("/static/**", "/error");
```

> ⇒ 新增 `/agent/chat` **天然要求登录**，未认证走 `HTTP 200 + code=401`（既有契约）。
> ⚠️ 同时意味着：**Agent 端点的认证语义与全站一致**，不需要新写鉴权。

### 2.4 异常与返回结构

| 组件 | 事实 |
| --- | --- |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，已统一 400/403/404/500 + `EIException` |
| `R extends HashMap<String,Object>` | `R.ok().put(k,v)` / `R.error(code,msg)` |
| 未认证 | `HTTP 200 + body.code=401`（拦截器直写，**不走 ExceptionHandler**）|
| 授权失败 | `HTTP 403 + body.code=403` |

> ⇒ Agent 端点沿用 `R` 即可，**不需要新的响应包装**。

### 2.5 测试基座

```java
@SpringBootTest
@AutoConfigureMockMvc
class IdentityAuthorizationRegressionTest { @Autowired MockMvc mockMvc; ... }
```

- 现有 **63/63**（Identity 30 · Phase2Core 17 · PasswordHasher 16）
- 风格：`MockMvc` 真实 HTTP 链路 + 真实 DB，**无 mock**

> ⚠️ **Step 11 的测试难点**：真实 LLM 调用**不适合放进回归套件**（慢 / 花钱 / 不确定）。
> 需设计「可替换的 ChatClient 边界」（见 §5.4）。

### 2.6 第一个 Tool 的数据基础（已实测）

```text
assessment_record : 1 行 → id=1, student_id=1, paper_id=1, total_score=15, submitted_at=2022-03-29
assessment_paper  : 2 行 → id=1 "考试试卷1"(100 分), id=2 "试卷2"(10 分)
```

`AssessmentRecordEntity` 字段：`id / recordNo / studentId / paperId / totalScore / submittedAt / createdAt`
`AssessmentRecordService`：仅 `queryPage(Map)` + 继承 `IService`（可用 `lambdaQuery()`）

---

## 三、③ 第一个 Tool 的选择

### 3.1 建议：`get_my_assessment_records`

| 维度 | 评估 |
| --- | --- |
| 副作用 | **零**（纯读）✅ |
| 权限边界 | **极清晰**：`student_id = 当前登录用户`，不接受任何外部 id ✅ |
| 数据可用 | 种子数据 student 1 有 1 条记录 ✅ |
| 业务叙事 | 「帮我看看我最近的心理测评记录」→ 天然贴合 Agent 场景 ✅ |
| 复用度 | 直接 `AssessmentRecordService.lambdaQuery()` + `AssessmentPaperService`（取试卷名）✅ |

### 3.2 为什么**不选**其他

| 候选 | 不选的理由 |
| --- | --- |
| `counseling_appointment`（预约）| 涉及时间/状态，易被诱导「帮我约一个」→ 越权写风险 |
| `counselor_message`（留言）| 存在 counselor 双向可见性，权限矩阵复杂 |
| 任何 `save/update/delete` | 有副作用，需人工确认机制，**第一阶段不做** |
| 「心理危机识别」类 | 安全敏感度极高，出错代价不可接受 |
| `sys_config` / `sys_dict` | 无业务价值，纯技术验证 |

### 3.3 Tool 输出形态（建议）

```json
[{"recordNo":"1648530233813","paperName":"考试试卷1","totalScore":15,"paperTotalScore":100,"submittedAt":"2022-03-29 13:03:54"}]
```

> 只返回**该用户自己的**记录；空结果返回 `[]` 而非 null（让模型能正确表述「暂无记录」）。

---

## 四、④ 调用链与身份传递（**本调研最关键的设计约束**）

### 4.1 目标链路

```text
HTTP POST /agent/chat
  ↓
AuthorizationInterceptor（已存在，自动生效）
  ↓
AgentController           ← 此处有 HttpServletRequest ✅
  ↓ CurrentUserProvider.requireCurrentUserId(request)
  ↓ 注入 ToolContext(Map.of("userId", id))
AgentService
  ↓ ChatClient.prompt().user(msg).tools(assessmentTools).toolContext(ctx).call()
DeepSeekChatModel
  ↓ ToolCallingManager（1.1.x 默认在 ChatModel 内部执行）
AssessmentTools.getMyAssessmentRecords(ToolContext ctx)   ← 从 ctx 取 userId
  ↓
AssessmentRecordService.lambdaQuery().eq("student_id", userId)
  ↓
MySQL
  ↓ ToolResponseMessage
LLM
  ↓
自然语言
```

### 4.2 身份为什么必须走 ToolContext（而不是方法参数）

| 方案 | LLM 能否看到 userId | 判定 |
| --- | --- | :-: |
| Tool 方法参数 `getRecords(Integer studentId)` | ✅ **能**（进 JSON Schema）⇒ 可被诱导篡改 | ❌ **禁止** |
| Tool 内部读 `RequestContextHolder` | ❌ | ⚠️ 可用，但**依赖同步线程假设**（未实测）|
| **ToolContext 传入** | ❌ **官方保证不发给模型** | ✅ **推荐** |

**为什么推荐 ToolContext 而不是 `RequestContextHolder`**：

```text
① ToolContext 是官方契约，语义明确「不经过模型」
② 不依赖「tool 在 HTTP 请求线程执行」这一未验证假设
   （若未来改用 stream / 异步 / ToolCallAdvisor，RequestContextHolder 会失效）
③ userId 仍来自 CurrentUserProvider（Controller 内取）⇒ 没有新造 UserContext
④ 可测试：Tool 单测直接 new 一个 ToolContext(Map.of("userId", 1))
```

> **与用户裁定的关系**：用户要求「优先复用 `CurrentUserProvider`，不为展示 Spring AI 特性增加机制」。
> 本方案**两者都满足** —— 身份仍只有 `CurrentUserProvider` 一个来源，
> `ToolContext` 只是把 Controller 已取到的值传给 Tool 的**通道**，不是新的身份机制。

### 4.3 兜底设计（Tool 内 fail-closed）

```text
ctx == null 或 ctx.get("userId") 不是 Integer
  → 不查库，直接返回结构化错误（"无法确认当前用户身份"）
  → 绝不使用「默认 userId」或「查询全部」
```

> 这与 `requireCurrentUserId` 的 fail-closed 语义一致，但**不抛异常**（避免 Tool 异常回喂模型时信息泄露）。

### 4.4 🔴 待实测（调研无法确认）

| 假设 | 风险 | 验证方式 |
| --- | --- | --- |
| 1.1.8 与 Boot 3.5.16 实际可启动 | 🟠 中 | 加依赖后 `mvn test` + 启动 |
| `spring-webflux` 传递依赖不破坏 Tomcat | 🟠 中 | 启动 + 现有 63/63 |
| `WebMvcConfigurationSupport` 不影响 Spring AI 自动配置 | 🟠 中 | 启动后 `ChatClient` Bean 是否存在 |
| DeepSeek 实际支持 tool calling（`deepseek-chat`）| 🟠 中 | 真实 API 调用 1 次 |
| `ToolContext` 包名 `org.springframework.ai.chat.model.ToolContext` | 🟡 低 | IDE 编译 |
| 1.1.8 的 `@Tool` 方法是否支持 `ToolContext` 参数 | 🟡 低 | 文档已确认，编译验证 |

---

## 五、设计前必须明确的边界

### 5.1 本批**做**

```text
✅ 新增 spring-ai-bom(1.1.8) + spring-ai-starter-model-deepseek
✅ application.yml 增加 spring.ai.deepseek.*（api-key 走环境变量）
✅ 新增 ChatClient Bean（配置类）
✅ 新增 AgentController（POST /agent/chat）
✅ 新增 AgentService（编排）
✅ 新增 AssessmentTools（1 个 @Tool）
✅ 新增 Prompt 常量（System Prompt，限定职责与拒答边界）
✅ 测试：Tool 单测（不走 LLM）+ Controller 集成测试（可替换 ChatClient）
```

### 5.2 本批**不做**

```text
❌ RAG / 向量库 / Embedding
❌ ChatMemory / 多轮会话持久化
❌ 多 Tool / Tool 编排 / 并行工具
❌ 写操作类 Tool（预约、留言、保存）
❌ 前端页面（Step 8 之后再接）
❌ 升级 Spring Boot 到 4.x
❌ 流式响应（SSE）—— 先同步
```

### 5.3 Prompt 边界（设计要求，实现时固化）

```text
角色：心理健康平台的助手
能做：查询当前登录用户自己的测评记录
不能做：诊断、开药、替代医生；不回答与平台数据无关的闲聊
不知道就说不知道，不编造记录
```

### 5.4 测试策略（关键：不让回归依赖真实 LLM）

| 层 | 方式 | 是否进 CI |
| --- | --- | --- |
| `AssessmentTools` | 纯单测：`new AssessmentTools(service)` + `new ToolContext(Map.of("userId",1))` | ✅ |
| `AgentService` | 注入**可替换的 ChatClient**（接口或 `@MockBean`），断言「工具被正确挂载 + prompt 正确」 | ✅ |
| `AgentController` | `MockMvc`：未认证 → 401；已认证 → 200（ChatClient 用测试替身） | ✅ |
| **真实 DeepSeek 调用** | **手动/一次性验证**，不写入回归套件 | ❌ 不进 CI |

> 若无法在 1.1.8 中优雅替换 ChatClient，则退化为：
> **AgentController 只做「参数 + 身份 + 委派」**，LLM 调用封装在一个 `AgentChatClient` 接口后面，
> 测试注入 Fake 实现 —— **不 mock 框架内部**。

---

## 六、🔴 待你裁定（这是本报告的核心）

### 裁定项 1：版本路线（**阻塞设计**）

| 选项 | 内容 | 代价 |
| --- | --- | :-: |
| **A（推荐）** | **Boot 3.5.16 + Spring AI 1.1.8** | 零升级；API 非最新（但 `ChatClient + @Tool + ToolContext` 用法与 2.x 基本一致）|
| B | 升级到 Boot 4.x + Spring AI 2.0.2 | 🔴 **高风险**：Spring Framework 7 世代迁移、MyBatis-Plus 3.5.8 兼容性未知、刚完成 2.2→3.5 升级 |
| C | 不引入 Spring AI，自研 HTTP + tool loop | 零框架风险，但失去框架抽象，简历叙事变弱 |

**我的建议：A。** 理由：
1. 官方明确 2.0.x 需要 Boot 4 —— 不是配置问题，是世代问题
2. 项目刚完成 Boot 2.2.2 → 3.5.16 大迁移，再升 4.x 会重演一次全量回归
3. 1.1.8 已支持 `ChatClient + @Tool + ToolContext`，Step 11 所需能力**全部具备**
4. 未来若要升 2.x，1.x 的 `@Tool` / `ToolContext` 代码**大部分可平移**

### 裁定项 2：身份传递方式

| 选项 | 说明 |
| --- | --- |
| **A（推荐）** | Controller 取 `CurrentUserProvider.requireCurrentUserId` → **ToolContext** 传入 Tool |
| B | Tool 内 `RequestContextHolder` → CurrentUserProvider |
| C | Tool 方法参数暴露 userId | ❌ 已排除（LLM 可篡改）|

### 裁定项 3：第一个 Tool

| 选项 | 说明 |
| --- | --- |
| **A（推荐）** | `get_my_assessment_records`（纯读 · 权限最清晰）|
| B | 其他查询型 Service（请指定）|

### 裁定项 4：API 密钥来源

| 选项 | 说明 |
| --- | --- |
| **A（推荐）** | 环境变量 `DEEPSEEK_API_KEY`，`application.yml` 用 `${DEEPSEEK_API_KEY:}`（缺失时不阻断启动，但 Agent 端点返回明确错误）|
| B | 写入 `application.yml`（❌ 会进 Git，禁止）|
| C | 本批不接真实 API，只做可替换替身（验收降级为「链路打通」）|

> 若选 C，则 Step 11 验收不含「真实 LLM 回答」，需你确认是否接受。

---

## 七、风险清单

| # | 风险 | 级别 | 缓解 |
| :-: | --- | :-: | --- |
| 1 | **Boot 3.5.16 与 Spring AI 1.1.8 实际不兼容** | 🟠 中 | 加依赖后**先跑 63/63 + 启动**，通过再设计代码 |
| 2 | `spring-webflux` 传递依赖影响现有 Web | 🟠 中 | 同上 |
| 3 | `WebMvcConfigurationSupport` 屏蔽 MVC 自动配置 | 🟠 中 | 启动验证 `ChatClient` Bean 存在 |
| 4 | LLM 幻觉出「不存在的测评记录」 | 🟠 中 | Tool 返回 `[]` + Prompt 明确「无记录就说无记录」 |
| 5 | 提示词注入诱导调用其他工具 | 🟡 低 | 第一阶段只挂 1 个 Tool，无破坏性工具可调 |
| 6 | 回归套件依赖真实 LLM | 🟠 中 | §5.4 可替换边界 |
| 7 | API Key 泄露进仓库 | 🔴 高 | 环境变量 + `.gitignore` 检查 + 不写入任何文档 |
| 8 | 升级 2.0.x 的诱惑导致范围失控 | 🟠 中 | 本批明确「不升 Boot 4」 |

---

## 八、调研结论

```text
① 版本：🔴 Spring AI 2.0.x 需要 Boot 4.x ⇒ 本项目只能用 1.1.8（需你裁定）
② 复用：✅ 身份/异常/返回/测试/拦截器全部可复用，无需新造
③ Tool：✅ get_my_assessment_records（纯读，数据已就绪）
④ 链路：✅ ChatClient + @Tool + ToolContext 可闭环，身份不进 LLM 视野
```

**下一步**：你裁定 §6 的 4 项后，我产出 `PHASE4-STEP11-DESIGN.md`（设计阶段仍**不写代码**）。

---

## 附：调研方法与证据来源

| 结论 | 来源 |
| --- | --- |
| 2.0.x 需 Boot 4.0/4.1 | docs.spring.io/spring-ai/reference/getting-started.html（2.0.1 分支）原文 |
| 1.1.x 支持 Boot 3.4/3.5 | docs.spring.io/spring-ai/reference/1.1/getting-started.html（1.1.8 分支）原文 |
| 1.x Tool Context 语义 | docs.spring.io/spring-ai/reference/1.1/api/tools.html |
| 2.x ToolCallingAdvisor | docs.spring.io/spring-ai/reference/api/tools.html |
| DeepSeek starter 坐标 | docs 1.1.8 §DeepSeek Chat + `repo1.maven.org` metadata 实测 |
| BOM 版本真实性 | `https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml`（实测 1.1.8 / 2.0.2 均存在）|
| 项目 pom / 配置 / 身份链 / 拦截器 / 异常 / 测试 | 直接读取仓库源文件 |
| 种子数据 | `mysql` 只读查询 |
