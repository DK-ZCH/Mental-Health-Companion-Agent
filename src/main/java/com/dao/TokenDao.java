
package com.dao;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.entity.TokenEntity;

/**
 * token
 */
public interface TokenDao extends BaseMapper<TokenEntity> {
	
	List<TokenEntity> selectListView(@Param("ew") QueryWrapper<TokenEntity> wrapper);

	List<TokenEntity> selectListView(IPage<TokenEntity> page,@Param("ew") QueryWrapper<TokenEntity> wrapper);
	
}
