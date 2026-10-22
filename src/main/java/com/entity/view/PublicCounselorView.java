package com.entity.view;

import com.entity.CounselorEntity;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;

/**
 * 心理老师 · <b>公开目录视图</b>（S2-E-2-1 · Counselor Public Data Boundary）。
 *
 * <p><b>用途</b>：{@code /counselor/list} 与 {@code /counselor/detail/{id}} 的返回类型 ——
 * 仅承载匿名用户浏览「咨询师目录」所需的 8 个字段。
 *
 * <p><b>⚠️ 结构约束（不可放宽）</b>：本类<b>刻意不继承</b> {@link CounselorEntity} / {@link CounselorView}。
 * 一旦继承，父类的 {@code username} / {@code phone} / {@code email} / {@code createdAt} 等 getter
 * 仍会被 Jackson 序列化 → 形成「看似投影、实际泄露」。因此字段必须在本类**独立声明**。
 *
 * <p><b>字段白名单（8 个）</b>：{@code id} · {@code name} · {@code avatarUrl} · {@code gender} ·
 * {@code genderLabel} · {@code expertise} · {@code resume} · {@code introduction}
 *
 * <p><b>明确排除</b>：{@code username}（登录账户名，账号枚举面）· {@code password} ·
 * {@code phone} / {@code email}（联系方式 PII）· {@code createdAt}（内部字段）。
 *
 * <p><b>⚠️ 字典回填顺序（不可调换）</b>：{@code SysDictItemServiceImpl#dictionaryConvert} 的字典键
 * 依赖字段的<b>声明类</b>（{@code Counselor.gender}）。若直接对本类调用该方法，键会变成
 * {@code PublicCounselorView.gender} 而不命中 → {@code genderLabel} 静默失效。
 * 因此必须：<b>先</b>对 {@link CounselorView} 回填字段，<b>再</b>用本类构造器投影（复制已回填的 {@code genderLabel}）。
 *
 * @see CounselorView 内部管理侧完整视图（12 字段，仅 admin 端点使用）
 */
public class PublicCounselorView implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键（路由 / 收藏 / 预约所需） */
    private Integer id;

    /** 心理老师姓名 */
    private String name;

    /** 心理老师头像 */
    private String avatarUrl;

    /** 性别（字典编码） */
    private Integer gender;

    /** 性别的值（由 {@link CounselorView} 的字典回填结果复制而来） */
    private String genderLabel;

    /** 擅长 */
    private String expertise;

    /** 履历 */
    private String resume;

    /** 心理老师详细介绍 */
    private String introduction;

    public PublicCounselorView() {
    }

    /**
     * 从<b>已完成字典回填</b>的 {@link CounselorView} 投影。
     *
     * <p>仅同名的 8 个字段被复制；{@code username} / {@code password} / {@code phone} /
     * {@code email} / {@code createdAt} 因本类无对应属性而<b>不会</b>进入公开响应。
     *
     * @param src 已调用过 {@code dictionaryConvert} 的视图对象
     */
    public PublicCounselorView(CounselorEntity src) {
        BeanUtils.copyProperties(src, this);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getGenderLabel() {
        return genderLabel;
    }

    public void setGenderLabel(String genderLabel) {
        this.genderLabel = genderLabel;
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
}
