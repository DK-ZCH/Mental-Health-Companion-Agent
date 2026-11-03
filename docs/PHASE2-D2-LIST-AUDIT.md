# D2-1：免鉴权 `/list` 最终治理审计（**只读，零代码改动**）

> **状态**：✅ 审计完成（**未改任何代码 / 配置 / API**）
> **目标（审阅锁定）**：收敛「**应当受保护却仍可匿名访问**」的 `/list`，**不是**机械地给所有 `/list` 加认证
> **原则（沿用）**：**没有业务证据，不替业务方发明权限**

---

## 1. 方法与口径

| 层 | 手段 |
| --- | --- |
| **静态** | 全量枚举 `@RequestMapping("/list")`；逐个方法向前查 3 行判定 `@IgnoreAuth`；读 `AuthorizationInterceptor` 判定链；统计 `DataScope.apply` 覆盖面 |
| **运行期** | 真实容器（jar）实测：**匿名**（无 Token）与**学生**（a1）两种身份逐个调用 14 个 `/list`，记录 `code` 与响应是否含 PII 字段名 |
| **调用面** | 分别扫描 `admin/admin/src`（含 dist）与 `front/`（学生端）中对 `X/list` 的**真实 API 调用**，并区分「模块导入路径」与「API 调用」；特别标出**首页/登录页**调用（= 公开可达的硬证据）|

**认证判定链（实测自 `AuthorizationInterceptor`）**：

```text
① 硬编码白名单（仅 3 条）: /dictionary/page  /file/upload  /yonghu/register  → 直接放行
② 方法级 @IgnoreAuth（getMethodAnnotation，仅看方法）→ 直接放行
③ 否则必须带 Token → 写入 session(userId/role/tableName/username)
```

---

## 2. 全量清单（14 个 `/list`）

| # | 端点 | `@IgnoreAuth` | 数据表 | 含个人/业务数据 | 学生端 API 调用 | 首页/登录页调用 | 匿名实测 | 学生实测 | 已有 `DataScope` | 结论 |
| :-: | --- | :-: | --- | :-: | :- | :-: | :-: | :-: | :-: | --- |
| 1 | `/yonghu/list` | **YES** | `student` | **是（PII）** | `yonghu/list.html` | — | `code=0` | `code=0` | 否 | **收敛** |
| 2 | `/xinlilaoshiLiuyan/list` | **YES** | `counselor_message` | **是（PII）** | `xinlilaoshiLiuyan/list.html` | — | `code=0` | `code=0` | 否 | **收敛** |
| 3 | `/xinlilaoshiCollection/list` | **YES** | `counselor_favorite` | **是（PII）** | `xinlilaoshiCollection/detail.html` | — | `code=0` | `code=0` | 否 | **收敛** |
| 4 | `/xinlilaoshiOrder/list` | **YES** | `counseling_appointment` | **是（PII）** | **无任何调用** | — | `code=0` | `code=0` | 否 | **收敛** |
| 5 | `/examrecord/list` | **YES** | `assessment_record` | **是（PII）** | **无任何调用** | — | `code=0` | `code=0` | 否 | **收敛** |
| 6 | `/examredetails/list` | **YES** | `assessment_answer` | **是（PII）** | `examredetails/list.html` | — | `code=0` | `code=0` | 否 | **收敛** |
| 7 | `/examrewrongquestion/list` | **YES** | `assessment_wrong_question` | **是（PII）** | **无任何调用** | — | `code=0` | `code=0` | 否 | **收敛** |
| 8 | `/xinlilaoshi/list` | **YES** | `counselor` | **是（PII）** | `xinlilaoshi/list.html` | ✅ **home.html:742** | `code=0` | `code=0` | 否 | **保持公开** ⚠️ + 登记 PII |
| 9 | `/jiankangzhishi/list` | **YES** | `knowledge_article` | 否 | `jiankangzhishi/list.html` | ✅ **home.html:702** | `code=0` | `code=0` | 否 | **保持公开** |
| 10 | `/tongzhi/list` | **YES** | `notification` | 否 | `tongzhi/list.html` | ✅ **home.html:782** | `code=0` | `code=0` | 否 | **保持公开** |
| 11 | `/config/list` | **YES** | `sys_config` | 否 | `add/list/center/confirm/detail/home.html` | ✅ **home.html:663** | `code=0` | `code=0` | 否 | **保持公开** |
| 12 | `/exampaper/list` | **YES** | `assessment_paper` | 否（量表元数据）| **无任何调用** | — | `code=0` | `code=0` | 否 | **证据不足（暂缓）** |
| 13 | `/examquestion/list` | **YES** | `assessment_question` | 否（题目内容）| `examquestion/list.html` | — | `code=0` | `code=0` | 否 | **证据不足（暂缓）** |
| 14 | `/users/list` | NO | `admin_user` | **是（含 password）** | 无 API 调用（仅 `router-static.js` 模块导入路径）| — | **`code=401`** ✅ | **`code=403`** ✅ | 是（`assertAdminOnly`）| **已收敛**（小批次 A/C）|

**统计**：匿名可达 **13/14** ✗；其中返回 PII **8 个**；被**首页**调用 **4 个**；`DataScope` 覆盖 `/list` = **0**（12 处全部在 `/page`）✓

