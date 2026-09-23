package com.entity.view;

import com.entity.ExamquestionEntity;
import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 试题表
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("assessment_question")
public class ExamquestionView extends ExamquestionEntity implements Serializable {
    private static final long serialVersionUID = 1L;

		/**
		* 试题类型的值
		*/
		private String questionTypeLabel;



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

	public ExamquestionView() {

	}

	public ExamquestionView(ExamquestionEntity examquestionEntity) {
		try {
			BeanUtils.copyProperties(this, examquestionEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
























}
