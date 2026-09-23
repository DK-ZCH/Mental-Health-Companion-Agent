package com.entity.model;

import com.entity.XinlilaoshiLiuyanEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 心理老师留言
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class XinlilaoshiLiuyanModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 心理老师
     */
    private Integer counselorId;


    /**
     * 学生
     */
    private Integer studentId;


    /**
     * 留言内容
     */
    private String content;


    /**
     * 留言时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date sentAt;


    /**
     * 回复内容
     */
    private String replyContent;


    /**
     * 回复时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date repliedAt;


    /**
     * 创建时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date createdAt;


    /**
	 * 获取：主键
	 */
    public Integer getId() {
        return id;
    }


    /**
	 * 设置：主键
	 */
    public void setId(Integer id) {
        this.id = id;
    }
    /**
	 * 获取：心理老师
	 */
    public Integer getCounselorId() {
        return counselorId;
    }


    /**
	 * 设置：心理老师
	 */
    public void setCounselorId(Integer counselorId) {
        this.counselorId = counselorId;
    }
    /**
	 * 获取：学生
	 */
    public Integer getStudentId() {
        return studentId;
    }


    /**
	 * 设置：学生
	 */
    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 获取：留言内容
	 */
    public String getContent() {
        return content;
    }


    /**
	 * 设置：留言内容
	 */
    public void setContent(String content) {
        this.content = content;
    }
    /**
	 * 获取：留言时间
	 */
    public Date getSentAt() {
        return sentAt;
    }


    /**
	 * 设置：留言时间
	 */
    public void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }
    /**
	 * 获取：回复内容
	 */
    public String getReplyContent() {
        return replyContent;
    }


    /**
	 * 设置：回复内容
	 */
    public void setReplyContent(String replyContent) {
        this.replyContent = replyContent;
    }
    /**
	 * 获取：回复时间
	 */
    public Date getRepliedAt() {
        return repliedAt;
    }


    /**
	 * 设置：回复时间
	 */
    public void setRepliedAt(Date repliedAt) {
        this.repliedAt = repliedAt;
    }
    /**
	 * 获取：创建时间
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 设置：创建时间
	 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    }
