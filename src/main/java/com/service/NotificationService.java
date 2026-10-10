package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.utils.PageUtils;
import com.entity.NotificationEntity;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 通知 服务类
 */
public interface NotificationService extends IService<NotificationEntity> {

    /**
    * @param params 查询参数
    * @return 带分页的查询出来的数据
    */
     PageUtils queryPage(Map<String, Object> params);
}