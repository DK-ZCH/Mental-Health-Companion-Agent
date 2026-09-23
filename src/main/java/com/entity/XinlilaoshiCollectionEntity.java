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
 * 心理老师收藏
 *
 * @author 
 * @email
 */
@TableName("counselor_favorite")
public class XinlilaoshiCollectionEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public XinlilaoshiCollectionEntity() {

	}

	public XinlilaoshiCollectionEntity(T t) {
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
    @TableField(value = "favorited_at",fill = FieldFill.INSERT)

    private Date favoritedAt;


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
        return "XinlilaoshiCollection{" +
            "id=" + id +
            ", counselorId=" + counselorId +
            ", studentId=" + studentId +
            ", favoriteType=" + favoriteType +
            ", favoritedAt=" + favoritedAt +
            ", createdAt=" + createdAt +
        "}";
    }
}
