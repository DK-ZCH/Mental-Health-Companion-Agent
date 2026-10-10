package com.ServletContextListener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.entity.SysDictItemEntity;
import com.service.SysDictItemService;
import com.thread.MyThreadMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.annotation.WebListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典初始化监视器  用的是服务器监听,每次项目启动,都会调用这个类
 */
@WebListener
public class SysDictItemServletContextListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(SysDictItemServletContextListener.class);
    private MyThreadMethod myThreadMethod;
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("----------服务器停止----------");
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ApplicationContext appContext = WebApplicationContextUtils.getWebApplicationContext(sce.getServletContext());

        logger.info("----------字典表初始化开始----------");
        SysDictItemService dictionaryService = (SysDictItemService)appContext.getBean("dictionaryService");
        List<SysDictItemEntity> dictionaryEntities = dictionaryService.list(new QueryWrapper<SysDictItemEntity>());
        Map<String, Map<Integer,String>> map = new HashMap<>();
        for(SysDictItemEntity d :dictionaryEntities){
            Map<Integer, String> m = map.get(d.getDictCode());
            if(m ==null || m.isEmpty()){
                m = new HashMap<>();
            }
            m.put(d.getItemCode(),d.getItemName());
            map.put(d.getDictCode(),m);
        }
        sce.getServletContext().setAttribute("dictionaryMap", map);
        logger.info("----------字典表初始化完成----------");



        logger.info("----------线程执行开始----------");
        if (myThreadMethod == null) {
            myThreadMethod = new MyThreadMethod();
            myThreadMethod.start(); // servlet 上下文初始化时启动线程myThreadMethod
        }
        logger.info("----------线程执行结束----------");
    }

}
