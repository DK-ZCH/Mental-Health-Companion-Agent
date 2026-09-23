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
 * 健康知识
 *
 * @author 
 * @email
 */
@TableName("knowledge_article")
public class JiankangzhishiEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public JiankangzhishiEntity() {

	}

	public JiankangzhishiEntity(T t) {
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
     * 健康知识名称
     */
    @TableField(value = "title")

    private String title;


    /**
     * 健康知识图片
     */
    @TableField(value = "cover_url")

    private String coverUrl;


    /**
     * 健康知识类型
     */
    @TableField(value = "category")

    private Integer category;


    /**
     * 健康知识发布时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    @TableField(value = "published_at",fill = FieldFill.INSERT)

    private Date publishedAt;


    /**
     * 健康知识详情
     */
    @TableField(value = "content")

    private String content;


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
	 * 设置：健康知识名称
	 */
    public String getTitle() {
        return title;
    }
    /**
	 * 获取：健康知识名称
	 */

    public void setTitle(String title) {
        this.title = title;
    }
    /**
	 * 设置：健康知识图片
	 */
    public String getCoverUrl() {
        return coverUrl;
    }
    /**
	 * 获取：健康知识图片
	 */

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    /**
	 * 设置：健康知识类型
	 */
    public Integer getCategory() {
        return category;
    }
    /**
	 * 获取：健康知识类型
	 */

    public void setCategory(Integer category) {
        this.category = category;
    }
    /**
	 * 设置：健康知识发布时间
	 */
    public Date getPublishedAt() {
        return publishedAt;
    }
    /**
	 * 获取：健康知识发布时间
	 */

    public void setPublishedAt(Date publishedAt) {
        this.publishedAt = publishedAt;
    }
    /**
	 * 设置：健康知识详情
	 */
    public String getContent() {
        return content;
    }
    /**
	 * 获取：健康知识详情
	 */

    public void setContent(String content) {
        this.content = content;
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
        return "Jiankangzhishi{" +
            "id=" + id +
            ", title=" + title +
            ", coverUrl=" + coverUrl +
            ", category=" + category +
            ", publishedAt=" + publishedAt +
            ", content=" + content +
            ", createdAt=" + createdAt +
        "}";
    }
}
