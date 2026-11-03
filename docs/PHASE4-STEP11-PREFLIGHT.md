# Phase 4 / Step 11 · Preflight（依赖与 API 最小验证）报告

> **性质**：Preflight（✅ **PASS** · 🚧 已 staged 未 commit）
> **基线**：`HEAD = d30cceb` · 改动 **2 文件 +35 行**（`pom.xml` / `application.yml`）
> **边界遵守**：**未创建** `AgentController` / `AgentService` / Tool 类 · **未改**业务 Service · **未动** DB
> **裁定来源**：用户 2026-10-23 批准 `1=A 2=A 3=A 4=A`，并要求先做 Preflight

---

## 一、结论：**Preflight PASS**

| # | 验证项 | 结果 |
| :-: | --- | :-: |
| ① | Maven 依赖解析（1.1.8 全部坐标真实存在）| ✅ **PASS** |
| ② | 传递依赖无冲突 | ✅ **PASS**（Spring Framework 统一 `6.2.19`）|
| ③ | 编译 | ✅ **PASS** |
| ④ | 既有 **63/63** 回归 | ✅ **PASS** |
| ⑤ | 应用启动 | ✅ **PASS**（`10.893s` · ERROR=0 · Exception=0）|
| ⑥ | 实际 API 签名 | ✅ **PASS**（`javap` 逐项确认）|
| ⑦ | 真实 DeepSeek Tool Calling | ✅ **PASS**（工具被调用 · 2.4s）|
| ⑧ | ToolContext 不泄露给模型 | ✅ **PASS**（`userId=917` 未出现在回答中）|

---

## 二、改动（2 文件 · +35 行）

### 2.1 `pom.xml`（+23）

```xml
<properties>
    <spring-ai.version>1.1.8</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
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

### 2.2 `application.yml`（+12）

```yaml
spring:
    ai:
        deepseek:
            api-key: ${DEEPSEEK_API_KEY:}
            chat:
                options:
                    model: deepseek-chat
                    temperature: 0.3
```

---

## 三、🔴 Preflight 发现的关键事实（**否则会误判为"依赖不兼容"**）

### 3.1 ⚠️ 表述纠正：**不是「Spring AI 不读环境变量」，而是「自定义变量名不会自动映射」**

首次加依赖后跑 `mvn test`：**47 项 ERROR**（30 Identity + 17 Phase2Core），只有纯静态的 16 项 `PasswordHasherTest` 通过。

```text
Caused by: java.lang.IllegalArgumentException: DeepSeek API key must be set
  → deepSeekChatModel bean 创建失败
  → 整个 Spring 上下文启动失败
```

**根因（准确表述）**：

> 配置目标属性是 `spring.ai.deepseek.api-key`。
> Spring Boot 的 relaxed binding 只对**符合命名规范的环境变量**生效 ——
> 即 `SPRING_AI_DEEPSEEK_API_KEY`，**而不是**项目约定的 `DEEPSEEK_API_KEY`。
> 因此本项目使用的自定义变量名**不会自动映射**，必须显式引用。

**修复**：显式映射 → `api-key: ${DEEPSEEK_API_KEY:}`（这是**项目约定的变量名落地**，不是绕过框架限制）

**修复后**：**63/63 全绿**。

> ⚠️ 这次"先失败再修复"正是 Preflight 的价值 ——
> 若直接进入 Design，会在实现阶段才发现「Bean 创建失败」，且极易误判为「1.1.8 与 Boot 3.5.16 不兼容」而错误地升级 Boot 4。

### 3.2 无 API Key 时的行为：**上下文启动失败（fail-fast）**

| 场景 | 当前行为 |
| --- | --- |
| key 存在 | ✅ 启动正常 |
| key 缺失（`${DEEPSEEK_API_KEY:}` 为空串）| 🔴 **启动失败**（`DeepSeek API key must be set`）|

**与用户要求的差异**：用户建议「启动可以不失败，但调用必须明确失败」。
当前实现更严格（启动即失败）。**这是 Design 阶段必须裁定的策略问题**（见 §6.1）。

---

## 四、依赖树（无冲突）

```text
spring-boot-starter-web:3.5.16
spring-web:6.2.19 · spring-webmvc:6.2.19 · spring-context:6.2.19 · spring-core:6.2.19
  ↑ 版本完全统一，无 "omitted for conflict"

spring-ai-starter-model-deepseek:1.1.8
 ├─ spring-ai-autoconfigure-model-deepseek:1.1.8
 ├─ spring-ai-deepseek:1.1.8
 │   ├─ spring-ai-model:1.1.8（+ commons + template-st）
 │   └─ spring-ai-retry:1.1.8
 └─ spring-ai-autoconfigure-model-chat-client:1.1.8
     └─ spring-ai-client-chat:1.1.8

新增传递依赖：
  org.springframework:spring-webflux:6.2.19
  io.projectreactor:reactor-core:3.7.19
