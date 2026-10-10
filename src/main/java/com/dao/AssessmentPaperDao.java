package com.dao;

import com.entity.AssessmentPaperEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.AssessmentPaperView;

/**
 * 试卷表 Dao 接口
 *
 * @author 
 */
public interface AssessmentPaperDao extends BaseMapper<AssessmentPaperEntity> {

   List<AssessmentPaperView> selectListView(IPage<AssessmentPaperView> page,@Param("params")Map<String,Object> params);

}
