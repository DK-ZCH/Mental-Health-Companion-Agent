package com.dto;

/**
 * 试题公开投影 DTO（S2-B）
 *
 * <p>用途：{@code /assessmentQuestion} 的公开 / 学生侧响应。
 * <b>结构上不含 {@code answer}、{@code analysis}</b> ⇒ 从类型层面杜绝标准答案/解析泄露 ✓。
 * 管理员侧仍使用 {@link com.entity.view.AssessmentQuestionView}（完整视图，含 answer/analysis）。
 *
 * <p>字段仅为「完成公开浏览 / 学生答题」所需的最小集合。
 */
public class QuestionPublicDTO {

    private Integer id;
    private Integer paperId;
    private String content;
    /** 安全投影后的 options（JSON 字符串；见 QuestionOptionSanitizer）*/
    private String options;
    private Integer score;
    private Integer questionType;
    private Integer sortOrder;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getPaperId() {
        return paperId;
    }

    public void setPaperId(Integer paperId) {
        this.paperId = paperId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getQuestionType() {
        return questionType;
    }

    public void setQuestionType(Integer questionType) {
        this.questionType = questionType;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
