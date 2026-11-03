# `student` 读取链路与字段暴露审计

> **性质**：只读审计（📐 **零代码改动 · 待用户裁定**）
> **基线**：`HEAD = origin = e15f104` · LEAD 0 · worktree CLEAN
> **上游**：`PHASE4-AUTHZ-GAP-INVENTORY.md` · 用户 2026-10-25 裁定「先做 student 读取链路审计，不改代码」
> **本批定位**：确定**风险到底在哪些接口、实际泄露哪些字段**，再决定修复范围

---

## 一、结论摘要

| # | 发现 | 级别 | 性质 |
| :-: | --- | :-: | --- |
| **1** | 🔴 **`/student/detail/{id}` 完全没有授权校验** | **P1** | **既有横向越权（IDOR）**：任何登录用户可读任意学生资料 |
| **2** | 🔴 `/student/page` / `/list` 对 counselor 全量可见 | **P1** | 行级授权缺失（已登记项的具体化）|
| **3** | 🟠 `phone` / `email` 在 **6 个 View** 中系统性返回 | **P2** | **字段级**问题；前端已「不渲染」，但响应体仍含 |
| **4** | ✅ `password` / `idCardNo` 受 `@JsonIgnore` 保护 | — | **机制可靠**（Entity 层 + View 层均不序列化）|

> **最重要的发现是 #1**：它比「counselor 全量」更严重 ——
> **学生之间可以互相读取资料**，且完全不区分角色。

---

## 二、读取入口清点（5 个）

| # | 端点 | 现有授权 | 实测行为 |
| :-: | --- | --- | --- |
| 1 | `/student/page` | `DataScope.apply(..., "studentId", "counselorId")` | 学生 ✅ 1 条；**counselor 🔴 3/3 全量** |
| 2 | `/student/list` | 同上（共用 `selectListView`）| 同 `/page` |
| 3 | `/student/info/{id}` | ✅ `OwnershipGuard.assertOwnership(request, yonghu, id, null)` | 学生读他人 → **403** ✅ |
| 4 | **`/student/detail/{id}`** | ❌ **无任何授权** | **学生读他人 → 200 + 手机号** 🔴 |
| 5 | `/student/session` | 本人（`currentUserIdOrNull`）| 本人 ✅ |

### 2.1 🔴 `/detail/{id}` 的代码（问题根源）

```java
@RequestMapping("/detail/{id}")
public R detail(@PathVariable("id") Long id, HttpServletRequest request){
    StudentEntity yonghu = yonghuService.getById(id);
    if(yonghu !=null){
        StudentView view = new StudentView();
        BeanUtils.copyProperties( yonghu , view );
        dictionaryService.dictionaryConvert(view, request);
        return R.ok().put("data", view);      // ← 无任何授权判断
    }
    ...
}
```

**对比 `/info/{id}`**（同一 Controller 内）：

```java
OwnershipGuard.assertOwnership(request, yonghu, id.intValue(), null);   // ✅ 有
```

⇒ **同一张表、相邻两个端点，一个有一个没有** —— 典型的「入口遗漏」。

### 2.2 实测对照

| 请求 | `/detail/{id}` | `/info/{id}` |
| --- | :-: | :-: |
| student a1 → 自己（id=1）| 200 ✅ | 200 ✅ |
| **student a1 → 学生 2** | **🔴 200 + 返回 a2 / 17703786902** | **403** ✅ |
| **student a1 → 学生 3** | **🔴 200 + 返回 a3 / 17703786903** | — |
| counselor a1 → 学生 2 | 🔴 200 | 403（③ 后）|

> **`/detail` 是既有缺陷**（非本批引入），且**学生彼此可查** —— 横向越权。

---

## 三、字段暴露矩阵（实测原始响应）

### 3.1 `/student/info/1` 实际返回

```json
{"code":0,"data":{"id":1,"username":"a1","name":"学生姓名1",
 "phone":"17703786901","avatarUrl":"...","gender":2,
 "email":"1@qq.com","createdAt":"...","genderLabel":"女"}}
```

| 字段 | 是否返回 | 保护机制 |
| --- | :-: | --- |
| `password` | ❌ 不返回 | `@JsonIgnore`（`StudentEntity:156`）|
| `idCardNo` | ❌ 不返回 | `@JsonIgnore`（`StudentEntity:196`）|
| **`phone`** | **🔴 返回** | 无 |
| **`email`** | **🔴 返回** | 无 |
| `name` / `avatarUrl` / `gender` | 返回 | — |

### 3.2 JOIN 学生表的 6 个 View（全部含敏感字段）

| View | `studentPhone` | `studentIdCardNo` | `studentEmail` | `@JsonIgnore` 数 |
| --- | :-: | :-: | :-: | :-: |
| `AssessmentRecordView` | 有 | 有 | 有 | 2 |
| `AssessmentAnswerView` | 有 | 有 | 有 | 2 |
| `AssessmentWrongQuestionView` | 有 | 有 | 有 | 2 |
| `CounselingAppointmentView` | 有 | 有 | 有 | 未统计 |
| `CounselorFavoriteView` | 有 | 有 | 有 | 未统计 |
| `CounselorMessageView` | 有 | 有 | 有 | 未统计 |

### 3.3 实测 `/assessmentRecord/page`（student a1）

返回字段包含：

```text
studentName · studentPhone(17703786901) · studentAvatarUrl · studentEmail(1@qq.com)
```

