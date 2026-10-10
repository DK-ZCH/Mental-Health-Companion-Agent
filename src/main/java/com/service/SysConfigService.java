
package com.service;

import java.util.Map;

import com.baomidou.mybatisplus.extension.service.IService;
import com.entity.SysConfigEntity;
import com.utils.PageUtils;


/**
 * 系统学生
 * @author yangliyuan
 * @date 2019年10月10日 上午9:18:20
 */
public interface SysConfigService extends IService<SysConfigEntity> {
	PageUtils queryPage(Map<String, Object> params);
}
