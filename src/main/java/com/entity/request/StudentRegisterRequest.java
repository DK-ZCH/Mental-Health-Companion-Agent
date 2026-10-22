package com.entity.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * 学生注册请求（S2-F-2-1 · 注册专用 DTO）。
 *
 * <p><b>为什么需要独立 DTO</b>：{@code StudentEntity#getPassword()} / {@code getIdCardNo()}
 * 均标注 {@code @JsonIgnore} —— 该注解使 Jackson 在**反序列化时也忽略**对应属性
 * （已用项目 classpath 运行时探针实证：提交 {@code password}/{@code idCardNo} 后实体字段仍为 {@code null}）。
 * 因此若直接以 {@code @RequestBody StudentEntity} 接收注册请求，用户填写的密码与身份证号会被静默丢弃，
 * 导致注册账号 {@code password = NULL}（无法登录）且身份证查重失效。
 *
 * <p><b>为什么不改 {@code @JsonIgnore}</b>：把它改成 {@code WRITE_ONLY} 会同时让
 * {@code /student/update} 获得 password 绑定能力，从而把「修复注册」扩大为
 * 「修复注册 + 设计改密授权 + 旧密码校验」。注册专用 DTO 可把两者彻底解耦。
 *
 * <p><b>字段集口径</b>：严格对齐前端注册页 {@code yonghu/register.html} 实际提交项，
 * 而不是 Entity 的全部字段 —— 避免未来 Entity 新增 {@code role}/{@code status} 等字段时，
 * 注册接口因自动绑定而意外获得写入能力。
 *
 * <p>{@code password2}（确认密码）为前端 {@code lay-verify} 校验项，<b>不进入本 DTO</b>；
 * {@code ignoreUnknown = true} 保证前端多传字段不会导致 400。
 *
 * @see com.entity.StudentEntity 数据库模型（保持 {@code @JsonIgnore} 原样）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudentRegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 账户（学号） */
    private String username;

    /** 密码（注册页提交；本 DTO 使其能被后端接收） */
    private String password;

    /** 学生姓名 */
    private String name;

    /** 学生手机号 */
    private String phone;

    /** 学生身份证号（注册页提交；本 DTO 使其能被后端接收） */
    private String idCardNo;

    /** 性别（字典编码） */
    private Integer gender;

    /** 电子邮箱 */
    private String email;

    /** 学生头像（注册页未提交，保留可选） */
    private String avatarUrl;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getIdCardNo() {
        return idCardNo;
    }

    public void setIdCardNo(String idCardNo) {
        this.idCardNo = idCardNo;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
