package com.entity.model;

import com.entity.YonghuEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;


/**
 * 学生
 * 接收传参的实体类
 *（实际开发中配合移动端接口开发手动去掉些没用的字段， 后端一般用entity就够用了）
 * 取自ModelAndView 的model名称
 */
public class YonghuModel implements Serializable {
    private static final long serialVersionUID = 1L;




    /**
     * 主键
     */
    private Integer id;


    /**
     * 账户
     */
    private String username;


    /**
     * 密码
     */
    private String password;


    /**
     * 学生姓名
     */
    private String name;


    /**
     * 学生手机号
     */
    private String phone;


    /**
     * 学生身份证号
     */
    private String idCardNo;


    /**
     * 学生头像
     */
    private String avatarUrl;


    /**
     * 性别
     */
    private Integer gender;


    /**
     * 电子邮箱
     */
    private String email;


    /**
     * 创建时间
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
	 * 获取：账户
	 */
    public String getUsername() {
        return username;
    }


    /**
	 * 设置：账户
	 */
    public void setUsername(String username) {
        this.username = username;
    }
    /**
	 * 获取：密码
	 */
    public String getPassword() {
        return password;
    }


    /**
	 * 设置：密码
	 */
    public void setPassword(String password) {
        this.password = password;
    }
    /**
	 * 获取：学生姓名
	 */
    public String getName() {
        return name;
    }


    /**
	 * 设置：学生姓名
	 */
    public void setName(String name) {
        this.name = name;
    }
    /**
	 * 获取：学生手机号
	 */
    public String getPhone() {
        return phone;
    }


    /**
	 * 设置：学生手机号
	 */
    public void setPhone(String phone) {
        this.phone = phone;
    }
    /**
	 * 获取：学生身份证号
	 */
    public String getIdCardNo() {
        return idCardNo;
    }


    /**
	 * 设置：学生身份证号
	 */
    public void setIdCardNo(String idCardNo) {
        this.idCardNo = idCardNo;
    }
    /**
	 * 获取：学生头像
	 */
    public String getAvatarUrl() {
        return avatarUrl;
    }


    /**
	 * 设置：学生头像
	 */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    /**
	 * 获取：性别
	 */
    public Integer getGender() {
        return gender;
    }


    /**
	 * 设置：性别
	 */
    public void setGender(Integer gender) {
        this.gender = gender;
    }
    /**
	 * 获取：电子邮箱
	 */
    public String getEmail() {
        return email;
    }


    /**
	 * 设置：电子邮箱
	 */
    public void setEmail(String email) {
        this.email = email;
    }
    /**
	 * 获取：创建时间
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 设置：创建时间
	 */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    }
