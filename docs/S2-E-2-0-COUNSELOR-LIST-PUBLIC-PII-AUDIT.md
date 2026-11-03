# S2-E-2-0 · `counselor/list` 公开 PII 只读审计

> **性质**：只读取证 · **零代码 · 零 DB 变更 · 零 commit**
> **基线**：`HEAD = origin = 6da1c0b` · worktree CLEAN
> **日期**：2026-10-22
> **对象**：`DEF-COUNSELOR-LIST-PUBLIC-PII`（S2-D3-0 登记，⚠️ 高优先级待裁定）

---

## 0. 一句话结论

`/counselor/list` **匿名**返回 **12 字段全量**（含 `username` + `phone` + `email`）；
而 `counselor` 表密码为**明文 `123456`**（3/3）⇒ **匿名可枚举账号并直接登录任意老师账号**。
`phone`/`email` 属数据最小化问题（**P2**），但 `username` + 明文默认口令构成**账号接管链（P1）**。

---

## 1. Endpoint × Role 字段矩阵（真实 HTTP 实测）

| 端点 | 匿名 | student | counselor | admin | 字段数 | 关键 PII |
| --- | :-: | :-: | :-: | :-: | :-: | --- |
| **`/counselor/list`**（`@IgnoreAuth`）| **200** | 200 | 200 | 200 | **12** | 🔴 `username` `phone` `email` |
| `/counselor/page`（需登录 + DataScope）| `code=401` | 200 | 200 | 200 | **12** | 🔴 同上 |
| `/counselor/detail/{id}`（需登录）| `code=401` | 200 | 200 | 200 | **12** | 🔴 同上 |
| `/counselor/info/{id}`（S2-D3-1 admin-only）| `code=401` | **403** | **403** | 200 | 12 | 🟡 仅 admin |

### 1.1 实际 JSON 字段（4 端点完全一致）

```text
id, username, name, phone, avatarUrl, gender, email,
expertise, resume, introduction, createdAt, genderLabel
```

| 字段 | 类别 | 匿名可见（`/list`）| 判定 |
| --- | --- | :-: | --- |
| `id` | 主键 | ✓ | 必要（路由/收藏/预约）|
| `name` | 展示名 | ✓ | ✅ 公开目录必要 |
| `avatarUrl` | 头像 | ✓ | ✅ 公开目录必要 |
| `gender` / `genderLabel` | 性别 | ✓ | ✅ 公开目录必要（弱）|
| `expertise` | 擅长 | ✓ | ✅ 公开目录必要 |
| `resume` | 履历 | ✓ | ✅ 公开目录必要 |
| `introduction` | 介绍 | ✓ | ✅ 公开目录必要 |
| **`username`** | **登录账户名** | **✓** | 🔴 **不该公开**（登录枚举面）|
| **`phone`** | 手机号 PII | **✓** | 🔴 **不该公开**（非目录必要）|
| **`email`** | 邮箱 PII | **✓** | 🔴 **不该公开**（非目录必要）|
| `createdAt` | 内部管理字段 | ✓ | 🟡 无业务用途 |

**⇒ 匿名侧 3 个越界字段：`username` / `phone` / `email`（+ 1 个无用的 `createdAt`）**

### 1.2 注意：`/counselor/page` 的 DataScope 未产生隔离

student/counselor 调用 `/page` 仍返回 **total=3 全量**（与 admin 一致）。
原因：`DataScope.apply(request, params, "studentId", "counselorId")` 对 `counselor` 表无对应过滤列 ⇒ 参数不生效。
**但该端点不返回敏感信息以外的额外权限**，且已有 `/list` 匿名暴露 ⇒ 不提高整体风险，仅登记。

---

## 2. 真实调用方

### 2.1 Vue3（`frontend/src`）

| 文件 | 端点 | 使用字段 | 需要 PII？ |
| --- | --- | --- | :-: |
| `api/counselor.ts:21` `listCounselors()` | `/counselor/list` | **已做白名单投影**：`id` `name` `avatarUrl` `genderLabel` `expertise` `resume` `introduction` | ❌ **不需要** |
| `views/CounselorListView.vue` | 同上 | 0 处 `phone`/`email`/`username` | ❌ |
| `views/CounselorDetailView.vue` | 同上（G1 过渡：全表 + 前端按 id 匹配）| 同上 7 字段 | ❌ |

**⇒ Vue3 前端在 API 层已主动丢弃 PII；后端仍在传输。**

### 2.2 老前端（`src/main/resources/front`）

