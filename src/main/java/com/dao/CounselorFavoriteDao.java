package com.dao;

import com.entity.CounselorFavoriteEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.CounselorFavoriteView;

/**
 * 心理老师收藏 Dao 接口
 *
 * @author 
 */
public interface CounselorFavoriteDao extends BaseMapper<CounselorFavoriteEntity> {

   List<CounselorFavoriteView> selectListView(IPage<CounselorFavoriteView> page,@Param("params")Map<String,Object> params);

}
