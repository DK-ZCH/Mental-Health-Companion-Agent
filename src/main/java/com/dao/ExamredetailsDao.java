package com.dao;

import com.entity.ExamredetailsEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.ExamredetailsView;

/**
 * 答题详情表 Dao 接口
 *
 * @author 
 */
public interface ExamredetailsDao extends BaseMapper<ExamredetailsEntity> {

   List<ExamredetailsView> selectListView(IPage<ExamredetailsView> page,@Param("params")Map<String,Object> params);

}
