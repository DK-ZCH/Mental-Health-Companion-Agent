# Phase 3 / Step 7 / R13 —— 我的留言（实现与验收）

> 日期：2026-10-20 ｜ 基线：`9ddb1ef`（R12 已推送，LEAD=0）
> 状态：**R13 前端最小闭环已实现 · 🚧 未提交** ✗ ｜ **Git 可见改动 5 文件**（3 M + 2 A）
> ⚠️ **Git 口径说明**：本文件与 R13 审计文档位于 `docs/`，被 `.gitignore:35` **整体忽略** ⇒ **不出现在 `git status`** ✗（`git check-ignore -v` 已取证）；提交时需 `-f` 显式纳管 ✓

---

## 1. 实现清单

| 文件 | 变更 | 要点 |
| --- | :-: | --- |
| `frontend/src/api/message.ts` | **A** | `listMyMessages()` / `addMessage()`；**字段白名单投影**（PII 不入模型）✓；**只提交 `counselorId` + `content`** ✓ |
| `frontend/src/views/MyMessagesView.vue` | **A** | `/me/messages`：列表 + 写留言弹窗（老师用 `el-radio-group`，便于稳定操作 ✓）|
| `frontend/src/router/index.ts` | M | `me/messages` · `meta.requiresAuth` ✓ |
| `frontend/src/layouts/PublicLayout.vue` | M | 导航「留言」（`data-test="nav-messages"`）—— 无入口则页面不可达，属必要改动 ✓ |
| `frontend/src/views/CounselorDetailView.vue` | M | 原 `action('留言')` 占位 → 真实表单弹窗（`counselorId` 预置）✓ |

**未改动**：后端 **0** ✓ · Legacy Admin **0** ✓ · `front/` **0** ✓ · `api/http.ts` **0** ✓

---

## 2. 静态检查（A）

| 检查 | 结果 |
| --- | :-: |
| `views` 中手写 `Token` / `axios.` | **0** ✓ |
| `mock` / 假数据 | **0** ✓ |
| 前端出现 `/detail` `/delete` `/save` `/info` | **0** ✓ |
| 渲染 `studentPhone` / `studentEmail` / `counselorPhone` / `counselorEmail` | **0** ✓ |
| 提交体包含 `replyContent` / `repliedAt` / `studentId` | **0** ✓（仅 `counselorId` + `content`）|
| `vue-tsc --noEmit` | **exit 0** ✓ |
| lint | **0** ✓ |

---

## 3. 浏览器验收（Playwright + 系统 Chrome）

| 项 | 实测 | 判定 |
| :-: | --- | :-: |
| B 未登录访问 `/me/messages` | → **`/login?redirect=/me/messages`** | ✓ |
| C 登录后回跳 | → **`/me/messages`** | ✓ |
| D 列表真实数据 | **`共 2 条`** + `留言内容2` / `回复信息2` + `333333`（= DB `student_id=1` 的 id 2、6）| ✓✓ **非 Mock** |
| E 写留言弹窗 | `dialog "写留言"` 渲染；老师选项 3 位（`心理老师姓名1..3`）；textarea 存在 | ✓（**未提交** ✗）|
| Console | **Errors 0 / Warnings 0** ✓ | ✓ |

### D2 · UI 真实写入（**第 2 次受控写入 · 已批准**）

链路：`/counselors/1` → 「留言」→ 弹窗填 `R13 UI E2E test message` → 「提交」→ `api/message.ts` → `POST /counselorMessage/add` → DB

| 项 | 实测 |
| --- | --- |
| 落库 | `id=147` · `counselor_id=1` · `student_id=**1**`（服务端强制 ✓）· `content` 正确 ✓ |
| **`reply_content`** | **NULL** ✓✓ ⇒ **UI 未把 `replyContent` 带入请求**（与静态检查互补）|
| `replied_at` | NULL ✓ |
| 列表联动 | `/me/messages` → **`共 3 条`** + 新留言可见 ✓ |
| Console | **0 / 0** ✓ |
| 成功 toast | **未捕获**（等待 2s 后已自动消失；落库已证明成功）⚠️ |

