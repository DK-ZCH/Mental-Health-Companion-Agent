
package com.dao;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.entity.AuthTokenEntity;

/**
 * token
 */
public interface AuthTokenDao extends BaseMapper<AuthTokenEntity> {
	
	List<AuthTokenEntity> selectListView(@Param("ew") QueryWrapper<AuthTokenEntity> wrapper);

	List<AuthTokenEntity> selectListView(IPage<AuthTokenEntity> page,@Param("ew") QueryWrapper<AuthTokenEntity> wrapper);
	
}
