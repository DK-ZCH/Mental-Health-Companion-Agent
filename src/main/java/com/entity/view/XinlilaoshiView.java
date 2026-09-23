package com.entity.view;

import com.entity.XinlilaoshiEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.beans.BeanUtils;

import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 心理老师
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("counselor")
public class XinlilaoshiView extends XinlilaoshiEntity implements Serializable {
    private static final long serialVersionUID = 1L;

		/**
		* 性别的值
		*/
		private String genderLabel;



	public XinlilaoshiView() {

	}

	public XinlilaoshiView(XinlilaoshiEntity xinlilaoshiEntity) {
		BeanUtils.copyProperties(xinlilaoshiEntity, this);
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
