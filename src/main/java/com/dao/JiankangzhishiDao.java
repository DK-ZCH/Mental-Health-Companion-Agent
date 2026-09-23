package com.dao;

import com.entity.JiankangzhishiEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.JiankangzhishiView;

/**
 * 健康知识 Dao 接口
 *
 * @author 
 */
public interface JiankangzhishiDao extends BaseMapper<JiankangzhishiEntity> {

   List<JiankangzhishiView> selectListView(IPage<JiankangzhishiView> page,@Param("params")Map<String,Object> params);

}
