package com.entity.view;

import com.entity.ExamredetailsEntity;
import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 答题详情表
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("assessment_answer")
public class ExamredetailsView extends ExamredetailsEntity implements Serializable {
    private static final long serialVersionUID = 1L;




		//级联表 examquestion
			/**
			* 试题名称
			*/
			private String questionContent;
			/**
			* 选项，json字符串
			*/
			private String questionOptions;
			/**
			* 分值
			*/
			private Integer questionScore;
			/**
			* 正确答案
			*/
			private String questionAnswer;
			/**
			* 答案解析
			*/
			private String questionAnalysis;
			/**
			* 试题类型
			*/
			private Integer questionType;
				/**
				* 试题类型的值
				*/
				private String questionTypeLabel;
			/**
			* 试题排序，值越大排越前面
			*/
			private Integer questionSortOrder;

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

	public ExamredetailsView() {

	}

	public ExamredetailsView(ExamredetailsEntity examredetailsEntity) {
		try {
			BeanUtils.copyProperties(this, examredetailsEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}














				//级联表的get和set examquestion


					/**
					* 获取： 试题名称
					*/
					public String getQuestionContent() {
						return questionContent;
					}
					/**
					* 设置： 试题名称
					*/
					public void setQuestionContent(String questionContent) {
						this.questionContent = questionContent;
					}

					/**
					* 获取： 选项，json字符串
					*/
					public String getQuestionOptions() {
						return questionOptions;
					}
					/**
					* 设置： 选项，json字符串
					*/
					public void setQuestionOptions(String questionOptions) {
						this.questionOptions = questionOptions;
					}

					/**
					* 获取： 分值
					*/
					public Integer getQuestionScore() {
						return questionScore;
					}
					/**
					* 设置： 分值
					*/
					public void setQuestionScore(Integer questionScore) {
						this.questionScore = questionScore;
					}

					/**
					* 获取： 正确答案
					*/
					public String getQuestionAnswer() {
						return questionAnswer;
					}
					/**
					* 设置： 正确答案
					*/
					public void setQuestionAnswer(String questionAnswer) {
						this.questionAnswer = questionAnswer;
					}

					/**
					* 获取： 答案解析
					*/
					public String getQuestionAnalysis() {
						return questionAnalysis;
					}
					/**
					* 设置： 答案解析
					*/
					public void setQuestionAnalysis(String questionAnalysis) {
						this.questionAnalysis = questionAnalysis;
					}

					/**
					* 获取： 试题类型
					*/
					public Integer getQuestionType() {
						return questionType;
					}
					/**
					* 设置： 试题类型
					*/
					public void setQuestionType(Integer questionType) {
						this.questionType = questionType;
					}


						/**
						* 获取： 试题类型的值
						*/
						public String getQuestionTypeLabel() {
							return questionTypeLabel;
						}
						/**
						* 设置： 试题类型的值
						*/
						public void setQuestionTypeLabel(String questionTypeLabel) {
							this.questionTypeLabel = questionTypeLabel;
						}

					/**
					* 获取： 试题排序，值越大排越前面
					*/
					public Integer getQuestionSortOrder() {
						return questionSortOrder;
					}
					/**
					* 设置： 试题排序，值越大排越前面
					*/
					public void setQuestionSortOrder(Integer questionSortOrder) {
						this.questionSortOrder = questionSortOrder;
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
