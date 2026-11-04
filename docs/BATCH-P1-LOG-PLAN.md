# P1 敏感 DAO 日志治理 —— 精简实施计划（零代码 · 停等批准）

> **性质**：📋 **精简计划**（按用户新工作原则：功能/稳定优先 · 测试适度 · 不扩大范围）
> **基线**：`f07100d` 不动 · 本计划零代码
> **与上版差异**：修正数量不一致（实为 **9 个待降级**，上版误写 8）；**不再新增测试类**，
> 实施后以冒烟 + 探针词 grep 验证即可。

---

## 一、全量核实结果（18 DAO = 16 XML + 2 无 XML）

| 核验项 | 结果 |
| --- | --- |
| XML namespace | 16 个全部 = `com.dao.XxxDao`，与 DAO 接口一一对应 ✅ |
| 无 XML 的 2 个 | `AgentConversationDao`/`AgentMessageDao`（MyBatis-Plus 纯接口）—— logger 名 = 接口 FQN，`com.dao.AgentConversationDao: warn` 已实测生效（0 泄露）✅ |
| 数量澄清 | 上版「P1×8」**有误**：精确为 **9 个待降级**（下表）+ 3 个已降级（AuthToken/两会话 DAO）+ 6 个维持 debug |

---

## 二、精确分级清单（9 降 + 6 维持）

### 待降级（9 个，yml 追加 9 行）

| # | DAO | 敏感参数（实体取证）| 触发业务 |
| :-: | --- | --- | --- |
| 1 | `CounselorMessageDao` | content · replyContent（用户留言+咨询师回复）| 留言保存/更新 |
| 2 | `AssessmentAnswerDao` | studentAnswer · score | 测评提交 |
| 3 | `AssessmentWrongQuestionDao` | studentAnswer | 错题记录 |
| 4 | `AssessmentQuestionDao` | **answer（题目正确答案）** · content | 试卷管理 |
| 5 | `AdminUserDao` | username · password | 管理员登录/管理 |
| 6 | `CounselorDao` | username · password · phone · email | 咨询师登录/管理 |
| 7 | `StudentDao` | username · password · phone · email | 学生登录/注册 |
| 8 | `AssessmentRecordDao` | totalScore（成绩）| 测评提交 |
| 9 | `CounselingAppointmentDao` | appointmentNo · reviewComment · status | 预约创建/审核 |

### 维持 debug（6 个，公开/低敏 —— 保留调试能力）

```text
AssessmentPaperDao（试卷名/总分，公开）· CounselorFavoriteDao（仅 id/类型参数）
KnowledgeArticleDao（公开语料）· NotificationDao（公告）· SysDictItemDao · SysConfigDao
```

### yml 变更（9 行，追加至现有 logging 块）

```yaml
    com.dao.CounselorMessageDao: warn
    com.dao.AssessmentAnswerDao: warn
    com.dao.AssessmentWrongQuestionDao: warn
    com.dao.AssessmentQuestionDao: warn
    com.dao.AdminUserDao: warn
    com.dao.CounselorDao: warn
    com.dao.StudentDao: warn
    com.dao.AssessmentRecordDao: warn
    com.dao.CounselingAppointmentDao: warn
```

---

## 三、精简验证方案（不再新增测试类 · 不做变异矩阵）

| 步骤 | 验证 |
| :-: | --- |
| 1 | yml 追加 9 行 → 重启 |
| 2 | **冒烟**：登录 ✅ · /agent/chat ✅ · 测评列表 ✅ · 知识列表 ✅ —— 正常业务不受影响 |
| 3 | **探针 grep**：日志中 9 个 DAO 的 DEBUG 行 = 0（降级生效）· KnowledgeArticleDao DEBUG 行仍 > 0（低敏对照，未全局关闭）|
| 4 | 全量回归 332/332 · DB 基线核对 |

> 按用户新原则：不做 OutputCapture 测试类、不做逐 DAO 变异矩阵。
> 「还原变异」保留一个极简手动核验（临时还原 1 个 DAO 验证探针重现即可，或直接以步骤 3 双向证据为准）。

---

## 四、待批准项

| # | 项 | 建议 |
| :-: | --- | :-: |
| 1 | 9 个 DAO 降级清单（修正后数量）| ✅ |
| 2 | 6 个维持 debug（低敏对照保留）| ✅ |
| 3 | 精简验证（冒烟 + grep，无新测试类）| ✅ |
| 4 | 实施后与后续批次文档一起 commit | ✅ |
