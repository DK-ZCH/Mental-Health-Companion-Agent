package com.entity.view;

import com.entity.DictionaryEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.beans.BeanUtils;

import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 字典
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("sys_dict_item")
public class DictionaryView extends DictionaryEntity implements Serializable {
    private static final long serialVersionUID = 1L;




	public DictionaryView() {

	}

	public DictionaryView(DictionaryEntity dictionaryEntity) {
		BeanUtils.copyProperties(dictionaryEntity, this);
	}

































}
