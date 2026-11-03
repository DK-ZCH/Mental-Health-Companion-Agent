# Phase 2 / Step 4 第一阶段：异常处理现状取证报告

> **阶段**：Step 4（统一异常处理）· **第一阶段：仅取证**
> **状态**：⏳ **待拍板**（§6 的 4 项决策确认后，方可进入实现）
> **代码改动**：⛔ **零**（本阶段只做静态检索 + 运行时实测）
> **范围声明**：**未触碰** D2（匿名 `/list` 收敛）、Spring Security、Spring AI、DTO 重构
> **取证时间**：2026-10-05

---

## 0. 一句话结论

**现状不是「异常处理不统一」，而是「根本没有异常处理」** —— 全仓**零**全局异常处理器、**零**业务异常类型，所有错误靠 Controller 内的 `R.error(511, ...)`（163 处）手工返回；而**未捕获异常 / 参数错误 / 404 的响应体，会被鉴权拦截器覆写成 `{"code":401,"msg":"请先登录"}`**（实测，见 §2）。

**关于 O6**：取证结论是 —— **改为标准 HTTP 401 会真实破坏两套前端共 4 处登录跳转逻辑**（§3），因此 §5 推荐 **方案 A**：**统一异常结构、不改变未登录契约**，O6 继续记录。

---

## 1. 取证结果（对应 7 条链）

### 1.1 ① `R` / 返回结构

`com/utils/R.java`（51 行）：

| 构造 | 结果 |
| --- | --- |
| `new R()` | `code = 0`（成功） |
| `R.error()` | `code = 500`，`msg = "未知异常，请联系管理员"` |
| `R.error(String msg)` | `code = **500**`，业务提示 |
| `R.error(int code, String msg)` | 自定义 code |
| `R.ok()` / `R.ok(msg)` | `code = 0` |

**关键事实**：`R extends HashMap<String, Object>`，**不携带任何 HTTP 状态语义** —— 它只是响应体。全仓 `R.error(` 共 **189 处**：

| 形式 | 处数 | code | 备注 |
| --- | --- | --- | --- |
| `R.error(code, msg)` | **163** | 几乎全为 **`511`** | 代码生成器统一编号（"永不会进入"、"查不到数据"、"批量插入数据异常"…） |
| `R.error("msg")` | **10** | `500` | 如 `账号或密码不正确` |
| `R.error(401, ...)` | **1** | `401` | 仅 `AuthorizationInterceptor` 内 1 处 |

### 1.2 ② `AuthorizationInterceptor`（`com/interceptor/AuthorizationInterceptor.java`）

未登录时的完整行为（第 80–92 行）：

```java
PrintWriter writer = null;
response.setCharacterEncoding("UTF-8");
response.setContentType("application/json; charset=utf-8");
writer = response.getWriter();
writer.print(JSONObject.toJSONString(R.error(401, "请先登录")));
return false;
```

**两个决定性事实**：

1. **从未调用 `response.setStatus(...)`** → HTTP 状态保持默认 **200**
2. 第 91 行留着**被注释掉的历史写法**：`// throw new EIException("请先登录", 401);` —— 说明原作者原本想走「抛异常」，后改为直接写响应体。这是当前契约形态的**成因证据**

另有 **3 条硬编码放行路径**（第 40 行）：`/dictionary/page`、`/file/upload`、`/yonghu/register`。

### 1.3 ③ 全局异常处理：**不存在**

全仓检索 `@ControllerAdvice` / `@RestControllerAdvice` / `@ExceptionHandler` / `ErrorController` / `HandlerExceptionResolver` → **0 匹配**。

### 1.4 ④ Controller 层

16 个 Controller 中 **14 个**各有 **1 处** `catch (Exception)`（全仓 `catch` 共 17 处），且高度集中在 Excel 批量导入：

```java
} catch (Exception e) {
    e.printStackTrace();
    return R.error(511, "批量插入数据异常，请联系管理员");
}
```

| Controller | catch | 说明 |
| --- | --- | --- |
| `ConfigController` | **0** | 无任何错误处理 |
| `UsersController` | **0** | 管理端登录/注册，无错误处理 |
| 其余 14 个 | 1 | 单点包裹，返回 `R.error(511, ...)` |