> **测量口径说明（如实登记）**：本轮我一度用 `-Include` 却漏了 `-Recurse`，得到「`DataScope.apply` = 0」的假结果 ✗，已修正为 **12**（与批 4-A 一致）✓。
> 另：`/list` 的**返回条数**列我**未采信**（PowerShell 对单元素数组的处理可能失真 ✗），D2-2 将建立正式基线 ✓。

---

## 3. 三组分类与依据

### A. 必须收敛（**7 个**）

`/yonghu/list` · `/xinlilaoshiLiuyan/list` · `/xinlilaoshiCollection/list` · `/xinlilaoshiOrder/list` · `/examrecord/list` · `/examredetails/list` · `/examrewrongquestion/list`

**共同证据**：`@IgnoreAuth=YES` ✓ + 匿名实测 `code=0` ✓ + **响应含 PII/联系方式字段** ✓ + **无首页调用**（即不存在"公开展示"的业务需求证据）✓ + 管理端只用 `/page` ✓

### B. 明确公开（**4 个**）

`/jiankangzhishi/list` · `/tongzhi/list` · `/config/list` · **`/xinlilaoshi/list`** ⚠️

**证据**：均为 **`home.html`（首页）真实 API 调用** ✓ —— 公开落地页需要它们渲染内容 ✓
**⚠️ 例外登记**：`/xinlilaoshi/list` 虽是首页所需（老师展示），但**响应含 PII**（phone/email/avatar）✗
→ 处置：**保持匿名可达** ✓（不动它，避免打断首页 ✓）+ 把「**公开接口暴露联系方式**」登记到 **② phone/email 治理批次** ✓（属字段级治理，**不是**认证问题 ✓）

### C. 业务证据不足（**2 个**，暂缓）

`/exampaper/list` · `/examquestion/list`

**现状**：匿名可达 ✓ + 不含 PII ✓ + `exampaper` **无任何前端调用**、`examquestion` 仅在**登录后**页面调用 ✓
**为何暂缓**：属「测评内容」而非个人数据 ✓ —— 既无个人数据风险，也**无证据**表明必须公开 ✓
→ **不替业务方发明规则** ✓：待业务确认「量表/题目是否属公开内容」后再定 ✓

### D. 已收敛（1 个）

`/users/list` —— 小批次 A 加 `assertAdminOnly` ✓（匿名 `401` / 学生 `403` ✓，实测 ✓）

---

## 4. 关键发现（按重要性）

1. **13/14 个 `/list` 匿名可达**，且**全部带 `@IgnoreAuth`**（方法级）✗ —— 这是 Step 5 授权体系**最后一个明显缺口** ✓
2. **`/list` 完全没有数据范围控制**：`DataScope` 的 12 处调用**全在 `/page`** ✓ → 即使登录，学生调用 `/yonghu/list` 也能拿到**全量**学生 ✗（学生实测 `code=0` ✓ 与匿名行为无差别 ✗）
3. **8 个匿名接口返回 PII/联系方式** ✗（Step 3 已移除 password/身份证 ✓，但 phone/email/avatar 仍在 ✓ → 与 ② 批次直接相关 ✓）
4. **认证 ≠ 授权**：A 组「去 `@IgnoreAuth`」只解决**匿名**问题 ✗；**范围**问题（学生看到全量）**必须**另做 ✓
5. 管理端**只调用 `/page`** ✓（dist 中 `/list` 命中 = 0 ✓；src 中的命中全是 `router-static.js` 的**模块导入路径** ✓）→ 收敛 `/list` **不会**影响管理端 ✓
6. `xinlilaoshi/list` 存在**需求冲突**（首页要公开 vs 响应含 PII）⚠️ → 归入字段级治理 ✓，**不在 D2 里发明规则** ✓

---

## 5. D2-2 实施建议（**分两步，各自独立提交**）

### D2-2a：认证（去掉 A 组的方法级 `@IgnoreAuth`）

```text
范围：7 个端点（A 组）
效果：匿名 → 沿用现有认证契约（401）；登录用户 → 保持当前行为（不变）
风险：低 —— 管理端未调用 /list ✓；学生端调用点均在【登录后页面】✓
      唯一需人工确认：A 组 7 个的学生端页面是否**必然**在登录后打开
```

### D2-2b：范围（给 A 组接入 `DataScope` / 对象级范围）

```text
挑战：/list 的实现与 /page 不同（参数管道不同）—— 必须逐模块确认「注入的条件真的生效」✗
      （批 4-A 的 /page 是靠 XML 的 params.studentId 生效的；/list 走的是另一条查询构造 ✗）
原则：不为了"看起来更安全"而发明规则 —— student=自己 / teacher=既定范围 / admin=全量
验证：必须含【反向对照】（临时关闭限制 → 越权行为重现）
```

### D2-3：回归与 E2E（按审阅要求）

```text
匿名 → 拒绝          student A → 只看到自己      student B → 看不到 A
teacher → 既定范围    admin → 原管理功能不变      反向对照 → 关闭限制后越权重现
```

---

## 6. 本审计**未做**的事（守边界）

* ❌ 未修改任何代码 / `@IgnoreAuth` / XML / 配置 ✓
* ❌ 未推进 ② phone/email 治理（含 `xinlilaoshi/list` 的 PII 冲突）✓ —— 仅登记
* ❌ 未推进 ③ `TypeEnum` 清理 ✓
* ❌ 未改 `/page`（已受 `DataScope` 保护 ✓）
* ❌ 未对 C 组两个端点做任何处置 ✓（证据不足）

---

## 7. D2-2a 前置登录态确认（gate 证据，只读）

