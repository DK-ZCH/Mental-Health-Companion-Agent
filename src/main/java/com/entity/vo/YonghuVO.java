package com.entity.vo;

import com.entity.YonghuEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 学生
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("student")
public class YonghuVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

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

}
