package com.dao;

import com.entity.XinlilaoshiEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.XinlilaoshiView;

/**
 * 心理老师 Dao 接口
 *
 * @author 
 */
public interface XinlilaoshiDao extends BaseMapper<XinlilaoshiEntity> {

   List<XinlilaoshiView> selectListView(IPage<XinlilaoshiView> page,@Param("params")Map<String,Object> params);

}
