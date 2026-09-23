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
 * 学生
 *
 * @author 
 * @email
 */
@TableName("student")
public class YonghuEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public YonghuEntity() {

	}

	public YonghuEntity(T t) {
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
     * 账户
     */
    @TableField(value = "username")

    private String username;


    /**
     * 密码
     */
    @TableField(value = "password")

    private String password;


    /**
     * 学生姓名
     */
    @TableField(value = "name")

    private String name;


    /**
     * 学生手机号
     */
    @TableField(value = "phone")

    private String phone;


    /**
     * 学生身份证号
     */
    @TableField(value = "id_card_no")

    private String idCardNo;


    /**
     * 学生头像
     */
    @TableField(value = "avatar_url")

    private String avatarUrl;


    /**
     * 性别
     */
    @TableField(value = "gender")

    private Integer gender;


    /**
     * 电子邮箱
     */
    @TableField(value = "email")

    private String email;


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
	 * 设置：账户
	 */
    public String getUsername() {
        return username;
    }
    /**
	 * 获取：账户
	 */

    public void setUsername(String username) {
        this.username = username;
    }
    /**
	 * 设置：密码
	 */
    public String getPassword() {
        return password;
    }
    /**
	 * 获取：密码
	 */

    public void setPassword(String password) {
        this.password = password;
    }
    /**
	 * 设置：学生姓名
	 */
    public String getName() {
        return name;
    }
    /**
	 * 获取：学生姓名
	 */

    public void setName(String name) {
        this.name = name;
    }
    /**
	 * 设置：学生手机号
	 */
    public String getPhone() {
        return phone;
    }
    /**
	 * 获取：学生手机号
	 */

    public void setPhone(String phone) {
        this.phone = phone;
    }
    /**
	 * 设置：学生身份证号
	 */
    public String getIdCardNo() {
        return idCardNo;
    }
    /**
	 * 获取：学生身份证号
	 */

    public void setIdCardNo(String idCardNo) {
        this.idCardNo = idCardNo;
    }
    /**
	 * 设置：学生头像
	 */
    public String getAvatarUrl() {
        return avatarUrl;
    }
    /**
	 * 获取：学生头像
	 */

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    /**
	 * 设置：性别
	 */
    public Integer getGender() {
        return gender;
    }
    /**
	 * 获取：性别
	 */

    public void setGender(Integer gender) {
        this.gender = gender;
    }
    /**
	 * 设置：电子邮箱
	 */
    public String getEmail() {
        return email;
    }
    /**
	 * 获取：电子邮箱
	 */

    public void setEmail(String email) {
        this.email = email;
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
        return "Yonghu{" +
            "id=" + id +
            ", username=" + username +
            ", password=" + password +
            ", name=" + name +
            ", phone=" + phone +
            ", idCardNo=" + idCardNo +
            ", avatarUrl=" + avatarUrl +
            ", gender=" + gender +
            ", email=" + email +
            ", createdAt=" + createdAt +
        "}";
    }
}
