package com.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotations.TableId;
import com.baomidou.mybatisplus.annotations.TableName;
import com.baomidou.mybatisplus.enums.IdType;

/** 
 * token表
 */
@TableName("auth_token")
public class TokenEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@TableId(type = IdType.AUTO)
	private Integer id;
	
	/**
	 * 学生id
	 */
	private Integer userId;
	
	/**
	 * 学生名
	 */
	private String username;
	
	/**
	 * 表名
	 */
	private String userType;
	
	/**
	 * 角色
	 */
	private String role;
	
	/**
	 * token
	 */
	private String token;
	
	/**
	 * 过期时间
	 */
	private Date expiredAt;
	
	/**
	 * 新增时间
	 */
	private Date createdAt;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getToken() {
		return token;
	}

	public String getUserType() {
		return userType;
	}

	public void setUserType(String userType) {
		this.userType = userType;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public Date getExpiredAt() {
		return expiredAt;
	}

	public void setExpiredAt(Date expiredAt) {
		this.expiredAt = expiredAt;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public TokenEntity(Integer userId, String username, String userType,String role, String token, Date expiredAt) {
		super();
		this.userId = userId;
		this.username = username;
		this.userType = userType;
		this.role = role;
		this.token = token;
		this.expiredAt = expiredAt;
	}
	
	public TokenEntity() {
	}
	
}
