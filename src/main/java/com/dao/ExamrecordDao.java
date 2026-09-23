package com.dao;

import com.entity.ExamrecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.ExamrecordView;

/**
 * 考试记录表 Dao 接口
 *
 * @author 
 */
public interface ExamrecordDao extends BaseMapper<ExamrecordEntity> {

   List<ExamrecordView> selectListView(IPage<ExamrecordView> page,@Param("params")Map<String,Object> params);

}
