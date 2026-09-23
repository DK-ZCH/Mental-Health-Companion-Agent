package com.entity.view;

import com.entity.ExamrewrongquestionEntity;
import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 错题表
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("assessment_wrong_question")
public class ExamrewrongquestionView extends ExamrewrongquestionEntity implements Serializable {
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

	public ExamrewrongquestionView() {

	}

	public ExamrewrongquestionView(ExamrewrongquestionEntity examrewrongquestionEntity) {
		try {
			BeanUtils.copyProperties(this, examrewrongquestionEntity);
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
