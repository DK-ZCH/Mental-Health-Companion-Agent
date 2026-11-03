# Phase 2 / Step 5 第二阶段 批 4-B 交付报告：自助资料更新授权

> **范围**：批 4 的「④⑤ 写原语统一」中 **由 Q1/Q2 答复解锁**的部分（`yonghu` / `xinlilaoshi` 资料更新）
> **状态**：✅ **已完成**（38/38 全绿 + 反向对照 + 数据库回种子）
> **业务规则来源**：需求方答复 **Q1 / Q2 / Q3**（2026-10-08）
> **前置**：[`PHASE2-T5B4A-REPORT.md`](./PHASE2-T5B4A-REPORT.md)（批 4-A）

---

## 1. Q1 取证结论：**老师对业务数据没有写入口**（全部来自代码）

| 入口 | 证据 | 结论 |
| --- | --- | --- |
| 用户端 `xinlilaoshiLiuyan/list.html` | 只调 `/xinlilaoshiLiuyan/list`（读） | 读 |
| 用户端 `xinlilaoshiLiuyan/detail.html` | 调 `xinlilaoshiLiuyanOrder/add`（下单预约） | 非留言写入 |
| 用户端 `xinlilaoshiLiuyan/add.html` | `data["studentId"] = localStorage.getItem("userId")` → `/xinlilaoshiLiuyan/add` | **学生**发起留言 |
| **管理端 `xinlilaoshiLiuyan/add-or-update.vue:75`** | `label="回复内容" prop="replyContent"` —— **必填表单项** | **「回复」由管理员在管理端完成** |

→ **「老师回复留言」在系统中没有专属入口**；业务数据（预约/留言/收藏/测评）的写路径只有「学生自助」与「管理员代操作」两类。
→ 因此 `U1/U2 教师侧写授权` 可判定为**无业务需求**（不凭空发明规则）。

## 2. Q2 业务规则（需求方答复，本批的实施依据）

| 允许自助修改 | 不允许修改（固定身份信息） |
| --- | --- |
| 头像（`avatarUrl`）、密码（`password`）；老师另可改个人描述等非固定信息（`expertise`/`resume`/`introduction`） | **学号/工号（`username`）**、**性别（`gender`）**、**学院（字段不存在，N/A）**；按「如」的同类推定，**身份证号（`idCardNo`）**一并视为固定身份信息 |

> 说明：`idCardNo` **不是 Q2 命名的四类字段之一**，而是本批的**额外安全完整性收紧**，
> 其独立依据（含回退方式）已单独记录为 **[`ADR-0001`](./ADR-0001-server-authoritative-identity-fields.md)**。
> 另一个复核事实：`getIdCardNo()` 标注 `@JsonIgnore`（`YonghuEntity:196`）→ **本就不从 JSON 绑定** →
> 服务端恢复属**纵深防御**（防将来注解被摘掉或出现非 JSON 路径），而非当下唯一的防线。

> **更正（同一次复核）**：`getPassword()` 亦为 `@JsonIgnore`（`YonghuEntity:156` / `XinlilaoshiEntity:172`）
> → `/update` **无法改密码** → §3 初版的「含 `password`」「绕开批 2」表述**不成立，已更正**（详见 §3 与 §5.3）。

**Q3 答复：不需要收紧** → 两个 A 类 `/add`（`xinlilaoshiLiuyan`、`xinlilaoshiCollection`）**保持现状**，本批未动。

---

## 3. 本批闭合的真实风险（对象级授权缺失 / IDOR）

改造前，`/yonghu/update`、`/xinlilaoshi/update`：

```java
String role = String.valueOf(...);   // ← 定义了但从不使用（原 role 分支被注释）
// 无任何授权校验；@RequestBody 实体被整体采信并 updateById
```

后果：**任一已登录账号**（学生 a1、老师 a1、…）都能 PUT 一个他人 `id` 的实体，
**篡改该对象的可绑定资料字段**（`name` / `phone` / `email` / `avatarUrl` 等）→
 = **对象级授权缺失（IDOR / BOLA）**。批 3A 曾因缺业务规则而暂缓（#13/#14），**Q2 给出了规则，故本批实施**。

