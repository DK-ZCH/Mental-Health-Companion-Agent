package com.entity.model;

import com.entity.JiankangzhishiEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 健康知识
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class JiankangzhishiModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 健康知识名称
     */
    private String title;


    /**
     * 健康知识图片
     */
    private String coverUrl;


    /**
     * 健康知识类型
     */
    private Integer category;


    /**
     * 健康知识发布时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date publishedAt;


    /**
     * 健康知识详情
     */
    private String content;


    /**
     * 创建时间 show1 show2 nameShow
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
	 * 获取：健康知识名称
	 */
    public String getTitle() {
        return title;
    }


    /**
	 * 设置：健康知识名称
	 */
    public void setTitle(String title) {
        this.title = title;
    }
    /**
	 * 获取：健康知识图片
	 */
    public String getCoverUrl() {
        return coverUrl;
    }


    /**
	 * 设置：健康知识图片
	 */
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    /**
	 * 获取：健康知识类型
	 */
    public Integer getCategory() {
        return category;
    }


    /**
	 * 设置：健康知识类型
	 */
    public void setCategory(Integer category) {
        this.category = category;
    }
    /**
	 * 获取：健康知识发布时间
	 */
    public Date getPublishedAt() {
        return publishedAt;
    }


    /**
	 * 设置：健康知识发布时间
	 */
    public void setPublishedAt(Date publishedAt) {
        this.publishedAt = publishedAt;
    }
    /**
	 * 获取：健康知识详情
	 */
    public String getContent() {
        return content;
    }


    /**
	 * 设置：健康知识详情
	 */
    public void setContent(String content) {
        this.content = content;
    }
    /**
	 * 获取：创建时间 show1 show2 nameShow
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 设置：创建时间 show1 show2 nameShow
	 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    }
