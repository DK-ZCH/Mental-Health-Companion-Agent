# Phase 2 / Step 5 第二阶段 批 1 交付报告：对象归属授权（8 个 `/info/{id}`）

> **范围**：批 1 = `OwnershipGuard` + 8 个 `/info/{id}` + 授权回归 + 原 14 项回归
> **状态**：✅ **已完成并通过全量回归**（22/22）
> **设计基线**：[`PHASE2-T5B-DESIGN.md`](./PHASE2-T5B-DESIGN.md)（v2 已批准）
> **明确不含**：`DataScope`、`CurrentUserProvider`、写路径（`/add`/`/update`）、密码重置（批 2）

---

## 1. 交付物

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | `src/main/java/com/security/OwnershipGuard.java` | **新增**（归属守卫，静态工具类） |
| 2 | `src/main/java/com/security/ForbiddenException.java` | **新增**（授权失败异常 → HTTP 403） |
| 3 | `src/main/java/com/handler/GlobalExceptionHandler.java` | **修改**：新增 `@ExceptionHandler(ForbiddenException.class)` → `403 + {code:403}` |
| 4–11 | 8 个 Controller 的 `/info/{id}` | **各 +2~4 行**：导入守卫 + 调用一次 |

接入点（8 个）：

```text
YonghuController#info                  → assertOwnership(request, yonghu, id, null)      // 记录 id 即学生 id
XinlilaoshiOrderController#info        → assertOwnership(request, e, e.studentId, e.counselorId)
XinlilaoshiLiuyanController#info       → assertOwnership(request, e, e.studentId, e.counselorId)
XinlilaoshiCollectionController#info   → assertOwnership(request, e, e.studentId, e.counselorId)
ExamrecordController#info              → assertOwnership(request, e, e.studentId, null)
ExamredetailsController#info           → assertOwnership(request, e, e.studentId, null)
ExamrewrongquestionController#info     → assertOwnership(request, e, e.studentId, null)
UsersController#info                   → assertAdminOnly(request)                      // 仅管理员
```

**零新增依赖**；`EIException` 未被复用（它按既有契约返回 HTTP 200，而本批需要 HTTP 403）。

---

## 2. 契约（D1 已锁定：这是**新增行为契约**）

```text
未认证                → HTTP 200 + body.code=401 + "请先登录"     ← 既有契约，本批【不改】
已认证但无权访问       → HTTP 403 + body.code=403 + "无权访问"     ← 本批【新增】
```

因为它是**新增契约**，回归**必须同时覆盖成功路径**（见 §4）——只测「越权失败」无法证明没有误伤。

---

## 3. 守卫规则（证据驱动 + fail-closed）

依据（实测）：`assessment_record` / `assessment_answer` / `assessment_wrong_question` **只有 `student_id`**；
`counseling_appointment` / `counselor_message` / `counselor_favorite` **两者都有**。

| 角色 | 判定 |
| --- | --- |
| `管理员` | **显式放行**（B 类，不再是 `else` 兜底） |
| `学生` | 目标对象 `studentId` 必须 == 当前用户，否则拒绝 |
| `心理老师` | 实体**有** `counselorId` → 必须 == 当前用户，否则拒绝；<br>实体**无** `counselorId` → **维持现状**（见 §6 未收敛项） |
| **其他 / null / 空** | **默认拒绝**（fail-closed —— 不沿用旧代码的「else 即全量」） |

额外：**记录不存在 → 一律拒绝**，避免用「200 空数据 vs 403」的差异探测 id 是否存在（存在性预言机）。

---

## 4. 验证

### 4.1 全量回归

```text
mvn -B clean test → BUILD SUCCESS
IdentityAuthorizationRegressionTest  Tests run: 8,  Failures: 0, Errors: 0   ← 批1 新增
Phase2CoreRegressionTest             Tests run: 14, Failures: 0, Errors: 0   ← Step 4 基线
合计                                 Tests run: 22, Failures: 0, Errors: 0
```

### 4.2 新增 8 项用例（含成功路径）