| 文件 | 端点 | 使用字段 | 需要 PII？ |
| --- | --- | --- | :-: |
| `xinlilaoshi/list.html` | `/counselor/list` | `name` `avatarUrl` | ❌ |
| **`xinlilaoshi/detail.html`** | `/counselor/list` | **`phone`**（L117）· `genderLabel` · **`email`**（L133）· `expertise` · `resume` | ⚠️ **模板展示 phone/email** |
| `xinlilaoshiOrder/add.html` | `/counselor/detail/{id}` | `counselorName`/`counselorPhone`/… | ❌ **坏字段**（后端无 `counselor*` 前缀字段 ⇒ 恒空）|
| `xinlilaoshi/center.html` | `/counselor/session` | 本人 | — |

> **`detail.html` 的 phone/email 来自脚手架「全字段详情页」模板**，并非产品刻意设计「公开联系方式」。产品语义判断见 §3。

### 2.3 Admin 管理端（Vue2）

| 文件 | 端点 | 使用字段 | 需要 PII？ |
| --- | --- | --- | :-: |
| `xinlilaoshi/list.vue:668` | `/counselor/page` | **`username`**（账户）· `name` · **`phone`** · `avatarUrl` · `gender` · **`email`** · `expertise` · `resume` | ✅ **需要完整** |
| `xinlilaoshi/add-or-update.vue:285` | `/counselor/info/{id}` | 全字段（编辑表单）| ✅ 需要 |
| `xinlilaoshiOrder/Liuyan/Collection` | `/counselor/info/{id}` | 仅 `counselorName`（坏字段）| ❌ 实际空 |

**⇒ 完整 DTO 的唯一真实消费者是 Admin 管理端（`/page` + `/info`）。**

### 2.4 Agent / RAG

| 检索 | 结果 |
| --- | --- |
| `src/main/java` 中 `agent`/`rag`/`embedding`/向量/知识库检索 | **0 处** |

**当前无 Agent/RAG 实现。** 若未来引入，`/counselor/list` 的公开 DTO 应作为**唯一可信边界** —— 越晚收口，未来接入时越容易把 PII 一起灌入检索语料。

---

## 3. 产品语义判定

### 3.1 `counselor` 表是「双重语义」表

| 语义 | 字段 | 证据 |
| --- | --- | --- |
| **公开咨询师目录** | `name` `avatarUrl` `gender` `expertise` `resume` `introduction` | Vue3 公开路由 `/counselors` · `/counselors/:id`（`router/index.ts:29-30` 标注「公开」）|
| **内部人员账号** | **`username`** · `password` · `phone` · `email` · `createdAt` | `/counselor/login`（L291）· `password` 字段 · Admin 编辑页 |

**⇒ 同一 Entity 承担两种语义，是「复用 Entity 导致过度暴露」的典型。**

### 3.2 phone/email 是否属于「合理公开」

- ✅ 公开目录场景**不需要**联系方式：学生浏览目录 → 通过站内「留言/预约」联系，不依赖电话/邮箱。
- ⚠️ 唯一展示处（老前端 `detail.html`）是脚手架全字段模板，非产品设计。
- **⇒ `phone`/`email` 不属必要公开字段。**

### 3.3 `username` 是否属于「合理公开」

- **绝对不属于**：`username` 是 `/counselor/login` 的登录标识，公开它等于提供**账号枚举**能力。
- 与 §4 的明文默认口令叠加 ⇒ **可直接接管账号**。

---

## 4. 🔴 关键新发现：匿名账号接管链（决定性证据）

### 4.1 三步链路（全部实测/只读 SQL）

```text
① 匿名 GET /counselor/list
     → data.list[].username = a1 / a2 / a3        ← 实测，无需任何凭证

② 密码存储形态（只读 SQL）
     counselor: 3/3 条 password = "123456"（明文，非 MD5/非 bcrypt）
     student  : 3/3 条 password = "123456"
     admin_user: 1/1 条 password = "admin"（用户名=密码）

③ 登录逻辑（CounselorController L298）
     if(xinlilaoshi==null || !xinlilaoshi.getPassword().equals(password))
     → 明文直接比对，无哈希、无锁定、无速率限制

⇒ 匿名 → 枚举 username → 用 123456 登录 → 获得老师 token
```

### 4.2 三表密码形态汇总（只读 SQL）

