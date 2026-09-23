package com.entity.vo;

import com.entity.JiankangzhishiEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 健康知识
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("knowledge_article")
public class JiankangzhishiVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

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

    @TableField(value = "published_at")
    private Date publishedAt;


    /**
     * 健康知识详情
     */

    @TableField(value = "content")
    private String content;


    /**
     * 创建时间 show1 show2 nameShow
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
	 * 设置：创建时间 show1 show2 nameShow
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 获取：创建时间 show1 show2 nameShow
	 */

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

}
