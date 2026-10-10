
package com.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.entity.AdminUserEntity;
import com.utils.PageUtils;


/**
 * 系统学生
 * @author yangliyuan
 * @date 2019年10月10日 上午9:18:20
 */
public interface AdminUserService extends IService<AdminUserEntity> {
 	PageUtils queryPage(Map<String, Object> params);
    
   	List<AdminUserEntity> selectListView(QueryWrapper<AdminUserEntity> wrapper);
   	
   	PageUtils queryPage(Map<String, Object> params, QueryWrapper<AdminUserEntity> wrapper);
	   	
}
