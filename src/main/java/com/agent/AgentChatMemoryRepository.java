package com.agent;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ChatMemoryRepository} 的自建实现（Phase 4 · Step ⑤ · 用户裁定 D1=A）。
 *
 * <p><b>为什么不引官方 JDBC starter</b>：官方默认表没有归属字段（student_id），
 * 而「conversation → student」的归属正是本步的安全核心（防 IDOR）。
 * 自建实现把归属、时间戳、清理策略都放在可控的表结构里，且<b>零新增依赖</b>。
 *
 * <p><b>saveAll 语义（整窗替换）</b>：先删后插。
 * {@code MessageWindowChatMemory} 每次传入的是裁剪后的完整窗口，
 * 替换语义保证窗口裁剪与删除在存储层自然生效（T9 变异可证）。
 *
 * <p><b>安全边界</b>：{@link #findConversationIds()} 返回全部会话 id，
 * 该方法<b>仅供框架内部使用，绝不对外暴露</b>（接口无用户上下文，无法做归属过滤）。
 */
public class AgentChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        // 🔴 仅供框架内部使用：不区分用户（接口无用户上下文）。绝不暴露为端点。
        return jdbcTemplate.queryForList("SELECT id FROM agent_conversation ORDER BY id")
                .stream().map(String::valueOf).toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Long id = parse(conversationId);
        if (id == null) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT role, content FROM agent_message WHERE conversation_id = ? ORDER BY id ASC",
                rs -> {
                    Message message = toMessage(rs.getString("role"), rs.getString("content"));
                    if (message != null) {
                        messages.add(message);
                    }
                },
                id);
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Long id = parse(conversationId);
        if (id == null) {
            return;
        }
        // 整窗替换：先删后插（T9 验证窗口裁剪在存储层自然生效）
        jdbcTemplate.update("DELETE FROM agent_message WHERE conversation_id = ?", id);
        for (Message message : messages) {
            String role = roleOf(message);
            if (role == null) {
                continue;   // SYSTEM / TOOL 不落库（Memory 只保存 user/assistant）
            }
            jdbcTemplate.update(
                    "INSERT INTO agent_message (conversation_id, role, content, created_at) "
                            + "VALUES (?, ?, ?, NOW())",
                    id, role, message.getText());
        }
        jdbcTemplate.update("UPDATE agent_conversation SET last_msg_at = NOW() WHERE id = ?", id);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Long id = parse(conversationId);
        if (id != null) {
            jdbcTemplate.update("DELETE FROM agent_message WHERE conversation_id = ?", id);
        }
    }

    /** user / assistant 之外的角色（SYSTEM / TOOL）不进入持久化历史 */
    private String roleOf(Message message) {
        MessageType type = message.getMessageType();
        if (type == MessageType.USER) {
            return "user";
        }
        if (type == MessageType.ASSISTANT) {
            return "assistant";
        }
        return null;
    }

    private Message toMessage(String role, String content) {
        if ("user".equals(role)) {
            return new UserMessage(content);
        }
        if ("assistant".equals(role)) {
            return new AssistantMessage(content);
        }
        return null;
    }

    private Long parse(String conversationId) {
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
