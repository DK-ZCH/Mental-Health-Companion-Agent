# S2-E-2-1 · Counselor Public Data Boundary 收口设计

> **性质**：实现设计（📐 待批准 · **不写代码**）
> **基线**：`HEAD = origin = 6da1c0b` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-E-2-0-COUNSELOR-LIST-PUBLIC-PII-AUDIT.md`](./S2-E-2-0-COUNSELOR-LIST-PUBLIC-PII-AUDIT.md)（已 PASS · 方案 A + `/page` admin-only 已裁定）

---

## 0. 设计目标（一句话）

把 Counselor 数据拆成**两条互不越界的返回通道**：
**公开目录（8 字段）** 与 **内部管理（12 字段 + admin-only）**。
不删除 `/detail`，不改密码策略，不改前端，不改 DB。

---

## 1. 数据边界（目标形态）

```text
                      Counselor 数据
                           │
              ┌────────────┴────────────┐
              ↓                         ↓
      PublicCounselorView          CounselorView
        （8 字段 · 无 PII）        （12 字段 · 含 PII）
              │                         │
      /counselor/list            /counselor/page
      /counselor/detail/{id}     /counselor/info/{id}
      （匿名可访问 ✓）            （assertAdminOnly ✓）
```

| 通道 | 端点 | 访问 | DTO | 字段数 |
| --- | --- | --- | --- | :-: |
| 公开目录 | `/counselor/list` | 匿名 ✓ | **`PublicCounselorView`** | **8** |
| 公开目录 | `/counselor/detail/{id}` | 登录 ✓ | **`PublicCounselorView`** | **8** |
| 内部管理 | `/counselor/page` | **admin-only**（新增）| `CounselorView` | 12 |
| 内部管理 | `/counselor/info/{id}` | **admin-only**（已就位）| `CounselorView` | 12 |

---

## 2. 新增 `PublicCounselorView`

### 2.1 字段白名单（8 个）

| 字段 | 类型 | 说明 |
| --- | --- | :-: |
| `id` | Integer | 路由 / 收藏 / 预约所需 |
| `name` | String | 展示名 |
| `avatarUrl` | String | 头像 |
| `gender` | Integer | 性别编码（字典源）|
| `genderLabel` | String | 性别文案（字典回填）|
| `expertise` | String | 擅长 |
| `resume` | String | 履历 |
| `introduction` | String | 介绍 |

**明确不含**：`username` · `password` · `phone` · `email` · `createdAt`

### 2.2 🔑 关键约束 1：**必须独立类，不得继承 `CounselorEntity`**

```java
// ✅ 正确形态
public class PublicCounselorView implements Serializable {
    private Integer id;
    private String name;
    // ... 仅 8 个字段
}

// ❌ 错误形态：extends CounselorEntity
// → 继承 username/phone/email/createdAt 的全部 getter
// → Jackson 仍会把它们序列化出去（PII 泄漏原样保留，投影形同虚设）
```

> `CounselorView extends CounselorEntity` 正是当前问题的根源；公开通道**不能复用**该继承链。

### 2.3 构造器

```java
public PublicCounselorView() { }

/** 从已完成字典回填的 CounselorView 投影（保留 genderLabel）*/
public PublicCounselorView(CounselorEntity src) {
    BeanUtils.copyProperties(src, this);   // 仅 8 个同名字段被复制
}
```

---

## 3. 🔑 关键约束 2：**先 `dictionaryConvert`，再投影**

### 3.1 原因（已核实的实现事实）

`SysDictItemServiceImpl.dictionaryConvert`（L100-121）的字典键是：

```java
String owner = fieldOwner.get(fieldName);           // 字段的【声明类】简单名
if (owner.endsWith("Entity")) owner = owner - "Entity";
String dictCode = DICT_CODE.get(owner + "." + fieldName);
```

`DICT_CODE` 只登记了：

```java
DICT_CODE.put("Counselor.gender", "gender");   // ← 键名固定
```

因此：

| 对象 | `gender` 的声明类 | 拼出的键 | 是否回填 `genderLabel` |
| --- | --- | --- | :-: |
| `CounselorView`（继承 `CounselorEntity`）| `CounselorEntity` → `Counselor` | `Counselor.gender` | ✅ **命中** |
| `PublicCounselorView`（独立声明 `gender`）| `PublicCounselorView` | `PublicCounselorView.gender` | ❌ **不命中（静默失效）** |

### 3.2 因此顺序固定为

```java
// ① 先把实体投影到 CounselorView（既有路径）
CounselorView view = new CounselorView();
BeanUtils.copyProperties(entity, view);

