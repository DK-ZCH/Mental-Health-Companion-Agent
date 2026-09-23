package com.entity.model;

import com.entity.XinlilaoshiCollectionEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 心理老师收藏
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class XinlilaoshiCollectionModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 心理老师
     */
    private Integer counselorId;


    /**
     * 学生
     */
    private Integer studentId;


    /**
     * 类型
     */
    private Integer favoriteType;


    /**
     * 收藏时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    private Date favoritedAt;


    /**
     * 创建时间 show3 photoShow
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
	 * 获取：心理老师
	 */
    public Integer getCounselorId() {
        return counselorId;
    }


    /**
	 * 设置：心理老师
	 */
    public void setCounselorId(Integer counselorId) {
        this.counselorId = counselorId;
    }
    /**
	 * 获取：学生
	 */
    public Integer getStudentId() {
        return studentId;
    }


    /**
	 * 设置：学生
	 */
    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 获取：类型
	 */
    public Integer getFavoriteType() {
        return favoriteType;
    }


    /**
	 * 设置：类型
	 */
    public void setFavoriteType(Integer favoriteType) {
        this.favoriteType = favoriteType;
    }
    /**
	 * 获取：收藏时间
	 */
    public Date getFavoritedAt() {
        return favoritedAt;
    }


    /**
	 * 设置：收藏时间
	 */
    public void setFavoritedAt(Date favoritedAt) {
        this.favoritedAt = favoritedAt;
    }
    /**
	 * 获取：创建时间 show3 photoShow
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 设置：创建时间 show3 photoShow
	 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    }
