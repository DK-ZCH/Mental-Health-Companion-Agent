package com.dao;

import com.entity.CounselorEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.CounselorView;

/**
 * 心理老师 Dao 接口
 *
 * @author 
 */
public interface CounselorDao extends BaseMapper<CounselorEntity> {

   List<CounselorView> selectListView(IPage<CounselorView> page,@Param("params")Map<String,Object> params);

}
