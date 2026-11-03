# S2-E-2-1 · Counselor Public Data Boundary 实现报告

> **性质**：实现 · **2 文件（1 新增 + 1 修改）· +149/−3** · **已提交 `d27c10a` · 已推送 ✓**
> **基线**：`6da1c0b` → `d27c10a` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-E-2-1-COUNSELOR-PUBLIC-DTO-DESIGN.md`](./S2-E-2-1-COUNSELOR-PUBLIC-DTO-DESIGN.md)（已 PASS）

---

## 0. 一句话结论

公开通道 `/counselor/list` + `/counselor/detail/{id}` 现只返回 **8 个白名单字段**，
**原始 JSON 文本中 `username`/`phone`/`email`/`createdAt` 命中 0**；
管理通道 `/counselor/page`（新增 admin-only）+ `/counselor/info` 仍返回 12 字段。
47/47 回归通过；`genderLabel` 与分页元信息未受影响。

---

## 1. 改动清单

| # | 文件 | 动作 | 行数 |
| :-: | --- | --- | :-: |
| 1 | `src/main/java/com/entity/view/PublicCounselorView.java` | **新增** | 136 行（含注释）|
| 2 | `src/main/java/com/controller/CounselorController.java` | `/page` +守卫 · `/list` 投影 · `/detail` 投影 | **+13 / −3** |

`git diff --check`：**无空白错误** ✓ · 无其他文件被触碰 ✓

---

## 2. 关键实现点

### 2.1 独立类（不继承 Entity）—— 结构约束已落实

```java
public class PublicCounselorView implements Serializable {
    private Integer id;
    private String name;
    private String avatarUrl;
    private Integer gender;
    private String genderLabel;
    private String expertise;
    private String resume;
    private String introduction;
}
```

**不继承 `CounselorEntity` / `CounselorView`** ⇒ 父类 getter 无法把 `username`/`phone`/`email` 带回响应 ✓

### 2.2 字典回填顺序—— 已按设计固定

```java
// /list
for (CounselorView c : list) {
    dictionaryService.dictionaryConvert(c, request);   // ① 先回填（owner=Counselor → 键命中）
    publicList.add(new PublicCounselorView(c));        // ② 再投影（复制已回填的 genderLabel）
}
page.setList(publicList);
```

```java
// /detail
dictionaryService.dictionaryConvert(view, request);         // ①
return R.ok().put("data", new PublicCounselorView(view));   // ②
```

**未修改 `DICT_CODE`** ✓ · **未修改 `SysDictItemServiceImpl`** ✓

### 2.3 `/page` admin-only

```java
public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
    OwnershipGuard.assertAdminOnly(request);   // ← 新增（第 1 行）
    ...
    DataScope.apply(request, params, "studentId", "counselorId");   // 保留（未删除）
