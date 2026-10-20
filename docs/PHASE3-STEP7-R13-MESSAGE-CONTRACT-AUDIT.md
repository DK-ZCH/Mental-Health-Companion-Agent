# Phase 3 / Step 7 / R13 —— 咨询留言写契约只读审计

> 审计日期：2026-10-20 ｜ 基线提交：`9ddb1ef`（R12 已推送，LEAD=0）
> 性质：**只读审计** —— 零代码改动 ✗、零业务写操作 ✗、零 DB 变更 ✗
> 结论先行：**系统模型是「一条留言 + 一条回复」，不是会话流；回复由管理员在管理端完成，学生端只读** ✓

---

## 0. 方法与边界

| 项 | 做法 |
| --- | --- |
| 请求方式 | 仅 `GET`；`/add` `/save` `/update` `/delete` **均未执行** ✗ |
| 登录 | 仅为取得 `Token` 调用 `/student/login`、`/counselor/login`（不产生业务数据 ✓）|
| 令牌清理 | 审计前 `auth_token` max(id)=**3** → 审计后删除 `id > 3` → 计数 **3 → 3** ✓ |
| 工作区 | 审计前后 **CLEAN** ✓ |
| 代码改动 | **0** ✓ |

---

## 1. 数据模型（决定性事实）

`counselor_message` 实际结构（`SHOW CREATE TABLE` ✓）：

```sql
id            int AUTO_INCREMENT PRIMARY KEY
counselor_id  int NULL
student_id    int NULL
content       text NULL          -- 学生的留言
sent_at       timestamp NULL
reply_content text NULL          -- 老师的回复（单条）
replied_at    timestamp NULL
created_at    timestamp NULL
-- PRIMARY KEY(id) only：无唯一索引 ✗、无外键 ✗
-- AUTO_INCREMENT = 146（现存仅 6 行 → 历史漂移，见 §7）
```

**模型结论（决定前端形态）**：

```
一条留言 = { content（学生写） + reply_content（管理员/老师写，单条） }
```

| 能力 | 是否存在 | 依据 |
| --- | :-: | --- |
| `parentId` / 楼层 / 会话 id | **无** ✗ | 表无该列；Mapper `Base_Column_List` 无 |
| 独立「会话/会话成员」表 | **无** ✗ | 仅 1 张表 |
| 已读 / 未读 / 阅读时间 | **无** ✗ | 无任何 read 字段 |
| 多条往返（聊天） | **无** ✗ | 仅一个 `reply_content` |

> ⇒ **学生端不得设计为聊天气泡 / 会话列表 / 未读红点**；只能做「我的留言 + 针对每条留言的单条回复」✓。

现有 6 行数据（只读）：

| id | counselor_id | student_id | content 长度 | reply 长度 | replied_at |
| :-: | :-: | :-: | :-: | :-: | --- |
| 1 | 2 | 2 | 5 | 5 | 2022-03-29 11:39:43 |
| 2 | 1 | 1 | 5 | 5 | **2026-10-20 12:02:29** ⚠️（见 §6 `DEF-REGRESSION-TEST-DATA-MUTATION`）|
| 3 | 1 | 3 | 5 | 5 | 2022-03-29 11:39:43 |
| 4 | 2 | 2 | 5 | 5 | 2022-03-29 11:39:43 |
| 5 | 3 | 3 | 5 | 5 | 2022-03-29 11:39:43 |
| 6 | 1 | 1 | 6 | 11 | 2022-03-29 13:08:11 |

---

## 2. 端点契约（代码取证）

| 端点 | 认证 | 归属校验 | 读范围 | 结论 |
| --- | --- | --- | --- | --- |
| `GET /counselorMessage/list` | 需登录 ✓（匿名 `code=401` ✓） | — | `DataScope.apply(studentId, counselorId)` ✓ | 学生=本人 / 老师=本人 / 管理员=全量 ✓ |
| `GET /counselorMessage/page` | 同上 ✓ | — | 同上 ✓ | 与 `/list` 同源 ✓ |
| `GET /counselorMessage/info/{id}` | 需登录 ✓ | **`OwnershipGuard.assertOwnership`** ✓ | — | 学生他人 → **403** ✓；不存在 → 403 ✓（防存在性探测）|
| `GET /counselorMessage/detail/{id}` | 需登录 ✓ | **无守卫** ✗ | — | **=`SEC-DETAIL-IDOR` P1（已在册）** ✗ |
| `POST /add` | 需登录 ✓ | `resolveWriteOwner(ADD_MESSAGE)` → **`studentId` 强制本人** ✓ | — | `counselorId` **采信客户端且不校验存在** ✗ |
| `POST /save` | 需登录 ✓ | **无 `assertAdminOnly`** ⚠️（按角色分支：学生强制 studentId / 老师强制 counselorId / 管理员保留） | — | 非管理员亦可达 ⚠️ |
| `POST /update` | 需登录 ✓ | `assertWritableTarget`（学生=本人 ✓ / **老师=放行** ⚠️ 既有「批 4」挂账） | — | **`repliedAt` 无条件置 now** ✗ |
| `POST /delete` | 需登录 ✓ | **无任何归属校验** ✗ | — | `removeByIds` 直删 ⇒ 可删他人 ✗ |
| `POST /batchInsert` | 需登录 ✓ | 无 | — | 遗留；读服务器 `static/upload/*.xls`，**未评估** ✗ |