> 审阅要求：**进入 D2-2a 前，先只读确认 7 个端点对应的真实调用页面确实处于登录态；确认无公开入口后，再改代码。**
> 下表 6 列即该 gate 的完整证据（**零代码改动阶段完成**）

| # | A 组端点 | 改前 `@IgnoreAuth` 位置 | 真实前端调用页面 | 页面级登录守卫 | ★**公开页引用** | admin 真实 API 调用 | 匿名页直呼 |
| :-: | --- | --- | --- | :-: | :-: | :-: | :-: |
| 1 | `/yonghu/list` | `StudentController:427` | `yonghu/list.html:454,467` | 无 | **0** ✓ | **0** ✓ | **否** ✓ |
| 2 | `/xinlilaoshiLiuyan/list` | `CounselorMessageController:254` | `xinlilaoshiLiuyan/list.html:426,439` | 无 | **0** ✓ | **0** ✓ | **否** ✓ |
| 3 | `/xinlilaoshiCollection/list` | `CounselorFavoriteController:266` | `xinlilaoshiCollection/detail.html:321,413` | 无 | **0** ✓ | **0** ✓ | **否** ✓ |
| 4 | `/xinlilaoshiOrder/list` | `CounselingAppointmentController:273` | **前端无 API 调用** | — | **0** ✓ | **0** ✓ | **否** ✓ |
| 5 | `/examrecord/list` | `AssessmentRecordController:285` | **前端无 API 调用** | — | **0** ✓ | **0** ✓ | **否** ✓ |
| 6 | `/examredetails/list` | `AssessmentAnswerController:286` | `examredetails/list.html:426,439` | 无 | **0** ✓ | **0** ✓ | **否** ✓ |
| 7 | `/examrewrongquestion/list` | `AssessmentWrongQuestionController:275` | **前端无 API 调用** | — | **0** ✓ | **0** ✓ | **否** ✓ |

**★公开页引用 = 0 的口径**：用**完整路径**（`模块/list.html`）扫描全部 `.html`，筛出文件名匹配 `home|index|login|register|top|head|menu|nav` 的**公开页**中对其的引用 ⇒ 7 个模块**全为 0** ✓
> ⚠️ 过程纠正：我首次用**文件名 basename**（`list.html`）匹配 ⇒ 把 `jiankangzhishi/list.html` 误判成 `yonghu/list.html` ✗（假阳性）⇒ 已改为完整路径重测 ✓

**三项独立证据同时成立**（这才构成「无公开入口」）：

1. **未命中首页/登录页** ✓ —— `home.html` 的导航实际只跳 `tongzhi/`（×3）、`jiankangzhishi/`（×3）、`xinlilaoshi/`（×2）✓（= B 组），**完全不指向任何 A 组页面** ✓
2. **管理端真实 API 调用 = 0** ✓ —— dist 命中 0 ✓；src 中的命中**全是 `router-static.js` 的模块导入路径**（`import yonghu from '@/views/modules/yonghu/list'`）✓，**非 API 调用** ✓
3. **唯一入口是登录态菜单** ✓ —— A 组页面出现在 `front/front/js/config.js` 的 **`/** 个人中心菜单 */ var centerMenu`** 中 ✓，其首项用 `url: '../' + localStorage.getItem('userTable') + '/center.html'` 构造 ✓ ⇒ **`userTable` 只有登录后才有值** ✓ ⇒ 该菜单属**登录后**语境 ✓

**收口后的前端行为（已核实 `front/front/modules/http/http.js`）**：

```text
code == 401  →  window.parent.location.href = '../login/login.html'
```

⇒ 匿名误入这 7 个页面会被**干净地引导到登录页** ✓（不会出现坏页/死数据 ✓）
⇒ 且 `http.js` 的 `beforeSend` 始终 `setRequestHeader("Token", localStorage.getItem("Token"))` ✓ ⇒ 已登录用户天然带 Token ✓

---

## 8. D2-2a 实施与验收（✅ 完成，提交 `2eddbf8`）

**范围**：A 组 **7 个端点**移除**方法级** `@IgnoreAuth` ✓（本批**只挪认证边界**，不碰数据范围 ✓）

| 项 | 结果 |
| --- | --- |
| 实现 | 仅删除紧邻 `@RequestMapping("/list")` 的那一行 `@IgnoreAuth`（精确正则 ✓，不影响同文件其他 `@IgnoreAuth` ✓）；6 个文件因不再使用该注解而**同步移除失效 import** ✓（`StudentController` 仍有 3 处他用 ⇒ import 保留 ✓）|
| 测试 | 新增用例 **11** 三向断言：**匿名 A 组全 401** / **已登录（学生）A 组行为不变（全 `code=0`）** / **B 组公开项仍匿名可达（防误伤）** ✓；并把 7 个 `/list` 纳入 `TOKEN_PROTECTED_ENDPOINTS` ✓ |
| 回归 | `mvn clean test` → **44/44** ✓（28 + 16）|
| **反向对照** | 临时给 `/yonghu/list` 加回 `@IgnoreAuth` → 用例**失败**并精确报出 `[匿名访问 /yonghu/list 应被拒 body.code=401] expected: 401 but was: 0` ✓ ⇒ **证明断言测的正是本次改动**（而非其他拦截器"顺便挡住"）✓；恢复后复绿 ✓ |
| **端到端（真实容器）** | ① 匿名 A 组 **7/7 → `code=401`** ✓ ② 匿名 B/C 组 + `/dictionary/page` **全部 `code=0`** ✓（无误伤公开页 ✓）③ 已登录（学生）A 组 **7/7 → `code=0`** ✓（行为不变 ✓）|
| 数据 | `auth_token` = 3 ✓ 不变 |