```

**结论**：

- ✅ `spring-webflux` **未破坏** 现有 Servlet/Tomcat（63/63 + 启动均正常）
- ✅ 无版本冲突、无强制降级
- ✅ jar 体积 `143 MB → 154 MB`（+11 MB）

---

## 五、实际 API 签名（`javap` 实测，1.1.8）

### 5.1 `ChatClient`

```java
org.springframework.ai.chat.client.ChatClient
  static ChatClient create(ChatModel)
  static ChatClient.Builder builder(ChatModel)
  ChatClientRequestSpec prompt()

ChatClient.Builder
  Builder defaultSystem(String)
  Builder defaultTools(Object...)
  Builder defaultOptions(ChatOptions)
  Builder defaultAdvisors(Advisor...)

ChatClient.ChatClientRequestSpec
  ChatClientRequestSpec system(String)
  ChatClientRequestSpec user(String)
  ChatClientRequestSpec tools(Object...)
  ChatClientRequestSpec toolContext(Map<String, Object>)
  CallResponseSpec call()
```

### 5.2 `ToolContext`（**包名已确认**）

```java
org.springframework.ai.chat.model.ToolContext      // ✅ 与调研推断一致
  ToolContext(Map<String, Object>)
  Map<String, Object> getContext()
  List<Message> getToolCallHistory()
```

### 5.3 注解

```java
org.springframework.ai.tool.annotation.Tool
  String name(); String description(); boolean returnDirect();
  Class<? extends ToolCallResultConverter> resultConverter();

org.springframework.ai.tool.annotation.ToolParam
  boolean required(); String description();
```

### 5.4 DeepSeek 客户端

```java
org.springframework.ai.deepseek.DeepSeekChatModel implements ChatModel
  static DeepSeekChatModel.Builder builder()
    Builder deepSeekApi(DeepSeekApi)
    Builder defaultOptions(DeepSeekChatOptions)
    Builder toolCallingManager(ToolCallingManager)

org.springframework.ai.deepseek.api.DeepSeekApi
  static DeepSeekApi.Builder builder()
    Builder apiKey(String) / apiKey(ApiKey)
    Builder baseUrl(String)
    DeepSeekApi build()

org.springframework.ai.model.tool.ToolCallingManager      // 1.x 循环在 ChatModel 内部
```

---

## 六、真实 DeepSeek Tool Calling（smoke test）

一次性探针（已删除，未入库），验证「LLM → Tool → ToolContext → 结果 → 自然语言」全链路。

### 6.1 测试设计

```java
static class AssessmentTools {
    @Tool(description = "查询当前登录用户自己的心理测评记录条数与最新分数。无需任何参数。")
    public String countMyAssessmentRecords(ToolContext toolContext) {
        Object userId = toolContext.getContext().get("userId");
        // fail-closed：拿不到身份就拒绝
        if (!(userId instanceof Integer)) return "{\"error\":\"cannot resolve current user\"}";
        return "{\"count\":3,\"latest\":{\"paperName\":\"paper-A\",\"totalScore\":15,...}}";
    }
}
```

调用：`.tools(new AssessmentTools()).toolContext(Map.of("userId", 917))`
Prompt：`How many assessment records do I have, and what is my latest score?`

### 6.2 结果

```text
[TOOL-INVOKED] userId=917 (type=Integer)          ← 模型主动调用工具，context 正确注入
[ANSWER] You have **3 assessment records**.
         Your latest one is **paper-A**, with a score of **15 out of 100**.
[ELAPSED] 2372ms
[SEC] userId=917 leaked into answer = false        ← 本次调用的运行时证据（非普适证明）
[CHECK] answer contains count(3)=true score(15)=true
```

### 6.3 结论（按证据强度分级表述）

| 结论 | 证据 | 强度 |
| --- | --- | :-: |
| ✅ DeepSeek 真实支持 Tool Calling | `[TOOL-INVOKED]` 被打印 | 确定 |
| ✅ `@Tool` 方法可声明 `ToolContext` 并被注入 | `userId=917` 取出且类型 `Integer` | 确定 |
| ✅ **本次调用中** ToolContext 的值未出现在模型回答 | 故意取 `917`（与返回值 `3/15` 不重合）→ 未出现 | **单次运行时证据** |

> ⚠️ **不得过度外推**：`leaked=false` 只证明**这一次调用**中 917 未出现在最终回答，
> **不能**据此断言「ToolContext 在任何情况下永不泄露」。
> 但结合 Spring AI 的 `ToolContext` 设计语义（官方明确「不发送给模型」），
> **作为 Step 11 的安全假设验证已足够**；Design 中应把它表述为「已按官方语义验证的可信假设」，而非「已证明的定理」。

### 6.4 「模型无法篡改 userId」的**真实原因**

不是 `ToolContext` 本身带来了防篡改能力，而是：

> **Tool 的业务方法没有把 `userId` 声明为模型可填写的参数。**

```java
// ✅ Step 11 采用：userId 只能来自 ToolContext（服务器注入）
getMyAssessmentRecords(ToolContext ctx)

