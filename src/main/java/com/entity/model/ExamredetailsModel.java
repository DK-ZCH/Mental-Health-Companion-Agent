package com.entity.model;

import com.entity.ExamredetailsEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 答题详情表
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class ExamredetailsModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 试卷编号
     */
    private String recordNo;


    /**
     * 学生id
     */
    private Integer studentId;


    /**
     * 试题id（外键）
     */
    private Integer questionId;


    /**
     * 考生答案
     */
    private String studentAnswer;


    /**
     * 试题得分
     */
    private Integer score;


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
	 * 获取：试卷编号
	 */
    public String getRecordNo() {
        return recordNo;
    }


    /**
	 * 设置：试卷编号
	 */
    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
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
	 * 获取：考生答案
	 */
    public String getStudentAnswer() {
        return studentAnswer;
    }


    /**
	 * 设置：考生答案
	 */
    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }
    /**
	 * 获取：试题得分
	 */
    public Integer getScore() {
        return score;
    }


    /**
	 * 设置：试题得分
	 */
    public void setScore(Integer score) {
        this.score = score;
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
