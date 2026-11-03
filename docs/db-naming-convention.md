# 数据库与字段命名规范｜db-naming-convention

> **文档性质**：本次命名规范化的**唯一基准**。所有改动（SQL / Java / Mapper XML / 前端）必须逐条对照本文执行。
> **状态**：已获用户批准（2026-09-23）
> **配套**：`project-audit.md`（改造前基线）｜`PROJECT2-MASTER-PLAN.md`（Phase 2）
> **⚠️ 本文推翻了 `PROJECT2-MASTER-PLAN.md` §11 中"不应修改表名字段名"的原决定**，理由：命名拼音化 + 语义错误（exam=测评、order=预约）会在 Agent 化后长期放大成本，且用户将重建数据库，现在是最便宜的改造时点。

---

## 1. 目标与范围

### 1.1 目标

将 16 张表的**拼音化、语义错误、拼写错误**的命名，统一为**英文小写下划线、语义准确、无冗余前缀**的规范命名，并同步更新所有引用方。

### 1.2 范围

| 层 | 涉及 |
| --- | --- |
| 数据库 | `mental_health_companion_agent.sql`（16 表 / 150 列 / 8 类字典编码） |
| 后端 Java | `entity`(16) + `entity/model`(13) + `entity/view`(13) + `entity/vo`(13) + `dao`(17) + `service`(17) + `service/impl`(17) + `controller`(17) + `interceptor`(1) |
| 持久层 | `resources/mapper/*.xml`（12 个含自定义 SQL） |
| 前端 | `front/front/pages/*.html`（49 页）+ `admin/admin/src/**`（55 vue） |

### 1.3 非目标

- 不改业务逻辑、不改接口路径、不改页面功能
- 不改密码存储方式（明文 → 哈希留到 Phase 2）
- 不改数据库引擎、字符集（已是 utf8mb4）

---

## 2. 命名总则

| # | 规则 | 示例 |
| --- | --- | --- |
| R1 | 表名与字段名统一 **英文小写 + 下划线**（snake_case） | `student`、`assessment_paper` |
| R2 | 表名用**单数**、**无冗余前缀**（系统表统一 `sys_`） | `student`、`sys_config` |
| R3 | 字段名**不带表名前缀** | `student.yonghu_name` → `student.name` |
| R4 | 拼音一律替换为英文 | `shanchang` → `expertise` |
| R5 | 时间字段统一 `created_at` / `updated_at` / `<动宾>_at` | `create_time` → `created_at` |
| R6 | 逻辑删除统一 `is_deleted`（0 未删 / 1 已删） | `exampaper_delete` → `is_deleted` |
| R7 | 外键统一 `<被引用表单数>_id` | `yonghu_id` → `student_id` |
| R8 | 字典编码字段去掉 `_types` 后缀，用语义名 | `sex_types` → `gender` |
| R9 | 拼写错误必须修正 | `expiratedtime` → `expired_at` |
| R10 | `*_types`（字典翻译值）→ `*_label` | `sexValue` → `genderLabel` |

---

## 3. 表名映射（16）

| # | 现状 | 规范名 | 说明 |
| --- | --- | --- | --- |
| 1 | `config` | `sys_config` | 系统配置 |
| 2 | `dictionary` | `sys_dict_item` | 字典项 |
| 3 | `token` | `auth_token` | 登录令牌 |
| 4 | `users` | `admin_user` | 管理员 |
| 5 | `yonghu` | `student` | 学生 |
| 6 | `xinlilaoshi` | `counselor` | 心理老师 |
| 7 | `jiankangzhishi` | `knowledge_article` | 健康知识 |
| 8 | `tongzhi` | `notification` | 通知 |
| 9 | `exampaper` | `assessment_paper` | 测评量表（原 exam 语义错） |
| 10 | `examquestion` | `assessment_question` | 测评题目 |
| 11 | `examrecord` | `assessment_record` | 测评记录 |
| 12 | `examredetails` | `assessment_answer` | 答题明细 |
| 13 | `examrewrongquestion` | `assessment_wrong_question` | 错题 |
| 14 | `xinlilaoshi_collection` | `counselor_favorite` | 收藏 |
| 15 | `xinlilaoshi_liuyan` | `counselor_message` | 留言 |
| 16 | `xinlilaoshi_order` | `counseling_appointment` | 预约（原 order 语义错） |

---

