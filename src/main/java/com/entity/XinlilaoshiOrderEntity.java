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
 * 心理咨询预约申请
 *
 * @author 
 * @email
 */
@TableName("counseling_appointment")
public class XinlilaoshiOrderEntity<T> implements Serializable {
    private static final long serialVersionUID = 1L;


	public XinlilaoshiOrderEntity() {

	}

	public XinlilaoshiOrderEntity(T t) {
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
     * 预约流水号
     */
    @TableField(value = "appointment_no")

    private String appointmentNo;


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
     * 预约日期
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd")
	@DateTimeFormat
    @TableField(value = "appointment_date")

    private Date appointmentDate;


    /**
     * 预约时间段
     */
    @TableField(value = "time_slot")

    private Integer timeSlot;


    /**
     * 预约状态
     */
    @TableField(value = "status")

    private Integer status;


    /**
     * 审核意见
     */
    @TableField(value = "review_comment")

    private String reviewComment;


    /**
     * 申请时间
     */
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat
    @TableField(value = "applied_at",fill = FieldFill.INSERT)

    private Date appliedAt;


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
	 * 设置：预约流水号
	 */
    public String getAppointmentNo() {
        return appointmentNo;
    }
    /**
	 * 获取：预约流水号
	 */

    public void setAppointmentNo(String appointmentNo) {
        this.appointmentNo = appointmentNo;
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
	 * 设置：预约日期
	 */
    public Date getAppointmentDate() {
        return appointmentDate;
    }
    /**
	 * 获取：预约日期
	 */

    public void setAppointmentDate(Date appointmentDate) {
        this.appointmentDate = appointmentDate;
    }
    /**
	 * 设置：预约时间段
	 */
    public Integer getTimeSlot() {
        return timeSlot;
    }
    /**
	 * 获取：预约时间段
	 */

    public void setTimeSlot(Integer timeSlot) {
        this.timeSlot = timeSlot;
    }
    /**
	 * 设置：预约状态
	 */
    public Integer getStatus() {
        return status;
    }
    /**
	 * 获取：预约状态
	 */

    public void setStatus(Integer status) {
        this.status = status;
    }
    /**
	 * 设置：审核意见
	 */
    public String getReviewComment() {
        return reviewComment;
    }
    /**
	 * 获取：审核意见
	 */

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }
    /**
	 * 设置：申请时间
	 */
    public Date getAppliedAt() {
        return appliedAt;
    }
    /**
	 * 获取：申请时间
	 */

    public void setAppliedAt(Date appliedAt) {
        this.appliedAt = appliedAt;
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
        return "XinlilaoshiOrder{" +
            "id=" + id +
            ", appointmentNo=" + appointmentNo +
            ", counselorId=" + counselorId +
            ", studentId=" + studentId +
            ", appointmentDate=" + appointmentDate +
            ", timeSlot=" + timeSlot +
            ", status=" + status +
            ", reviewComment=" + reviewComment +
            ", appliedAt=" + appliedAt +
            ", createdAt=" + createdAt +
        "}";
    }
}