**可绑定字段（`@RequestBody Entity` 整体采信）**：`counselorId` `studentId` `content` `sentAt` `replyContent` `repliedAt` `createdAt`
**服务端覆盖**：`/add` → `sentAt`/`createdAt`=now、`studentId`=本人（学生）；`/update` → `repliedAt`=now、`studentId`=本人（学生）

---

## 3. 实测矩阵（全部只读 GET ✓）

| # | 请求 | 实际返回 | 判定 |
| :-: | --- | --- | --- |
| 1 | 匿名 `/list` | HTTP 200 · `code=401` | 需登录 ✓ |
| 2 | 学生 a1 `/list` | `total=2` · `ids=6,2` | = DB `student_id=1` ✓✓ |
| 3 | 学生 a1 `/page` | `total=2` · `ids=6,2` | 与 `/list` 一致 ✓ |
| 4 | a1 `/list?studentId=2`（伪造） | 仍 `total=2` · `ids=6,2` | 客户端伪造被 **DataScope 覆盖** ✓ |
| 5 | a1 `/list?counselorId=2` | `total=0` | 教师侧条件 **AND** 生效 ✓ |
| 6 | 学生 a2 `/list` | `total=2` · `ids=4,1` | = DB `student_id=2` ✓✓ |
| 7 | a1 `/info/1`（属 student 2） | **HTTP 403 · `code=403`** | 水平越权被拦 ✓ |
| 8 | a1 `/info/2`（属 student 1） | HTTP 200 · `code=0` | 本人放行 ✓ |
| 9 | a1 `/detail/1`（属 student 2） | **HTTP 200 · `code=0` + 他人数据** | **IDOR 复现** ✗（既有 P1）|
| 10 | 老师 a1 `/list` | `total=3` · `ids=6,3,2` | = DB `counselor_id=1` ✓✓ |
| 11 | 老师 a1 `/info/1`（属 counselor 2） | **HTTP 403** | 老师侧读归属 ✓ |
| 12 | 老师 a1 `/info/2`（属 counselor 1） | HTTP 200 | 本人服务对象 ✓ |
| 13 | 匿名 `/counselor/list?page=1&limit=2` | `code=0` · `total=3` | 公开（`@IgnoreAuth`）⇒ 可用于“选择老师” ✓ |

**响应字段（`/list` 与 `/detail` 相同）**：

```text
id, counselorId, studentId, content, sentAt, replyContent, repliedAt, createdAt,
studentName, studentPhone, studentEmail, studentAvatarUrl,
counselorName, counselorPhone, counselorEmail, counselorAvatarUrl,
counselorExpertise, counselorResume, counselorIntroduction
```

- **含 PII** ⚠️：`studentPhone` / `studentEmail` / `counselorPhone` / `counselorEmail`
- **不含** `studentIdCardNo` ✓（Mapper `Base_Column_List` 有 `student.id_card_no`，但 `CounselorMessageView` 无该属性 → **未输出**，实测未出现 ✓）
- ⇒ 前端**只渲染** `content` / `sentAt` / `replyContent` / `repliedAt` / `counselorName`，**不渲染任何 phone/email/resume** ✓

---

## 4. 「回复」的真实归属（关键产品结论）

| 证据 | 内容 |
| --- | --- |
| Phase 2 / Step 5 批 4-B **Q1 取证结论** | 「**老师对业务数据没有写入口**（回复留言由管理员在管理端完成）」 |
| 管理端 `admin/views/modules/xinlilaoshiLiuyan/add-or-update.vue:75` | 「回复内容」为**可编辑表单项**，且 `:147` 校验 **`required: true`（回复内容不能为空）** |
| 管理端 `list.vue:138` | 列表列 `label="回复内容" prop="replyContent"` |
| 旧用户端 `front/pages/xinlilaoshiLiuyan/add.html:110,278` | 用户端表单**只有「留言内容」**，提交 `/counselorMessage/add` |