## 3.9 关联字段的限定名规则（**重要，实施中发现**）

### 问题

`com.entity.view.*` 与部分列表接口会把**多张 JOIN 表的字段摊平到同一结果集**。例如 `XinlilaoshiOrderView` 同时含：

```java
private String yonghuName;        // 来自 xinlilaoshi_order 关联的 student
private String xinlilaoshiName;   // 来自关联的 counselor
```

若一律"去表名前缀"，两者都会变成 `name` → **字段与方法重复定义**（实测 8 个 View 类、100+ 编译错误）。

### 规则

| 字段来源 | 命名方式 | 示例 |
| --- | --- | --- |
| **当前文件的主表** | **去前缀** | `YonghuEntity.yonghuName` → `name` |
| **关联表（JOIN 进来的）** | **保留限定前缀**，用短限定词 | `XinlilaoshiOrderView.yonghuName` → `studentName` |

### 短限定前缀对照表

| 来源表 | 限定前缀 | 示例 |
| --- | --- | --- |
| `student` | `student` | `yonghuName` → `studentName` |
| `counselor` | `counselor` | `xinlilaoshiName` → `counselorName` |
| `assessment_paper` | `paper` | `exampaperName` → `paperName` |
| `assessment_question` | `question` | `examquestionName` → `questionName` |
| `assessment_record` | `record` | `examrecordUuidNumber` → `recordNo`（主表时）/ `record_no`（关联时） |
| `counseling_appointment` | `appointment` | `xinlilaoshiOrderUuidNumber` → `appointmentNo` |
| `counselor_message` | `message` | `xinlilaoshiLiuyanText` → `messageContent` |
| `counselor_favorite` | `favorite` | — |
| `knowledge_article` | `article` | `jiankangzhishiName` → `articleTitle` |
| `notification` | `notification` | `tongzhiName` → `notificationTitle` |

### 例外：外键列不限定

外键（`*_id`）始终属于**当前主表**，一律去前缀，**不加限定前缀**：

| 现状 | 规范名 |
| --- | --- |
| `exampaper_id` | `paper_id` |
| `examquestion_id` | `question_id` |
| `yonghu_id` | `student_id` |
| `xinlilaoshi_id` | `counselor_id` |

### 连带影响

- **Mapper XML**：`<alias>.<列> as <别名>` 中的**结果集别名**必须使用限定名（如 `student.name as studentName`），而 `WHERE`/`JOIN` 里的**列引用**仍用真列名
- **前端**：控件绑定的字段名随之变为限定名（如 `item.studentName`），与后端保持一致
- **判定依据**：文件的主表由其类名/路径推断（`XinlilaoshiOrder*` → `xinlilaoshi_order`；`pages/yonghu/*` → `yonghu`）

---

## 4. 逐表列映射

### 4.1 `config` → `sys_config`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `name` | `config_key` |
| `value` | `config_value` |

### 4.2 `dictionary` → `sys_dict_item`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `dic_code` | `dict_code` |
| `dic_name` | `dict_name` |
| `code_index` | `item_code` |
| `index_name` | `item_name` |
| `super_id` | `parent_id` |
| `beizhu` | `remark` |
| `create_time` | `created_at` |

### 4.3 `exampaper` → `assessment_paper`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `exampaper_name` | `name` |
| `exampaper_date` | `duration_minutes` |
| `exampaper_myscore` | `total_score` |
| `exampaper_types` | `status` |
| `exampaper_delete` | `is_deleted` |
| `create_time` | `created_at` |

### 4.4 `examquestion` → `assessment_question`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `exampaper_id` | `paper_id` |
| `examquestion_name` | `content` |
| `examquestion_options` | `options` |
| `examquestion_score` | `score` |
| `examquestion_answer` | `answer` |
| `examquestion_analysis` | `analysis` |
| `examquestion_types` | `question_type` |
| `examquestion_sequence` | `sort_order` |
| `create_time` | `created_at` |

### 4.5 `examrecord` → `assessment_record`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `examrecord_uuid_number` | `record_no` |
| `yonghu_id` | `student_id` |
| `exampaper_id` | `paper_id` |
| `total_score` | `total_score` |
| `insert_time` | `submitted_at` |
| `create_time` | `created_at` |

### 4.6 `examredetails` → `assessment_answer`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `examredetails_uuid_number` | `record_no` |
| `yonghu_id` | `student_id` |
| `examquestion_id` | `question_id` |
| `examredetails_myanswer` | `student_answer` |
| `examredetails_myscore` | `score` |
| `create_time` | `created_at` |