**D2-2b（数据范围）仍待实施** —— 关键风险已登记：**绝不可把 `DataScope.apply(...)` 从 `/page` 机械复制到 `/list`** ✓
（`/list` 与 `/page` 参数管道不同 ⇒ 每个模块都要验证「注入 → QueryWrapper → DAO → 最终 SQL → 实际返回」并留**真实 SQL/E2E 证据** ✓）

---

## 9bis. D2-2b-Phase 1：7 个 `/list` 参数管道只读勘察（**零代码改动**）

> 审阅要求：逐个形成「查询入口 → 归属字段 → 可注入位置 → 最终 SQL 形态」的表，**再决定统一方案还是分组实施**

### 9b.1 管道真相：`/list` 与 `/page` **最终汇聚到同一个 Mapper 调用** ✅

7 个 `/list` 方法体**完全同构**：

```java
public R list(@RequestParam Map<String,Object> params, HttpServletRequest request){
    if (StringUtil.isEmpty(String.valueOf(params.get("orderBy")))) params.put("orderBy","id");
    PageUtils page = xxxService.queryPage(params);          // ← 1 参重载（/list 用）
    List<XxxView> list = (List<XxxView>) page.getList();
    for (XxxView c : list) dictionaryService.dictionaryConvert(c, request);
    return R.ok().put("data", page);
}
```

而 1 参 `queryPage(params)` 的实现（7 个模块逐字相同）：

```java
Page<XxxView> page = new Query<XxxView>(params).getPage();
page.setRecords(baseMapper.selectListView(page, params));   // ← params 直通 XML
```

`/page` 的 2 参重载**同样**调用 `baseMapper.selectListView(page, params)`（差别只在于额外用 `QueryWrapper` 做排序/区间）。
⇒ **结论：向 `params` 注入范围条件，对 `/list` 与 `/page` 都有效** ✅（这是 D2-2b 可行性的基础；**不是**把 `DataScope` 从 `/page` 机械复制，而是两者本就共用同一条 SQL 条件入口）

### 9b.2 逐端点勘察表（5 问逐条作答）

| # | 端点 | ① 查询条件来源 | ② 已有身份条件 | ③ `DataScope` 注入位置 | ④ 最终 SQL 是否含范围条件 | ⑤ 前端是否传归属参数 |
| :-: | --- | --- | --- | --- | --- | --- |
| 1 | `/yonghu/list` | `@RequestParam params` → `queryPage` → `selectListView(page,params)` | **无**（`student_id` 不存在——本表主键是 `id`）| ⚠️ **仅注入不够**：`StudentDao.xml` **没有** `params.studentId` 条件 ⇒ 还需**补一条 XML 条件**（`a.id = #{params.studentId}`）；**同一缺口也存在于 `/yonghu/page`** ✗ | ❌ **当前不含**（实测学生看到 id1,id2,id3 = 全部 3 名 ✗）| 否（`yonghu/list.html` 0 命中）|
| 2 | `/xinlilaoshiLiuyan/list` | 同上 | `counselor_id` + `student_id`（表自带）| ✅ 控制器内 `queryPage(params)` 前一行注入 | ⚠️ **当前不含**（实测学生看到 6 = 全部 ✗，而 `/page` 仅 2 ✓ → 同一张表两个入口行为不一致 ✗）| 否 |
| 3 | `/xinlilaoshiCollection/list` | 同上 | `counselor_id` + `student_id` | ✅ 同上 | ⚠️ **当前不含**（实测 6 = 全部 ✗；`/page` 已过滤 ✓）| **是**（`detail.html:192` 客户端传 `studentId`，取自 localStorage ⚠️）|
| 4 | `/xinlilaoshiOrder/list` | 同上 | `counselor_id` + `student_id` | ✅ 同上 | ⚠️ **E2E 无法区分**（表内仅 s1 的 3 行 ✗）；XML 有 `params.studentId` 与 `params.counselorId` ⇒ 静态管道可证 ✓ | 否 |
| 5 | `/examrecord/list` | 同上 | `student_id`（表自带；**无 `counselor_id`**）| ✅ 注入 `studentId` **有效**；`counselorId` **无字段 ⇒ 不会生效**（= 维持现状，需业务规则，见 9b.4）| ⚠️ **E2E 无法区分**（仅 s1）；XML 有 `params.studentId` ✓ | **是**（`list.html:192`）|
| 6 | `/examredetails/list` | 同上 | **`student_id`（表自带！）** —— 业务上经 `record_no` 关联记录表，但**归属字段本身直接可读** ⇒ **不属于「关联推导授权」** ✅ | ✅ 同 5 | ⚠️ **E2E 无法区分**；XML 有 `params.studentId` ✓ | 否 |
| 7 | `/examrewrongquestion/list` | 同上 | `student_id`（表自带；无 `counselor_id`）| ✅ 同 5 | ⚠️ **E2E 无法区分**；XML 有 `params.studentId` ✓ | **是**（`list.html:211,229` 取自 **URL 参数** ⚠️）|

### 9b.3 三组实施形态（**据此决定统一 or 分组**）

