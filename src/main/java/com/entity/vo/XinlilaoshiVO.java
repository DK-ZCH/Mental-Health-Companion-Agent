package com.entity.vo;

import com.entity.XinlilaoshiEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 心理老师
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("counselor")
public class XinlilaoshiVO implements Serializable {
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
     * 心理老师姓名
     */

    @TableField(value = "name")
    private String name;


    /**
     * 心理老师手机号
     */

    @TableField(value = "phone")
    private String phone;


    /**
     * 心理老师头像
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
     * 擅长
     */

    @TableField(value = "expertise")
    private String expertise;


    /**
     * 履历
     */

    @TableField(value = "resume")
    private String resume;


    /**
     * 心理老师详细介绍
     */

    @TableField(value = "introduction")
    private String introduction;


    /**
     * 创建时间  show1 show2 photoShow
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
	 * 设置：心理老师姓名
	 */
    public String getName() {
        return name;
    }


    /**
	 * 获取：心理老师姓名
	 */

    public void setName(String name) {
        this.name = name;
    }
    /**
	 * 设置：心理老师手机号
	 */
    public String getPhone() {
        return phone;
    }


    /**
	 * 获取：心理老师手机号
	 */

    public void setPhone(String phone) {
        this.phone = phone;
    }
    /**
	 * 设置：心理老师头像
	 */
    public String getAvatarUrl() {
        return avatarUrl;
    }


    /**
	 * 获取：心理老师头像
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
	 * 设置：擅长
	 */
    public String getExpertise() {
        return expertise;
    }


    /**
	 * 获取：擅长
	 */

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }
    /**
	 * 设置：履历
	 */
    public String getResume() {
        return resume;
    }


    /**
	 * 获取：履历
	 */

    public void setResume(String resume) {
        this.resume = resume;
    }
    /**
	 * 设置：心理老师详细介绍
	 */
    public String getIntroduction() {
        return introduction;
    }


    /**
	 * 获取：心理老师详细介绍
	 */

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }
    /**
	 * 设置：创建时间  show1 show2 photoShow
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 获取：创建时间  show1 show2 photoShow
	 */

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

}
