package com.dao;

import com.entity.XinlilaoshiOrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.XinlilaoshiOrderView;

/**
 * 心理咨询预约申请 Dao 接口
 *
 * @author 
 */
public interface XinlilaoshiOrderDao extends BaseMapper<XinlilaoshiOrderEntity> {

   List<XinlilaoshiOrderView> selectListView(IPage<XinlilaoshiOrderView> page,@Param("params")Map<String,Object> params);

}
