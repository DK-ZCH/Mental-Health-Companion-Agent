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
 * 试题表
 *
 * @author 
 * @email
 */
@TableName("assessment_question")
public class ExamquestionEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public ExamquestionEntity() {

	}

	public ExamquestionEntity(T t) {
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
     * 所属试卷id（外键）
     */
    @TableField(value = "paper_id")

    private Integer paperId;


    /**
     * 试题名称
     */
    @TableField(value = "content")

    private String content;


    /**
     * 选项，json字符串
     */
    @TableField(value = "options")

    private String options;


    /**
     * 分值
     */
    @TableField(value = "score")

    private Integer score;


    /**
     * 正确答案
     */
    @TableField(value = "answer")

    private String answer;


    /**
     * 答案解析
     */
    @TableField(value = "analysis")

    private String analysis;


    /**
     * 试题类型
     */
    @TableField(value = "question_type")

    private Integer questionType;


    /**
     * 试题排序，值越大排越前面
     */
    @TableField(value = "sort_order")

    private Integer sortOrder;


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
	 * 设置：试题名称
	 */
    public String getContent() {
        return content;
    }
    /**
	 * 获取：试题名称
	 */

    public void setContent(String content) {
        this.content = content;
    }
    /**
	 * 设置：选项，json字符串
	 */
    public String getOptions() {
        return options;
    }
    /**
	 * 获取：选项，json字符串
	 */

    public void setOptions(String options) {
        this.options = options;
    }
    /**
	 * 设置：分值
	 */
    public Integer getScore() {
        return score;
    }
    /**
	 * 获取：分值
	 */

    public void setScore(Integer score) {
        this.score = score;
    }
    /**
	 * 设置：正确答案
	 */
    public String getAnswer() {
        return answer;
    }
    /**
	 * 获取：正确答案
	 */

    public void setAnswer(String answer) {
        this.answer = answer;
    }
    /**
	 * 设置：答案解析
	 */
    public String getAnalysis() {
        return analysis;
    }
    /**
	 * 获取：答案解析
	 */

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }
    /**
	 * 设置：试题类型
	 */
    public Integer getQuestionType() {
        return questionType;
    }
    /**
	 * 获取：试题类型
	 */

    public void setQuestionType(Integer questionType) {
        this.questionType = questionType;
    }
    /**
	 * 设置：试题排序，值越大排越前面
	 */
    public Integer getSortOrder() {
        return sortOrder;
    }
    /**
	 * 获取：试题排序，值越大排越前面
	 */

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
        return "Examquestion{" +
            "id=" + id +
            ", paperId=" + paperId +
            ", content=" + content +
            ", options=" + options +
            ", score=" + score +
            ", answer=" + answer +
            ", analysis=" + analysis +
            ", questionType=" + questionType +
            ", sortOrder=" + sortOrder +
            ", createdAt=" + createdAt +
        "}";
    }
}
