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
 * 错题表
 *
 * @author 
 * @email
 */
@TableName("assessment_wrong_question")
public class ExamrewrongquestionEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public ExamrewrongquestionEntity() {

	}

	public ExamrewrongquestionEntity(T t) {
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
     * 学生id
     */
    @TableField(value = "student_id")

    private Integer studentId;


    /**
     * 试卷（外键）
     */
    @TableField(value = "paper_id")

    private Integer paperId;


    /**
     * 试题id（外键）
     */
    @TableField(value = "question_id")

    private Integer questionId;


    /**
     * 考生作答
     */
    @TableField(value = "student_answer")

    private String studentAnswer;


    /**
     * 记录时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    @TableField(value = "answered_at",fill = FieldFill.INSERT)

    private Date answeredAt;


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
	 * 设置：试卷（外键）
	 */
    public Integer getPaperId() {
        return paperId;
    }
    /**
	 * 获取：试卷（外键）
	 */

    public void setPaperId(Integer paperId) {
        this.paperId = paperId;
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
	 * 设置：考生作答
	 */
    public String getStudentAnswer() {
        return studentAnswer;
    }
    /**
	 * 获取：考生作答
	 */

    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }
    /**
	 * 设置：记录时间
	 */
    public Date getAnsweredAt() {
        return answeredAt;
    }
    /**
	 * 获取：记录时间
	 */

    public void setAnsweredAt(Date answeredAt) {
        this.answeredAt = answeredAt;
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
        return "Examrewrongquestion{" +
            "id=" + id +
            ", studentId=" + studentId +
            ", paperId=" + paperId +
            ", questionId=" + questionId +
            ", studentAnswer=" + studentAnswer +
            ", answeredAt=" + answeredAt +
            ", createdAt=" + createdAt +
        "}";
    }
}