| 组 | 端点 | 实施形态 | 风险 |
| --- | --- | --- | --- |
| **G1 · 可直接注入**（6 个）| 2·3·4·5·6·7 | 控制器内**加一行** `DataScope.apply(request, params, "studentId", "counselorId");`（与各自 `/page` **完全同款**）| 低：XML 条件已存在 ✓、管道已实证 ✓ |
| **G2 · 需补 XML**（1 个）| 1 `/yonghu/list` | 注入 **+** 在 `StudentDao.xml` 补 `params.studentId` 条件（**同一缺口也在 `/yonghu/page`** ✗ —— 需一并裁定）| 中：改 XML；影响面 = `student` 表两个入口 |
| **G3 · 教师侧无字段**（4 个）| 4·5·6·7 | 学生侧可注入 ✓；**教师侧 `counselorId` 在表内不存在 ⇒ 注入也不会生效** ⇒ **不为了"看起来完整"而伪造字段** ✓（维持现状并登记业务待决）| — |

### 9b.4 明确的边界（与审阅要求一致）

* ✅ **`assessment_answer` 不需要关联推导**：表**直接带 `student_id`** ⇒ 归属条件可落在本表（业务上确有 `record_no` 链，但授权不必经它）
  * ⚠️ 仍登记一处**一致性观察**：`assessment_answer.student_id` 与其 `record_no` 对应记录的 `student_id` **理论上可能不一致**（当前种子数据一致）⇒ D2-2b 实施时用一条断言固化「同一 `record_no` 的 `student_id` 唯一」
* ❌ **不伪造 owner 字段**、❌ 不为教师侧发明规则（4 张表无 `counselor_id`）
* ✅ **客户端参数伪造已被 `DataScope` 覆盖语义中和**：学生侧 `studentId` 被**强制覆盖** ✓；教师侧 `counselorId` **强制 AND** ⇒ 即便客户端传任意 `studentId`，有效条件仍是 `counselor_id = 我 AND student_id = X` ⇒ **无越权** ✓
* ⚠️ **两处现存不一致必须登记**（D2-2b 的价值所在）：
  1. **同表两入口行为不一致**：`counselor_message`/`counselor_favorite` 的 `/page` 已过滤 ✓，而 `/list` 未过滤 ✗（学生实测：`/list` 见全部 6 条 ✗ vs `/page` 2 条 ✓）
  2. **`/yonghu` 两个入口（`/list` 与 `/page`）** 对学生**都未过滤** ✗（实测 3/3 名）

### 9b.5 本勘察的取证方式与局限（如实登记）

* **实测**：学生 a1（userId=1）对 7 个 `/list` + 7 个 `/page` 逐端点调用，记录条数与**行级归属值** ✓
* **DB ground truth**：逐表 `student_id` 分布 ✓（`counselor_message`/`counselor_favorite`/`student` 为**混合** ⇒ 可判定 ✓；其余 4 张**仅含 s1** ⇒ **E2E 无法区分** ✗ ⇒ 以「XML 条件存在 + 管道逐字比对」作静态证明，**D2-2b 实施时以对照数据复核** ✓
* 全过程**零代码改动、零数据库写入** ✓（只读勘察 ✓）

---

### 9b.6 G2 实施结果（**G2-A 与 G2-B 分别验收**；一次 XML 修复关闭两个入口）

| 项 | **G2-A** `/yonghu/list` | **G2-B** `/yonghu/page` |
| --- | --- | --- |
| 变更 | Controller 内**新增**注入 | 控制器**早已有**该注入（L72）；本次由**同一 XML 条件**恢复生效 |
| 修复前 | 学生看到 **3/3 全部** ✗ | 学生看到 **3/3 全部** ✗（注入被静默忽略 —— 历史缺口）|
| ① 匿名 | 401 ✓ | 401 ✓ |
| ② A（userId=1）| **1 条，仅 id=1** ✓ | **1 条，仅 id=1** ✓ |
| ③ A 伪造 `?studentId=2`（及 `&userId=2`）| 仍 **1 条 id=1** ✓ | 仍 **1 条 id=1** ✓（**SQL 仍绑定 1** ✓✓）|
| ④ B / ④b B 伪造 `?studentId=1` | 1 条 id=2 ✓ / 仍 id=2 ✓ | 同 ✓ |
| ⑤ admin / ⑤b admin+`?studentId=2` | **3 条全量** ✓ / **1 条 id=2** ✓ | 同 ✓ |
| ⑥ teacher c1 / c2 | 3 条全量、SQL **无 WHERE** ✗ ⇒ **G3 挂账** ✓ | 同 ✗ ⇒ G3 ✓ |
| ⑦ SQL 证据 | 学生 `WHERE (… a.id = ?)` 绑定 **1**；admin **无 WHERE**；admin+筛选 绑定 `2(String)` | 同 ✓ |

**核心 XML 修复**（`StudentDao.xml`：学生表以**自身主键 `id`** 作归属字段）：

```xml
<if test="params.studentId != null and params.studentId != ''">
    and a.id = #{params.studentId}
</if>
```

**影响面（已核实）**：`StudentDao.xml#selectListView` 的调用方**仅** `StudentServiceImpl.queryPage` → `StudentController` 的 `/page` 与 `/list` 两个端点 ✓

