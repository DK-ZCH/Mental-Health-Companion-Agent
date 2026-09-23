
package com.dao;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.entity.UsersEntity;

/**
 * 学生
 */
public interface UsersDao extends BaseMapper<UsersEntity> {
	
	List<UsersEntity> selectListView(@Param("ew") QueryWrapper<UsersEntity> wrapper);

	List<UsersEntity> selectListView(IPage<UsersEntity> page, @Param("ew") QueryWrapper<UsersEntity> wrapper);
	
}
