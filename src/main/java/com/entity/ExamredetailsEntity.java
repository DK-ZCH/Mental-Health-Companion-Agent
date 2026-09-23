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
 * 答题详情表
 *
 * @author 
 * @email
 */
@TableName("assessment_answer")
public class ExamredetailsEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public ExamredetailsEntity() {

	}

	public ExamredetailsEntity(T t) {
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
     * 试卷编号
     */
    @TableField(value = "record_no")

    private String recordNo;


    /**
     * 学生id
     */
    @TableField(value = "student_id")

    private Integer studentId;


    /**
     * 试题id（外键）
     */
    @TableField(value = "question_id")

    private Integer questionId;


    /**
     * 考生答案
     */
    @TableField(value = "student_answer")

    private String studentAnswer;


    /**
     * 试题得分
     */
    @TableField(value = "score")

    private Integer score;


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
	 * 设置：试卷编号
	 */
    public String getRecordNo() {
        return recordNo;
    }
    /**
	 * 获取：试卷编号
	 */

    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }
    /**
	 * 设置：学生id
	 */
    public Integer getStudentId() {
        return studentId;
    }
    /**
	 * 获取：学生id
	 */

    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 设置：试题id（外键）
	 */
    public Integer getQuestionId() {
        return questionId;
    }
    /**
	 * 获取：试题id（外键）
	 */

    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }
    /**
	 * 设置：考生答案
	 */
    public String getStudentAnswer() {
        return studentAnswer;
    }
    /**
	 * 获取：考生答案
	 */

    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }
    /**
	 * 设置：试题得分
	 */
    public Integer getScore() {
        return score;
    }
    /**
	 * 获取：试题得分
	 */

    public void setScore(Integer score) {
        this.score = score;
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
        return "Examredetails{" +
            "id=" + id +
            ", recordNo=" + recordNo +
            ", studentId=" + studentId +
            ", questionId=" + questionId +
            ", studentAnswer=" + studentAnswer +
            ", score=" + score +
            ", createdAt=" + createdAt +
        "}";
    }
}
