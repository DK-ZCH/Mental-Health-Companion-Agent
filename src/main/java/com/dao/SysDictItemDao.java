package com.dao;

import com.entity.SysDictItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.SysDictItemView;

/**
 * 字典 Dao 接口
 *
 * @author 
 */
public interface SysDictItemDao extends BaseMapper<SysDictItemEntity> {

   List<SysDictItemView> selectListView(IPage<SysDictItemView> page,@Param("params")Map<String,Object> params);

}
