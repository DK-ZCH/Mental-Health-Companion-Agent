package com.entity.view;

import com.entity.ExampaperEntity;
import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 试卷表
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("assessment_paper")
public class ExampaperView extends ExampaperEntity implements Serializable {
    private static final long serialVersionUID = 1L;

		/**
		* 试卷状态的值
		*/
		private String statusLabel;



	public ExampaperView() {

	}

	public ExampaperView(ExampaperEntity exampaperEntity) {
		try {
			BeanUtils.copyProperties(this, exampaperEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



			/**
			* 获取： 试卷状态的值
			*/
			public String getStatusLabel() {
				return statusLabel;
			}
			/**
			* 设置： 试卷状态的值
			*/
			public void setStatusLabel(String statusLabel) {
				this.statusLabel = statusLabel;
			}
















}