**⚠️ 回归用例修订（如实登记）**：`Phase2CoreRegressionTest#studentPage_paginationMetadataIsCorrect` 原本以**学生**身份断言「能看到全部 3 名学生」✗ —— 该断言把缺口当成了正确行为 ✓；已改为：分页元数据用**管理员**验证（保持"每页 2 条 / 2 页"原意 ✓）+ 学生侧断言「仅自己 1 条」锁住修复 ✓，并新增 `loginAsAdmin()` 工具方法 ✓

**过程纠正 #4（测量问题，非代码）**：G2-B 首跑 5 个 `code=500` ✗（异常 `NumberFormatException: "50?studentId=2"`）⇒ 探针把参数追加到了**已含查询串**的端点上 ✗；已修（自适应分隔符 ✓）并重跑全绿 ✓

---

## 9ter. D2-2b-G1 实施台账（**实施中：2/6 完成**）

> 验收口径（审阅裁定）：①匿名 ②A正常 ③**A伪造 owner 参数** ④B 不能看 A ⑤admin 不变（且自加筛选保留）⑥teacher 按既有规则 ⑦**真实 SQL 条件证据**；外加**每模块越权反向测试**与**对照数据**（仅含 s1 的表不用现有数据下结论）

### 9t.1 实施方式（6 个模块同一形态）

在各自控制器 `/list` 内、`queryPage(params)` 之前加**一行**：

```java
DataScope.apply(request, params, "studentId", "counselorId");
```

与同文件 `/page` **完全同款同位置** ⇒ 两入口共用同一条 `selectListView(page, params)` 的 `params` 条件（§9bis 已证）✓

### 9t.2 台账

| # | 端点 | 提交 | ①匿名 | ②A | ③A伪造 | ④B | ⑤admin | ⑤b admin+筛选 | ⑥teacher | ⑦SQL | 状态 |
| :-: | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| 1 | `/xinlilaoshiLiuyan/list` | `e164d2f` | 401 ✓ | 2条 仅s1 ✓ | 仍仅s1 ✓ | 2条 仅s2 ✓ | 6条全量 ✓ | 2条 s2 ✓ | 3条 c1(s1×2+s3) ✓ | `student_id=1` / `counselor_id=1` / admin 无 WHERE ✓ | ✅ 完成 |
| 2 | `/xinlilaoshiCollection/list` | `57269dc` | 401 ✓ | 2条 仅s1(c1+c2) ✓ | 仍仅s1 ✓ | 2条 仅s2(c2+c3) ✓ | 6条全量 ✓ | 2条 s2 ✓ | 2条 c1(s1+s3) ✓ | 同上 ✓ | ✅ 完成 |
| 3 | `/xinlilaoshiOrder/list` | `70acce9` | 401 ✓ | 3条 仅s1 ✓ | 仍仅s1 ✓（SQL 仍绑定 1）| **1条 = 对照行 s2/c2** ✓✓ | 4条(3+对照) ✓ | 1条 对照 ✓ | **c1=1条[s1] / c2=2条[s1+对照]** ✓✓ | `student_id=1`（伪造→仍 1）/ `counselor_id=1\|2` / admin 无 WHERE ✓ | ✅ 完成 |
| 4 | `/examrecord/list` | `58ce4de` | 401 ✓ | 1条 仅s1 ✓ | 仍仅s1 ✓ | **1条 = 对照行 s2** ✓✓ | 2条(1+对照) ✓ | 1条 对照 ✓ | **c1=c2=2条全量（未收敛）** ✗ | `student_id=1`（伪造→仍 1）/ admin 无 WHERE / **教师无 WHERE** ✓ | ✅ 完成（**教师侧 G3 挂账** ✓）|
| 5 | `/examredetails/list` | `5cfc51d` | 401 ✓ | 5条 仅s1 ✓ | 仍仅s1 ✓ | **1条 = 对照行 s2** ✓✓（无对应 record 仍返回，left join ✓）| 6条(5+对照) ✓ | 1条 对照 ✓ | **c1=c2=6条全量（未收敛）** ✗ | `student_id=1`（伪造→仍 1）/ admin 无 WHERE / **教师无 WHERE** ✓ | ✅ 完成（**教师侧 G3 挂账** ✓）|
| 6 | `/examrewrongquestion/list` | `99d6147` | 401 ✓ | 3条 仅s1 ✓ | 仍仅s1 ✓ | **1条 = 对照行 s2** ✓✓ | 4条(3+对照) ✓ | 1条 对照 ✓ | **c1=c2=4条全量（未收敛）** ✗ | `student_id=1`（伪造→仍 1）/ admin 无 WHERE / **教师无 WHERE** ✓ | ✅ 完成（**教师侧 G3 挂账** ✓）|

> 说明：模块 1 的教师侧结果 `c1 → s1×2 + s3×1` 与 `counselor_message` 库分布**逐行吻合**；模块 2 的 `c1 → s1 + s3` 同理 ⇒ 非"数据恰好"而是条件真实生效 ✓
> **对照数据方案（模块 3–6）**：为表插入 1 行 `student_id=2`（且 `counselor_id=2` 以保持教师/管理员预期干净）→ 断言 **A 看不到** ✓ **B 看得到** ✓ **admin 看得到（= 无 WHERE 的等价形态 ✓）** → 随后删除并核对行数复原 ✓；配合 SQL 证据（A 带条件 / admin 无 WHERE）构成"修复前后"等价证明 ✓

### 9t.2b 模块 4–6 的教师侧**预登记**（先定口径，避免事后解释）

