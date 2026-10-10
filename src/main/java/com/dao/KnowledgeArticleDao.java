package com.dao;

import com.entity.KnowledgeArticleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.KnowledgeArticleView;

/**
 * 健康知识 Dao 接口
 *
 * @author 
 */
public interface KnowledgeArticleDao extends BaseMapper<KnowledgeArticleEntity> {

   List<KnowledgeArticleView> selectListView(IPage<KnowledgeArticleView> page,@Param("params")Map<String,Object> params);

}