**即：绝大多数业务方法直接把返回值拼成 `R.ok()`，没有任何异常兜底；一旦抛异常就依赖 Spring 默认错误处理。**

### 1.5 ⑤ Service / 自定义异常

| 检索项 | 结果 |
| --- | --- |
| `BusinessException` / `ServiceException` / `BizException` | **0 匹配**（不存在） |
| 唯一自定义异常 | `com/entity/EIException.java`：`extends RuntimeException`，字段 `msg` + `code`（默认 **500**） |
| `throw new ...` 全仓 | **仅 4 处**：`ValidatorUtils:34`、`SQLFilter:36`、`FileController:51`、`AuthorizationInterceptor:91`（**已注释**） |

**关键推论**：`EIException` 被抛出后**无人捕获**（无全局处理器）→ 走 Spring 默认错误处理 → **响应体被拦截器覆写**（§4.1）。

### 1.6 ⑥ 两套前端如何消费 `code` 与 HTTP status

**用户端（Layui + jQuery）** —— `front/front/modules/http/http.js`，共 **3 处**同构逻辑（`request` / `requestJson` / `upload`）：

```js
success: function(result, status, xhr) {
    if (result.code == 0)        { callback(result); }
    else if (result.code == 401) { window.parent.location.href = '../login/login.html'; }
    else                         { layer.msg(result.msg, {time: 2000, icon: 5}); }
},
error: function(xhr, status, error) {
    console.log(xhr, status, error)
    layer.msg("请求接口失败", {time: 2000, icon: 5})   // ← 不读取 body
}
```

**管理端（Vue + axios）** —— `admin/admin/src/utils/http.js`：

```js
http.interceptors.response.use(response => {
    if (response.data && response.data.code === 401) { // 401, token失效
        router.push({ name: 'login' })
    }
    return response
}, error => {
    return Promise.reject(error)
})
```

**两条关键量化事实**：

| 事实 | 数值 |
| --- | --- |
| 跳登录逻辑**位于 `success` / 2xx 分支** | ✅ 两套前端**都是**（front 3 处、admin 1 处） |
| 前端代码**读取错误分支 body** 的地方 | 用户端**0 处**（非 vendor 代码中 `xhr.responseJSON` / `response.data` 无匹配）；管理端仅上述 1 处 |
| 只判断 `code === 0` 的位置 | 管理端 **199+ 处**、用户端 **38 个页面 + http.js** |
| 判断 `code === 401` 的位置 | 管理端 **1 处**、用户端 **3 处（http.js）** |

> **即：两套前端只区分 `0`（成功）、`401`（跳登录）、**其他**（显示 `msg`）三种情形。** 这个事实是 §5「方案 A 可安全收敛 code 语义」的核心支撑。

### 1.7 ⑦ 参数校验：**形同虚设**

| 检索项 | 结果 |
| --- | --- |
| `@NotBlank` / `@NotEmpty` / `@NotNull` | 仅出现在 **13 个 Entity 的 import 行**（第 5–7 行）；**字段上 0 处应用**（`YonghuEntity` 实测 0 匹配） |
| `@Valid` | 全仓 **0 处** |
| `ValidatorUtils.validateEntity(...)` 调用 | **6 处，全部被注释**（`ConfigController` 2、`YonghuController` 1、`XinlilaoshiController` 1、`UsersController` 3） |

**结论：校验注解从未生效，参数校验实际完全缺失。** 修复它属于**新增行为**（会让原本能通过的请求开始被拒绝），**不应混入「统一异常处理」**，需单独决策（见 §6-D4）。

### 1.8 补充：HTTP 状态码的使用面

全仓**仅 1 个方法**使用 `ResponseEntity` / `HttpStatus` —— `FileController.download`（第 84–107 行），且其中：

- 下载成功返回 **`HttpStatus.CREATED`（201）** ← 用法错误（语义应为 200）
- 失败返回 `HttpStatus.INTERNAL_SERVER_ERROR`（500）

除此之外，**所有接口一律返回 `R` → HTTP 200**。

### 1.9 补充：拦截器注册方式（决定异常处理器的插入点）

