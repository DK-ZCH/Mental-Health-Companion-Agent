package com.dao;

import com.entity.CounselorMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.CounselorMessageView;

/**
 * 心理老师留言 Dao 接口
 *
 * @author 
 */
public interface CounselorMessageDao extends BaseMapper<CounselorMessageEntity> {

   List<CounselorMessageView> selectListView(IPage<CounselorMessageView> page,@Param("params")Map<String,Object> params);

}
