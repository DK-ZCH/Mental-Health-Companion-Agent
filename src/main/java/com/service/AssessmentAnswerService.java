package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.utils.PageUtils;
import com.entity.AssessmentAnswerEntity;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 答题详情表 服务类
 */
public interface AssessmentAnswerService extends IService<AssessmentAnswerEntity> {

    /**
    * @param params 查询参数
    * @return 带分页的查询出来的数据
    */
     PageUtils queryPage(Map<String, Object> params);
}