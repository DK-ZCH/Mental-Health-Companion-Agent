package com.service.impl;

import com.utils.StringUtil;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.util.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;
import com.utils.PageUtils;
import com.utils.Query;
import org.springframework.web.context.ContextLoader;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import com.dao.NotificationDao;
import com.entity.NotificationEntity;
import com.service.NotificationService;
import com.entity.view.NotificationView;

/**
 * 通知 服务实现类
 */
@Service("tongzhiService")
@Transactional
public class NotificationServiceImpl extends ServiceImpl<NotificationDao, NotificationEntity> implements NotificationService {

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<NotificationView> page =new Query<NotificationView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }


}
