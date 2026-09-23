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
 * 试卷表
 *
 * @author 
 * @email
 */
@TableName("assessment_paper")
public class ExampaperEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public ExampaperEntity() {

	}

	public ExampaperEntity(T t) {
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
     * 试卷名称
     */
    @TableField(value = "name")

    private String name;


    /**
     * 考试时长(分钟)
     */
    @TableField(value = "duration_minutes")

    private Integer durationMinutes;


    /**
     * 试卷总分数
     */
    @TableField(value = "total_score")

    private Integer totalScore;


    /**
     * 试卷状态
     */
    @TableField(value = "status")

    private Integer status;


    /**
     * 逻辑删除（0代表未删除 1代表已删除）
     */
    @TableField(value = "is_deleted")

    private Integer isDeleted;


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
	 * 设置：试卷名称
	 */
    public String getName() {
        return name;
    }
    /**
	 * 获取：试卷名称
	 */

    public void setName(String name) {
        this.name = name;
    }
    /**
	 * 设置：考试时长(分钟)
	 */
    public Integer getDurationMinutes() {
        return durationMinutes;
    }
    /**
	 * 获取：考试时长(分钟)
	 */

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
    /**
	 * 设置：试卷总分数
	 */
    public Integer getTotalScore() {
        return totalScore;
    }
    /**
	 * 获取：试卷总分数
	 */

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }
    /**
	 * 设置：试卷状态
	 */
    public Integer getStatus() {
        return status;
    }
    /**
	 * 获取：试卷状态
	 */

    public void setStatus(Integer status) {
        this.status = status;
    }
    /**
	 * 设置：逻辑删除（0代表未删除 1代表已删除）
	 */
    public Integer getIsDeleted() {
        return isDeleted;
    }
    /**
	 * 获取：逻辑删除（0代表未删除 1代表已删除）
	 */

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
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
        return "Exampaper{" +
            "id=" + id +
            ", name=" + name +
            ", durationMinutes=" + durationMinutes +
            ", totalScore=" + totalScore +
            ", status=" + status +
            ", isDeleted=" + isDeleted +
            ", createdAt=" + createdAt +
        "}";
    }
}
