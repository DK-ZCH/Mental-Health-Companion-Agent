
package com.service.impl;


import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dao.AuthTokenDao;
import com.entity.AuthTokenEntity;
import com.entity.AuthTokenEntity;
import com.service.AuthTokenService;
import com.utils.CommonUtil;
import com.utils.PageUtils;
import com.utils.Query;


/**
 * token
 * @author
 */
@Service("authTokenService")
public class AuthTokenServiceImpl extends ServiceImpl<AuthTokenDao, AuthTokenEntity> implements AuthTokenService {

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		Page<AuthTokenEntity> page = this.page(
                new Query<AuthTokenEntity>(params).getPage(),
                new QueryWrapper<AuthTokenEntity>()
        );
        return new PageUtils(page);
	}

	@Override
	public List<AuthTokenEntity> selectListView(QueryWrapper<AuthTokenEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public PageUtils queryPage(Map<String, Object> params,
			QueryWrapper<AuthTokenEntity> wrapper) {
		 Page<AuthTokenEntity> page =new Query<AuthTokenEntity>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
	}

	@Override
	public String generateToken(Integer userId,String username, String tableName, String role) {
		AuthTokenEntity tokenEntity = this.getOne(new QueryWrapper<AuthTokenEntity>().eq("user_id", userId).eq("role", role));
		String token = CommonUtil.getRandomString(32);
		Calendar cal = Calendar.getInstance();   
    	cal.setTime(new Date());   
    	cal.add(Calendar.HOUR_OF_DAY, 1);
		if(tokenEntity!=null) {
			tokenEntity.setToken(token);
			tokenEntity.setExpiredAt(cal.getTime());
			this.updateById(tokenEntity);
		} else {
			this.save(new AuthTokenEntity(userId,username, tableName, role, token, cal.getTime()));
		}
		return token;
	}

	@Override
	public AuthTokenEntity getAuthTokenEntity(String token) {
		AuthTokenEntity tokenEntity = this.getOne(new QueryWrapper<AuthTokenEntity>().eq("token", token));
		if(tokenEntity == null || tokenEntity.getExpiredAt().getTime()<new Date().getTime()) {
			return null;
		}
		return tokenEntity;
	}
}
