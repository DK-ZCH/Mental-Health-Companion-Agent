package com.entity.model;

import com.entity.ExamrecordEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 考试记录表
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class ExamrecordModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 考试编号
     */
    private String recordNo;


    /**
     * 考试学生
     */
    private Integer studentId;


    /**
     * 所属试卷id（外键）
     */
    private Integer paperId;


    /**
     * 所得总分
     */
    private Integer totalScore;


    /**
     * 考试时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date submittedAt;


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
	 * 获取：考试编号
	 */
    public String getRecordNo() {
        return recordNo;
    }


    /**
	 * 设置：考试编号
	 */
    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }
    /**
	 * 获取：考试学生
	 */
    public Integer getStudentId() {
        return studentId;
    }


    /**
	 * 设置：考试学生
	 */
    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 获取：所属试卷id（外键）
	 */
    public Integer getPaperId() {
        return paperId;
    }


    /**
	 * 设置：所属试卷id（外键）
	 */
    public void setPaperId(Integer paperId) {
        this.paperId = paperId;
    }
    /**
	 * 获取：所得总分
	 */
    public Integer getTotalScore() {
        return totalScore;
    }


    /**
	 * 设置：所得总分
	 */
    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }
    /**
	 * 获取：考试时间
	 */
    public Date getSubmittedAt() {
        return submittedAt;
    }


    /**
	 * 设置：考试时间
	 */
    public void setSubmittedAt(Date submittedAt) {
        this.submittedAt = submittedAt;
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
