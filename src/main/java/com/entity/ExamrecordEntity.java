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
 * 考试记录表
 *
 * @author 
 * @email
 */
@TableName("assessment_record")
public class ExamrecordEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public ExamrecordEntity() {

	}

	public ExamrecordEntity(T t) {
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
    @TableField(value = "submitted_at",fill = FieldFill.INSERT)

    private Date submittedAt;


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

    @Override
    public String toString() {
        return "Examrecord{" +
            "id=" + id +
            ", recordNo=" + recordNo +
            ", studentId=" + studentId +
            ", paperId=" + paperId +
            ", totalScore=" + totalScore +
            ", submittedAt=" + submittedAt +
            ", createdAt=" + createdAt +
        "}";
    }
}
