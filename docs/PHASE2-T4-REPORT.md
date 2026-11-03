# Phase 2 / Step 4 第二阶段（A1–A4）交付报告：全局异常处理

> **阶段**：Step 4 第二阶段（A1–A4）+ 第三阶段（异常契约回归）· **状态**：✅ **Step 4 全部完成，可关闭**
> **提交**：`6861eb4`（实现，+113 −1）+ `1bd738c`（契约用例，+127）
> **第一阶段（取证）**：[`PHASE2-T4-EXCEPTION-AUDIT.md`](./PHASE2-T4-EXCEPTION-AUDIT.md)
> **明确不做**：`511` 收敛（D3）、参数校验启用（D4）、HTTP 401 契约迁移（D1）

---

## 1. 交付物

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | `src/main/java/com/handler/GlobalExceptionHandler.java` | **新增**（全局异常处理器） |
| 2 | `src/main/java/com/config/InterceptorConfig.java` | **修改 1 行**：拦截器排除 `/error` |

**零新增依赖**。

---

## 2. 统一异常契约

```text
EIException        → HTTP 200 + {code: 异常自带 code, msg}   ← 沿用「HTTP 200 + body.code」既有契约
参数/请求体错误     → HTTP 400 + {code: 400, msg}
资源不存在          → HTTP 404 + {code: 404, msg}
其他 MVC 标准异常   → HTTP 其自带状态码 + {code: 状态码, msg}   ← 如 405 / 415
未捕获异常          → HTTP 500 + {code: 500, msg}
未登录（拦截器）    → HTTP 200 + {code: 401, msg}             ← 不在本类处理（D1）
```

| 场景 | 改造前 | 改造后 |
| --- | --- | --- |
| 无 Token | `200` + `code=401` | `200` + `code=401`（**不动**，D1） |
| 成功 | `200` + `code=0` | `200` + `code=0`（不变） |
| 不存在路径 | `404` + **`code=401`** 🔴 | **`404` + `code=404`** ✅ |
| 参数/请求错误 | `400` + **`code=401`** 🔴 | **`400` + `code=400`** ✅ |
| 未捕获异常 | `500` + **`code=401`** 🔴 | **`500` + `code=500`** ✅ |
| 其他标准异常 | `405` + **`code=401`** 🔴 | **`405` + `code=405`** ✅ |
| `EIException` | `500` + **`code=401`** 🔴 | **`200`** + `code=500` + **业务 msg** ✅ |

---

## 3. 实测验证（对照第一阶段验收要求）

> 方法：构建后启动 jar，逐一抓取 **HTTP 状态 + 响应体** 并配对。

| 验收要求 | 实测结果 | 判定 |
| --- | --- | --- |
| 无 Token → `HTTP 200` + `body.code=401` | `HTTP=200` `{"msg":"请先登录","code":401}` | ✅ |
| 不存在路径 → `HTTP 404` + **不再 `code=401`** | `HTTP=404` `{"msg":"请求的资源不存在","code":404}` | ✅ |
| 参数/请求错误 → `HTTP 400` + **不再 `code=401`** | `HTTP=400` `{"msg":"请求参数错误","code":400}` | ✅ |
| 未捕获异常 → `HTTP 500` + **不再 `code=401`** | `HTTP=500` `{"msg":"服务器内部错误，请联系管理员","code":500}` | ✅ |

**补充覆盖（每类尽量双触发源，避免单点验证）**：

| 场景 | 触发方式 | 实测 |
| --- | --- | --- |
| 未捕获异常（源 1） | `sidx=not_exist_col`（SQL 列不存在） | `500` + `code=500` ✅ |
| 未捕获异常（源 2） | `page=abc`（分页参数非数字） | `500` + `code=500` ✅ |
| `EIException`（源 1） | `POST /file/upload` 上传**空文件** | `200` + `{"msg":"上传文件不能为空","code":500}` ✅ |
| `EIException`（源 2） | `sidx=select`（SQL 关键词） | `200` + `{"msg":"包含非法字符","code":500}` ✅ |
| 参数缺失 | `/file/download`（缺 `fileName`） | `400` + `code=400` ✅ |
| 参数类型不符 | `/yonghu/resetPassword?id=abc` | `400` + `code=400` ✅ |
| 请求体不可读 | `POST /yonghu/update` + 非法 JSON | `400` + `code=400` ✅ |
| 方法不支持 | `GET /users/login`（应为 POST） | `405` + `code=405` ✅ **未被误报成 500** |
| 成功对照 | `/jiankangzhishi/list` | `200` + `code=0` ✅ |
| 静态资源未被误伤 | `/admin/dist/index.html`、`/front/index.html` | 均 `200` ✅ |
| 运行期日志 | 本次运行 | **ERROR = 0** ✅ |