// ② 对 CounselorView 做字典回填（确保 owner=Counselor → 键命中）
dictionaryService.dictionaryConvert(view, request);

// ③ 再把已回填的 view 投影到 PublicCounselorView（genderLabel 一并复制）
PublicCounselorView pub = new PublicCounselorView(view);
```

**不改 `DICT_CODE`**（不改字典配置，避免影响其他实体）。

---

## 4. 三处改动

### 4.1 `/counselor/list`（L403-418）—— 投影

**现状**：

```java
PageUtils page = xinlilaoshiService.queryPage(params);
List<CounselorView> list = (List<CounselorView>) page.getList();
for (CounselorView c : list)
    dictionaryService.dictionaryConvert(c, request);
return R.ok().put("data", page);          // ← 12 字段全量（含 PII）
```

**改为**：

```java
PageUtils page = xinlilaoshiService.queryPage(params);
List<CounselorView> list = (List<CounselorView>) page.getList();
List<PublicCounselorView> publicList = new ArrayList<>();
for (CounselorView c : list) {
    dictionaryService.dictionaryConvert(c, request);      // ① 字典回填（不变）
    publicList.add(new PublicCounselorView(c));           // ② 投影 8 字段
}
page.setList(publicList);                                 // ③ 替换分页数据
return R.ok().put("data", page);
```

- `PageUtils.setList(List<?>)` **已存在**（L82）✓ 分页元信息（total/pageSize/totalPage/currPage）不变
- JSON 结构 `data.list[]` 不变（只是元素字段变少）✓

### 4.2 `/counselor/detail/{id}`（L423-440）—— 投影（**保留端点，不删除**）

**改为**：

```java
CounselorEntity xinlilaoshi = xinlilaoshiService.getById(id);
if (xinlilaoshi != null) {
    CounselorView view = new CounselorView();
    BeanUtils.copyProperties(xinlilaoshi, view);
    dictionaryService.dictionaryConvert(view, request);   // ① 字典回填
    return R.ok().put("data", new PublicCounselorView(view));  // ② 投影
} else {
    return R.error(511, "查不到数据");
}
```

### 4.3 `/counselor/page`（L68-85）—— 加 admin-only（**保留全量 DTO**）

```java
public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
    OwnershipGuard.assertAdminOnly(request);      // ← 新增（第 1 行）
    DataScope.apply(request, params, "studentId", "counselorId");   // 保留（admin 路径无副作用）
    ...
    return R.ok().put("data", page);              // ← 仍为 12 字段 CounselorView
}
```

> `DataScope.apply` **保留不删**：admin-only 后它只会在 admin 路径执行（等价于无过滤），
> 删除属于「顺手重构」，本批不做。

### 4.4 `/counselor/info/{id}`

**零改动**（S2-D3-1 已 admin-only，仍返回 12 字段 `CounselorView`）。

---

## 5. 文件清单

| # | 文件 | 动作 | 预计行数 |
| :-: | --- | --- | :-: |
| 1 | `src/main/java/com/entity/view/PublicCounselorView.java` | **新增** | ~65 |
| 2 | `src/main/java/com/controller/CounselorController.java` | `/page` +1 行；`/list` 投影；`/detail` 投影；import 新增 1 个 | ~+20 / −6 |

**总计 2 个文件**（1 新增 + 1 修改）。

> `OwnershipGuard` 已有 import（L32）✓ · `ArrayList` 已通过 `java.util.*` 可用 ✓
> 仅需新增 `import com.entity.view.PublicCounselorView;`（`com.entity.view.*` 已存在 → **实际无需新增 import** ✓）

---

## 6. Acceptance Matrix

| 角色 | `/counselor/list` | `/counselor/detail/1` | `/counselor/page` | `/counselor/info/1` |
| --- | :-: | :-: | :-: | :-: |
| **anonymous** | **200 · 8 字段** | `code=401` | `code=401` | `code=401` |
| **student** | **200 · 8 字段** | **200 · 8 字段** | **403** | **403** |
| **counselor** | **200 · 8 字段** | **200 · 8 字段** | **403** | **403** |
| **admin** | **200 · 8 字段** | **200 · 8 字段** | **200 · 12 字段** | **200 · 12 字段** |

### 6.1 必测断言（字段级，不只 HTTP 状态）

| # | 断言 |
| :-: | --- |
| 1 | `/list` 匿名返回字段集合 **恰好** = `{id,name,avatarUrl,gender,genderLabel,expertise,resume,introduction}` |
| 2 | `/list` **不含** `username` / `phone` / `email` / `createdAt` / `password`（5 个逐一断言）|
| 3 | `/detail/1`（student）同上，**8 字段** |
| 4 | `/page`（admin）**含** `username`/`phone`/`email`（12 字段）—— 证明管理端能力未损 |
| 5 | `/info/1`（admin）**含** `username`/`phone`/`email`（12 字段）|
| 6 | `genderLabel` **非空**（证明字典回填未被投影破坏）|
| 7 | 分页元信息 `total` / `pageSize` / `totalPage` / `currPage` 与改造前一致 |

> 断言 6 是本设计的**独有风险点**（§3），必须显式验证。

---

## 7. 前端影响

| 调用方 | 使用字段 | 影响 |
| --- | --- | --- |
| **Vue3** `api/counselor.ts`（7 字段白名单）| `id` `name` `avatarUrl` `genderLabel` `expertise` `resume` `introduction` | ✅ **全部保留，零影响** |
| Vue3 `CounselorListView` / `CounselorDetailView` | 同上 | ✅ 零影响 |
| 老前端 `xinlilaoshi/list.html` | `name` `avatarUrl` | ✅ 零影响 |
| **老前端 `xinlilaoshi/detail.html`** | `{{detail.phone}}`（L117）· `{{detail.email}}`（L133）| ⚠️ **变空 / 不存在**（**已裁定的兼容性影响**）|
| 老前端 `xinlilaoshiOrder/add.html` | `counselorPhone` 等 | ✅ 本就是**坏字段**（后端从无此字段）→ 无变化 |
| Admin `xinlilaoshi/list.vue` | 8 字段（`/page`）| ✅ 零影响（12 字段保留）|
| Admin `xinlilaoshi/add-or-update.vue` | 全字段（`/info`）| ✅ 零影响 |

### 7.1 对老前端 `detail.html` 的准确表述（按裁定）

> 老前端当前存在对 `phone`/`email` 的**展示依赖**，但该依赖与「公开咨询师目录最小数据原则」冲突：
> Vue3 不依赖这些字段，站内留言/预约也不依赖电话邮箱。
> Public DTO 落地后 `{{detail.phone}}` / `{{detail.email}}` 将为空 ——
> **这是已识别的旧前端兼容性影响，予以记录；不为维持它而继续公开联系方式。**

---

## 8. 回归与部署

| 步骤 | 内容 |
| --- | --- |
| 编译 | `mvn -q -DskipTests compile` |
| 回归 | `mvn test` → **47/47** |
| 部署 | 停旧 jar → `package` → 启新 jar → 确认 **PID 变化 + jar 时间戳** |
| HTTP | 4×4 矩阵 + 字段级断言 7 项 |
| 数据 | **全 GET，零写入**（无探针/无恢复）|

---

## 9. 风险与回滚

| 风险 | 等级 | 缓解 |
| --- | :-: | --- |
| 老前端 `detail.html` 的 phone/email 变空 | 🟡 已裁定接受 | 记录在案（§7.1）|
| `genderLabel` 因投影失效而变空 | 🟠 **设计已规避** | §3 顺序固定 + 验收断言 6 |
| `PublicCounselorView` 误继承导致字段回流 | 🟠 **设计已规避** | §2.2 明确禁止 + 断言 2 |
| `page.setList` 破坏分页结构 | 🟡 | 断言 7 |
| Admin 管理端能力受损 | 🟡 | `/page`、`/info` 保留 12 字段；断言 4/5 |
| 回滚 | — | 单提交 `git revert`，无 DB/schema |

---

## 10. 明确不做（边界锁死）

```text
❌ 密码哈希迁移 / 默认口令修改 / 登录算法重构（→ SEC-PLAINTEXT-PASSWORD-STORAGE，独立 P1 批次）
❌ 删除 /counselor/list 或 /counselor/detail（保留业务能力）
❌ 修改 CounselorEntity / CounselorView（管理侧继续使用）
❌ 修改 DICT_CODE 字典配置
❌ 修改 /counselor/info（已 admin-only，不动）
❌ 删除 DataScope 调用（避免顺手重构）
❌ 修改前端（Vue3 / 老前端 / Admin）
❌ 修改 DB schema · Agent/RAG 接入 · 其他用户表
```

---

## 11. 已拆分的同级债务（不在本批）

| ID | 级别 | 说明 |
| --- | :-: | --- |
| **`SEC-COUNSELOR-LIST-USERNAME-EXPOSURE`** | 🔴 P1 | 本批**修复**（username 从公开 DTO 移除）|
| `SEC-COUNSELOR-LIST-CONTACT-PII` | 🟠 P2 | 本批**修复**（phone/email 从公开 DTO 移除）|
| **`SEC-PLAINTEXT-PASSWORD-STORAGE`** | 🔴 **P1（独立批次）** | 三表明文 + 明文比对 —— **本批不碰** |
| `SEC-ADMIN-DEFAULT-CREDENTIAL` | 🟡 | 默认凭据（既有）—— 本批不碰 |
| `DEF-COUNSELOR-LIST-CREATEDAT-EXPOSURE` | 🟡 P3 | 本批**顺带关闭**（`createdAt` 不在公开 DTO）|
| `SEC-COUNSELOR-DETAIL-CROSS-USER-PII` | 🟠 P2 | 本批**随 Public DTO 一并收口**（不再独立立项）|

---

## 12. 待你批准的设计项

| # | 项 | 本设计取值 |
| :-: | --- | --- |
| 1 | 新类命名 / 位置 | `com.entity.view.PublicCounselorView` |
| 2 | 继承策略 | **独立类（不继承 `CounselorEntity`）** |
| 3 | 字段白名单 | **8 个**（`id`/`name`/`avatarUrl`/`gender`/`genderLabel`/`expertise`/`resume`/`introduction`）|
| 4 | 字典回填顺序 | **先 `dictionaryConvert(CounselorView)` 再投影**（§3）|
| 5 | `/page` | 加 `assertAdminOnly`，保留 `CounselorView` 全量，**保留 DataScope 调用** |
| 6 | `/detail` | **保留**，改 8 字段 |
| 7 | `/list` | 改 8 字段（分页结构不变）|
| 8 | 文件数 | **2**（1 新增 + 1 修改）|
| 9 | 是否允许真实写测试 | **不需要**（全 GET）|

---

## 13. 状态

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = 6da1c0b` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓（设计阶段）|
| 文档 | 新增本文件 |

---

## 14. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-E-2-1 设计：`PublicCounselorView`（8 字段，独立类）+ `/list`+`/detail` 投影 + `/page` admin-only；**两项关键技术约束**（不继承 Entity / 先字典回填再投影）；2 文件；4×4 matrix + 7 项字段级断言；明确不碰密码策略；零改动 |
