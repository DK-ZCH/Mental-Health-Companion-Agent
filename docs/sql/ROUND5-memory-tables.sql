-- ============================================================================
-- Phase 4 · Step ⑤ 多轮会话（Memory + 会话体系）
--
-- 职责分离（用户 2026-11-03 冻结）：
--   agent_message        = 持久化聊天历史（完整，用户可见）
--   ChatMemoryRepository = 读写该历史的适配层（实现 Spring AI 接口）
--   MessageWindowChatMemory = 「模型上下文窗口」策略（最多 20 条，淘汰由它负责）
--   ⇒ 窗口调整不需要动数据库历史；永久保存 ≠ 永久作为 LLM 上下文
--
-- 归属（防 IDOR）：
--   agent_conversation.student_id = 当前学生；携带 conversationId 的每次请求
--   都必须校验归属，否则 403（不存在同样 403，防会话存在性预言机）
--
-- ⚠️ 本项目无 Flyway/Liquibase：需手工执行，并在交付文档记录执行时间与结果。
-- ============================================================================

-- ---------- 正向 ----------
CREATE TABLE agent_conversation (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  student_id  INT          NOT NULL COMMENT '归属学生（来自登录态，绝不来自请求体）',
  title       VARCHAR(100) NULL     COMMENT '会话标题（首条消息前 20 字）',
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_msg_at TIMESTAMP    NULL,
  PRIMARY KEY (id),
  KEY idx_agent_conv_student (student_id, last_msg_at)
) COMMENT 'Agent 会话（学生域，归属强校验）';

CREATE TABLE agent_message (
  id              BIGINT   NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT   NOT NULL,
  role            VARCHAR(10) NOT NULL COMMENT 'user / assistant',
  content         TEXT     NOT NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_agent_msg_conv (conversation_id, id),
  CONSTRAINT fk_agent_msg_conv FOREIGN KEY (conversation_id)
    REFERENCES agent_conversation (id)
) COMMENT 'Agent 会话消息（user/assistant，不含 Tool 中间消息）';

-- ---------- 回滚 ----------
-- DROP TABLE IF EXISTS agent_message;
-- DROP TABLE IF EXISTS agent_conversation;