| 回归 | 结果 |
| --- | --- |
| **T-2 全部 9 项** | ✅ `Tests run: 9, Failures: 0, Errors: 0` |

> ⚠️ 取证陷阱：用 `Select-String -Pattern "ERROR"` 查日志时会命中 handler 的 **WARN** 行（消息文本含 `JSON parse error`），**并非 ERROR 级日志**。判级别必须看级别字段。

---

## 4. 三个关键设计决策

### 4.1 为什么 `EIException` 返回 HTTP **200** 而非 4xx

`EIException` 的 `msg` 是**给用户看的业务提示**，而两套前端**只在 2xx 上显示 `msg`**（用户端 `success` 分支才 `layer.msg(result.msg)`；非 2xx 进 `error` 分支只显示「请求接口失败」且不读 body；管理端 axios 非 2xx 直接 reject）。返回 4xx 会**丢失业务提示**。

**正向副作用**：`/file/upload` 空文件原先返回 500，而用户端 `upload()` **没有 `error` 回调** → **静默失败**；现在 200 + msg → **能正常提示**。

### 4.2 为什么兜底处理器要保留 `ErrorResponse` 的状态码

若兜底一律返 500，会把 Spring MVC 自带语义的标准异常（405/415/400…）误报成服务器错误。这些异常都实现 `org.springframework.web.ErrorResponse`，故兜底先分流：

```java
if (e instanceof ErrorResponse errorResponse) {
    HttpStatusCode status = errorResponse.getStatusCode();
    logger.warn("请求处理失败 [{}]: {}", status.value(), e.getMessage());
    return ResponseEntity.status(status).body(R.error(status.value(), e.getMessage()));
}
logger.error("未捕获异常", e);
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(R.error(500, "服务器内部错误，请联系管理员"));
```

**实测印证**：`GET /users/login` → `405 + code=405`（而不是 500）✅

### 4.3 为什么 A4 只排除 `/error`，而不改 `DispatcherType`

`/error` 是 Spring Boot 内部错误分派路径，**不应参与业务鉴权** —— `.excludePathPatterns("/static/**", "/error")` 一行即解决（实测 404/400/500 的 body 均已不再是 401）。

另一可选方案是在拦截器里判断 `request.getDispatcherType() != REQUEST` 直接放行。本次**未采用**：改动面更大、会影响 FORWARD/ASYNC 等所有内部分派，超出「消除劫持」的范围。若后续出现同类内部分派问题，再评估。

---

## 5. 刻意未做的事（保持你的 D 决策）

| 项 | 决策 | 现状 |
| --- | --- | --- |
| **D1** 未登录契约（O6） | 不改，保持 `200 + code=401` | ✅ 实测未变；改动它需同步改用户端 3 处 + 管理端 1 处跳转逻辑（均在 2xx 分支内） |
| **D3** `511` 收敛 | 暂缓，先分类 | ✅ 163 处 `R.error(511, ...)` 原样保留 |
| **D4** 参数校验启用 | 不启用 | ✅ 校验注解 / `ValidatorUtils` 维持现状（仍是注释状态） |

另：`GlobalExceptionHandler` 类注释中已显式写明这三条「不做的事」，避免后续 Agent 误以为遗漏。

---

## 6. 本轮新增观察项