| 表 | 记录数 | `password='123456'` | 形似 MD5 | 形似 bcrypt | 明文 |
| --- | :-: | :-: | :-: | :-: | :-: |
| `student` | 3 | **3** | 0 | 0 | ✅ **全部明文** |
| `counselor` | 3 | **3** | 0 | 0 | ✅ **全部明文** |
| `admin_user` | 1 | 0（值为 `admin`）| 0 | 0 | ✅ **明文** |

### 4.3 归因拆分（重要）

| 造成账号接管的原因 | 归属 | 能否单独修复 |
| --- | --- | --- |
| `/counselor/list` 匿名返回 `username` | **本批对象**（`DEF-COUNSELOR-LIST-PUBLIC-PII`）| ✅ 字段投影即可切断枚举面 |
| 密码明文存储 + 默认 `123456`（三表）| **独立系统级债务**（新登记）| ✅ 但需独立批次（改哈希 = 数据迁移 + 全端登录逻辑）|

**两者各自独立成立，叠加才形成完整接管链。** 本批只应处理前者。

---

## 5. 匿名访问必要性

| 问题 | 答案 | 依据 |
| --- | --- | --- |
| 匿名浏览咨询师目录是否合理？ | ✅ **合理** | Vue3 `/counselors` 与 `/counselors/:id` 均为公开路由，未登录可浏览 |
| 是否应把 `/counselor/list` 改为必须登录？ | ❌ **不应** | 会破坏公开目录产品能力，属过度收紧 |
| 匿名应拿到什么？ | **最小字段集** | `id` `name` `avatarUrl` `gender` `genderLabel` `expertise` `resume` `introduction` |

**⇒ 正确命题是「公开接口应返回什么」，而不是「公开接口是否应存在」。**

---

## 6. 与 `SEC-COUNSELOR-DETAIL-CROSS-USER-PII` 的关系

| 端点 | 当前问题 | 是否同族 | 建议统一处理 |
| --- | --- | :-: | :-: |
| `/counselor/list` | 匿名返回 12 字段全量 | — | ✅ 改 Public DTO |
| `/counselor/detail/{id}` | **任意登录用户**可读任意 id 的 12 字段 | ✅ **完全同族**（同为 `CounselorView` 全量）| ✅ 改 Public DTO（或直接删除 —— 见下）|
| `/counselor/info/{id}` | 已 admin-only（S2-D3-1）| — | ✅ **保持不动** |
| `/counselor/page` | 需登录，12 字段（Admin 需要全量）| — | 建议 admin-only（管理端唯一消费者）|

### 6.1 `detail` 是否可直接删除

| 消费方 | 实际使用 |
| --- | --- |
| Vue3 | **0 调用**（用 `/list`）|
| 老前端 `xinlilaoshiOrder/add.html` | 调用，但绑定 `counselorPhone` 等**不存在字段** ⇒ **假消费** |
| Admin | 0 调用 |
| 测试 | 0 调用 |

**⇒ `detail` 与 S2-C2 的 `/detail` 死端点同性质，可考虑删除（但需与你确认"假消费"是否算业务依赖）。**

---

## 7. 已裁定定级（用户 2026-10-22）

### 7.1 已批准拆分

| ID | 级别 | 内容 | 裁定 |
| --- | :-: | --- | :-: |
| **`SEC-COUNSELOR-LIST-USERNAME-EXPOSURE`** | 🔴 **P1** | `/counselor/list` 匿名返回 `username`；与明文默认口令叠加 = **账号接管** | ✅ 批准 |
| `SEC-COUNSELOR-LIST-CONTACT-PII` | 🟠 **P2** | 同一端点匿名返回 `phone` + `email` | ✅ 批准（**不拔高到 P1**）|
| **`SEC-PLAINTEXT-PASSWORD-STORAGE`** | 🔴 **P1（系统级，独立批次）** | 三表密码**明文存储** + 登录明文比对 | ✅ 批准 · **命名已更正** |
| `SEC-ADMIN-DEFAULT-CREDENTIAL` | 🟡 | 默认凭据（`admin/admin` · `123456` 默认口令）| ✅ 与上项**保持区分**，不合并 |
| `DEF-COUNSELOR-LIST-CREATEDAT-EXPOSURE` | 🟡 **P3** | 匿名返回内部字段 `createdAt` | ✅ 低优先级，**不阻塞 P1/P2** |

### 7.2 原 ID 处置（已裁定）

`DEF-COUNSELOR-LIST-PUBLIC-PII` → ✅ **拆分后关闭/替换**（避免「一个 ID 混装 P1+P2」导致定级失真）。

### 7.2bis 两个关键归因原则（用户强调）

