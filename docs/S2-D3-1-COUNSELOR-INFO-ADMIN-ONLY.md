# S2-D3-1 · `/counselor/info/{id}` Admin-only 收口

> **性质**：实现 · **1 文件 +4 行** · **已提交 `39f336d` · 已推送 ✓**
> **基线**：`bff4abe` → `39f336d` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-D3-0-COUNSELOR-INFO-PII-AUDIT.md`](./S2-D3-0-COUNSELOR-INFO-PII-AUDIT.md)（方案 A 已批准）

---

## 0. 一句话结论

`/counselor/info/{id}` 加 1 行 `OwnershipGuard.assertAdminOnly(request)`：**student / counselor 由 200 变 403，admin 12 字段完全不变**；
47/47 回归通过；`/list` · `/detail` · `/session` · View/Entity · 前端 · DB **零改动**。

---

## 1. 改动清单（唯一文件）

| 文件 | 改动 | 行数 |
| --- | --- | --- |
| `src/main/java/com/controller/CounselorController.java` | `info()` 首行加 `OwnershipGuard.assertAdminOnly(request)` + 3 行说明注释 | **+4 / −0** |

**未改动（严格边界）**：
`/counselor/list` · `/counselor/detail/{id}` · `/counselor/session` · `CounselorView` · `CounselorEntity` · Vue3 · 老前端 · Admin · DB · 其他 Controller

---

## 2. 实际 diff

```diff
@@ -89,6 +89,10 @@ public class CounselorController {
      */
      @RequestMapping("/info/{id}")
      public R info(@PathVariable("id") Long id, HttpServletRequest request){
+        // S2-D3-1：返回老师完整实体（含 username/phone/email PII）→ 仅管理员
+        // 实测消费者：Admin 管理端 4 处（编辑页需全字段）；Vue3 / 老前端 / 后端 / 测试均 0 调用
+        // 前台老师目录走 /counselor/list，不依赖本端点
+        OwnershipGuard.assertAdminOnly(request);
         logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
         CounselorEntity xinlilaoshi = xinlilaoshiService.getById(id);
         if(xinlilaoshi !=null){
```

`git diff --stat`：`1 file changed, 4 insertions(+)`
`git diff --check`：**无空白错误** ✓

---

## 3. 回归（47/47）

```text
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0 -- IdentityAuthorizationRegressionTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0 -- Phase2CoreRegressionTest
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 4. 部署证据（真实 HTTP，非 MockMvc）

| 项 | 停旧前 | 启新后 |
| --- | --- | --- |
| PID | `50464` | **`38436`** ✓ 变更 |
| 进程启动 | 10:25:26 | 11:39:29 |
| jar 时间戳 | 10:25:21 | **11:39:25** ✓ 更新 |
| jar 大小 | 143,199,482 | 143,199,485 |
| 端口就绪 | — | 4s |

---

## 5. 四角色矩阵 `/counselor/info/{id}`（实测）

| 请求者 | 目标 | 结果 | 判定 |
| --- | --- | --- | :-: |
| **anonymous** | id=1 | HTTP 200 · `code=401`「请先登录」 | ✅ 既有认证契约不变 |
| **student(a1)** | id=1（同号） | **HTTP 403** · `{"msg":"无权访问","code":403}` | ✅ |
| **student(a1)** | id=2 | **HTTP 403** · 同上 | ✅ |
| **student(a1)** | id=3 | **HTTP 403** · 同上 | ✅ |
| **counselor(a1)** | id=1（自己） | **HTTP 403** · 同上 | ✅ 老师本人走 `/session` |
| **counselor(a1)** | id=2（他人） | **HTTP 403** · 同上 | ✅ |
| **admin** | id=1 | HTTP 200 · `code=0` · **12 字段** | ✅ **原业务行为保持** |
| **admin** | id=2 | HTTP 200 · `code=0` · **12 字段** | ✅ |

### 5.1 admin 正向验收（不只是"403 挡住了"）

```text
id, username, name, phone, avatarUrl, gender, email,
expertise, resume, introduction, createdAt, genderLabel
```

- 字段数 **12/12 齐全** ✓
- `phone` / `email` / `username` 均存在 ✓（管理端编辑页需要）
- `password` **仍不出现** ✓（`@JsonIgnore` 未被破坏）

---

## 6. 未改动端点的只读回归断言（证明无意外影响）

> 目的：验证 D3 改动**没有**波及共享代码，而非验收这些端点。

| 端点 | 请求者 | 实测 | 与 D3-0 基线对比 |
| --- | --- | --- | :-: |
| `/counselor/session` | student(a1) | HTTP 200 · `code=0` · 12 字段 · 含 phone/email | **完全一致** ✓（缺陷按原样保留）|
| `/counselor/list` | 匿名 | HTTP 200 · `code=0` · 单条 12 字段 · `phone=17703786903` | **完全一致** ✓ |
| `/counselor/detail/1` | student(a1) | HTTP 200 · `code=0` · 12 字段 | **完全一致** ✓ |
| `/counselor/page` | admin | HTTP 200 · `code=0` · 单条 12 字段 | **完全一致** ✓ |

**⇒ 4 个未改端点行为零漂移。**

---

## 7. 已闭环 / 未闭环

| 项 | 状态 |
| --- | :-: |
| **`SEC-COUNSELOR-INFO-CROSS-USER-PII`**（P2）| ✅ **本批收口**（student/counselor 403）|
| `SEC-COUNSELOR-SESSION-TABLE-ID-CONFUSION`（🔴 **P1，最高优先级**）| ❌ **未处理**（独立批次；本批仅确认无回归）|
| `SEC-COUNSELOR-DETAIL-CROSS-USER-PII`（P2）| ❌ 未处理（独立项）|
| `DEF-COUNSELOR-LIST-PUBLIC-PII`（⚠️ 高优先级待裁定，**暂不定级**）| ❌ 未处理（匿名暴露面 > info）|
| `DEF-COUNSELOR-CASCADE-FIELD-MISMATCH`（🟡）| ❌ 未处理（功能债务）|

---

## 8. 状态

| 项 | 值 |
|---|---|
| `git status --short` | **(空)** ✓ |
| `git diff --cached --check` | 无空白错误 ✓ |
| HEAD / origin | **`39f336d` / `39f336d`** · LEAD **0** ✓ |
| 回归 | **47/47** ✓ |
| 服务 | PID `38436` · 8080 就绪 ✓ |
| 提交 | **✅ `39f336d`**（`fix(security/s2-d3-1)`）· **已推送 ✓** |

---

## 9. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-D3-1 实现：`info` +1 行 `assertAdminOnly`（+4/−0，1 文件）；47/47；四角色矩阵（匿名 401 / student 403 ×3 / counselor 403 ×2 / admin 200 ×2 12 字段）；4 未改端点零漂移；jar 11:39:25 · PID 38436；**未 commit** |