### 4.7 `examrewrongquestion` → `assessment_wrong_question`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `yonghu_id` | `student_id` |
| `exampaper_id` | `paper_id` |
| `examquestion_id` | `question_id` |
| `examredetails_myanswer` | `student_answer` |
| `insert_time` | `answered_at` |
| `create_time` | `created_at` |

### 4.8 `jiankangzhishi` → `knowledge_article`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `jiankangzhishi_name` | `title` |
| `jiankangzhishi_photo` | `cover_url` |
| `jiankangzhishi_types` | `category` |
| `insert_time` | `published_at` |
| `jiankangzhishi_content` | `content` |
| `create_time` | `created_at` |

### 4.9 `token` → `auth_token`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `userid` | `user_id` |
| `username` | `username` |
| `tablename` | `user_type` |
| `role` | `role` |
| `token` | `token` |
| `addtime` | `created_at` |
| `expiratedtime` | `expired_at` |

> `user_type` 取值改为规范表名：`admin_user` / `student` / `counselor`（原为 `users` / `yonghu` / `xinlilaoshi`）

### 4.10 `tongzhi` → `notification`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `tongzhi_name` | `title` |
| `tongzhi_photo` | `cover_url` |
| `tongzhi_types` | `category` |
| `insert_time` | `published_at` |
| `tongzhi_content` | `content` |
| `create_time` | `created_at` |

### 4.11 `users` → `admin_user`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `username` | `username` |
| `password` | `password` |
| `role` | `role` |
| `addtime` | `created_at` |

### 4.12 `xinlilaoshi` → `counselor`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `username` | `username` |
| `password` | `password` |
| `xinlilaoshi_name` | `name` |
| `xinlilaoshi_phone` | `phone` |
| `xinlilaoshi_photo` | `avatar_url` |
| `sex_types` | `gender` |
| `xinlilaoshi_email` | `email` |
| `xinlilaoshi_shanchang` | `expertise` |
| `xinlilaoshi_lvli` | `resume` |
| `xinlilaoshi_content` | `introduction` |
| `create_time` | `created_at` |

### 4.13 `xinlilaoshi_collection` → `counselor_favorite`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `xinlilaoshi_id` | `counselor_id` |
| `yonghu_id` | `student_id` |
| `xinlilaoshi_collection_types` | `favorite_type` |
| `insert_time` | `favorited_at` |
| `create_time` | `created_at` |

### 4.14 `xinlilaoshi_liuyan` → `counselor_message`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `xinlilaoshi_id` | `counselor_id` |
| `yonghu_id` | `student_id` |
| `xinlilaoshi_liuyan_text` | `content` |
| `insert_time` | `sent_at` |
| `reply_text` | `reply_content` |
| `update_time` | `replied_at` |
| `create_time` | `created_at` |

### 4.15 `xinlilaoshi_order` → `counseling_appointment`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `xinlilaoshi_order_uuid_number` | `appointment_no` |
| `xinlilaoshi_id` | `counselor_id` |
| `yonghu_id` | `student_id` |
| `yuyue_time` | `appointment_date` |
| `shijianduan_types` | `time_slot` |
| `xinlilaoshi_order_yesno_types` | `status` |
| `xinlilaoshi_order_yesno_text` | `review_comment` |
| `insert_time` | `applied_at` |
| `create_time` | `created_at` |

### 4.16 `yonghu` → `student`

| 现状 | 规范名 |
| --- | --- |
| `id` | `id` |
| `username` | `username` |
| `password` | `password` |
| `yonghu_name` | `name` |
| `yonghu_phone` | `phone` |
| `yonghu_id_number` | `id_card_no` |
| `yonghu_photo` | `avatar_url` |
| `sex_types` | `gender` |
| `yonghu_email` | `email` |
| `create_time` | `created_at` |

---

## 5. 字典编码映射（`sys_dict_item.dict_code`）

