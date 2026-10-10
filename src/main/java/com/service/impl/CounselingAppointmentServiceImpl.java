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
import com.dao.CounselingAppointmentDao;
import com.entity.CounselingAppointmentEntity;
import com.service.CounselingAppointmentService;
import com.entity.view.CounselingAppointmentView;

/**
 * 心理咨询预约申请 服务实现类
 */
@Service("counselingAppointmentService")
@Transactional
public class CounselingAppointmentServiceImpl extends ServiceImpl<CounselingAppointmentDao, CounselingAppointmentEntity> implements CounselingAppointmentService {

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<CounselingAppointmentView> page =new Query<CounselingAppointmentView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }


}
