package com.dao;

import com.entity.YonghuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.YonghuView;

/**
 * 学生 Dao 接口
 *
 * @author 
 */
public interface YonghuDao extends BaseMapper<YonghuEntity> {

   List<YonghuView> selectListView(IPage<YonghuView> page,@Param("params")Map<String,Object> params);

}
