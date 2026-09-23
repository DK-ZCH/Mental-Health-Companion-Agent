package com.entity;

import com.baomidou.mybatisplus.annotations.TableId;
import com.baomidou.mybatisplus.annotations.TableName;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.lang.reflect.InvocationTargetException;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.beanutils.BeanUtils;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.enums.FieldFill;
import com.baomidou.mybatisplus.enums.IdType;

/**
 * 心理老师留言
 *
 * @author 
 * @email
 */
@TableName("counselor_message")
public class XinlilaoshiLiuyanEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public XinlilaoshiLiuyanEntity() {

	}

	public XinlilaoshiLiuyanEntity(T t) {
		try {
			BeanUtils.copyProperties(this, t);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")

    private Integer id;


    /**
     * 心理老师
     */
    @TableField(value = "counselor_id")

    private Integer counselorId;


    /**
     * 学生
     */
    @TableField(value = "student_id")

    private Integer studentId;


    /**
     * 留言内容
     */
    @TableField(value = "content")

    private String content;


    /**
     * 留言时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    @TableField(value = "sent_at",fill = FieldFill.INSERT)

    private Date sentAt;


    /**
     * 回复内容
     */
    @TableField(value = "reply_content")

    private String replyContent;


    /**
     * 回复时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    @TableField(value = "replied_at",fill = FieldFill.UPDATE)

    private Date repliedAt;


    /**
     * 创建时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    @TableField(value = "created_at",fill = FieldFill.INSERT)

    private Date createdAt;


    /**
	 * 设置：主键
	 */
    public Integer getId() {
        return id;
    }
    /**
	 * 获取：主键
	 */

    public void setId(Integer id) {
        this.id = id;
    }
    /**
	 * 设置：心理老师
	 */
    public Integer getCounselorId() {
        return counselorId;
    }
    /**
	 * 获取：心理老师
	 */

    public void setCounselorId(Integer counselorId) {
        this.counselorId = counselorId;
    }
    /**
	 * 设置：学生
	 */
    public Integer getStudentId() {
        return studentId;
    }
    /**
	 * 获取：学生
	 */

    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 设置：留言内容
	 */
    public String getContent() {
        return content;
    }
    /**
	 * 获取：留言内容
	 */

    public void setContent(String content) {
        this.content = content;
    }
    /**
	 * 设置：留言时间
	 */
    public Date getSentAt() {
        return sentAt;
    }
    /**
	 * 获取：留言时间
	 */

    public void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }
    /**
	 * 设置：回复内容
	 */
    public String getReplyContent() {
        return replyContent;
    }
    /**
	 * 获取：回复内容
	 */

    public void setReplyContent(String replyContent) {
        this.replyContent = replyContent;
    }
    /**
	 * 设置：回复时间
	 */
    public Date getRepliedAt() {
        return repliedAt;
    }
    /**
	 * 获取：回复时间
	 */

    public void setRepliedAt(Date repliedAt) {
        this.repliedAt = repliedAt;
    }
    /**
	 * 设置：创建时间
	 */
    public Date getCreatedAt() {
        return createdAt;
    }
    /**
	 * 获取：创建时间
	 */

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "XinlilaoshiLiuyan{" +
            "id=" + id +
            ", counselorId=" + counselorId +
            ", studentId=" + studentId +
            ", content=" + content +
            ", sentAt=" + sentAt +
            ", replyContent=" + replyContent +
            ", repliedAt=" + repliedAt +
            ", createdAt=" + createdAt +
        "}";
    }
}