| 模块 | XML 是否有 `params.counselorId` | 允许的结论 | 证据形态 |
| --- | :-: | --- | --- |
| `/examrecord/list` | ❌ 无（§9bis）| **仅"学生侧 G1 隔离成立"**；教师侧只能如实登记为**未收敛（维持现状）→ G3 挂账** | 教师请求的 SQL **无 `WHERE`**（`counselorId` 被 XML 忽略）✓ |
| `/examredetails/list` | ❌ 无 | 同上 | 同上 |
| `/examrewrongquestion/list` | ❌ 无 | 同上 | 同上 |

> ⛔ **不得**为了让矩阵"看起来完整"而新增 `counselorId` 字段或 SQL 条件（G3 禁止伪造字段）✓
> ✅ 模块 3 的教师隔离**可以**成立：`CounselingAppointmentDao.xml` **确有** `params.counselorId` ✓，并已用 **c1/c2 双教师对照**证明（c1 见到 1 条 [s1/c1]；c2 见到 2 条 [s1/c2 + 对照行 s2/c2]）✓✓

### 9t.4 G1 完成总结（**6/6**）

| 模块 | 提交 | 学生侧（A / A 伪造 / B / B 伪造）| 教师侧 | admin | SQL 条件证据 |
| :-: | --- | --- | --- | --- | --- |
| 1 `/xinlilaoshiLiuyan/list` | `e164d2f` | 2 ✓ / 2 ✓ / 2 ✓ / — | **已收敛** c1=3（s1×2+s3）✓ | 6 全量 ✓ + 筛选保留 ✓ | `student_id=1` / `counselor_id=1` / admin 无 WHERE |
| 2 `/xinlilaoshiCollection/list` | `57269dc` | 2 ✓ / 2 ✓ / 2 ✓ / 2 ✓ | **已收敛** c1=2（s1+s3）✓ | 6 全量 ✓ + 2 ✓ | 同上 |
| 3 `/xinlilaoshiOrder/list` | `70acce9` | 3 ✓ / 3 ✓ / **1=对照** ✓ / 1 ✓ | **已收敛（双教师对照）** c1=1 · **c2=2（含对照）** ✓✓ | 4 全量 ✓ + 1 ✓ | `student_id=1`（伪造→仍 1）/ `counselor_id=1\|2` / admin 无 WHERE |
| 4 `/examrecord/list` | `58ce4de` | 1 ✓ / 1 ✓ / **1=对照** ✓ / 1 ✓ | ✗ **未收敛（无字段）→ G3** | 2 全量 ✓ + 1 ✓ | `student_id=?` / admin 无 WHERE / **教师无 WHERE** |
| 5 `/examredetails/list` | `5cfc51d` | 5 ✓ / 5 ✓ / **1=对照** ✓ / 1 ✓ | ✗ 同上 → G3 | 6 全量 ✓ + 1 ✓ | 同上 |
| 6 `/examrewrongquestion/list` | `99d6147` | 3 ✓ / 3 ✓ / **1=对照** ✓ / 1 ✓ | ✗ 同上 → G3 | 4 全量 ✓ + 1 ✓ | 同上 |

**三条横向结论**：

1. **两入口口径现已一致** ✓：修复前 6 个端点中 message/favorite 的 `/list` 为全量而 `/page` 已过滤（同表两入口行为分裂 ✗）；修复后 `/list` 与 `/page` 走同一 `params` 条件 ⇒ **6/6 端点两入口口径统一** ✓
2. **伪造参数在 SQL 层就被中和** ✓：`?studentId=2`（甚至 `&userId=2`）时 SQL 仍为 `student_id = 1`（服务端身份）⇒ **覆盖发生在 SQL 执行之前** ✓（比仅比 HTTP 返回值更强 ✓）
3. **对照数据不可省** ✓：模块 3–6 原本仅含 s1 数据 ⇒ 若只看 A 的结果，**"已过滤"与"恰好只有 s1"不可区分** ✗；插入 `student_id=2` 对照行后，**B 恰可见 1 条** ⇒ 过滤真实生效 ✓；6 个模块的对照行**全部删除且行数逐一复原** ✓

**G3 挂账清单（教师侧，不新增字段）**：

| 端点 | 原因 | 证据 |
| --- | --- | --- |
| `/examrecord/list` | 表无 `counselor_id`，XML 无 `params.counselorId` | 教师请求 SQL **无 WHERE**（c1/c2 均全量）✓ |
| `/examredetails/list` | 同上 | 同上 ✓ |
| `/examrewrongquestion/list` | 同上 | 同上 ✓ |

**遗留**：**G2** = `/yonghu/list` + `/yonghu/page`（二者共享**同一 XML 条件缺口**，一次修复关闭两入口，但报告中**分别记录**）✓ —— 见 §9bis 的 G2 组 ✓

**两类问题的区分（复盘 / 项目讲解要点）**：本批次实际暴露出**两种性质完全不同**的缺口 —— 且**只有 SQL 层证据能把它们区分开**：

| 类别 | 形态 | 代表端点 | 判定证据 |
| --- | --- | --- | --- |
| **① 完全没有 owner scope** | `/list` **连注入都没有** ⇒ 学生/教师直接拿到全表 | G1 六模块（修复前：学生实测见 s1,s2,s3 全部）| 修复后 SQL 出现 `WHERE (… student_id = ?)`，绑定**服务端身份** |
| **② `DataScope` 空转** | 已调用注入，但 **XML 不认识该字段** ⇒ 条件根本不进 SQL | **G2-B `/yonghu/page`**（修复前：**管理员与学生同为"无 WHERE"**）| 修复后学生 `WHERE (… a.id = ?)` 绑定 `1`，而 admin **仍无 WHERE** |

