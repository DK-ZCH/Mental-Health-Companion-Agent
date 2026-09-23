package com.entity.view;

import com.entity.XinlilaoshiOrderEntity;
import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 心理咨询预约申请
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("counseling_appointment")
public class XinlilaoshiOrderView extends XinlilaoshiOrderEntity implements Serializable {
    private static final long serialVersionUID = 1L;

		/**
		* 预约时间段的值
		*/
		private String timeSlotLabel;
		/**
		* 预约状态的值
		*/
		private String statusLabel;



		//级联表 yonghu
			/**
			* 学生姓名
			*/
			private String studentName;
			/**
			* 学生手机号
			*/
			private String studentPhone;
			/**
			* 学生身份证号
			*/
			private String studentIdCardNo;
			/**
			* 学生头像
			*/
			private String studentAvatarUrl;
			/**
			* 电子邮箱
			*/
			private String studentEmail;

		//级联表 xinlilaoshi
			/**
			* 心理老师姓名
			*/
			private String counselorName;
			/**
			* 心理老师手机号
			*/
			private String counselorPhone;
			/**
			* 心理老师头像
			*/
			private String counselorAvatarUrl;
			/**
			* 电子邮箱
			*/
			private String counselorEmail;
			/**
			* 擅长
			*/
			private String counselorExpertise;
			/**
			* 履历
			*/
			private String counselorResume;
			/**
			* 心理老师详细介绍
			*/
			private String counselorIntroduction;

	public XinlilaoshiOrderView() {

	}

	public XinlilaoshiOrderView(XinlilaoshiOrderEntity xinlilaoshiOrderEntity) {
		try {
			BeanUtils.copyProperties(this, xinlilaoshiOrderEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



			/**
			* 获取： 预约时间段的值
			*/
			public String getTimeSlotLabel() {
				return timeSlotLabel;
			}
			/**
			* 设置： 预约时间段的值
			*/
			public void setTimeSlotLabel(String timeSlotLabel) {
				this.timeSlotLabel = timeSlotLabel;
			}
			/**
			* 获取： 预约状态的值
			*/
			public String getStatusLabel() {
				return statusLabel;
			}
			/**
			* 设置： 预约状态的值
			*/
			public void setStatusLabel(String statusLabel) {
				this.statusLabel = statusLabel;
			}






































				//级联表的get和set yonghu

					/**
					* 获取： 学生姓名
					*/
					public String getStudentName() {
						return studentName;
					}
					/**
					* 设置： 学生姓名
					*/
					public void setStudentName(String studentName) {
						this.studentName = studentName;
					}

					/**
					* 获取： 学生手机号
					*/
					public String getStudentPhone() {
						return studentPhone;
					}
					/**
					* 设置： 学生手机号
					*/
					public void setStudentPhone(String studentPhone) {
						this.studentPhone = studentPhone;
					}

					/**
					* 获取： 学生身份证号
					*/
					public String getStudentIdCardNo() {
						return studentIdCardNo;
					}
					/**
					* 设置： 学生身份证号
					*/
					public void setStudentIdCardNo(String studentIdCardNo) {
						this.studentIdCardNo = studentIdCardNo;
					}

					/**
					* 获取： 学生头像
					*/
					public String getStudentAvatarUrl() {
						return studentAvatarUrl;
					}
					/**
					* 设置： 学生头像
					*/
					public void setStudentAvatarUrl(String studentAvatarUrl) {
						this.studentAvatarUrl = studentAvatarUrl;
					}

					/**
					* 获取： 电子邮箱
					*/
					public String getStudentEmail() {
						return studentEmail;
					}
					/**
					* 设置： 电子邮箱
					*/
					public void setStudentEmail(String studentEmail) {
						this.studentEmail = studentEmail;
					}


				//级联表的get和set xinlilaoshi

					/**
					* 获取： 心理老师姓名
					*/
					public String getCounselorName() {
						return counselorName;
					}
					/**
					* 设置： 心理老师姓名
					*/
					public void setCounselorName(String counselorName) {
						this.counselorName = counselorName;
					}

					/**
					* 获取： 心理老师手机号
					*/
					public String getCounselorPhone() {
						return counselorPhone;
					}
					/**
					* 设置： 心理老师手机号
					*/
					public void setCounselorPhone(String counselorPhone) {
						this.counselorPhone = counselorPhone;
					}

					/**
					* 获取： 心理老师头像
					*/
					public String getCounselorAvatarUrl() {
						return counselorAvatarUrl;
					}
					/**
					* 设置： 心理老师头像
					*/
					public void setCounselorAvatarUrl(String counselorAvatarUrl) {
						this.counselorAvatarUrl = counselorAvatarUrl;
					}

					/**
					* 获取： 电子邮箱
					*/
					public String getCounselorEmail() {
						return counselorEmail;
					}
					/**
					* 设置： 电子邮箱
					*/
					public void setCounselorEmail(String counselorEmail) {
						this.counselorEmail = counselorEmail;
					}

					/**
					* 获取： 擅长
					*/
					public String getCounselorExpertise() {
						return counselorExpertise;
					}
					/**
					* 设置： 擅长
					*/
					public void setCounselorExpertise(String counselorExpertise) {
						this.counselorExpertise = counselorExpertise;
					}

					/**
					* 获取： 履历
					*/
					public String getCounselorResume() {
						return counselorResume;
					}
					/**
					* 设置： 履历
					*/
					public void setCounselorResume(String counselorResume) {
						this.counselorResume = counselorResume;
					}

					/**
					* 获取： 心理老师详细介绍
					*/
					public String getCounselorIntroduction() {
						return counselorIntroduction;
					}
					/**
					* 设置： 心理老师详细介绍
					*/
					public void setCounselorIntroduction(String counselorIntroduction) {
						this.counselorIntroduction = counselorIntroduction;
					}




}