### 明确未验证 ✗

| # | 项 | 原因 |
| :-: | --- | --- |
| 1 | `/add` 的 403 / 511 分支 | 本批无触发入口 ✗ |
| 2 | 「暂无回复」分支的浏览器复现 | 已有数据 id 2/6 均有回复，未构造无回复样本 ✗ |
| 3 | 删除 / 回复 / 未读 | **系统无这些能力**（无删除入口、回复属管理端、无已读字段）✗ |

---

## 4. 数据完整性核验

| 项 | 值 |
| --- | --- |
| `counselor_message` 行数 | **6** ✓ |
| 逐字段 md5（插入前 = 恢复后）| `e641499e9b92a8d05156fbc8d0649aa8` **一致** ✓✓ |
| `AUTO_INCREMENT` | **146 → 148** ⚠️（两次受控写入各 +1；删除后**不回退**，属环境状态漂移）|
| `auth_token` | **3 → 2** ⚠️（**留言业务数据零影响** ✓；但**测试会话状态发生持久化变化**：既有 `id=2 a1·学生` 行被覆盖后删除，**原 token 不可恢复** ✗ ⇒ **不等于“整个 DB 无影响”**）|
| 第 2 次写入的会话处理 | 只使用新生成的测试会话（`id>3`），清理后 `auth_token` 保持 **2**（`id=1 admin` / `id=3 a1·心理老师`）⇒ **未再产生附带影响** ✓ |

---

## 5. 产品语义（**必须保持的表述纪律**）

| 可以表述 ✓ | 不可表述 ✗ |
| --- | --- |
| 「我的留言 + 每条一条回复」 | 「消息中心」「在线咨询对话」「聊天」 |
| 「回复由老师侧/管理端录入，学生端只读」 | 「学生可与老师实时沟通」 |
| 「系统无未读状态」 | 「未读消息提醒」 |
| 「前端不主动制造伪造回复」 | 「伪造回复已修复」 |

---

## 6. 安全登记（**全部 Identified / Unfixed** ✓）

| ID | 级别 | 状态 |
| --- | :-: | --- |
| `SEC-MESSAGE-DELETE-IDOR` | 🔴 P1 | Unfixed（前端**不提供删除** ✓）|
| `SEC-MESSAGE-REPLY-SPOOF` | 🔴 **P1（实测后建议升级 · 待裁定）** | Unfixed（**实测学生可落库伪造回复** ✗；前端不提交该字段 ✓）|
| `DEF-MESSAGE-NO-COUNSELOR-CHECK` | 🟠 P2 | Unfixed |
| `DEF-MESSAGE-UPDATE-BUMPS-REPLIEDAT` | 🟠 P2 | Unfixed（本批**不使用** `/update` ✓）|
| `DEF-REGRESSION-TEST-DATA-MUTATION` | ⚪ **TBD** | Unfixed（独立处理，不并入 R13）|
| `DEF-MESSAGE-NO-CONTENT-VALIDATION` | 🟡 P3 | Unfixed（前端做非空校验 ✓，仅 UI 约束）|
| `SEC-DETAIL-IDOR` | 🔴 P1 | 既有；本批**不使用** `/detail` ✓ |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2 | 2026-10-20 | **UI 真实写入（第 2 次受控）**：`id=147` · `student_id=1`（服务端强制 ✓）· **`reply_content=NULL`** ✓✓ · 列表 `共 3 条` ✓ · Console 0/0 ✓；恢复后 6 行 **md5 一致** ✓；`AI 146→148` ⚠️；**Git 口径修正**：`docs/` 被 `.gitignore:35` 忽略 ⇒ `git status` 仅 5 个代码文件，文档需 `-f` 纳管 |
| v1 | 2026-10-20 | R13 实现：5 文件（3 M + 2 A）；静态 7 项 0；浏览器 B/C/D/E ✓ + Console 0/0 ✓；**UI 提交未验证**（仅 1 次写入已用于契约探针）✗；数据 md5 一致 ✓；`AI 146→147` / `auth_token 3→2` 如实登记 ⚠️；安全项全 Unfixed ✓ |