```

---

## 3. 回归（47/47）

```text
Tests run: 30, Failures: 0, Errors: 0 -- IdentityAuthorizationRegressionTest
Tests run: 17, Failures: 0, Errors: 0 -- Phase2CoreRegressionTest
Tests run: 47, Failures: 0, Errors: 0
BUILD SUCCESS
```

---

## 4. 部署证据

| 项 | 旧 | 新 |
| --- | --- | --- |
| PID | `42864` | **`46704`** ✓ |
| jar 时间戳 | 17:27:03 | **18:31:44** ✓ |
| jar 大小 | 143,199,816 | 143,201,238 |
| 8080 | — | 就绪（6s）✓ |

---

## 5. Acceptance Matrix（4×4 实测）

| 角色 | `/counselor/list` | `/counselor/detail/1` | `/counselor/page` | `/counselor/info/1` |
| --- | :-: | :-: | :-: | :-: |
| **anonymous** | **200 · 8 字段** ✓ | `code=401` ✓ | `code=401` ✓ | `code=401` ✓ |
| **student** | **200 · 8 字段** ✓ | **200 · 8 字段** ✓ | **403** ✓ | **403** ✓ |
| **counselor** | **200 · 8 字段** ✓ | **200 · 8 字段** ✓ | **403** ✓ | **403** ✓ |
| **admin** | **200 · 8 字段** ✓ | **200 · 8 字段** ✓ | **200 · 12 字段** ✓ | **200 · 12 字段** ✓ |

### 5.1 公开字段集（逐字）

```text
id, name, avatarUrl, gender, genderLabel, expertise, resume, introduction
```

**恰好 8 个**，无 `username` / `password` / `phone` / `email` / `createdAt` ✓

### 5.2 管理字段集（逐字，12 个）

```text
id, username, name, phone, avatarUrl, gender, email, expertise, resume, introduction, createdAt, genderLabel
```

**管理端能力未损** ✓

### 5.3 `genderLabel` 非空 + 分页元信息

| 端点 | `genderLabel` | `total` / `pageSize` / `totalPage` / `currPage` |
| --- | :-: | --- |
| `/list`（4 角色）| **非空**（`男`/`女`）✓ | 3 / 3 / 1 / 1 ✓ |
| `/detail/1` | **非空**（`女`）✓ | — |
| `/page`（admin）| 非空 ✓ | 3 / 3 / 1 / 1 ✓ |

**⇒ §3 的字典键风险已被规避**（顺序正确）。

---

## 6. 🔑 原始 JSON 文本断言（用户补充的验收项）

> 直接检查**原始响应字符串**，而非反序列化后的对象 —— 防止 Jackson getter / 父类属性造成意外泄露。

### 6.1 公开端点（应 0 命中）

| 用例 | 原始长度 | 敏感 **KEY** 命中 | 敏感**子串**命中 |
| --- | :-: | :-: | :-: |
| `/counselor/list`（anonymous）| 777 | **0** ✓ | **0** ✓ |
| `/counselor/list`（student）| 777 | **0** ✓ | **0** ✓ |
| `/counselor/detail/1`（student）| 250 | **0** ✓ | **0** ✓ |
| `/counselor/detail/999`（anonymous）| 25 | **0** ✓ | **0** ✓ |

检查的 5 个敏感词：`username` · `password` · `phone` · `email` · `createdAt`

### 6.2 管理端点（对照组，应命中）

| 用例 | 敏感 KEY 命中 |
| --- | --- |
| `/counselor/page`（admin）| `username` `phone` `email` `createdAt` ✓ |
| `/counselor/info/1`（admin）| `username` `phone` `email` `createdAt` ✓ |

**⇒ 检查方法有效**（管理端点确实命中，说明公开端点的 0 命中不是"检查失效"）。

`password` 在**所有端点**均 0 命中（三表 `getPassword()` 均 `@JsonIgnore`）✓

---

## 7. 前端影响核对

| 调用方 | 需要字段 | 结果 |
| --- | --- | :-: |
| Vue3 `api/counselor.ts`（7 字段）| `id` `name` `avatarUrl` `genderLabel` `expertise` `resume` `introduction` | ✅ **全部在 8 字段内** |
| 老前端 `list.html` | `name` `avatarUrl` | ✅ 保留 |
| **老前端 `detail.html`** | `phone` `email` | 🔴 **变空 —— 当前可触达的功能回归**（见 §7.1，非纯技术债）|
| 老前端预约页 | `counselorPhone` 等 | ✅ 本就是坏字段 → 无变化 |
| Admin `list.vue`（`/page`）| 8 字段 | ✅ 零影响（12 字段保留）|
| Admin `add-or-update.vue`（`/info`）| 全字段 | ✅ 零影响 |

---

## 7.1 🔴 老前端兼容性影响的实际性质（已在 commit 前核实）

| 核实项 | 结果 |
| --- | --- |
| `GET /xinlijiankangxitong/front/index.html` | **HTTP 200**（7672 字节，`<title>首页</title>` · layui 用户端首页）|
| `GET /xinlijiankangxitong/front/pages/xinlilaoshi/detail.html` | **HTTP 200**（29876 字节）|
| jar 内 | `BOOT-INF/classes/front/front/...` 存在 |
| `docs/jiaojie.md:139` | 明确记录「**前台入口** = `.../front/index.html`」|

**⇒ 老前端当前仍可作为前台入口访问，`detail.html` 的 `phone`/`email` 变空是「当前可触达的功能回归」，不是纯技术债。**

### 处置方向（按已定原则）

- ❌ **不**为兼容旧页面而把 `phone`/`email` 塞回 Public DTO（否则重新引入刚修掉的 PII 暴露）。
- ✅ 安全边界优先；旧前端依赖应在前端迁移/清理阶段以**移除该两行展示**的方式收口。
- ✅ 登记 **`DEF-LEGACY-COUNSELOR-DETAIL-CONTACT-FIELD`**：老前端 `xinlilaoshi/detail.html` 依赖 `/counselor/detail/{id}` 返回 `phone`/`email`；S2-E-2-1 收紧公共 DTO 后不再返回；Vue3 无该依赖；待旧前端退役/迁移后关闭。
- **本批不改前端**（边界锁定）→ 该回归在旧前端退役前保持可见，需在后续前端批次处理。

---

## 8. 已闭环 / 未闭环

| 项 | 状态 |
| --- | :-: |
| **`SEC-COUNSELOR-LIST-USERNAME-EXPOSURE`**（🔴 P1）| ✅ **本批收口**（公开响应 0 命中）|
| **`SEC-COUNSELOR-LIST-CONTACT-PII`**（🟠 P2）| ✅ **本批收口**（phone/email 0 命中）|
| **`SEC-COUNSELOR-DETAIL-CROSS-USER-PII`**（🟠 P2）| ✅ **随 Public DTO 一并收口** |
| `DEF-COUNSELOR-LIST-CREATEDAT-EXPOSURE`（🟡 P3）| ✅ **顺带关闭**（`createdAt` 不在公开 DTO）|
| `DEF-COUNSELOR-LIST-PUBLIC-PII` | ✅ **已拆分关闭** |
| **`SEC-PLAINTEXT-PASSWORD-STORAGE`**（🔴 P1）| ❌ **未处理**（独立批次，按裁定不碰）|
| `SEC-ADMIN-DEFAULT-CREDENTIAL` | ❌ 未处理 |
| `DEF-COUNSELOR-CASCADE-FIELD-MISMATCH` 等 | ❌ 未处理 |

---

## 9. 未改动（边界核对）

| 项 | 状态 |
| --- | :-: |
| `CounselorEntity` / `CounselorView` | ✗ 未改 |
| `SysDictItemServiceImpl` / `DICT_CODE` | ✗ 未改 |
| `/counselor/info/{id}` | ✗ 未改（仍 admin-only + 12 字段）|
| `DataScope` 实现 / 调用 | ✗ 未删未改 |
| `/counselor/session` `/counselor/update` `/counselor/save` `/counselor/delete` | ✗ 未改 |
| Vue3 / 老前端 / Admin | ✗ 0 改动 |
| DB schema / 数据 | ✗ 零写入（全 GET）|
| 登录 / 密码 | ✗ 未改 |

---

## 10. 状态

| 项 | 值 |
|---|---|
| `git status --short` | **(空)** ✓ |
| `git diff --cached --check` | 无空白错误 ✓ |
| `git show --stat` | 2 files changed, **+149 / −3** · `create mode 100644 PublicCounselorView.java` |
| HEAD / origin | **`d27c10a` / `d27c10a`** · LEAD **0** ✓ |
| 回归 | **47/47** ✓ |
| 服务 | PID `46704` · 8080 就绪 ✓ |
| 提交 | **✅ `d27c10a`**（`fix(security/s2-e-2-1)`）· **已推送 ✓** |

---

## 11. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-E-2-1 实现：新增 `PublicCounselorView`（8 字段，独立类）+ `/list`+`/detail` 投影 + `/page` admin-only；47/47；4×4 矩阵全绿；**原始 JSON 断言公开端点 0 命中 / 管理端点对照组命中**；`genderLabel` 非空 + 分页元信息不变；部署 PID 42864→46704 · jar 18:31:44；**未 commit** |
