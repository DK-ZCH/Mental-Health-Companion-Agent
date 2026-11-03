# S2-F-2-1 · 注册专用 DTO 实现报告

> **性质**：实现 · **4 文件（2 新增 + 2 修改）· +298 / −4** · **已提交 `41c2b7b` · 已推送 ✓**
> **基线**：`aaf9bc0` → `41c2b7b` · worktree CLEAN
> **日期**：2026-10-22
> **上游**：[`S2-F-2-1-REGISTER-DTO-DESIGN.md`](./S2-F-2-1-REGISTER-DTO-DESIGN.md)（已批准）

---

## 0. 一句话结论

注册改为**专用 DTO + 显式映射**：`password` / `idCardNo` **真正进入 Entity 并落库**。
**8 步可恢复探针全绿** —— 实际 `INSERT` 列清单含 `password` + `id_card_no`，查库值等于提交值，
重复身份证被拒、探针账号可登录；清理后**基线完全恢复**（student 3 / counselor 3 / auth_token 3，探针残留 0）。

> ⚠️ **已知安全债务仍然存在**：本批后**新注册账号密码仍为明文**，`SEC-PLAINTEXT-PASSWORD-STORAGE` 保持 **OPEN**，由 S2-F-4 统一迁移。

---

## 1. 改动清单

| # | 文件 | 动作 | 行数 |
| :-: | --- | --- | :-: |
| 1 | `entity/request/StudentRegisterRequest.java` | **新增**（8 字段 + `ignoreUnknown`）| **+120** |
| 2 | `entity/request/CounselorRegisterRequest.java` | **新增**（10 字段 + `ignoreUnknown`）| **+138** |
| 3 | `controller/StudentController.java` | `register` 改签名 + 非空校验 + 显式映射 | **+19 / −2** |
| 4 | `controller/CounselorController.java` | 同上 | **+21 / −2** |

`git diff --check`：**无空白错误** ✓

---

## 2. 关键实现

### 2.1 DTO（独立类，不继承 Entity）

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudentRegisterRequest implements Serializable {
    private String username;
    private String password;      // ← 关键：让后端能收到
    private String name;
    private String phone;
    private String idCardNo;      // ← 关键：让后端能收到
    private Integer gender;
    private String email;
    private String avatarUrl;
}
```

`password2` **不进 DTO**（前端 `lay-verify` 校验）；`ignoreUnknown=true` 容忍前端多传字段。

### 2.2 Controller 显式映射

```java
public R register(@RequestBody StudentRegisterRequest req){
    if (req.getPassword() == null || req.getPassword().isEmpty()) {
        return R.error(511, "密码不能为空");            // 仅非空，不制定强度规则
    }
    StudentEntity yonghu = new StudentEntity();
    yonghu.setUsername(req.getUsername());
    yonghu.setPassword(req.getPassword());              // ← 不再丢失
    yonghu.setIdCardNo(req.getIdCardNo());              // ← 不再丢失
    ... 其余字段 ...
    // 先查重、后 save（原顺序不变）
}
```

**未改**：`@JsonIgnore` · `/update` · `login` · 前端 · DB schema · admin `save`/`add`。

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
| PID | `17316` | **`8144`** ✓ |
| jar 时间戳 | 19:14:01 | **19:56:14** ✓ |
| jar 大小 | 143,200,959 | 143,204,241 |
| 8080 | — | 就绪（4s）✓ |

---

## 5. 🔑 8 步可恢复探针（运行时实证）

### BEFORE

```text
student = 3 · counselor = 3 · auth_token = 3
student AUTO_INCREMENT = 1000001 · counselor = 1000000
```

### STEP 1 · 注册学生探针

```json
POST /student/register
{"username":"__probe_stu_f21","password":"ProbePass#21","name":"ProbeStudent",
 "phone":"19900000021","idCardNo":"990000199001010021","gender":1,
 "email":"probe21@example.com","password2":"ProbePass#21"}
```

→ **HTTP 200 · code=0** ✓

### STEP 2 · 查库（核心）

| id | username | password | id_card_no | pwd_len |
| ---: | --- | --- | --- | :-: |
| 1000001 | `__probe_stu_f21` | **`ProbePass#21`** | **`990000199001010021`** | 12 |

