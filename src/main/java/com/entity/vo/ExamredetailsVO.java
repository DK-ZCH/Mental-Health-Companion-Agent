package com.entity.vo;

import com.entity.ExamredetailsEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 答题详情表
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("assessment_answer")
public class ExamredetailsVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

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

}