1. **不能因为 S2-E-2 的 username 泄露而把密码漏洞归因给 username。**
   `SEC-COUNSELOR-LIST-USERNAME-EXPOSURE`（枚举面）与 `SEC-PLAINTEXT-PASSWORD-STORAGE`（存储形态）**各自独立成立**，
   叠加才构成接管链；修复也分开进行。
2. **明文存储 ≠ 默认弱口令**：前者是存储形态问题（需哈希迁移），后者是凭据策略问题（`SEC-ADMIN-DEFAULT-CREDENTIAL`）。
   两者相关但不可混为一个 ID。

### 7.3 为什么不能只定 P2

- 若仅看 `phone`/`email`：P2 合理。
- 但 `username` 与**已实证的明文默认口令**叠加，已构成**可执行的账号接管路径**（匿名 → 枚举 → 登录）。
- **⇒ 必须按 P1 处理 `username` 暴露。**

---

## 8. 修复方向候选（**本批不实现**，待裁定）

| 方案 | 形态 | 影响 | 评价 |
| --- | --- | --- | --- |
| **A（推荐）** | 新增 `PublicCounselorView`（8 字段），`/list` + `/detail` 改用；`/page` + `/info` 保留全量 | 后端 2 端点返回变窄 | 精准，保留公开目录；Admin 不受影响 |
| B | `/list` 改为必须登录 | 破坏公开目录 | ❌ 与产品语义冲突 |
| C | 仅删 `username`，保留 phone/email | 切断接管链但留 PII | 🟡 不彻底 |
| D | 删除 `/counselor/detail`（死端点）+ `/list` 投影 | 最小面 | 🟡 需确认「假消费」是否算依赖 |

**Public DTO 字段建议**：`id` `name` `avatarUrl` `gender` `genderLabel` `expertise` `resume` `introduction`

**风险**：老前端 `detail.html` 的 `{{detail.phone}}` / `{{detail.email}}` 将变空（模板是脚手架产物，非产品设计）。
**密码策略**（`DEF-PLAINTEXT-DEFAULT-PASSWORD`）**独立批次**，不与本批混合（改哈希涉及数据迁移 + 三端登录逻辑）。

---

## 9. 已裁定事项（用户 2026-10-22）

| # | 问题 | 裁定 |
| :-: | --- | --- |
| 1 | 拆分定级 | ✅ **接受**（`username` → P1 · `phone/email` → P2）|
| 2 | `/counselor/detail` | ✅ **选 A：改 Public DTO，不删除**（项目原则：不为安全整改顺手删除旧业务能力）|
| 3 | 老前端 `detail.html` 失去 `phone/email` | ✅ **接受**，记录为「已识别的旧前端兼容性影响」；不为维持它继续公开联系方式 |
| 4 | `/counselor/page` 收敛 | ✅ **批准 admin-only**（anonymous 401 · student/counselor 403 · admin 200 + 12 字段）|
| 5 | 密码问题立项 | ✅ **`SEC-PLAINTEXT-PASSWORD-STORAGE` P1，单独立项**（不并入 S2-E-2-1）|

**派生结论**：`SEC-COUNSELOR-DETAIL-CROSS-USER-PII`（P2）**不再作为独立修复路线** —— 与 `/list` 同根因（`CounselorView` 被误作公开 DTO），随 Public DTO **一并收口**。
→ 设计见 [`S2-E-2-1-COUNSELOR-PUBLIC-DTO-DESIGN.md`](./S2-E-2-1-COUNSELOR-PUBLIC-DTO-DESIGN.md)

---

## 10. 状态声明

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = 6da1c0b` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 schema · 零 commit** ✓ |
| 数据影响 | **无业务数据写入**；审计期间 3 次登录用于角色矩阵，`auth_token` 前后均 **3 条**（无净增长 ✓）|
| 实测方式 | 真实 HTTP（PID 42864）· 4 端点 × 4 角色 · 只读 SQL |
| 文档 | 新增本文件 |

---

## 11. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-E-2-0 取证：4 端点 × 4 角色字段矩阵（`/list` **匿名 12 字段**）；真实调用方（Vue3 已投影 · 老前端 detail 模板展示 phone/email · Admin 需全量 · Agent/RAG 0 实现）；产品语义（双重语义表）；🔴 **匿名账号接管链**（username 公开 + 三表明文默认口令 + 明文比对）；建议拆分 `USERNAME-EXPOSURE` P1 + `CONTACT-PII` P2 + `PLAINTEXT-DEFAULT-PASSWORD` P1；4 方案（推荐 A）；5 项待裁定；**零改动** ✓ |
