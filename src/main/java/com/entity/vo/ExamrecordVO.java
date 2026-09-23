package com.entity.vo;

import com.entity.ExamrecordEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 考试记录表
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("assessment_record")
public class ExamrecordVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

    @TableField(value = "id")
    private Integer id;


    /**
     * 考试编号
     */

    @TableField(value = "record_no")
    private String recordNo;


    /**
     * 考试学生
     */

    @TableField(value = "student_id")
    private Integer studentId;


    /**
     * 所属试卷id（外键）
     */

    @TableField(value = "paper_id")
    private Integer paperId;


    /**
     * 所得总分
     */

    @TableField(value = "total_score")
    private Integer totalScore;


    /**
     * 考试时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat

    @TableField(value = "submitted_at")
    private Date submittedAt;


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
	 * 设置：考试编号
	 */
    public String getRecordNo() {
        return recordNo;
    }


    /**
	 * 获取：考试编号
	 */

    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }
    /**
	 * 设置：考试学生
	 */
    public Integer getStudentId() {
        return studentId;
    }


    /**
	 * 获取：考试学生
	 */

    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 设置：所属试卷id（外键）
	 */
    public Integer getPaperId() {
        return paperId;
    }


    /**
	 * 获取：所属试卷id（外键）
	 */

    public void setPaperId(Integer paperId) {
        this.paperId = paperId;
    }
    /**
	 * 设置：所得总分
	 */
    public Integer getTotalScore() {
        return totalScore;
    }


    /**
	 * 获取：所得总分
	 */

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }
    /**
	 * 设置：考试时间
	 */
    public Date getSubmittedAt() {
        return submittedAt;
    }


    /**
	 * 获取：考试时间
	 */

    public void setSubmittedAt(Date submittedAt) {
        this.submittedAt = submittedAt;
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
