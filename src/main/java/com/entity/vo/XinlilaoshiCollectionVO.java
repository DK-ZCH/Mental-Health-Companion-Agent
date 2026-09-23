package com.entity.vo;

import com.entity.XinlilaoshiCollectionEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 心理老师收藏
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("counselor_favorite")
public class XinlilaoshiCollectionVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

    @TableField(value = "id")
    private Integer id;


    /**
     * 心理老师
     */

    @TableField(value = "counselor_id")
    private Integer counselorId;


    /**
     * 学生
     */

    @TableField(value = "student_id")
    private Integer studentId;


    /**
     * 类型
     */

    @TableField(value = "favorite_type")
    private Integer favoriteType;


    /**
     * 收藏时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat

    @TableField(value = "favorited_at")
    private Date favoritedAt;


    /**
     * 创建时间 show3 photoShow
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
	 * 设置：心理老师
	 */
    public Integer getCounselorId() {
        return counselorId;
    }


    /**
	 * 获取：心理老师
	 */

    public void setCounselorId(Integer counselorId) {
        this.counselorId = counselorId;
    }
    /**
	 * 设置：学生
	 */
    public Integer getStudentId() {
        return studentId;
    }


    /**
	 * 获取：学生
	 */

    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }
    /**
	 * 设置：类型
	 */
    public Integer getFavoriteType() {
        return favoriteType;
    }


    /**
	 * 获取：类型
	 */

    public void setFavoriteType(Integer favoriteType) {
        this.favoriteType = favoriteType;
    }
    /**
	 * 设置：收藏时间
	 */
    public Date getFavoritedAt() {
        return favoritedAt;
    }


    /**
	 * 获取：收藏时间
	 */

    public void setFavoritedAt(Date favoritedAt) {
        this.favoritedAt = favoritedAt;
    }
    /**
	 * 设置：创建时间 show3 photoShow
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 获取：创建时间 show3 photoShow
	 */

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

}
