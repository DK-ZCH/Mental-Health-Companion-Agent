package com.dao;

import com.entity.ExamquestionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.ExamquestionView;

/**
 * 试题表 Dao 接口
 *
 * @author 
 */
public interface ExamquestionDao extends BaseMapper<ExamquestionEntity> {

   List<ExamquestionView> selectListView(IPage<ExamquestionView> page,@Param("params")Map<String,Object> params);

}
