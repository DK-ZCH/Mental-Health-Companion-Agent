package com.dao;

import com.entity.AssessmentRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.AssessmentRecordView;

/**
 * 考试记录表 Dao 接口
 *
 * @author 
 */
public interface AssessmentRecordDao extends BaseMapper<AssessmentRecordEntity> {

   List<AssessmentRecordView> selectListView(IPage<AssessmentRecordView> page,@Param("params")Map<String,Object> params);

}
