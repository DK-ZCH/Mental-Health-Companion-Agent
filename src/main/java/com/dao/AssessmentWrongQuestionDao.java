package com.dao;

import com.entity.AssessmentWrongQuestionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.AssessmentWrongQuestionView;

/**
 * 错题表 Dao 接口
 *
 * @author 
 */
public interface AssessmentWrongQuestionDao extends BaseMapper<AssessmentWrongQuestionEntity> {

   List<AssessmentWrongQuestionView> selectListView(IPage<AssessmentWrongQuestionView> page,@Param("params")Map<String,Object> params);

}
