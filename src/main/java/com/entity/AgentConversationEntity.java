package com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 会话（Phase 4 · Step ⑤ 多轮会话）。
 *
 * <p><b>归属即安全</b>：{@code studentId} 只能来自登录态；
 * 携带 {@code id} 的每次请求都必须校验归属，否则 403。
 */
@TableName("agent_conversation")
public class AgentConversationEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @TableField("id")
    private Long id;

    /** 归属学生（服务端写入，绝不来自请求体） */
    @TableField("student_id")
    private Integer studentId;

    /** 会话标题（首条消息前 20 字） */
    @TableField("title")
    private String title;

    @TableField("created_at")
    private Date createdAt;

    /** 最近一次消息时间（会话列表排序用） */
    @TableField("last_msg_at")
    private Date lastMsgAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getStudentId() { return studentId; }
    public void setStudentId(Integer studentId) { this.studentId = studentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getLastMsgAt() { return lastMsgAt; }
    public void setLastMsgAt(Date lastMsgAt) { this.lastMsgAt = lastMsgAt; }
}