> ⇒ **学生端 R13 不做回复 UI** ✓：回复是**管理端**职能，学生端只做「查看回复」。
> 若未来要做老师端自助回复，属于**新的权限规则 + 教师侧写授权（批 4 挂账）**，不在 R13 ✗。

---

## 5. R13 建议实现范围（**待批准后才实施** ✗）

### 5.1 页面与调用

| 项 | 设计 |
| --- | --- |
| 路由 `/me/messages` | 受保护（`meta.requiresAuth` ✓），与 `/me/appointments` 同族 ✓ |
| 列表 | `GET /counselorMessage/list`（DataScope ⇒ 仅本人 ✓）；字段只用 `content/sentAt/replyContent/repliedAt/counselorName` |
| 发送 | `POST /counselorMessage/add`，**请求体只含** `{counselorId, content}` |
| 老师选择 | `GET /counselor/list`（公开 ✓，仅渲染 `name`/`expertise`/`avatarUrl`）|
| 老师详情入口 | `CounselorDetailView.vue` 现有 `data-test="op-message"` 占位按钮 → 接通真实表单（`counselorId` 预置）|
| 回复展示 | 只读；`replyContent` 为空 → 显示「暂无回复」 |

### 5.2 文件清单（预计 5 个）

```text
A  frontend/src/api/message.ts              listMessages() / addMessage()
A  frontend/src/views/MyMessagesView.vue    /me/messages
M  frontend/src/router/index.ts             新增路由
M  frontend/src/layouts/PublicLayout.vue    导航项「我的留言」
M  frontend/src/views/CounselorDetailView.vue  替代 action('留言') 占位
```

### 5.3 明确不做 ✗

`/delete`（IDOR）· `/detail`（IDOR）· `/info`（本批不需要）· 回复 UI · 已读/未读 · 会话流 · 后端任何改动 · `npm run build` · Admin 端 · SEC-*/G3 修复

### 5.4 本批**唯一写操作**

```text
GET  /me/messages        → 只读
POST /counselorMessage/add  → 1 条真实留言（仅 counselorId + content）
```

⇒ **需要你单独批准「允许 1 次真实写入 + 事后物理清理」**（删除该行、行数恢复 6、登记 `AUTO_INCREMENT` 漂移）。

### 5.5 验收草案（A–H）

| 项 | 内容 |
| :-: | --- |
| A | 静态：`views` 中 0 手写 `Token`/`axios`；0 `mock`；**不出现** `/detail` `/delete` `/save` `/info`；不渲染 phone/email |
| B | 未登录 `/me/messages` → `/login?redirect=/me/messages` ✓ |
| C | 已登录列表 = **本人 2 条**（a1 → id 6、2 ✓，非 Mock）|
| D | 回复展示：id 6 有 `replyContent`（11 字符）→ 正常显示；无回复 → 「暂无回复」 |
| E | 从老师详情提交留言 → 成功提示 → `/me/messages` 出现新行（`counselorId`=该老师）|
| F | 清理：删除新增行 → 行数 6 ✓；`AUTO_INCREMENT` 漂移登记（146 → 147）|
| G | `vue-tsc --noEmit` = 0 · Console 0 error · **47/47**（⚠️ 但见 `DEF-REGRESSION-TEST-DATA-MUTATION`：跑测试会改 `id=2.replied_at`）|
| H | 单一提交 · 未推送（待裁定）|

---

## 6. 登记项（**全部 Identified / Unfixed** ✓）