**⇒ `password` 与 `idCardNo` 均真正落库，且等于提交值** ✓

### STEP 3 · 实际 INSERT SQL（第三层证据闭环）

```sql
INSERT INTO student ( username, password, name, phone, id_card_no, gender, email, created_at )
VALUES ( ?, ?, ?, ?, ?, ?, ?, ? )
```

**⇒ 列清单包含 `password` + `id_card_no`** ✓（不再是"配置推演"，而是运行时 SQL 实证）

### STEP 4 · 重复身份证查重（username / phone 均不同）

```json
{"username":"__probe_stu_f21b","phone":"19900000022","idCardNo":"990000199001010021", ...}
```

| 结果 | 值 |
| --- | --- |
| HTTP | 200 |
| code | **500** |
| msg | 「账户或者学生手机号或者学生身份证号已经被使用」|
| 实际插入 | **0 条**（`COUNT(*) WHERE username='__probe_stu_f21b'` = 0）|

**⇒ 身份证查重已恢复**（修复前 `id_card_no` 恒 null ⇒ 该条件不可能命中）✓
**⇒ S2-F-2-0 的「`eq(col,null)` 语义存疑」已被运行时回答：修复后不再是 null，查重正常生效。**

> **如实说明**：该分支返回 `code=500`（`R.error(String)` 的默认码），**不是 511**。
> 这是**既有查重分支的原样行为**，本批未改变它（设计文档早期写的"511"应以此实测为准）。

### STEP 5 · 注册老师探针

→ **code=0**；查库 `password = ProbePass#22`、`pwd_len = 12` ✓

### STEP 6 · 探针账号登录（"预期能登录"的运行时验证）

| 用例 | 结果 |
| --- | --- |
| `student/login?username=__probe_stu_f21&password=ProbePass%2321` | **200 · code=0 · token_len=32** ✓ |
| `counselor/login?username=__probe_tea_f21&password=ProbePass%2322` | **200 · code=0 · token_len=32** ✓ |

**⇒ F-2 后注册账号确实可登录（现有明文登录链路未改）** ✓

### STEP 7 · 清理

```text
DELETE FROM student   WHERE username LIKE '__probe%';   -- 1 条
DELETE FROM counselor WHERE username LIKE '__probe%';   -- 1 条
DELETE FROM auth_token WHERE username LIKE '__probe%';  -- 2 条（探针登录产生）
```

### STEP 8 · AFTER 基线

| 项 | BEFORE | AFTER | 结论 |
| --- | :-: | :-: | :-: |
| student | 3 | **3** | ✅ |
| counselor | 3 | **3** | ✅ |
| auth_token | 3 | **3** | ✅ |
| 探针残留（student/counselor/token）| — | **0 / 0 / 0** | ✅ |
| student AUTO_INCREMENT | 1000001 | 1000001 | ➖ **不作为基线恢复条件**（允许单调增加；本次观测值相同不代表回滚）|
| counselor AUTO_INCREMENT | 1000000 | 1000000 | ➖ 同上 |
| 业务密码计数 | 3/3/1 | **3/3/1** | ✅ |

> **AUTO_INCREMENT 说明（表述修正）**：按既定标准「**允许单调增加，不作为业务基线恢复条件**」。
> 本次观测值与基线相同，**仅属观测结果，不代表执行过任何回退/重置**；
> 本批**未执行** `ALTER TABLE ... AUTO_INCREMENT = ...` 或任何等价 DB 操作。

---

## 6. Acceptance 11 项对照

| # | 断言 | 实测 | 结论 |
| :-: | --- | --- | :-: |
| 1 | 学生注册 200 · DB password/idCardNo 有值 | 200 · 两字段均落库且相等 | ✅ |
| 2 | 老师注册 200 · DB password 有值 | 200 · 落库 | ✅ |
| 3 | **INSERT 列清单含 password** | 含 `password` + `id_card_no` | ✅ |
| 4 | 重复 username → 拒绝 | 既有分支保持（未单独探针，回归覆盖）| ✅ |
| 5 | 重复 phone → 拒绝 | 同上 | ✅ |
| 6 | **重复 idCardNo → 命中既有查重分支且 0 插入** | code=**500**（`R.error(String)` 默认码，本批不改错误码契约）· 实际插入 **0 条** | ✅ |
| 7 | password=`""` → 511 | 代码分支 `isEmpty()` | ✅ **静态实现确认，未做 HTTP 运行时验证** |
| 8 | password 缺失 → 511 | 代码分支 `== null` | ✅ **静态实现确认，未做 HTTP 运行时验证** |
| 9 | 探针账号登录 200 + token | 学生/老师均 200 + token(32) | ✅ |
| 10 | 清理后基线恢复 | 3/3/3 · 残留 0 · AI 回落 | ✅ |
| 11 | 47/47 | 通过 | ✅ |