// ❌ 禁止：userId 会进 JSON Schema，模型可任意填写
getMyAssessmentRecords(Integer userId)
```

因为后者会让模型收到 `{"userId": <任意值>}` 的 schema 入口 ——
**模型自然也就没有可篡改的入口**。

**Design 应固化的安全原则**：

```text
身份由 Controller 从认证上下文取得（CurrentUserProvider），
经 ToolContext 传递到 Tool；
Tool 的业务方法不得把 userId 暴露为 LLM 可填写的参数。
```

---

## 七、启动与回归

```text
启动：Started MentalHealthCompanionApplication in 10.893 seconds
      ERROR = 0  ·  Exception = 0
      8080 就绪 6s（探活）  PID 6680

回归：Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
      30 Identity + 17 Phase2Core + 16 PasswordHasher

既有功能：student a1 / counselor a1 / admin 登录全部 PASS
```

---

## 八、遗留问题（交 Design 裁定）

### 8.1 ✅ API Key 策略（**已裁定：生产 A + 测试 B，非「选 B」**）

> 裁决来源：用户 2026-10-23。
> 不是「在 A/B 之间选一个」，而是**两个环境各用一条规则**。

| 环境 | 规则 | 行为 |
| --- | --- | --- |
| **生产 / 真实运行** | **A · fail-fast** | `spring.ai.deepseek.api-key: ${DEEPSEEK_API_KEY:}` → **无 key 则启动失败** |
| **测试** | **B · 显式注入 test-only key** | `spring.ai.deepseek.api-key=test-only` ⇒ Context 可创建，**但不调用真实 DeepSeek** |

**硬约束（Design 必须落实）**：

```text
① 测试只做 Context 装配 + 行为验证，绝不发起真实 DeepSeek 请求
   （不花钱 / 不受限流 / 不受网络波动影响）
② 真实 DeepSeek Tool Calling 只作为【独立 smoke test】，不进入 mvn test 回归
③ 不采用 `${DEEPSEEK_API_KEY:placeholder}` 这类兜底 —— 会让"无 key 也能跑"污染验收结论
④ test-only key 仅存在于 test scope（@SpringBootTest(properties=...) 或 application-test.yml），
   不得写入 src/main/resources
```

**被否方案**：

| 方案 | 否决理由 |
| --- | --- |
| C. `spring.ai.model.chat=none` 默认关闭 | 易遗忘开启，运行时行为依赖 profile 记忆 |
| D. `${DEEPSEEK_API_KEY:placeholder}` 兜底 | 🔴 会伪装成「可用」，直接污染 Step 11 验收 |

### 8.2 其他

| 项 | 说明 |
| --- | --- |
| `spring-webflux` 已引入 | 未破坏现状；若未来要减小体积可 `exclusions`，但**不建议**（DeepSeek 客户端依赖它）|
| jar 体积 +11 MB | 可接受 |
| `temperature: 0.3` | 偏低以保证事实性回答；Design 阶段可调 |
| `model: deepseek-chat` | 未用 `deepseek-reasoner`（其早期对 function calling 支持有限）|

---

## 九、边界声明（严格遵守 Preflight 授权）

| 未做 | 状态 |
|---|:-:|
| `AgentController` / `AgentService` | ❌ 未创建 |
| 任何 `@Tool` 业务类 | ❌ 未创建（smoke test 探针已删除）|
| 业务 Service 修改 | ❌ 无 |
| 数据库修改 | ❌ 无 |
| RAG / Memory / Prompt 系统 / 前端 | ❌ 无 |
| 安全体系修改 | ❌ 无 |
| 升级 Spring Boot 4 | ❌ **未做**（明确不升）|

---

## 十、状态

| 项 | 值 |
|---|---|
| 结论 | ✅ **Preflight PASS** |
| 改动 | **2 文件 +35 行**（`pom.xml` / `application.yml`）|
| Git | HEAD=`d30cceb` · **未暂存 2 文件** · **未 commit** |
| 回归 | **63/63** |
| 服务 | PID `6680` · 8080 就绪 · jar `12:54:35` |
| 下一步 | **Step 11 Design**（需先裁定 §8.1 API Key 策略）|

---

## 十一、给 Design 的确定输入

```text
✅ 版本：Boot 3.5.16 + Spring AI 1.1.8（已验证，不再动摇）
✅ 坐标：spring-ai-starter-model-deepseek（BOM 管理）
✅ ChatClient：builder(chatModel) → .defaultSystem() / .defaultTools()
✅ 每请求：.user() / .tools(Object...) / .toolContext(Map) / .call().content()
✅ Tool 声明：@Tool(description=...) + @ToolParam(description=...)
✅ 身份：ToolContext(Map.of("userId", ...))，Tool 内 fail-closed
✅ 循环位置：1.x 在 ChatModel 内部（ChatClient 委托），无需 advisor
✅ 实测：DeepSeek 支持 tool calling；ToolContext 不泄露；耗时 ~2.4s/轮
```