| ID | 级别 | 事实（证据） | 新/既有 |
| --- | :-: | --- | :-: |
| **`SEC-MESSAGE-DELETE-IDOR`** | 🔴 **P1** | `/delete` 无任何归属校验（`removeByIds` 直删）⇒ 任一登录用户可按 id 删他人留言/回复 ✗（**代码取证，未执行越权删除** ✗）| **新** |
| **`SEC-MESSAGE-REPLY-SPOOF`** | 🔴 **P1（已批准 · Identified / Unfixed）** | `/add`、`/update`、`/save` 均**采信客户端 `replyContent`** ✗ ⇒（a）自造“老师回复”（b）覆盖/清空真实回复。**已实测**：学生 a1 提交 `replyContent` → `reply_content` **落库成功** ✗✗（见 §7bis）⇒ 已从「代码允许」升级为「**实际持久化成功**」 | **新** |
| **`DEF-MESSAGE-NO-COUNSELOR-CHECK`** | 🟠 **P2** | `/add` 的 `counselorId` **不校验存在/有效** ✗（对照 R12 `/counselingAppointment/add` **有**老师存在性校验 ✓）⇒ 可产生孤儿留言（`counselorId=999999`）| **新** |
| **`DEF-MESSAGE-UPDATE-BUMPS-REPLIEDAT`** | 🟠 **P2** | `/update` **无条件** `setRepliedAt(new Date())` ✗ ⇒ 学生仅修改自己的 `content` 也会把「回复时间」改为当前时刻（回复时间不可信）| **新** |
| **`DEF-REGRESSION-TEST-DATA-MUTATION`** | ⚪ **Severity TBD** | **已证明**：`mvn test` 会对**当前连接数据库**产生持久业务写入 ✗；**尚未证明**该数据库是否属于生产环境 ⚠️ ⇒ **不把“生产数据污染”作为既定事实**。事实链：`IdentityAuthorizationRegressionTest#studentCanUpdateOwnRecord_andForgedOwnerIsIgnored` 调 `/counselorMessage/update {"id":2,"studentId":2}` → 服务端置 `replied_at=now` → **只断言归属、不恢复时间**；`surefire` 报告写入时间 `2026-10-20 12:02:29` **等于** `id=2.replied_at` ✓✓ | **新（独立处理，不并入 R13）** |
| **`DEF-MESSAGE-NO-CONTENT-VALIDATION`** | 🟡 **P3** | `content` 允许 `null`/空串 ✗ ⇒ 可产生空留言（`/add` 无 `@NotBlank`）| **新（低）** |
| `SEC-DETAIL-IDOR` | 🔴 P1 | `/counselorMessage/detail/{id}` **本次实测复现**（a1 → 他人 id=1 → 200 + 数据）| **既有（不降级）** |
| 教师侧写授权未收敛 | ⚠️ 挂账 | `assertWritableTarget` 对心理老师**放行**（代码注释：→ 批 4）| **既有（G3/批4）** |

> **本批一律不修** ✗；R13 前端**绕开** `/detail`、`/delete`、`/save`、`/info` ✓。

---

## 7. 数据现状与既有漂移（如实登记）

| 项 | 值 | 说明 |
| --- | --- | --- |
| 行数 | **6** ✓ | 审计前后一致 ✓ |
| `max(id)` | 6 | — |
| `AUTO_INCREMENT` | **146** ⚠️ | 属**环境/测试数据历史状态**，**不是业务数据完整性问题** —— MySQL **不要求** `AUTO_INCREMENT = max(id)+1`，故「6 行 / max(id)=6 / AI=146」本身可以成立 ✓。真正需记录的是：**删除新插入行后 AI 不会自动回退**（146 → 147 后保持 147）⚠️ |
| `id=2.replied_at` | **2026-10-20 12:02:29** ⚠️ | **成因已查实**：回归测试副作用（见 `DEF-REGRESSION-TEST-DATA-MUTATION`），**非种子数据**；**该库是否为生产环境尚未证明** ⚠️ |

---

## 7bis. 受控真实写入（**已执行 1 次** · 按审阅要求「正常字段 + 恶意 `replyContent`」同一请求）

请求（学生 a1 的 Token · `POST /counselorMessage/add`）：

```json
{"counselorId":1,"content":"R13 E2E test message","replyContent":"R13 forged reply"}
```

响应：`HTTP 200 · {"code":0}`

落库实测（新行 id 由 `AUTO_INCREMENT` 生成 = **146**）：

| 字段 | 值 | 判定 |
| --- | --- | :-: |
| `id` | 146 | = 预期 ✓ |
| `counselor_id` | 1 | 客户端值 ✓ |
| `student_id` | **1** | **服务端强制为当前学生** ✓（请求**未提交** `studentId`）|
| `content` | R13 E2E test message | 正常写入 ✓ |
| **`reply_content`** | **R13 forged reply** | **伪造回复被持久化** ✗✗ **核心结论** |
| `replied_at` | **NULL** | `/add` 不设置回复时间（客户端亦未提交）⚠️ |
| `sent_at` / `created_at` | 2026-10-20 12:29:20 | 服务端覆盖 ✓ |

⇒ `SEC-MESSAGE-REPLY-SPOOF` 由「代码允许提交」升级为「**学生身份实际持久化成功**」（实测 ✓）。
**定级：P1（已批准 · Identified / Unfixed）** —— 依据是「学生可伪造教师回复」= 业务信任边界被破坏且已证实可落库；**P1 是风险等级，不代表 R13 必须修** ✗（本批不修，前端只保证不制造 ✓）。

### 恢复与核验（逐字段，非仅行数）