| 字段 | 是否返回 |
| --- | :-: |
| `studentIdCardNo` | ❌ **不返回** ✅ |
| **`studentPhone`** | **🔴 返回** |
| **`studentEmail`** | **🔴 返回** |
| `studentName` | 返回（业务上通常必要）|

⇒ **`studentIdCardNo` 被 `@JsonIgnore` 挡住**（View 层确实加了注解，与实测一致）。

---

## 四、前端是否真的使用这些敏感字段

| 文件 | 事实 |
| --- | --- |
| `api/assessment.ts:5` | *「后端还返回 `studentPhone` / `studentEmail` 等敏感字段 —— 页面**不渲染**这些字段」* |
| `api/assessment.ts:45/72` | 类型注释再次标注「**不渲染** `studentPhone`/`studentEmail`」 |
| `api/message.ts:34` | *「字段白名单投影：响应含 `studentPhone`/`counselorPhone` 等（PII）✗」* |
| `api/student.ts:52` | `phone` 仅用于**学生编辑自己资料**（`MeView`）|
| `MeView.vue:46-47` | 本人资料编辑使用 `phone` / `email`（**本人场景，合理**）|

### 4.1 由此得出的结论

```text
✅ 前端已自觉做「字段白名单投影」—— 团队已知该风险
🔴 但响应体仍然传输这些字段 ⇒ DevTools / 代理 / 抓包仍可读取
✅ 因此「移除/脱敏」的**前端影响面很小**（本来就不渲染）
```

> 这意味着 **#3 的修复成本低、收益明确** —— 不涉及 UI 改动。

---

## 五、行级 vs 字段级：两个独立问题

> 这是用户 2026-10-25 特别强调的区分。

### 5.1 行级授权（能否访问这名学生）

| 端点 | 现状 | 应有 |
| --- | --- | --- |
| `/detail/{id}` | ❌ 全开放 | 至少与 `/info` 同级 |
| `/page` `/list` | counselor 全量 | 按业务规则（C1 或更严）|
| `/info/{id}` | ✅ 正确 | — |

### 5.2 字段级授权（访问后能看见什么）

| 场景 | 当前 | 建议方向 |
| --- | --- | --- |
| 学生看自己 | 全部字段（含 phone/email）| ✅ 合理 |
| counselor 看学生 | phone/email/name | **name 必要；phone/email 需按用途裁定** |
| JOIN 列表（测评/预约/留言）| **全部带出 phone/email** | **业务不需要** ⇒ 建议移除 |
| 任何人 | password / idCardNo | ✅ 已由 `@JsonIgnore` 阻断 |

> **关键区分**：`@JsonIgnore` 是**全局字段级**控制（对所有场景一刀切），
> 而「学生本人需要 phone/email、咨询师不需要」是**按场景的字段级**控制 —— 后者当前**完全不存在**。

---

## 六、待裁定（3 项）

| # | 问题 | 选项 | 备注 |
| :-: | --- | --- | --- |
| **1** | `/student/detail/{id}` 如何修 | A. 直接复用 `OwnershipGuard.assertOwnership`（与 `/info` 完全一致）<br>B. 直接删除该端点（若无调用方）<br>C. 改为管理员专用 | **A 最小风险**；需先确认前端是否调用 |
| **2** | counselor 看 `student` 的行级范围 | A. 全量（现状）<br>B. 仅「已同意预约」（复用 C1）<br>C. B + 字段级限制 | 用户已倾向 **D 为基础**（按字段用途），需先定义字段分类 |
| **3** | JOIN 列表中的 `studentPhone` / `studentEmail` | A. 保留（前端不渲染即可）<br>B. **从 View/SQL 移除**（业务不需要）<br>C. 仅管理员可见 | **B**：前端已不渲染 ⇒ 移除无 UI 影响 |

---

## 七、证据强度与未确认项

| 结论 | 强度 | 说明 |
| --- | :-: | --- |
| `/detail/{id}` 无授权、可跨学生读取 | ✅ **实测**（a1 读到 a2/a3）| 含数据值 |
| `phone`/`email` 在响应中返回 | ✅ **实测原始 JSON** | — |
| `password`/`idCardNo` 不返回 | ✅ **实测 + 代码注解双向确认** | `@JsonIgnore` |
| counselor 对 `/page` 全量 | ✅ **实测 3/3** | — |
| **前端全部调用方** | 🟡 **未穷尽** | 仅扫 `frontend/src`；老 Admin（`dist` 不可重建）未核 |
| **学生侧过滤是否生效** | ❌ **无法证伪** | 全表都属于 student 1 |
| `CounselorFavoriteView` / `CounselorMessageView` 是否也有 `@JsonIgnore` | 🟡 **未逐个确认** | 仅统计了 3 个 View 的注解数 |

---

## 八、本批不做什么

| 不做 | 原因 |
| --- | --- |
| ❌ 修改任何代码 / SQL / View | 本批为审计 |
| ❌ 直接实施字段脱敏 | 需先裁定 §六 #2/#3 |
| ❌ 删除 `/detail` 端点 | 需先确认调用方（含老 Admin）|
| ❌ 修改 DB | 零改动 |

---

## 九、当前状态

| 项 | 值 |
| --- | --- |
| HEAD / origin | `e15f104` / `e15f104` · LEAD 0 |
| worktree | **CLEAN** |
| 代码 / DB / 前端 / pom | **0 改动** |
| 实测方式 | HTTP 只读 + SQL SELECT + 代码阅读，**无写入、无残留** |
| Step 13 | 🏁 CLOSED |

**未写任何代码，等你裁定 §六 的 3 项。**
