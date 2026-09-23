package com.entity.vo;

import com.entity.XinlilaoshiLiuyanEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 心理老师留言
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("counselor_message")
public class XinlilaoshiLiuyanVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

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

    @TableField(value = "sent_at")
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

    @TableField(value = "replied_at")
    private Date repliedAt;


    /**
     * 创建时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat

    @TableField(value = "created_at")
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

}