> ⚠️ **⚠️ 表述更正（2026-10-08 复核，见 §5.3）**：本报告初版写作「可改他人 `password`」「等于绕开批 2」——**不准确，已更正**。
> 复核证据：`getPassword()` 标注 `@JsonIgnore`（`YonghuEntity:156`、`XinlilaoshiEntity:172`）→ 请求体中的 `password` **不会绑定** →
> 该路径**改不了密码**，也不构成对批 2 的绕过。（提交信息 `37ae1b1` 中同样沿用了初版措辞，无法改写，特此留痕。）

## 4. 实现

| 组件 | 变更 |
| --- | --- |
| `OwnershipGuard.assertSelfOrAdmin(request, selfRole, targetId, existing)` | **新增**：管理员放行；**角色匹配的本人**放行；其他一律拒绝；`existing == null`（记录不存在）也拒绝 |
| `YonghuController.update` | 接入守卫（`selfRole = 学生`）+ 自助路径**恢复** `username` / `gender` / `idCardNo` 为库中现值 |
| `XinlilaoshiController.update` | 接入守卫（`selfRole = 心理老师`）+ 自助路径**恢复** `username` / `gender` |

**为什么必须「角色匹配」**：`student.id` 与 `counselor.id` 是两套独立 id 空间 —— 若只判「id 相等」，一个 id 恰好相同的老师就能改学生资料（反之亦然）= **跨角色串位**。

**管理员路径保持现状** ✓（不削减管理端编辑能力；未提交的字段不变）。

---

## 5. 验收

```text
mvn -B clean test → BUILD SUCCESS
IdentityAuthorizationRegressionTest   24 项 ✓（新增 21–24）
Phase2CoreRegressionTest              14 项 ✓
合计                                  38/38  ✓
```

| # | 用例 | 结果 |
| --- | --- | --- |
| 21 | 学生改**自己**（伪造 `username`/`gender`/`idCardNo`） | ✅ 200，且三个固定字段**被服务端恢复**为库中现值 |
| 22 | 学生改**他人**资料（含 `password`） | ✅ **403**，且目标记录（含密码）**完全未变** |
| 23 | 老师改**自己**（含描述）✅ 200 且工号/性别被恢复；老师改**学生** ❌ **403** | ✅ |
| 24 | 管理员改学生资料 | ✅ 仍可执行（保持管理端能力） |

**数据库独立核对**（真实表名：`student` / `counselor` —— 已用 `@TableName` 确认）：

```text
student  ：1,a1,123456,2,410224199610232001 / 2,a2,... / 3,a3,...   （= 种子）
counselor：1,a1,123456,2 / 2,a2,123456,2 / 3,a3,123456,1           （= 种子）
counselor_message：m1:s2,m2:s1,m3:s3,m4:s2,m5:s3,m6:s1   auth_token：3 行
```

### 5.1 反向对照（精确隔离）

临时禁用 `assertSelfOrAdmin`（模拟改造前「无授权校验」）：

| 用例 | 禁用后 |
| --- | --- |
| 22 学生改他人 | **失败** `expected: 403 but was: 200` ✓（越权写**真的通过**） |
| 23 老师改学生 | **失败** `expected: 403 but was: 500` ✓ |
| 21 学生改自己 / 24 管理员 | **仍通过** ✓ → 断言各自精准 |

### 5.2 两个过程事故（如实记录）

| # | 事故 | 处置 |
| --- | --- | --- |
| 1 | 首次对照编辑**漏了块注释闭合 `*/`** → 编译失败，而我看的是**上一轮的旧 surefire 报告**（误以为「无失败」） | 发现后补齐 `*/`，对照才真正执行 → **教训：报告文件必须先确认是本轮产物** |
| 2 | 禁用状态下用例 22 返回 `{"code":0}`（200），但事后独立核对**资料表并无变化** | ✅ **已于 §5.3 查清并解释**（非「被拒绝」，而是空更新） |

> 另：本轮我两次用错物理表名（`users` → 实为 `admin_user`；已用 `@TableName` 核对 `student`/`counselor`）——
> 「按表名查库」必须**先从实体确认表名**，否则会得到「看起来干净」的**空跑**证据。

### 5.3 「老师改学生为什么仍失败」—— **没有任何一层拒绝**（按要求查实）