`com/config/InterceptorConfig.java`（注意在 `config` 包，非 `interceptor` 包）：

```java
@Configuration
public class InterceptorConfig extends WebMvcConfigurationSupport {   // ← 关键
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getAuthorizationInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**");
    }
}
```

**`extends WebMvcConfigurationSupport`** 会**关闭 Spring Boot 的 Web MVC 自动配置**（源码注释也自认了这点：*"配置 WebMvcConfigurationSupport 之后，会导致默认配置被覆盖"*），因此静态资源需手动 `addResourceHandlers`。**这正是 §4.1「错误分派被拦截」的机制来源**。

---

## 2. 现状异常契约（运行时实测）

> 方法：启动应用，对每类请求**同时**抓取 HTTP 状态与响应体，一一配对。

| 场景 | HTTP | `body.code` | `body.msg` | 判定 |
| --- | --- | --- | --- | --- |
| G 成功（知识列表） | 200 | `0` | — | ✅ 正常 |
| A 业务错误（管理端密码错） | 200 | **`500`** | 账号或密码不正确 | ⚠️ 业务错误复用 500 |
| B 未登录（受保护接口无 Token） | **200** | `401` | 请先登录 | ⚠️ **O6：应为 HTTP 401** |
| C 未捕获异常（空文件上传） | **500** | **`401`** | **请先登录** | 🔴 **body 被劫持，语义错误** |
| D 参数缺失（`/file/download` 无参数） | **400** | **`401`** | **请先登录** | 🔴 **同上** |
| E 不存在的路径 | **404** | **`401`** | **请先登录** | 🔴 **同上** |

**HTTP 状态码本身是对的**（500/400/404 都正确），**但 body 一律撒谎说「未登录」**。

---

## 3. 核心问题的答案：改成标准 HTTP 401 会影响谁

> 你的问题：**如果把「HTTP 200 + body.code=401」改成标准 HTTP 401，究竟有哪些真实调用方会受影响？**

**答：会真实破坏 2 个文件、4 处登录跳转逻辑。** 逐条例出：

| # | 调用方 | 位置 | 现状依赖 | 改成 HTTP 401 后 |
| --- | --- | --- | --- | --- |
| 1 | 用户端 `request()` | `front/front/modules/http/http.js` | `result.code == 401` → `window.parent.location.href = '../login/login.html'`（**在 `success` 分支**） | 🔴 **失效**：HTTP 401 走 `error` 分支 → 只弹「请求接口失败」，**不再跳登录页** |
| 2 | 用户端 `requestJson()` | 同上 | 同上 | 🔴 同上 |
| 3 | 用户端 `upload()` | 同上 | 同上（该函数**没有 `error` 回调**，失败静默） | 🔴 **更差**：连「请求接口失败」都不提示 |
| 4 | 管理端 axios 拦截器 | `admin/admin/src/utils/http.js` | `response.data.code === 401` → `router.push({name:'login'})`（**在 `success` 分支**） | 🔴 **失效**：axios 对非 2xx 直接 reject → 走 `error` 分支 `Promise.reject` → **不跳登录页**，调用方拿到异常 |

**附带影响**：

| 项 | 影响 |
| --- | --- |
| T-2 回归用例 8 | ✅ **不会失败** —— 它只断言 `body.code`、**刻意不断言 HTTP 状态**（当时的设计现在正好兑现价值） |
| 其他前端代码 | ✅ 无额外影响（前端**不在错误分支读 body**，见 §1.6） |
| 改造成本 | 需同步改 **2 个文件 4 处**（用户端要把跳转逻辑**从 `success` 移到 `error`**，而非仅改数字）+ 更新用例 8 契约 |

> **结论**：改 HTTP 401 **不是「修一个错误码」，而是接口契约变更**，且必须在**同一批改动内**同步前端，否则会出现「Token 过期后页面卡住、不跳登录」的功能回归。

---

## 4. 三个额外发现（取证过程中暴露，均非本次改造引入）

### 4.1 🔴 P0：**所有错误响应的 body 被鉴权拦截器覆写为「未登录」**

**现象**（§2 的 C/D/E）：未捕获异常、参数错误、404 —— HTTP 状态正确，但 body 一律是 `{"code":401,"msg":"请先登录"}`。

