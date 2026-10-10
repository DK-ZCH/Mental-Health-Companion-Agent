
package com.service.impl;


import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dao.SysConfigDao;
import com.entity.SysConfigEntity;
import com.service.SysConfigService;
import com.utils.PageUtils;
import com.utils.Query;


/**
 * 系统学生
 * @author yangliyuan
 * @date 2019年10月10日 上午9:17:59
 */
@Service("configService")
public class SysSysConfigServiceImpl extends ServiceImpl<SysConfigDao, SysConfigEntity> implements SysConfigService {
	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		Page<SysConfigEntity> page = this.page(
                new Query<SysConfigEntity>(params).getPage(),
                new QueryWrapper<SysConfigEntity>()
        );
        return new PageUtils(page);
	}
}
