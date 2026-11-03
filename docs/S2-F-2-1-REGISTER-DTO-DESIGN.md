# S2-F-2-1 · 注册专用 DTO 实现设计

> **性质**：实现设计（📐 待批准 · **不写代码**）
> **基线**：`HEAD = origin = aaf9bc0` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-F-2-0-REGISTER-PASSWORD-AUDIT.md`](./S2-F-2-0-REGISTER-PASSWORD-AUDIT.md)（方案 B 已批准）

---

## 0. 设计目标（单一）

让**注册时用户提交的 `password` 与 `idCardNo` 真正进入 Entity 并落库**，
方法 = **注册专用 DTO**（不改 `@JsonIgnore`、不改 `update` 语义）。

**本批不涉及**：密码哈希、密码强度策略、登录后改密、前端改动。

---

## 1. ⚠️ 必须显式记录的事实（不可隐藏）

> **F-2 修复后，新注册账号的密码仍以【明文】写入数据库。**
> 原因：`SEC-PLAINTEXT-PASSWORD-STORAGE`（三表明文 + 明文比对）尚未处理，属 **S2-F-3 设计 / S2-F-4 实现**。
>
> ```text
> F-2  修复注册数据流（password 不再丢失）
>         ↓
>      password 暂时仍按现有明文机制存储   ← 本批的已知不完美
>         ↓
> F-4  统一迁移为 hash（bcrypt 暂定）
>         ↓
>      新旧账号一并转为 hash
> ```
>
> **风险权衡**：当前状态是「注册后根本无法登录」；F-2 后是「能登录但密码仍明文」。
> 后者在安全上并非完美，但**优于功能完全不可用**，且明文风险本就在 `SEC-PLAINTEXT-PASSWORD-STORAGE` 中登记为 OPEN。

---

## 2. 新增两个 DTO

### 2.1 包位置

`com.entity.request`（新增子包）

> 说明：Phase 2 T-1 曾清理 `entity/model`、`entity/vo` 孤儿包，故新增包必须**有真实引用**（本批 2 处 Controller 直接使用）✓

### 2.2 `StudentRegisterRequest`（字段 = 学生注册页实际提交项）

| 字段 | 类型 | 来源（`yonghu/register.html`）|
| --- | --- | :-: |
| `username` | String | ✓ |
| **`password`** | String | ✓（当前被丢弃）|
| `name` | String | ✓ |
| `phone` | String | ✓ |
| **`idCardNo`** | String | ✓（当前被丢弃）|
| `gender` | Integer | ✓ |
| `email` | String | ✓ |
| `avatarUrl` | String | —（注册页无；保留可选，默认 null）|

> `password2` 为前端确认框，**不进入 DTO**（后端不做二次确认；Jackson 默认忽略未知字段）。

### 2.3 `CounselorRegisterRequest`（字段 = 老师注册页实际提交项）

| 字段 | 类型 | 来源（`xinlilaoshi/register.html`）|
| --- | --- | :-: |
| `username` | String | ✓ |
| **`password`** | String | ✓（当前被丢弃）|
| `name` | String | ✓ |
| `phone` | String | ✓ |
| `gender` | Integer | ✓ |
| `email` | String | ✓ |
| `expertise` | String | ✓ |
| `resume` | String | ✓ |
| `introduction` | String | —（注册页无；保留可选）|
| `avatarUrl` | String | —（保留可选）|

### 2.4 注解约定

```java
@JsonIgnoreProperties(ignoreUnknown = true)   // 容忍 password2 等前端多余字段
public class StudentRegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    // ... 字段 + getter/setter
}
```

**不继承 Entity**（与 `PublicCounselorView` 同一原则：独立类、显式字段）。

---

## 3. Controller 改动（2 处）

### 3.1 `StudentController.register`

```java
@IgnoreAuth
@PostMapping(value = "/register")
public R register(@RequestBody StudentRegisterRequest req){        // ← 签名变更
    // F-2-1：密码不允许 null / 空串（仅做非空校验，不制定复杂度规则）
    if (req.getPassword() == null || req.getPassword().isEmpty()) {
        return R.error(511, "密码不能为空");
    }

    StudentEntity yonghu = new StudentEntity();                     // ← 显式映射
    yonghu.setUsername(req.getUsername());
    yonghu.setPassword(req.getPassword());                          // ← 关键：不再丢失
    yonghu.setName(req.getName());
    yonghu.setPhone(req.getPhone());
    yonghu.setIdCardNo(req.getIdCardNo());                          // ← 关键：不再丢失
    yonghu.setGender(req.getGender());
    yonghu.setEmail(req.getEmail());
    yonghu.setAvatarUrl(req.getAvatarUrl());

    QueryWrapper<StudentEntity> queryWrapper = new QueryWrapper<StudentEntity>()
        .eq("username", yonghu.getUsername())
        .or().eq("phone", yonghu.getPhone())
        .or().eq("id_card_no", yonghu.getIdCardNo());               // ← 不再是 null

    if (yonghuService.getOne(queryWrapper) != null) {
        return R.error("账户或者学生手机号或者学生身份证号已经被使用");
    }
    yonghu.setCreatedAt(new Date());
    yonghuService.save(yonghu);
    return R.ok();
}
```

### 3.2 `CounselorController.register`

同构（`CounselorRegisterRequest` → `CounselorEntity`，映射 `expertise`/`resume`/`introduction`，无 `idCardNo`）。

### 3.3 关键设计点

| # | 点 | 说明 |
| :-: | --- | --- |
| 1 | **查重顺序** | 保持原顺序：**先查重、后 save**（不改变业务语义）|
| 2 | **`id_card_no` 不再是 null** | 修复后 `eq("id_card_no", <真实值>)` ⇒ 原「null 语义存疑」问题**自然消失**，无需单独研究 `eq(col, null)` |
| 3 | **密码非空校验** | 仅 `null` / `""` → 511；**不制定长度/复杂度规则**（按裁定留到 F-3/F-4）|
| 4 | **不 trim 密码** | 密码可含空格，不做静默裁剪 |
| 5 | **`avatarUrl`** | 注册页不提交 → 为 null（与现状一致，不引入默认头像）|
| 6 | **`password2`** | 不进入 DTO；由前端 `lay-verify` 负责一致性 |

---

## 4. 文件清单

| # | 文件 | 动作 | 预计行数 |
| :-: | --- | --- | :-: |
| 1 | `src/main/java/com/entity/request/StudentRegisterRequest.java` | **新增** | ~75 |
| 2 | `src/main/java/com/entity/request/CounselorRegisterRequest.java` | **新增** | ~85 |
| 3 | `src/main/java/com/controller/StudentController.java` | `register` 改签名 + 显式映射 | ~+18 / −8 |
| 4 | `src/main/java/com/controller/CounselorController.java` | 同上 | ~+18 / −8 |

**共 4 文件**（2 新增 + 2 修改）· 需新增 import：`com.entity.request.*`

---

## 5. 可恢复探针（三层证据升级为运行时实证）

> 按裁定批准。**探针完成后必须恢复基线**。

### 5.1 探针流程（严格 8 步）

```text
BEFORE     student count = 3 · counselor count = 3