| 现状 | 规范名 | 对应字段 |
| --- | --- | --- |
| `sex_types` | `gender` | `student.gender`、`counselor.gender` |
| `xinlilaoshi_collection_types` | `counselor_favorite_type` | `counselor_favorite.favorite_type` |
| `xinlilaoshi_order_yesno_types` | `appointment_status` | `counseling_appointment.status` |
| `shijianduan_types` | `time_slot` | `counseling_appointment.time_slot` |
| `tongzhi_types` | `notification_category` | `notification.category` |
| `jiankangzhishi_types` | `knowledge_category` | `knowledge_article.category` |
| `examquestion_types` | `question_type` | `assessment_question.question_type` |
| `exampaper_types` | `assessment_paper_status` | `assessment_paper.status` |

---

## 6. 字典翻译机制（必须一并重构）

**现状问题**：`DictionaryServiceImpl.dictionaryConvert` 用**反射 + 属性名后缀 `Types`** 识别待翻译字段，并写入同名的 `xxxValue` 字段。

改名后该启发式**必然失效**（`gender`、`status`、`category` 都不含 `Types`）。

**改造方案**：改为**显式映射**，并把翻译结果字段统一命名为 `<字段>Label`：

| 表 | 编码字段 | 翻译结果字段 |
| --- | --- | --- |
| `student` | `gender` | `genderLabel` |
| `counselor` | `gender` | `genderLabel` |
| `assessment_paper` | `status` | `statusLabel` |
| `assessment_question` | `question_type` | `questionTypeLabel` |
| `knowledge_article` | `category` | `categoryLabel` |
| `notification` | `category` | `categoryLabel` |
| `counselor_favorite` | `favorite_type` | `favoriteTypeLabel` |
| `counseling_appointment` | `time_slot` | `timeSlotLabel` |
| `counseling_appointment` | `status` | `statusLabel` |

具体实现形式（配置表 / 注解 / 常量映射）在 Phase 2 落地时确定，但**必须去掉"按名字猜"的反射逻辑**。

---

## 7. 执行顺序（分批，每批独立验证 + 提交）

| 批次 | 内容 | 验证方式 |
| --- | --- | --- |
| **B1** | SQL 脚本（16 表 + 字典数据）+ `entity`(16) + `model`(13) + `view`(13) + `vo`(13) | `mvn -DskipTests compile` 通过 |
| **B2** | `resources/mapper/*.xml`(12) + `dao`(17) | 编译通过 |
| **B3** | `service`(17) + `impl`(17) + `controller`(17) + `interceptor`(1) + 字典翻译重构 | 编译 + 启动 + 5 项冒烟测试 |
| **B4** | 前端字段适配：`front/pages/*.html`(49) + `admin/admin/src/**`(55 vue) | 页面可访问、字段渲染正常 |
| **B5** | 管理端重新构建 `dist`（如需） | `/admin/dist/index.html` 返回 200 |

**每批完成后**：构建 → 测试 → 展示改动清单 → 用户确认 → commit + push（中文概括信息）。

---

## 8. 验收标准

| # | 标准 |
| --- | --- |
| 1 | `mental_health_companion_agent.sql` 可在全新数据库完整导入，16 张表全部为规范命名 |
| 2 | 全仓搜索旧表名/旧字段名（`yonghu`、`xinlilaoshi`、`exam`、`tongzhi`、`jiankangzhishi`、`*_types` 等）**零命中**（本文档与历史审计文档除外） |
| 3 | `mvn -DskipTests clean package` 构建成功 |
| 4 | 应用启动成功，无 MyBatis 绑定异常 |
| 5 | 核心接口冒烟通过：登录 / 知识列表 / 带 Token 分页 / 管理端静态页 |
| 6 | Java 类名与表名语义一致（取决于第 9 节决策） |

---

## 9. 已决策项（2026-09-23 定）

| # | 问题 | 决策 | 影响 |
| --- | --- | --- | --- |
| D1 | Java 类名是否一并重命名 | **类名保留**（`YonghuEntity` 仍叫此名），只改 **表名、列名、Java 属性名**（`@TableName("student")`、属性 `name`/`gender`） | 涉及文件数从 ~200 降到 ~110；类名与表名暂不一致，留待 Phase 2 分层重构处理 |
| D2 | `admin/dist` 编译产物 | **暂不处理**，等 Phase 3（Vue 3 重建前端） | 本阶段 `admin/dist` 页面会因字段名不匹配而渲染异常，属**已知且接受**的代价 |
| D3 | 字典翻译机制 | **必须重构为显式映射**（非可选） | `*Value` → `*Label`；B3 批次执行 |

### 9.1 附带说明：接口路径保持不变

