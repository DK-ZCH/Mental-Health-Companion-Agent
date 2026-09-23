package com.entity.view;

import com.entity.YonghuEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.beans.BeanUtils;

import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 学生
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("student")
public class YonghuView extends YonghuEntity implements Serializable {
    private static final long serialVersionUID = 1L;

		/**
		* 性别的值
		*/
		private String genderLabel;



	public YonghuView() {

	}

	public YonghuView(YonghuEntity yonghuEntity) {
		BeanUtils.copyProperties(yonghuEntity, this);
	}



			/**
			* 获取： 性别的值
			*/
			public String getGenderLabel() {
				return genderLabel;
			}
			/**
			* 设置： 性别的值
			*/
			public void setGenderLabel(String genderLabel) {
				this.genderLabel = genderLabel;
			}
















}