| # | 观察项 | 结论 |
| --- | --- | --- |
| O7 | `/file/download` **没有任何调用方** | 用户端非 vendor 代码 0 处；管理端 `download` 匹配全是 `el-icon-download` 图标、`download-excel` 组件、上传回调 `window.open(file)`（指向 `/upload/xxx`）；Excel 模板走 `href=".../upload/xxxMuBan.xls"`。→ **D5 结论：201 语义问题不修**（无调用方，无「调用方误判」证据），仅记录 |
| O8 | `com/model/enums/TypeEnum.java` **0 外部引用** | 仅自引用；注释指向已不存在的 `spring-mybatis.xml`；`application.yml` 无 `typeEnums` 配置 → **T-1 清理的漏项**（当时只扫 `entity/model` 与 `entity/vo`）。建议并入后续 cleanup，不在本步动 |
| O9 | `ConfigController` / `UsersController` 仍无 try-catch | ✅ **不再是缺口** —— 已由全局处理器兜底 |
| O10 | SQLFilter 是「剥离」而非「拒绝」 | `sidx=%27` 不报错（引号被 `StringUtils.replace` 剥离）、`sidx=select` 才抛 `EIException`。安全语义偏弱，属独立议题 |

---

## 7. 第三阶段：异常契约回归（✅ 已完成并提交 `1bd738c`）

把第二阶段的手工实测**固化为自动化用例**（并入 `Phase2CoreRegressionTest`，第 9 组）：

| 用例 | 内容 | 触发源 |
| --- | --- | --- |
| 9a | `EIException` → HTTP 200 + 异常自带 code + 业务 msg | ① 空文件上传 ② `sidx=select` |
| 9b | 参数/请求错误 → HTTP 400 + `code=400` | ① 缺参 ② 类型不符 ③ 非法 JSON |
| 9c | 资源不存在 → HTTP 404 + `code=404` | 不存在的路径 |
| 9d | 未捕获异常 → HTTP 500 + `code=500` 且不泄漏内部细节 | ① 排序列不存在 ② 分页参数非数字 |
| 9e | 标准异常保留自带状态码 + `/error` 不再被劫持 | `GET /users/login`（405）、直接访问 `/error` |

**断言语义**：同时断言 **HTTP 状态** 与 **body.code** 且要求**精确匹配** ——
只断言其一，都可能漏掉「HTTP 状态正确但 body 被劫持」这类缺陷（**改造前正是这种组合**）。

### 7.1 反向对照（证明断言不是空跑）

| # | 对照操作 | 结果 |
| --- | --- | --- |
| 1 | 临时禁用 `@RestControllerAdvice` | 5 项中 **4 项失败**（9b/9c/9d/9e）✓；**第 8 项鉴权用例仍通过** —— 证明「401 契约」与「异常处理器」相互独立 ✓ |
| 2 | 定向移除 `/error` 排除项 | **9e 失败**（先命中防空跑断言，因响应体形态被劫持改变）✓ |

对照期间的两处临时改动**已精确还原**（由 `git status` 干净佐证，提交 `1bd738c` 仅含测试文件）。

### 7.2 已知测试环境限制（需在后续用例中注意）

**MockMvc 不像真实容器那样填充 `request.getServletPath()`**，因此 `AuthorizationInterceptor` 中的
**3 条硬编码路径白名单**在 MockMvc 下**不生效**：

```text
/dictionary/page、/file/upload、/yonghu/register
```

（注解式免鉴权 `@IgnoreAuth` 不受影响 —— 只有这个基于 `getServletPath()` 字符串比较的白名单会受影响。）

> 这是**测试环境差异，不是生产缺陷**：生产环境已由真实 HTTP 实测确认这 3 条路径无 Token 可访问。
> 因此 9a 的 multipart 用例**显式带上 Token**，使其只测「EIException 契约」而与环境无关。

### 7.3 最终回归

```text
mvn -B clean test
→ BUILD SUCCESS
→ Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

> **Step 4 三阶段全部完成，可关闭。** 后续独立项：`511` 语义分类（D3）、参数校验启用（D4）、HTTP 401 契约迁移（D1，建议随 Phase 3 前端重建）。

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-05 | Step 4 第二阶段（A1–A4）交付：新增 `GlobalExceptionHandler` + 拦截器排除 `/error`；契约改造前后对照；4 项验收要求 + 7 项补充覆盖实测通过；T-2 9/9 回归；3 项设计决策；新增观察项 O7–O10 |
| v2 | 2026-10-05 | 第三阶段交付：异常契约用例第 9 组（5 项）固化，总计 14 项全绿；新增 §7.1 反向对照（两次，证明断言非空跑，并证明 401 契约与异常处理器相互独立）、§7.2 MockMvc `getServletPath()` 环境限制；**Step 4 关闭** |