本次**不改 HTTP 接口路径**（如 `/yonghu/login`、`/yonghu/page`）。因此会出现"表叫 `student`、接口路径仍叫 `yonghu`"的不一致——这是**有意为之**，因为：

- 改路径会连带前端所有调用与 `@RequestMapping`，属于 Phase 3 前端重构的范畴；
- 本阶段目标是"后端能跑 + 数据库命名规范"，路径不在其中。

若后续要把路径也改为 `/student/*`，需单独提出，成本主要在 `controller` 的 `@RequestMapping` 与前端调用点。

### 9.2 附带说明：`user_type` 取值变更

`auth_token.user_type`（原 `tablename`）的取值由旧表名改为新表名：

| 旧值 | 新值 |
| --- | --- |
| `users` | `admin_user` |
| `yonghu` | `student` |
| `xinlilaoshi` | `counselor` |

**连带影响**：`AuthorizationInterceptor` 写入 Session 的 `tableName`，以及各 Controller 中形如 `"yonghu".equals(tableName)` 的角色判断，**必须同步改为新值**。这是 B3 批次的必改项。

---

## 10. 执行记录（2026-09-23 已完成）

| 项 | 结果 |
| --- | --- |
| 数据库脚本 | 16 表 / 150 列全部改名，utf8mb4；临时库导入验证通过 |
| Java 代码 | **76 个文件** |
| Mapper XML | **16 个文件** |
| 前端 | 49 个 html + 46 个 vue（**不在版本控制内**，本地工作区已同步） |
| 字典翻译机制 | 由"按 `Types` 后缀反射猜测"重构为**显式映射**（`DictionaryServiceImpl.DICT_CODE`） |
| 构建 | ✅ `BUILD SUCCESS` |
| 冒烟测试 | ✅ 三角色登录 + 学生/预约分页 + `config/list` + 管理端静态页，**全部通过** |
| 旧命名残留 | ⚠️ 后端代码**零残留**；用户端静态页存在**遗漏**（见 §10.2，已修复） |
| 临时资源 | ✅ 迁移脚本、备份、临时库均已清理 |

### 10.1 实施中踩到的坑（已全部修正，供后续参考）

1. **PowerShell `-replace` 默认忽略大小写** —— 必须用 `-creplace`，否则 `tablename` 规则会把 `@TableName` 误改成 `@userType`
2. **XML 必须先改 `JOIN <表> <同名别名>` 再改表名** —— 否则出现 `JOIN student yonghu ON a.student_id = student.id` 这类别名与引用不一致的失效 SQL
3. **`EntityWrapper.eq("列", ...)` 里的字符串是真实列名** —— 不能套用驼峰替换（曾把 `"userid"` 改成 `"userId"` 导致登录 500）
4. **`@RequestMapping("users"/"config")` 是接口路径，不是表名** —— 曾误改为 `admin_user` / `sys_config`
5. **`"token"` 是表名 + 列名 + JSON 响应键三重身份** —— 替换时必须排除
6. **关联字段必须带限定前缀** —— 否则 `XinlilaoshiOrderView` 等视图类字段冲突（8 个类、100+ 编译错误）
7. **`insert_time` 语义随表而异** —— 需要"文件级映射 + 跨实体引用"双重处理
8. **前端主表判定不能用全路径扫描** —— Windows 路径中的 `C:\Users\...` 会被误匹配成 `users` 表

### 10.2 补验与残留修复（2026-09-23 晚｜数据库重建后）

> **背景**：§10 记录的是「代码改完 + 临时库冒烟」。当时**本地正式库尚未用新脚本重建**（仍是旧表名），因此「零残留」的结论是在**旧结构库**上得出的，且未覆盖前端字面量。用户重建库后补做全量验证，发现以下遗漏并已修复。

**残留 1：用户端静态页的旧字典编码（21 处 / 20 个页面）**

`dictionary/page?...&dictCode=<旧码>` 中的 8 类字典码仍是旧值，导致**筛选与新增/编辑表单的下拉框为空**：

| 旧编码 | 新编码 | 涉及页面数 |
| --- | --- | --- |
| `sex_types` | `gender` | 8 |
| `shijianduan_types` | `time_slot` | 3 |
| `xinlilaoshi_order_yesno_types` | `appointment_status` | 3 |
| `xinlilaoshi_collection_types` | `counselor_favorite_type` | 2 |
| `tongzhi_types` | `notification_category` | 2 |
| `jiankangzhishi_types` | `knowledge_category` | 2 |
| `examquestion_types` | `question_type` | 2 |
| `exampaper_types` | `assessment_paper_status` | 1 |

