package com.dao;

import com.entity.CounselingAppointmentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.apache.ibatis.annotations.Param;
import com.entity.view.CounselingAppointmentView;

/**
 * 心理咨询预约申请 Dao 接口
 *
 * @author 
 */
public interface CounselingAppointmentDao extends BaseMapper<CounselingAppointmentEntity> {

   List<CounselingAppointmentView> selectListView(IPage<CounselingAppointmentView> page,@Param("params")Map<String,Object> params);

}
