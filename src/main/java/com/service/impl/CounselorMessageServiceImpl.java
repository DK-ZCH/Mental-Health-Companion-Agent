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
import com.dao.CounselorMessageDao;
import com.entity.CounselorMessageEntity;
import com.service.CounselorMessageService;
import com.entity.view.CounselorMessageView;

/**
 * 心理老师留言 服务实现类
 */
@Service("xinlilaoshiLiuyanService")
@Transactional
public class CounselorMessageServiceImpl extends ServiceImpl<CounselorMessageDao, CounselorMessageEntity> implements CounselorMessageService {

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<CounselorMessageView> page =new Query<CounselorMessageView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }


}