**残留 2：用户端静态页的 `insertTime`（14 处 / 12 个页面）**

Java 属性已按表语义改名，前端仍读 `item.insertTime` → 时间列**渲染为空白**：

| 页面 | 正确字段 |
| --- | --- |
| `home/home.html`、`jiankangzhishi/{detail,add}.html`、`tongzhi/{detail,add}.html` | `publishedAt` |
| `xinlilaoshiOrder/{list,add}.html` | `appliedAt` |
| `xinlilaoshiLiuyan/add.html` | `sentAt` |
| `xinlilaoshiCollection/add.html` | `favoritedAt` |
| `examrewrongquestion/{list,add}.html` | `answeredAt` |
| `examrecord/add.html` | `submittedAt` |

**修复与验证结果**

| 项 | 结果 |
| --- | --- |
| 改动范围 | **26 个 html**（仅用户端静态页，后端与 Mapper 未动） |
| 旧引用残留 | `dictCode=<旧码>` **0 处**、`insertTime` **0 处** |
| 字典接口实测 | `gender`=2、`time_slot`=8、`appointment_status`=3、`counselor_favorite_type`=1、`notification_category`=3、`knowledge_category`=2、`question_type`=4、`assessment_paper_status`=2 —— **全部命中** |
| 字段名实测 | 预约列表返回 `appliedAt`、通知列表返回 `publishedAt`，与前端口径**完全匹配** |
| 构建 / 启动 | ✅ `BUILD SUCCESS`；启动 6.4s；全流程 **无 ERROR** |

> ⚠️ **教训**：字典编码属于**数据层命名**，容易逃过「只扫代码标识符」的替换脚本。前端 query string、配置项、文档字面量必须一并纳入检查清单。

### 10.3 已知未修项：管理端图表空壳参数（**非本次重命名引入**）

`admin/src/views/modules/*/list.vue` 的 **8 个**图表块仍传入 `tableName:'yonghu'`、`sumColum:'yonghu_number'`、`date:'insert_time'`。

经核对：`yonghu_number`、`jiankangzhishi_number` 这类「数值列」**在旧库中同样从未存在**（`student` 实际列为 `id/username/password/name/phone/id_card_no/avatar_url/gender/email/created_at`），故该图表功能**在改造之前即已失效**，属代码生成器遗留空壳，**不是本次重命名的回归**。

按 §9 的 **D2 决策**，管理端前端将在 **Phase 3（Vue 3 重建）** 一并重新设计图表，届时清理。

> 另：`admin/dist/`（已编译产物）同样因字段改名而与新后端不匹配，同属 D2 已接受代价。

### 10.4 回滚风险：前端与文档不在版本控制内

`.gitignore` 明确排除以下路径（`docs/` 已定为**永久排除**，见下方注）：

```text
src/main/resources/front/           # 用户端 Layui 静态页（本轮修复的 26 个 html 在此）
src/main/resources/static/          # 上传资源
src/main/resources/img/
src/main/resources/admin/admin/src/ # 管理端 Vue 源码
docs/                               # 本目录全部文档
```

**后果**：本轮的前端修复与文档更新**无法通过 `git revert` 回退**。在 Phase 3 前端重构之前，前端改动必须先自行备份。

> **`docs/` 的最终决定（2026-10-05）**：**不纳入版本控制、不上传 GitHub**（原因：文档含明文凭据与未修复漏洞的攻击细节，上传公开仓库等同漏洞披露）。
> 2026-10-05 曾误推送到 GitHub，已用 `git push --force-with-lease` 重写远程分支历史移除，并以 `gc --prune=now` 清除本地对象。
> **文档备份请走离线副本**（打包到私有位置），不要走本仓库的 Git。规则见 `AGENT-EXECUTION-RULES.md` §9.1。

---

## 11. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1 | 2026-09-23 | 首次制定；16 表 / 150 列 / 8 类字典编码映射；已获用户批准 |
| v2 | 2026-09-23 | 数据库按新脚本重建后补验：新增 §10.2（残留修复与验证）、§10.3（管理端图表空壳，改造前已失效）、§10.4（前端不在版本控制内的回滚风险）；修正 §10「零残留」结论 |
