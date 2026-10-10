
package com.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.entity.AuthTokenEntity;
import com.utils.PageUtils;


/**
 * token
 * @author yangliyuan
 * @date 2019年10月10日 上午9:18:20
 */
public interface AuthTokenService extends IService<AuthTokenEntity> {
 	PageUtils queryPage(Map<String, Object> params);
    
   	List<AuthTokenEntity> selectListView(QueryWrapper<AuthTokenEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,QueryWrapper<AuthTokenEntity> wrapper);
	
   	String generateToken(Integer userId,String username,String tableName, String role);
   	
   	AuthTokenEntity getAuthTokenEntity(String token);
}
