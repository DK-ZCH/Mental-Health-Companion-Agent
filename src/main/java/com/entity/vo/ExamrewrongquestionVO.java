package com.entity.vo;

import com.entity.ExamrewrongquestionEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 错题表
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("assessment_wrong_question")
public class ExamrewrongquestionVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

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

    @TableField(value = "answered_at")
    private Date answeredAt;


    /**
     * 创建时间 show3
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
	 * 设置：创建时间 show3
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 获取：创建时间 show3
	 */

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

}
