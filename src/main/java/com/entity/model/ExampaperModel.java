package com.entity.model;

import com.entity.ExampaperEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 试卷表
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class ExampaperModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 试卷名称
     */
    private String name;


    /**
     * 考试时长(分钟)
     */
    private Integer durationMinutes;


    /**
     * 试卷总分数
     */
    private Integer totalScore;


    /**
     * 试卷状态
     */
    private Integer status;


    /**
     * 逻辑删除（0代表未删除 1代表已删除）
     */
    private Integer isDeleted;


    /**
     * 创建时间 show2 photoShow
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
	 * 获取：试卷名称
	 */
    public String getName() {
        return name;
    }


    /**
	 * 设置：试卷名称
	 */
    public void setName(String name) {
        this.name = name;
    }
    /**
	 * 获取：考试时长(分钟)
	 */
    public Integer getDurationMinutes() {
        return durationMinutes;
    }


    /**
	 * 设置：考试时长(分钟)
	 */
    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
    /**
	 * 获取：试卷总分数
	 */
    public Integer getTotalScore() {
        return totalScore;
    }


    /**
	 * 设置：试卷总分数
	 */
    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }
    /**
	 * 获取：试卷状态
	 */
    public Integer getStatus() {
        return status;
    }


    /**
	 * 设置：试卷状态
	 */
    public void setStatus(Integer status) {
        this.status = status;
    }
    /**
	 * 获取：逻辑删除（0代表未删除 1代表已删除）
	 */
    public Integer getIsDeleted() {
        return isDeleted;
    }


    /**
	 * 设置：逻辑删除（0代表未删除 1代表已删除）
	 */
    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
    /**
	 * 获取：创建时间 show2 photoShow
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 设置：创建时间 show2 photoShow
	 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    }
