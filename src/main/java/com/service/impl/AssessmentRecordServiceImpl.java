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
import com.dao.AssessmentRecordDao;
import com.entity.AssessmentRecordEntity;
import com.service.AssessmentRecordService;
import com.entity.view.AssessmentRecordView;

/**
 * 考试记录表 服务实现类
 */
@Service("assessmentRecordService")
@Transactional
public class AssessmentRecordServiceImpl extends ServiceImpl<AssessmentRecordDao, AssessmentRecordEntity> implements AssessmentRecordService {

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<AssessmentRecordView> page =new Query<AssessmentRecordView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }


}
