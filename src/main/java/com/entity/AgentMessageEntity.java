package com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 会话消息（Phase 4 · Step ⑤）。
 *
 * <p><b>职责</b>：持久化聊天历史（用户可见），同时也是
 * {@code ChatMemoryRepository} 读取「模型上下文窗口」的数据来源。
 * 不保存 Tool calling 中间消息（Observability 负责工具执行情况）。
 */
@TableName("agent_message")
public class AgentMessageEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @TableField("id")
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    /** user / assistant */
    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("created_at")
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