| # | 场景 | 期望 | 结果 |
| --- | --- | --- | --- |
| 1 | 学生读**自己**：留言/收藏/预约/测评/本人资料 | 200 + code=0 | ✅ |
| 2 | **管理员**读他人：留言/收藏/学生资料/管理端账号 | 200 + code=0 | ✅ |
| 3 | **心理老师**读自己服务对象（留言 id3 / 预约 id3） | 200 + code=0 | ✅ |
| 3 | 心理老师读**他人**服务对象（留言 id1 / 预约 id1） | **403** | ✅ |
| 4 | **6 个内容类端点**（试卷/题目/知识/通知/字典/配置） | 200 + code=0（**不受影响**） | ✅ |
| 5 | 学生读**他人**留言/收藏/资料 | **403** | ✅（第一阶段实测的越权链已堵住） |
| 6 | 学生/老师读**管理端账号** | **403** | ✅ |
| 7 | **记录不存在**（留言 999999 / 学生 999999） | **403** | ✅ |
| 8 | **无 Token** | HTTP 200 + code=**401**（既有契约不变） | ✅ |

### 4.3 反向对照（证明断言不是空跑）

| 操作 | 结果 |
| --- | --- |
| 临时把 `OwnershipGuard.deny()` 置空（守卫失效） | **4 个拒绝用例全部失败**（`expected: 403 but was: 200`）✓<br>而**成功路径用例仍通过** ✓ → 证明 403 断言精准测量守卫，而非被别的原因带过 |

对照期间的临时改动**已精确还原**（`throw new ForbiddenException()` 已回位）。

---

## 5. 未做（严格守批次边界）

| 项 | 归属 |
| --- | --- |
| `/yonghu/resetPassword`、`/xinlilaoshi/resetPassword` 越权 | **批 2** |
| `/add`、`/update` 写路径归属（9 处注释 + 部分 `/add`） | 批 3 |
| `DataScope` fail-closed、写原语 `stampOwner` / `authorizedTargetUser` | 批 4 |
| `CurrentUserProvider` 收敛 103 处 Session 读取 | 批 5 |
| `docs/` 不纳入版本控制 | 已在规则 §9.1 固化 |

---

## 6. ⚠️ 已知未收敛项（**明确登记，不凭空发明业务规则**）

| # | 项 | 原因 | 现状 |
| --- | --- | --- | --- |
| U1 | 心理老师对 `examrecord` / `examredetails` / `examrewrongquestion` 的 `/info/{id}` | 这 3 张表**没有 `counselor_id`** → 「老师能看到哪些学生的测评」在**代码与数据中都没有依据** | **维持现状（放行）**，未做教师侧收敛 |
| U2 | 心理老师对 `/yonghu/info/{id}`（学生资料） | 同上：`student` 表无老师归属字段 | **维持现状（放行）** |

> **为什么不强行拒绝**：既不能凭空创造「老师可看全部学生测评」的规则，也不能凭空创造「老师一律不可看」的规则。
> 且前端菜单（`front/front/js/config.js` 的 `centerMenu`）对学生与老师**共用**（含「考试记录」「错题表」），
> 贸然拒绝会**误伤老师端页面**。 → 待有明确业务证据后再收敛（建议批 4 一并处理）。
>
> **注意**：**学生侧规则是全量生效的** —— 第一阶段实测证实的那条越权链（a1 读 student 2/3 的留言与收藏）**已被彻底堵住** ✓

---

## 7. 待确认项

| # | 项 | 建议 |
| --- | --- | --- |
| 1 | 是否启动**批 2**（2 个 `/resetPassword` 限管理员 + DB 前后快照断言） | 建议启动 |
| 2 | U1/U2 的教师侧归属规则 | 需业务证据；建议并入批 4，或在批 2 后单独确认 |
| 3 | 是否把批 1 的代码提交推送到 GitHub | **需你明确授权**（规则 §9.1：push 属「需明确授权」操作） |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-05 | 批 1 交付：新增 `OwnershipGuard` / `ForbiddenException` + `GlobalExceptionHandler` 403 分支 + 8 个 `/info/{id}` 接入；**22/22 全绿**；反向对照 4/4 失败证明断言有效；登记 U1/U2 未收敛项与下一步待确认 |
