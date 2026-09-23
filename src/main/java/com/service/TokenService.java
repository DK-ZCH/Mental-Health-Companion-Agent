
package com.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.entity.TokenEntity;
import com.utils.PageUtils;


/**
 * token
 * @author yangliyuan
 * @date 2019年10月10日 上午9:18:20
 */
public interface TokenService extends IService<TokenEntity> {
 	PageUtils queryPage(Map<String, Object> params);
    
   	List<TokenEntity> selectListView(QueryWrapper<TokenEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params,QueryWrapper<TokenEntity> wrapper);
	
   	String generateToken(Integer userId,String username,String tableName, String role);
   	
   	TokenEntity getTokenEntity(String token);
}