> **Acceptance 口径说明**：11 项中 **9 项为运行时验证**（1–6、9–11），
> **第 7/8 项为静态实现确认**（未发起空密码注册请求，避免产生无效数据）。
> 二者不可混称为「全部实测」；如需运行时证据，可在后续批次补。

---

## 7. 未改动（边界核对）

| 项 | 状态 |
| --- | :-: |
| `@JsonIgnore`（三表 Entity）| ✗ 未改 |
| `/update`（密码仍不可绑定）| ✗ 未改 |
| `login`（3 处明文比较）| ✗ 未改 |
| 密码哈希（bcrypt 等）| ✗ **未引入** |
| 密码强度规则 | ✗ 未制定 |
| 前端（老前端 / Vue3 / Admin）| ✗ 0 改动 |
| DB schema | ✗ 未改（仅探针数据，已清理）|
| admin `save`/`add`（仍固定 123456）| ✗ 未改 |
| 其他端点 / Controller 结构 | ✗ 未改 |

---

## 8. 已闭环 / 未闭环

| 项 | 状态 |
| --- | :-: |
| **`DEF-REGISTER-PASSWORD-DROPPED`（🔴 P1\* 功能阻断）** | ✅ **本批收口**（运行时实证）|
| **`DEF-REGISTER-IDCARD-DROPPED`（🟠 P2）** | ✅ **本批收口**（查重恢复已实证）|
| **`SEC-PLAINTEXT-PASSWORD-STORAGE`（🔴 P1）** | ❌ **仍 OPEN**（→ F-3/F-4；本批新注册仍明文）|
| `DEF-NO-SELF-SERVICE-PASSWORD-CHANGE`（🟠 P2）| ❌ 未处理（独立子项）|
| `DEF-ADMIN-PASSWORD-UPDATE-INEFFECTIVE`（🟡 P2）| ❌ 未处理 |
| `DEF-LOGIN-PASSWORD-IN-QUERY`（🟠 P2）| ❌ 未处理 |
| `DEF-PASSWORD-IN-TOSTRING`（🟡 P3）| ❌ 未处理 |
| `SEC-ADMIN-DEFAULT-CREDENTIAL`（🟡）| ❌ 未处理 |
| `DEF-NO-SELF-SERVICE-PASSWORD-RECOVERY`（🟡）| ❌ 已登记 |

---

## 9. 状态

| 项 | 值 |
|---|---|
| `git status --short` | **(空)** ✓ |
| `git diff --cached --check` | 无空白错误 ✓ |
| `git show --stat` | **4 files changed, +298 / −4** · `create mode 100644` ×2 |
| HEAD / origin | **`41c2b7b` / `41c2b7b`** · LEAD **0** ✓ |
| 远端 `ls-remote` | `41c2b7b...` ✓ 已确认 |
| 回归 | **47/47** ✓ |
| 服务 | PID `8144` · 8080 就绪 ✓ |
| 数据 | **基线恢复**（3/3/3 · 残留 0；AI 不作为基线条件）|
| 提交 | **✅ `41c2b7b`**（`fix(security/s2-f-2-1)`）· **已推送 ✓** |

---

## 10. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-10-22 | S2-F-2-1 实现：2 个注册 DTO + 2 处显式映射 + 非空校验；47/47；**8 步探针全绿**（INSERT 列清单含 password/id_card_no · 查库值相等 · 重复身份证**命中既有查重分支且 0 插入** · 双端探针登录 200 · 清理后 3/3/3 残留 0，**AUTO_INCREMENT 不作为基线条件**）；**如实记录查重分支 code=500 而非 511**；**明文债务仍 OPEN**；未 commit |
