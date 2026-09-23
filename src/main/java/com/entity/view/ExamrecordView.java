package com.entity.view;

import com.entity.ExamrecordEntity;
import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 考试记录表
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("assessment_record")
public class ExamrecordView extends ExamrecordEntity implements Serializable {
    private static final long serialVersionUID = 1L;




		//级联表 exampaper
			/**
			* 试卷名称
			*/
			private String paperName;
			/**
			* 考试时长(分钟)
			*/
			private Integer paperDurationMinutes;
			/**
			* 试卷总分数
			*/
			private Integer paperTotalScore;
			/**
			* 试卷状态
			*/
			private Integer paperStatus;
				/**
				* 试卷状态的值
				*/
				private String paperStatusLabel;
			/**
			* 逻辑删除（0代表未删除 1代表已删除）
			*/
			private Integer paperIsDeleted;

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

	public ExamrecordView() {

	}

	public ExamrecordView(ExamrecordEntity examrecordEntity) {
		try {
			BeanUtils.copyProperties(this, examrecordEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}











				//级联表的get和set exampaper

					/**
					* 获取： 试卷名称
					*/
					public String getPaperName() {
						return paperName;
					}
					/**
					* 设置： 试卷名称
					*/
					public void setPaperName(String paperName) {
						this.paperName = paperName;
					}

					/**
					* 获取： 考试时长(分钟)
					*/
					public Integer getPaperDurationMinutes() {
						return paperDurationMinutes;
					}
					/**
					* 设置： 考试时长(分钟)
					*/
					public void setPaperDurationMinutes(Integer paperDurationMinutes) {
						this.paperDurationMinutes = paperDurationMinutes;
					}

					/**
					* 获取： 试卷总分数
					*/
					public Integer getPaperTotalScore() {
						return paperTotalScore;
					}
					/**
					* 设置： 试卷总分数
					*/
					public void setPaperTotalScore(Integer paperTotalScore) {
						this.paperTotalScore = paperTotalScore;
					}

					/**
					* 获取： 试卷状态
					*/
					public Integer getPaperStatus() {
						return paperStatus;
					}
					/**
					* 设置： 试卷状态
					*/
					public void setPaperStatus(Integer paperStatus) {
						this.paperStatus = paperStatus;
					}


						/**
						* 获取： 试卷状态的值
						*/
						public String getPaperStatusLabel() {
							return paperStatusLabel;
						}
						/**
						* 设置： 试卷状态的值
						*/
						public void setPaperStatusLabel(String paperStatusLabel) {
							this.paperStatusLabel = paperStatusLabel;
						}

					/**
					* 获取： 逻辑删除（0代表未删除 1代表已删除）
					*/
					public Integer getPaperIsDeleted() {
						return paperIsDeleted;
					}
					/**
					* 设置： 逻辑删除（0代表未删除 1代表已删除）
					*/
					public void setPaperIsDeleted(Integer paperIsDeleted) {
						this.paperIsDeleted = paperIsDeleted;
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







}