> 若只看「代码里调了 `DataScope`」或 HTTP 返回码/条数，**②会被误判为已修** ✓；若只用"表里恰好只有一名学生的数据"，**①会被误判为已过滤** ✓ —— 这正是本批坚持「**SQL 条件证据 + 不同 owner 对照数据**」双证据的原因 ✓

### 9t.3 流程纠正 #3（**测量/工具链问题，非代码**）

| # | 现象 | 根因 | 处置 |
| :-: | --- | --- | --- |
| 3 | 模块 2 首跑 5 个探针返回 `401`（同一批令牌 10 分钟前在模块 1 可用）| 应用会**重新签发令牌**（`auth_token` 行值被更新，`expired_at` 同步刷新）⇒ 跨"应用重启"复用旧令牌不可靠 ✗ | **改为同一命令内为四角色现场登录**（`/yonghu/login`、`/xinlilaoshi/login`、`/users/login`）✓；并把探针固化为脚本（ASCII 输出 + 空令牌防护）✓ |

> 附带修正一处认知：管理员登录端点是 **`/users/login`**（类级 `@RequestMapping("users")`）✓ —— 与 Step 5b "类名重命名不改变 URL"的结论一致 ✓
> 探针脚本与提交信息为**临时文件**（仓库根 `.d2b-probe.ps1` / `.d2b-commit-msg.txt`）：**始终不入提交**（G1 每模块只 `git add src/main`；G2 为 `git add src/main src/test`）✓；**已于 D2-2b 收尾（G2 推送后）删除** ✓ —— 若日后重启 G3，可按本报告口径重建探针（**四角色现场登录** + SQL 日志 + **追加参数时自适应分隔符**）✓

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v5 | 2026-10-16 | **D2-2b-G2 实施完成（G2-A/G2-B 分别验收）**：一处 XML 缺口（`StudentDao.xml` 无 `params.studentId`）同时影响 `/yonghu/list` 与 `/yonghu/page`，一次修复同时关闭；`DataScope` 注入现已在 SQL 层可见（`WHERE (… a.id = ?)` 绑定服务端身份 1 ✓，伪造仍为 1 ✓），admin 无 WHERE ✓；**发现并修订一个把缺口当正确行为固化的回归用例**（原以学生身份断言"能看到全部 3 名学生"）→ 改为管理员验证分页元数据 + 学生侧锁行为 ✓；**过程纠正 #4**（探针把参数追加到已含查询串的端点 ✗ 已修）☆ ⇒ 见 §9b.6 |
| v4 | 2026-10-15 | **D2-2b-G1 实施完成（6/6，含代码改动）**：6 个 `/list` 各加一行 `DataScope.apply(...)`（与各自 `/page` 同款）；每模块独立提交（`e164d2f` `57269dc` `70acce9` `58ce4de` `5cfc51d` `99d6147`）；**9–10 项矩阵逐模块实测**（含伪造参数在 SQL 层被中和 ✓）；模块 3–6 按审阅要求**先建临时对照数据**（`student_id=2`）再 E2E，**B 恰可见 1 条**证明过滤非空跑 ✓，随后对照行**全部删除、行数逐一复原** ✓；教师侧**仅模块 1–3 可主张收敛**（XML 有 `params.counselorId`，模块 3 更以 c1/c2 双教师对照证明 ✓），**模块 4–6 教师侧如实登记为未收敛 → G3 挂账**（SQL 无 WHERE ✓，不新增字段 ✓）⇒ 见 §9t.4 |
| v3 | 2026-10-15 | **D2-2b Phase 1（只读勘察，零代码零写库）**：证明 `/list` 与 `/page` **最终共用同一 `baseMapper.selectListView(page, params)`** ⇒ 向 `params` 注入对两者均有效；逐端点 5 问表（查询来源 / 已有身份条件 / 注入位置 / SQL 是否含范围条件 / 前端是否传归属参数）；分三组 **G1 可直接注入 6 个 · G2 需补 XML 1 个（`/yonghu/list`，且 `/yonghu/page` 同缺口）· G3 教师侧无字段 4 个**；实测发现**同表两入口不一致**（message/favorite 的 `/page` 已过滤而 `/list` 未过滤）与 **`/yonghu` 两入口均未过滤**；确认 `assessment_answer` **无需关联推导**（表直接带 `student_id`）；客户端参数伪造被 `DataScope` 覆盖语义中和 ⇒ 见 §9bis |
| v2 | 2026-10-15 | **D2-2a 完成**：§7 补充 gate 登录态证据（7 端点 × 6 列；三项独立证据：未命中首页/登录页 + 管理端 API 调用 0 + 唯一入口是登录态"个人中心菜单"）；§8 实施与验收（44/44 + **反向对照实测命中** + 端到端三组探针全通 + 前端 401 跳登录已核实）；提交 `2eddbf8` |
| v1 | 2026-10-15 | D2-1 只读最终审计：14 个 `/list` 全量清单（@IgnoreAuth / 数据表 / PII / 前端调用 / 匿名与登录实测 / DataScope 覆盖）；三组分类 **A=7 收敛 / B=4 公开 / C=2 暂缓 + 1 已收敛**；登记 6 项关键发现与 D2-2 两步实施建议；**零代码改动** |
