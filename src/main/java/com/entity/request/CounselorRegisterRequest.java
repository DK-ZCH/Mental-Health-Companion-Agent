package com.entity.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * 心理老师注册请求（S2-F-2-1 · 注册专用 DTO）。
 *
 * <p><b>为什么需要独立 DTO</b>：{@code CounselorEntity#getPassword()} 标注 {@code @JsonIgnore}，
 * Jackson 在**反序列化时也会忽略**该属性（已运行时探针实证：提交 {@code password} 后实体字段仍为 {@code null}）。
 * 若直接以 {@code @RequestBody CounselorEntity} 接收注册请求，用户填写的密码会被静默丢弃，
 * 导致注册账号 {@code password = NULL}（无法登录）。
 *
 * <p><b>为什么不改 {@code @JsonIgnore}</b>：改为 {@code WRITE_ONLY} 会同时让
 * {@code /counselor/update} 获得 password 绑定能力，扩大修复范围。注册专用 DTO 可彻底解耦。
 *
 * <p><b>字段集口径</b>：严格对齐前端注册页 {@code xinlilaoshi/register.html} 实际提交项，
 * 不是 Entity 全字段 —— 避免未来 Entity 新增字段时注册接口意外获得写入能力。
 *
 * <p>{@code password2} 为前端校验项，<b>不进入本 DTO</b>；{@code ignoreUnknown = true} 容忍多余字段。
 *
 * @see com.entity.CounselorEntity 数据库模型（保持 {@code @JsonIgnore} 原样）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CounselorRegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 账户（工号） */
    private String username;

    /** 密码（注册页提交；本 DTO 使其能被后端接收） */
    private String password;

    /** 心理老师姓名 */
    private String name;

    /** 心理老师手机号 */
    private String phone;

    /** 性别（字典编码） */
    private Integer gender;

    /** 电子邮箱 */
    private String email;

    /** 擅长 */
    private String expertise;

    /** 履历 */
    private String resume;

    /** 心理老师详细介绍（注册页未提交，保留可选） */
    private String introduction;

    /** 心理老师头像（注册页未提交，保留可选） */
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

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public String getResume() {
        return resume;
    }

    public void setResume(String resume) {
        this.resume = resume;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