**机制**（据 §1.9 配置推断，已由实测印证）：

```text
请求 → DispatcherServlet
        ↓ 抛异常 / 无处理器
异常解析器设置 HTTP 状态（500 / 400 / 404）
        ↓ 转发到 /error（ERROR 分派）
InterceptorConfig: addPathPatterns("/**")  ← 拦截器在 ERROR 分派上再次生效
        ↓ /error 无 @IgnoreAuth、不在 3 条硬编码白名单内
AuthorizationInterceptor: 无 Token → 写出 {"code":401}，return false
        ↓
BasicErrorController 被短路，Spring 默认错误体从未写出
最终 = 正确的 HTTP 状态 + 错误的 body
```

**当前真实影响面（必须说清楚，避免夸大成 P0 事故）**：

| 维度 | 影响 |
| --- | --- |
| 两套前端 | ✅ **当前不误判** —— jQuery 与 axios **都先按 HTTP 状态分发**，非 2xx 直接进 `error` 分支且**不读 body**（§1.6）→ 用户看到的是「请求接口失败」，不会误跳登录页 |
| 排障 | 🔴 **严重误导** —— 500 的响应体说「请先登录」，掩盖真实异常（本次取证差点被它误导） |
| 未来风险 | 🔴 **高** —— 用户端已出现「按 `code` 判断」的写法、管理端 axios 已是该模式；一旦某处改为按 body 判断（或 Phase 4 的 Agent / HTTP 客户端按 `code` 判断），**服务器异常会被误判为未登录并触发跳转** |
| 契约一致性 | 🔴 HTTP 状态与 body 自相矛盾 |

**这不是 O6**。O6 是「未登录给了 200」，本项是「**其他错误给了错误的 body**」——两者叠加，导致「HTTP 状态与 body.code 到底谁说了算」完全无定义。

### 4.2 🟠 P1：**参数校验形同虚设**（§1.7）

注解只 import 未使用、`@Valid` 零处、`ValidatorUtils` 6 处调用**全被注释**。

### 4.3 🟠 P1：**`code` 语义崩坏**

| code | 现状被用于 | 处数 |
| --- | --- | --- |
| `0` | 成功 | — |
| `401` | 未登录 | 1（+ 劫持全部错误） |
| `500` | ① 业务错误（`R.error("msg")`）② 默认「未知异常」 | 10 |
| `511` | **几乎所有业务错误**（代码生成器统一编号） | 163 |

同一个 `500` 既表示「密码错误」也表示「服务器异常」；`511` 是一个无标准含义的魔法数字。

---

## 5. 决策选项

### ✅ 方案 A（推荐）：**统一异常结构，不改变未登录契约**

| 动作 | 内容 | 前端影响 |
| --- | --- | --- |
| A1 | 新增 `@RestControllerAdvice` + `GlobalExceptionHandler` | 无 |
| A2 | 处理 `EIException`（业务异常，**保留其 code**）、参数类异常（`MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` / `HttpMessageNotReadableException`）、`NoResourceFoundException`(404)、兜底 `Exception` → 统一返回 **`R` 结构** | 无（这些场景前端本就进 `error` 分支） |
| A3 | **未登录保持 `HTTP 200 + body.code=401`** | **零改动** |
| A4 | 顺带**消除 §4.1 的错误 body 劫持**（异常被 advice 接管后不再走 `/error`） | 无（影响面仅限排障与未来风险） |
| A5 | 收敛 `code` 语义：`0` 成功 / `401` 未登录 / `4xx` 参数与客户端错误 / `5xx` 服务端错误；**`511` → 语义化 code** | ✅ **安全**：两套前端只区分 `0 / 401 / 其他`，其余一律只显示 `msg`（§1.6）。**前提：非 0 非 401 的响应必须保留可读 `msg`** |

**为什么推荐**：一次性消除 3 个真实问题（劫持、语义崩坏、缺兜底），而**前端改动为 0**、可回滚性最好。

**需在实现时验证**：`extends WebMvcConfigurationSupport` 的手动配置下，`ExceptionHandlerExceptionResolver` 是否正常注册并识别 `@RestControllerAdvice`（预期可以 —— 它是 `WebMvcConfigurationSupport` 的默认组件，但**必须实测**）。

