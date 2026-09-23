package com.dao;

import com.entity.ExampaperEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.ExampaperView;

/**
 * 试卷表 Dao 接口
 *
 * @author 
 */
public interface ExampaperDao extends BaseMapper<ExampaperEntity> {

   List<ExampaperView> selectListView(IPage<ExampaperView> page,@Param("params")Map<String,Object> params);

}