① 注册学生（唯一探针值）
   POST /student/register
   {"username":"__probe_stu_f21","password":"ProbePass#21",
    "name":"探针学生","phone":"19900000021",
    "idCardNo":"990000199001010021","gender":1,"email":"probe21@example.com"}
   → 期望 HTTP 200 · code=0

② 查库实证（核心）
   SELECT id,username,password,id_card_no,created_at FROM student WHERE username='__probe_stu_f21'
   → 期望 password = 'ProbePass#21'（明文，本批已知）· id_card_no = '990000199001010021'

③ SQL 日志实证
   抓 app.log 中该请求的 INSERT：列清单必须包含 password 与 id_card_no

④ 身份证查重验证（回答 F-2-0 的待确认项）
   再注册一个不同 username/phone 但相同 idCardNo 的账号
   → 期望 code=511「...身份证号已经被使用」 ⇒ 查重生效

⑤ 注册老师（唯一探针值）→ 查库 password 落库

⑥ 登录探针账号（学生）→ 期望 200 + token（证明密码真正可用）

⑦ 清理
   DELETE FROM student WHERE username='__probe_stu_f21'
   DELETE FROM counselor WHERE username='__probe_tea_f21'
   （含第 ④ 步的重复身份证探针，若其已插入）

⑧ AFTER   student count = 3 · counselor count = 3 · 基线逐项一致
```

### 5.2 证据留存

| 证据 | 形式 |
| --- | --- |
| 实际 HTTP 请求/响应 | 探针输出（code / msg / 字段）|
| 实际 DB 行 | `SELECT` 结果（**密码值仅记录"等于提交值"结论，不在报告中展开**）|
| 实际 SQL | `app.log` 中 `INSERT INTO student (...)` 列清单 |
| 清理后基线 | count / 默认值计数逐项对比 |

> **敏感信息处理**：探针密码为**专用测试值**（非真实用户密码），报告可记录"落库值与提交值一致"的结论，不必展开明文。

---

## 6. Acceptance Matrix

| # | 断言 | 期望 |
| :-: | --- | --- |
| 1 | 学生注册（含 password/idCardNo）| 200 · DB 两字段均有值 |
| 2 | 老师注册（含 password）| 200 · DB password 有值 |
| 3 | INSERT SQL 列清单 | **包含** `password`（学生另含 `id_card_no`）|
| 4 | 重复 username 注册 | 511（既有行为不变）|
| 5 | 重复 phone 注册 | 511（既有行为不变）|
| 6 | **重复 idCardNo 注册**（修复前恒不命中）| **511**（查重恢复）|
| 7 | 密码为空 `""` | **511「密码不能为空」** |
| 8 | 密码字段缺失（null）| **511「密码不能为空」** |
| 9 | 探针账号登录 | **200 + token** |
| 10 | 清理后基线 | student 3 · counselor 3 一致 |
| 11 | 47/47 回归 | **通过** |

---

## 7. 明确不做（边界锁死）

```text
❌ 不改 @JsonIgnore（三表 Entity 保持原样）
❌ 不改 /update（密码仍不可通过 update 绑定）
❌ 不实现"登录后修改密码"（保留 DEF-NO-SELF-SERVICE-PASSWORD-CHANGE，P2 独立）
❌ 不引入 bcrypt / Argon2 / PBKDF2（→ S2-F-3 / F-4）
❌ 不制定密码强度规则（仅非空校验）
❌ 不改 login（3 处明文比较保持）
❌ 不改前端（注册页字段不变，password2 仍由前端校验）
❌ 不改 DB schema · 不改 admin 的 save/add（仍固定 123456）
❌ 不重构 Controller · 不动其他端点
```

---

## 8. 风险与回滚

| 风险 | 等级 | 缓解 |
| --- | :-: | --- |
| 已有前端提交 `password2` 等多余字段 | 🟢 低 | DTO 标 `ignoreUnknown = true` + Spring 默认不失败 |
| 注册页未提交 `avatarUrl` 导致 null | 🟢 低 | 与现状一致 |
| 新注册密码仍明文 | 🟡 **已在 §1 显式记录** | 由 F-4 统一迁移 |
| 密码非空校验过严误伤 | 🟢 低 | 仅拒 null / `""` |
| 探针残留数据 | 🟠 | 严格 8 步含清理 + 基线逐项核对 |
| 回滚 | — | 单提交 `git revert`，无 schema 变更 |

---

## 9. 待你批准的设计项

| # | 项 | 本设计取值 |
| :-: | --- | --- |
| 1 | DTO 数量/位置 | **2 个**，`com.entity.request` |
| 2 | DTO 是否继承 Entity | **否**（独立类 + `ignoreUnknown`）|
| 3 | 密码非空校验 | `null` / `""` → **511「密码不能为空」** |
| 4 | 密码强度 | **不做**（留 F-3/F-4）|
| 5 | `password2` | **不进 DTO**（前端校验）|
| 6 | `avatarUrl` / `introduction` | 保留可选字段（注册页不提交 → null）|
| 7 | 查重顺序 | 保持「先查重后 save」|
| 8 | 探针 | **8 步含清理 + 重复身份证验证 + SQL 日志** |
| 9 | 文件数 | **4**（2 新增 + 2 修改）|
| 10 | **明文存储声明** | **§1 已显式记录，将同步写入提交信息与报告** |

---

## 10. 实施纪律

```text
设计批准 → 新增 DTO + 改 2 Controller → 编译
   → 47/47 回归
   → 停旧 jar → package → 启新 jar → 确认 PID/jar 时间戳
   → 8 步可恢复探针（含 SQL 日志 + 查重验证 + 基线恢复）
   → 11 项 Acceptance
   → staged review → 你审核 → commit → 你审核 → push
```

---

## 11. 状态

| 项 | 值 |
|---|---|
| 基线 | `HEAD = origin = aaf9bc0` · LEAD 0 · worktree CLEAN |
| 本批改动 | **零代码 · 零 DB · 零 commit** ✓（设计阶段）|
| 文档 | 新增本文件 |

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-F-2-1 设计：2 个注册专用 DTO（`com.entity.request`，独立类 + `ignoreUnknown`）+ 2 处 Controller 显式映射（含 `setPassword`/`setIdCardNo`）+ 非空校验；**§1 显式记录「新注册仍写明文」**；**8 步可恢复探针**（含 SQL 列清单 + 重复身份证查重 + 基线恢复）；11 项 Acceptance；4 文件；零改动 |