### 方案 B：**改为标准 HTTP 401** + 同步改造前端

- 内容：方案 A 全部 + O6 改为 HTTP 401 + 同步改 §3 的 **2 个文件 4 处**（用户端跳转逻辑需从 `success` 移到 `error`）+ 更新用例 8
- 优点：契约最干净，符合 HTTP 语义
- 代价：动 2 个前端文件（且 `front/` 不在 Git 内，**无法 `git revert`**）；回归面扩大；与 Phase 3 前端重建存在重叠投入
- 建议时机：**与 Phase 3 Vue 3 前端重建一并做**，而非现在

### 方案 C：**只产出契约定义，不实现**

- 适配场景：希望 Step 4 保持「零行为变更」时
- 代价：§4.1 的劫持继续存在

---

## 6. 待拍板项（进入实现前必须确认）

| # | 决策项 | 选项 | 建议 |
| --- | --- | --- | --- |
| **D1** | 未登录契约（O6） | A. 保持 `HTTP 200 + code 401`／B. 改标准 HTTP 401 | **A** —— §3 证明改 B 需同步改 2 文件 4 处，收益不对称；O6 继续记录，留待 Phase 3 |
| **D2** | 是否现在引入全局异常处理器 | A. 引入（方案 A）／B. 暂不引入 | **A** —— 这是「统一异常处理」的本体，且零前端改动 |
| **D3** | `code` 语义是否收敛 | A. 收敛（`0/401/4xx/5xx`，`511` 语义化）／B. 保持现状数值 | **A**（前提：保留可读 `msg`，§5-A5 已论证安全） |
| **D4** | 参数校验是否启用（§4.2） | A. 本步不启用，仅记录／B. 本步一并启用 | **A** —— 启用会**新增拒绝行为**，属独立变更，建议单独立项（否则会与「统一异常」混在一起，回归定位困难） |

> 另需你确认：`FileController.download` 的 `HttpStatus.CREATED(201)`（§1.8）是否纳入本步顺带修正？**建议纳入**（1 行改动、无前端依赖，属明确错误）。

---

## 7. 下一步

```text
✅ Step 4 第一阶段（本报告）：取证 → 影响面 → 决策建议
⏳ 待你拍板 §6 的 D1–D4（+ 是否顺带修 201）
→ 实现（方案 A：新增 advice + handler，前端零改动）
→ 复跑 T-2 全套回归（9 项，重点：用例 8 鉴权 + 用例 7 SH-A 404）
→ 新增异常契约用例（4xx/5xx body 结构 + 无劫持）
→ 产出 Step 4 交付报告
```

**回归验证要求**（对应你的第 4 点）：T-2 的 **9 项用例必须全绿**，尤其：
- 用例 8（鉴权）—— 若 D1 选 A，应**原样通过**，不需要修改
- 用例 7（SH-A 15 个端点 404）—— 引入 advice 后 404 由 `NoResourceFoundException` 处理器接管，**状态码必须仍为 404**

---

## 8. 附：取证方法与可复现要点

| 手段 | 说明 |
| --- | --- |
| 静态检索 | 全仓 `src/main/java` 检索异常相关注解/类型/调用；前端检索限定 `front/front`、`admin/admin/src` 并排除 vendor（`layui` / `tinymce` / `elementui` / `xznstatic`） |
| 运行时实测 | 启动 jar，对 6 类请求**同时**抓 HTTP 状态与 body 并配对（避免只看单一维度而误判） |
| 关键陷阱 | ① 只看 body 会漏掉「HTTP 状态其实是对的」；② 只看状态会漏掉「body 被劫持」；③ PowerShell `Get-Content -Raw` 默认按 GBK 读取 UTF-8 响应体会产生乱码，**需按 UTF-8 读取**（本次已遇到，不影响状态码结论） |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-05 | Step 4 第一阶段取证：7 条链取证结果、现状异常契约实测表、核心问题（改 HTTP 401 的影响面）答案、3 个额外发现（P0 错误体劫持 / P1 参数校验缺失 / P1 code 语义崩坏）、3 个决策方案与 4 项待拍板项 |
