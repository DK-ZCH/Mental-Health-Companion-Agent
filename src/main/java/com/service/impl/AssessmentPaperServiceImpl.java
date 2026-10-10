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
import com.dao.AssessmentPaperDao;
import com.entity.AssessmentPaperEntity;
import com.service.AssessmentPaperService;
import com.entity.view.AssessmentPaperView;

/**
 * 试卷表 服务实现类
 */
@Service("exampaperService")
@Transactional
public class AssessmentPaperServiceImpl extends ServiceImpl<AssessmentPaperDao, AssessmentPaperEntity> implements AssessmentPaperService {

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<AssessmentPaperView> page =new Query<AssessmentPaperView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }


}
