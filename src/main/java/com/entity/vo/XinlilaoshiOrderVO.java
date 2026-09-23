package com.entity.vo;

import com.entity.XinlilaoshiOrderEntity;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 心理咨询预约申请
 * 手机端接口返回实体辅助类
 * （主要作用去除一些不必要的字段）
 */
@TableName("counseling_appointment")
public class XinlilaoshiOrderVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */

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
    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
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

    @TableField(value = "applied_at")
    private Date appliedAt;


    /**
     * 创建时间 show3
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
	 * 设置：创建时间 show3
	 */
    public Date getCreatedAt() {
        return createdAt;
    }


    /**
	 * 获取：创建时间 show3
	 */

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

}
