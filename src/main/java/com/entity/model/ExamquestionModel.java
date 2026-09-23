package com.entity.model;

import com.entity.ExamquestionEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 试题表
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class ExamquestionModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 所属试卷id（外键）
     */
    private Integer paperId;


    /**
     * 试题名称
     */
    private String content;


    /**
     * 选项，json字符串
     */
    private String options;


    /**
     * 分值
     */
    private Integer score;


    /**
     * 正确答案
     */
    private String answer;


    /**
     * 答案解析
     */
    private String analysis;


    /**
     * 试题类型
     */
    private Integer questionType;


    /**
     * 试题排序，值越大排越前面
     */
    private Integer sortOrder;


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
	 * 获取：试题名称
	 */
    public String getContent() {
        return content;
    }


    /**
	 * 设置：试题名称
	 */
    public void setContent(String content) {
        this.content = content;
    }
    /**
	 * 获取：选项，json字符串
	 */
    public String getOptions() {
        return options;
    }


    /**
	 * 设置：选项，json字符串
	 */
    public void setOptions(String options) {
        this.options = options;
    }
    /**
	 * 获取：分值
	 */
    public Integer getScore() {
        return score;
    }


    /**
	 * 设置：分值
	 */
    public void setScore(Integer score) {
        this.score = score;
    }
    /**
	 * 获取：正确答案
	 */
    public String getAnswer() {
        return answer;
    }


    /**
	 * 设置：正确答案
	 */
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    /**
	 * 获取：答案解析
	 */
    public String getAnalysis() {
        return analysis;
    }


    /**
	 * 设置：答案解析
	 */
    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }
    /**
	 * 获取：试题类型
	 */
    public Integer getQuestionType() {
        return questionType;
    }


    /**
	 * 设置：试题类型
	 */
    public void setQuestionType(Integer questionType) {
        this.questionType = questionType;
    }
    /**
	 * 获取：试题排序，值越大排越前面
	 */
    public Integer getSortOrder() {
        return sortOrder;
    }


    /**
	 * 设置：试题排序，值越大排越前面
	 */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
