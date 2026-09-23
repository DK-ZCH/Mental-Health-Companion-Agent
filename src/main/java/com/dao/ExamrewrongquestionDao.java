package com.dao;

import com.entity.ExamrewrongquestionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.ExamrewrongquestionView;

/**
 * 错题表 Dao 接口
 *
 * @author 
 */
public interface ExamrewrongquestionDao extends BaseMapper<ExamrewrongquestionEntity> {

   List<ExamrewrongquestionView> selectListView(IPage<ExamrewrongquestionView> page,@Param("params")Map<String,Object> params);

}
