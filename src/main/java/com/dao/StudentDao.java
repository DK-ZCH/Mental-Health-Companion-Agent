package com.dao;

import com.entity.StudentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.StudentView;

/**
 * 学生 Dao 接口
 *
 * @author 
 */
public interface StudentDao extends BaseMapper<StudentEntity> {

   List<StudentView> selectListView(IPage<StudentView> page,@Param("params")Map<String,Object> params);

}
