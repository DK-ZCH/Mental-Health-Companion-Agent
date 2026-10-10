package com.dao;

import com.entity.AssessmentQuestionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.AssessmentQuestionView;

/**
 * 试题表 Dao 接口
 *
 * @author 
 */
public interface AssessmentQuestionDao extends BaseMapper<AssessmentQuestionEntity> {

   List<AssessmentQuestionView> selectListView(IPage<AssessmentQuestionView> page,@Param("params")Map<String,Object> params);

}