审阅意见要求说清「是谁拒绝的」。**答案：没有授权层拒绝，请求一路走到了 DAO**。
对照运行日志中的决定性证据：

```text
ERROR com.handler.GlobalExceptionHandler : 未捕获异常
org.springframework.jdbc.BadSqlGrammarException
  ### The error may involve com.dao.YonghuDao.updateById-Inline
  ### Cause: SQLSyntaxErrorException ... near 'WHERE id=1'
```

即生成的 SQL 是 **`UPDATE student SET WHERE id=1`** —— **SET 子句为空** → 语法错误 → 500。
空 SET 的原因：该用例负载是 `{"id":1,"password":"hacked-by-counselor"}`，而
`password` 因 `@JsonIgnore` **不绑定** → 实体里除 `id` 外**没有任何非空字段**。

**因此**：

| 观察 | 正确解释 |
| --- | --- |
| 用例 23 老师改学生 → **500** | ❌ **不是被拒绝**；是「空 SET」引发的 **SQL 语法错误**（当时容器内**没有其它授权机制**） |
| 用例 22 学生改他人 → **200 但库不变** | ❌ **不是被保护**；这次写**确实执行了**，但 `username` 被（仍启用的）固定字段恢复逻辑还原、`password` 不绑定 → 实际是**空更新** |

⚠️ **由此得到一个重要推论**：在不含授权的情况下，用例 22 的「DB 未变化」断言是**假阴性**——
它无法单独证明保护存在。真正有效的是 **403 断言**（本轮对照中它正确地失败了 ✓）。
这一点印证了审阅意见的判断：**「反向对照也失败」这种写法不足以说明问题，必须指明拒绝层**。

> 附带发现（登记，不在本批处理）：**任何「无任何可更新字段」的 `updateById` 都会 500**
> （`BadSqlGrammarException`）—— 这是既有鲁棒性缺陷，与授权无关。

---

## 6. 观察项

| # | 项 | 说明 |
| --- | --- | --- |
| 1 | 批 4-B 代码**尚未推送** | 按规则 §9.1 |
| 2 | 老师改他人资料现为 403 | **这是一处收紧**（原为「无校验」）。依据：批 2「重置密码限管理员」+ Q1「老师无写入口」；若业务上需要老师代改，请给出规则 |
| 3 | `idCardNo` 纳入固定字段 | **独立依据 = [`ADR-0001`](./ADR-0001-server-authoritative-identity-fields.md)**（非 Q2 命名字段）；可显式回退（见 §2 注） |
| 4 | 管理员能否改「固定身份信息」 | **未限制**（保持现状）。若管理端也需要禁止（如学号只读），请给规则 |
| 5 | ~~禁用态「200 但未落库」~~ | ✅ **已查清**（§5.3）：空更新，非「被保护」 |
| 6 | 🆕 **`password` / `idCardNo` 不可由 JSON 绑定** | `getPassword()`/`getIdCardNo()` 标注 `@JsonIgnore`（`YonghuEntity:156/196`、`XinlilaoshiEntity:172`）→ 影响风险定性（§3 已更正），并意味着**「自助改密码」不可能经 `/update` 实现** → 见 #7 |
| 7 | **「自助改密码」→ 登记为待确认业务需求**（审阅裁定） | 现有证据只能证明「`/update` **不能**改密码」，**不能**证明「系统需要**自助改密码**」→ 按「无业务证据不凭空发明规则」，**暂不实现**；如需，须先有业务证据/既有前端调用面证据 |
| 8 | **「空 `SET` → `updateById` 抛 500」→ 登记为独立技术债**（审阅裁定） | 与授权是**两个问题**（授权＝谁可以 update 谁；更新＝无字段时应如何处理）→ **不并入 4-B、不顺手修**；后续建议形态：`400 + 明确错误，绝不进入 DAO`，但须先单独取证现有前端调用与 API 语义 |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-10-08 | 批 4-B：Q1 代码取证（老师无写入口）+ Q2 规则落地（`assertSelfOrAdmin` + 固定字段恢复）+ Q3 保持现状；闭合「任一账号可改他人资料（含密码）」越权路径；**38/38 全绿**；反向对照精确隔离；如实记录 2 起过程事故 |
