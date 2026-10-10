package com.dao;

import com.entity.NotificationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.NotificationView;

/**
 * 通知 Dao 接口
 *
 * @author 
 */
public interface NotificationDao extends BaseMapper<NotificationEntity> {

   List<NotificationView> selectListView(IPage<NotificationView> page,@Param("params")Map<String,Object> params);

}