| 步骤 | 结果 |
| --- | --- |
| 插入前 | 6 行 · `max(id)=6` · `AUTO_INCREMENT=146` · md5 `e641499e…` |
| 插入后 | 7 行 · 新行 `id=146` |
| 删除探针行 | `DELETE FROM counselor_message WHERE id>6` ✓ |
| 删除后 | 6 行 · `max(id)=6` · **md5 `e641499e…` = 插入前** ✓✓ ⇒ **原 6 行逐字段一致** |
| `AUTO_INCREMENT` | **146 → 147** ⚠️（环境状态漂移，非完整性缺陷；**不回退**）|
| `auth_token` | **3 → 2** ⚠️（见下） |

### ⚠️ 会话表侧效应（如实登记）

| 事实 | 说明 |
| --- | --- |
| 审计前 `auth_token` = **3**（id 1 `admin` / id 2 `a1·学生` / id 3 `a1·心理老师`）| 读取基线 |
| 审计登录 a1（学生）| `AuthTokenServiceImpl` 对同一 `user_id+username+table_name` 走 **`updateById`**（非插入）⇒ 覆盖 id=2 的 token 值（L65/L67 代码取证 ✓）|
| 随后按 token 值删除该行 | **连带删除了审计前就存在的 id=2 会话行** ✗ |
| 结果 | `auth_token` **3 → 2**；**原 a1 学生 token 值已被覆盖，不可还原** ⚠️ |
| 业务影响 | **留言业务数据零影响** ✓；**测试会话状态发生持久化变化**：一个既有会话（`id=2 a1·学生`）被覆盖后删除，**原 token 不可恢复** ⚠️ ⇒ **不得表述为“整个数据库无影响”** ✗ |
| 结论 | 属**测试登录副作用**，**不并入 R13 功能验收** ✗；后续测试**只使用新生成的测试会话**（`id>3` 一次性清理）✓ —— 第 2 次 UI 写入已按此执行，`auth_token` 未再产生附带影响 ✓ |

---

## 8. 未验证 / 不能验证（明确边界）

| # | 项 | 原因 |
| :-: | --- | --- |
| 1 | `/add` 实际写入行为（落库字段、`counselorId` 孤儿）| **需写操作** ⇒ 未执行 ✗（R13 实施时验证）|
| 2 | `replyContent` 伪造是否真的可落库 | **需写操作** ⇒ 仅代码取证 ✗ |
| 3 | `/update` 把 `replied_at` 置 now 的线上效果 | 已由**回归测试副作用**间接证实 ✓（id=2），但未主动发请求 ✗ |
| 4 | `/delete` 越权删除他人 | **未执行** ✗（避免破坏数据）|
| 5 | 老师写路径（回复）授权 | 未执行 ✗（既有挂账）|
| 6 | `/batchInsert` | 未评估 ✗ |

---

## 9. 回归测试对留言的覆盖现状

| 覆盖 ✓ | 未覆盖 ✗ |
| --- | --- |
| `/counselorMessage/list` 匿名 401（D2-2a）✓ | `/delete` 归属 ✗ |
| `/counselorMessage/info` 学生本人/他人、老师归属、不存在 → 403 ✓ | `/detail` 守卫 ✗（P1 已在册）|
| `/counselorMessage/update` 学生本人放行 / 他人 403 / 伪造 `studentId` 被忽略 ✓ | `replyContent` 伪造 ✗ · `replied_at` 副作用 ✗ |

> ⇒ **`47/47` 不能表述为“留言模块已验证”** ✗；它只覆盖上述三组行为 ✓。

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2 | 2026-10-20 | **证据口径修正（按审阅裁定）**：① `DEF-REGRESSION-TEST-BUMPS-REPLIEDAT` → **`DEF-REGRESSION-TEST-DATA-MUTATION`**，级别 **TBD**（**不并入 R13**），表述改为「永久改写**当前连接数据库中的业务数据**；**尚未证明该库是否属于生产环境**」；② `SEC-MESSAGE-REPLY-SPOOF` 标注 **P2（定级待实测）**，区分「代码允许提交」与「实际持久化成功」；③ `AUTO_INCREMENT=146` 明确定性为**环境状态**，非完整性缺陷 |
| v1 | 2026-10-20 | R13 只读审计：模型=「一留言一回复」无会话/无已读 ✓；学生读范围/归属实测 ✓；`/detail` IDOR 复现（既有 P1）✗；`/delete` 无守卫（新 P1）✗；`replyContent` 客户端可控（待实测定级）✗；发现**回归测试会持久改写当前库业务数据**（新 TBD）✗；`AUTO_INCREMENT=146` 环境漂移登记 ⚠️；**零代码、零业务写操作** ✓ |
