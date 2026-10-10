
package com.service.impl;


import java.util.List;
import java.util.Map;

import com.service.AdminUserService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dao.AdminUserDao;
import com.entity.AdminUserEntity;
import com.utils.PageUtils;
import com.utils.Query;


/**
 * 系统学生
 * @author
 */
@Service("userService")
public class AdminUserServiceImpl extends ServiceImpl<AdminUserDao, AdminUserEntity> implements AdminUserService {

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		Page<AdminUserEntity> page = this.page(
                new Query<AdminUserEntity>(params).getPage(),
                new QueryWrapper<AdminUserEntity>()
        );
        return new PageUtils(page);
	}

	@Override
	public List<AdminUserEntity> selectListView(QueryWrapper<AdminUserEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public PageUtils queryPage(Map<String, Object> params,
			QueryWrapper<AdminUserEntity> wrapper) {
		 Page<AdminUserEntity> page =new Query<AdminUserEntity>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
	}
}
