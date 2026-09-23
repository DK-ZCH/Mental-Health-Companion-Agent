package com.entity.model;

import com.entity.ExamrewrongquestionEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 错题表
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class ExamrewrongquestionModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 学生id
     */
    private Integer studentId;


    /**
     * 试卷（外键）
     */
    private Integer paperId;


    /**
     * 试题id（外键）
     */
    private Integer questionId;


    /**
     * 考生作答
     */
    private String studentAnswer;


    /**
     * 记录时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date answeredAt;


    /**
     * 创建时间 show3
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
	 * 获取：学生id
	 */
    public Integer getStudentId() {
        return studentId;
    }


    /**
	 * 设置：学生id
	 */
    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 获取：试卷（外键）
	 */
    public Integer getPaperId() {
        return paperId;
    }


    /**
	 * 设置：试卷（外键）
	 */
    public void setPaperId(Integer paperId) {
        this.paperId = paperId;
    }
    /**
	 * 获取：试题id（外键）
	 */
    public Integer getQuestionId() {
        return questionId;
    }


    /**
	 * 设置：试题id（外键）
	 */
    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }
    /**
	 * 获取：考生作答
	 */
    public String getStudentAnswer() {
        return studentAnswer;
    }


    /**
	 * 设置：考生作答
	 */
    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }
    /**
	 * 获取：记录时间
	 */
    public Date getAnsweredAt() {
        return answeredAt;
    }


    /**
	 * 设置：记录时间
	 */
    public void setAnsweredAt(Date answeredAt) {
        this.answeredAt = answeredAt;
    }
    /**
	 * 获取：创建时间 show3
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 设置：创建时间 show3
	 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    }
