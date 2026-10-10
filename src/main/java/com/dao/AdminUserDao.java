
package com.dao;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.entity.AdminUserEntity;

/**
 * 学生
 */
public interface AdminUserDao extends BaseMapper<AdminUserEntity> {
	
	List<AdminUserEntity> selectListView(@Param("ew") QueryWrapper<AdminUserEntity> wrapper);

	List<AdminUserEntity> selectListView(IPage<AdminUserEntity> page, @Param("ew") QueryWrapper<AdminUserEntity> wrapper);
	
}
