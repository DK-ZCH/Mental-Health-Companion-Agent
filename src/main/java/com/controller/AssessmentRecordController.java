
package com.controller;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import java.util.*;
import org.springframework.beans.BeanUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import jakarta.servlet.ServletContext;
import com.service.AuthTokenService;
import com.utils.*;
import java.lang.reflect.InvocationTargetException;

import com.service.SysDictItemService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.entity.*;
import com.entity.view.*;
import com.service.*;
import com.utils.PageUtils;
import com.security.OwnershipGuard;
import com.security.CurrentUserProvider;
import com.security.DataScope;
import com.security.CounselorAssessmentScope;
import com.utils.R;
import com.alibaba.fastjson.*;

/**
 * 考试记录表
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/assessmentRecord")
public class AssessmentRecordController {
    private static final Logger logger = LoggerFactory.getLogger(AssessmentRecordController.class);

    @Autowired
    private AssessmentRecordService examrecordService;

    /** ③ Counselor Assessment Access Rule v1：心理老师访问测评记录的授权判定 */
    @Autowired
    private CounselorAssessmentScope counselorAssessmentScope;


    @Autowired
    private AuthTokenService tokenService;
    @Autowired
    private SysDictItemService dictionaryService;

    //级联表service
    @Autowired
    private AssessmentPaperService exampaperService;
    @Autowired
    private StudentService yonghuService;

    @Autowired
    private CounselorService xinlilaoshiService;


    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        // Step 5 批4：读范围收敛（原 if/else-if 角色链）—— 未知角色由「不加过滤 → 全量」改为 fail-closed
        DataScope.apply(request, params, "studentId", "counselorId");
        // ③ C1 规则：把「已同意」状态常量传给 Mapper（XML 内不写死数字，规则口径只在一处定义）。
        //    仅 counselor 会进入 XML 的 counselorId 分支；学生不传 counselorId、管理员被 DataScope 直接放行。
        params.put(CounselorAssessmentScope.PARAM_APPROVED_STATUS,
                CounselorAssessmentScope.STATUS_APPROVED);
        if(params.get("orderBy")==null || params.get("orderBy")==""){
            params.put("orderBy","id");
        }
        PageUtils page = examrecordService.queryPage(params);

        //字典表数据转换
        List<AssessmentRecordView> list =(List<AssessmentRecordView>)page.getList();
        for(AssessmentRecordView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c, request);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        AssessmentRecordEntity examrecord = examrecordService.getById(id);
        // ③ C1：授权入口【唯一】—— 归属判定与关系判定一次完成，Controller 不再串联第二个 Guard。
        //     · 学生 / 管理员：由 OwnershipGuard 既有语义决定（不触发回调）
        //     · 心理老师 + 实体无 counselor_id（本表正是如此）：由回调判定
        //       「是否与该学生存在已同意预约」，无关系 → 403
        //    ⚠️ 若只改列表 SQL 而不加这一处，则「列表看不到、详情能打开」= 修了一半。
        Integer targetStudentId = examrecord == null ? null : examrecord.getStudentId();
        OwnershipGuard.assertOwnership(request, examrecord, targetStudentId, null,
                req -> counselorAssessmentScope.hasApprovedAppointment(req, targetStudentId));
        if(examrecord !=null){
            //entity转view
            AssessmentRecordView view = new AssessmentRecordView();
            BeanUtils.copyProperties( examrecord , view );//把实体数据重构到view中

                //级联表
                AssessmentPaperEntity exampaper = exampaperService.getById(examrecord.getPaperId());
                if(exampaper != null){
                    BeanUtils.copyProperties( exampaper , view ,new String[]{ "id", "createdAt", "submittedAt", "repliedAt"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setPaperId(exampaper.getId());
                }
                //级联表
                StudentEntity yonghu = yonghuService.getById(examrecord.getStudentId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createdAt", "submittedAt", "repliedAt"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setStudentId(yonghu.getId());
                }
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view, request);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 后端保存
    */
    @RequestMapping("/save")
    public R save(@RequestBody AssessmentRecordEntity examrecord, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,examrecord:{}",this.getClass().getName(),examrecord.toString());

        String role = CurrentUserProvider.currentRole(request);
        if(false)
            return R.error(511,"永远不会进入");
        else if("学生".equals(role))
            examrecord.setStudentId(CurrentUserProvider.requireCurrentUserId(request));

        QueryWrapper<AssessmentRecordEntity> queryWrapper = new QueryWrapper<AssessmentRecordEntity>()
            .eq("record_no", examrecord.getRecordNo())
            .eq("student_id", examrecord.getStudentId())
            .eq("paper_id", examrecord.getPaperId())
            .eq("total_score", examrecord.getTotalScore())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        AssessmentRecordEntity examrecordEntity = examrecordService.getOne(queryWrapper);
        if(examrecordEntity==null){
            examrecord.setSubmittedAt(new Date());
            examrecord.setCreatedAt(new Date());
            examrecordService.save(examrecord);
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody AssessmentRecordEntity examrecord, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,examrecord:{}",this.getClass().getName(),examrecord.toString());

        // Step 5 批3B：写路径归属授权（此前整个 role 分支被注释 → /update 完全采信客户端实体）
        // ① 目标记录必须可写：学生仅限自己的记录（否则 403）；管理员放行；心理老师暂保持现状（→ 批 4）
        AssessmentRecordEntity existing = examrecord.getId() == null ? null
                : examrecordService.getById(examrecord.getId());
        OwnershipGuard.assertWritableTarget(request, existing,
                existing == null ? null : existing.getStudentId(), null);
        // ② 归属由服务端决定：学生强制本人（客户端伪造的 studentId 被忽略）；管理员保留其显式目标
        examrecord.setStudentId(OwnershipGuard.resolveWriteOwner(request,
                OwnershipGuard.AdminWriteOperation.UPDATE_ASSESSMENT_RECORD, examrecord.getStudentId()));
        //根据字段查询是否有相同数据
        QueryWrapper<AssessmentRecordEntity> queryWrapper = new QueryWrapper<AssessmentRecordEntity>()
            .notIn("id",examrecord.getId()).and(w -> w
            .eq("record_no", examrecord.getRecordNo())
            .eq("student_id", examrecord.getStudentId())
            .eq("paper_id", examrecord.getPaperId())
            .eq("total_score", examrecord.getTotalScore())
            );

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        AssessmentRecordEntity examrecordEntity = examrecordService.getOne(queryWrapper);
        if(examrecordEntity==null){
            com.security.UpdateGuard.assertHasUpdatableFields(examrecord); // T1：空更新防护（400，不进入 DAO）
            examrecordService.updateById(examrecord);//根据id更新
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids, HttpServletRequest request){
        OwnershipGuard.assertAdminOnly(request);
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        examrecordService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }


    /**
     * 批量上传
     */
    @RequestMapping("/batchInsert")
    public R save( String fileName, HttpServletRequest request){
        logger.debug("batchInsert方法:,,Controller:{},,fileName:{}",this.getClass().getName(),fileName);
        Integer studentId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            List<AssessmentRecordEntity> examrecordList = new ArrayList<>();//上传的东西
            Map<String, List<String>> seachFields= new HashMap<>();//要查询的字段
            Date date = new Date();
            int lastIndexOf = fileName.lastIndexOf(".");
            if(lastIndexOf == -1){
                return R.error(511,"该文件没有后缀");
            }else{
                String suffix = fileName.substring(lastIndexOf);
                if(!".xls".equals(suffix)){
                    return R.error(511,"只支持后缀为xls的excel文件");
                }else{
                    URL resource = this.getClass().getClassLoader().getResource("static/upload/" + fileName);//获取文件路径
                    File file = new File(resource.getFile());
                    if(!file.exists()){
                        return R.error(511,"找不到上传文件，请联系管理员");
                    }else{
                        List<List<String>> dataList = PoiUtil.poiImport(file.getPath());//读取xls文件
                        dataList.remove(0);//删除第一行，因为第一行是提示
                        for(List<String> data:dataList){
                            //循环
                            AssessmentRecordEntity examrecordEntity = new AssessmentRecordEntity();
//                            examrecordEntity.setRecordNo(data.get(0));                    //考试编号 要改的
//                            examrecordEntity.setStudentId(Integer.valueOf(data.get(0)));   //考试学生 要改的
//                            examrecordEntity.setPaperId(Integer.valueOf(data.get(0)));   //所属试卷id（外键） 要改的
//                            examrecordEntity.setTotalScore(Integer.valueOf(data.get(0)));   //所得总分 要改的
//                            examrecordEntity.setSubmittedAt(date);//时间
//                            examrecordEntity.setCreatedAt(date);//时间
                            examrecordList.add(examrecordEntity);


                            //把要查询是否重复的字段放入map中
                                //考试编号
                                if(seachFields.containsKey("recordNo")){
                                    List<String> recordNo = seachFields.get("recordNo");
                                    recordNo.add(data.get(0));//要改的
                                }else{
                                    List<String> recordNo = new ArrayList<>();
                                    recordNo.add(data.get(0));//要改的
                                    seachFields.put("recordNo",recordNo);
                                }
                        }

                        //查询是否重复
                         //考试编号
                        List<AssessmentRecordEntity> examrecordEntities_examrecordUuidNumber = examrecordService.list(new QueryWrapper<AssessmentRecordEntity>().in("record_no", seachFields.get("recordNo")));
                        if(examrecordEntities_examrecordUuidNumber.size() >0 ){
                            ArrayList<String> repeatFields = new ArrayList<>();
                            for(AssessmentRecordEntity s:examrecordEntities_examrecordUuidNumber){
                                repeatFields.add(s.getRecordNo());
                            }
                            return R.error(511,"数据库的该表中的 [考试编号] 字段已经存在 存在数据为:"+repeatFields.toString());
                        }
                        examrecordService.saveBatch(examrecordList);
                        return R.ok();
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            return R.error(511,"批量插入数据异常，请联系管理员");
        }
    }





    /**
    * 前端列表
    */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("list方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));

        // D2-2b：读范围收敛（与 /page 同款，两入口共用同一条 selectListView 的 params 条件）
        // ③ C1：教师侧原先「XML 无 counselor_id 条件 → 条件被静默丢弃」（G3 挂账）已由
        //    XML 的 EXISTS 子查询补齐；学生侧语义不变。两个入口共用此 SQL ⇒ 改一处同时生效。
        DataScope.apply(request, params, "studentId", "counselorId");
        params.put(CounselorAssessmentScope.PARAM_APPROVED_STATUS,
                CounselorAssessmentScope.STATUS_APPROVED);

        // 没有指定排序字段就默认id倒序
        if(StringUtil.isEmpty(String.valueOf(params.get("orderBy")))){
            params.put("orderBy","id");
        }
        PageUtils page = examrecordService.queryPage(params);

        //字典表数据转换
        List<AssessmentRecordView> list =(List<AssessmentRecordView>)page.getList();
        for(AssessmentRecordView c:list)
            dictionaryService.dictionaryConvert(c, request); //修改对应字典表字段
        return R.ok().put("data", page);
    }

    /**
    * 前端保存
    */
    @RequestMapping("/add")
    public R add(@RequestBody AssessmentRecordEntity examrecord, HttpServletRequest request){
        logger.debug("add方法:,,Controller:{},,examrecord:{}",this.getClass().getName(),examrecord.toString());
        QueryWrapper<AssessmentRecordEntity> queryWrapper = new QueryWrapper<AssessmentRecordEntity>()
            .eq("record_no", examrecord.getRecordNo())
            .eq("student_id", examrecord.getStudentId())
            .eq("paper_id", examrecord.getPaperId())
            .eq("total_score", examrecord.getTotalScore())
            ;
        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        AssessmentRecordEntity examrecordEntity = examrecordService.getOne(queryWrapper);
        if(examrecordEntity==null){
            examrecord.setSubmittedAt(new Date());
            examrecord.setCreatedAt(new Date());
        examrecordService.save(examrecord);
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }



    @Autowired
    private AssessmentAnswerService examredetailsService;
    /**
    * 后端保存
    */
    @RequestMapping("/saveAssessmentRecord")
    public R saveAssessmentRecord(Integer paperId, HttpServletRequest request){
        AssessmentRecordEntity examrecord = new AssessmentRecordEntity();
        String uuid = String.valueOf(new Date().getTime());
        examrecord.setRecordNo(uuid);
        examrecord.setPaperId(paperId);
        examrecord.setTotalScore(0);
        examrecord.setStudentId(CurrentUserProvider.requireCurrentUserId(request));
        examrecord.setSubmittedAt(new Date());
        examrecord.setCreatedAt(new Date());
        boolean insert = examrecordService.save(examrecord);
        if(!insert){
            return R.error();
        }
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("uuid",uuid);
        map.put("id",examrecord.getId());
        return R.ok(map);
    }

    /**
     * 删除
     */
    @RequestMapping("/deleteAssessmentRecord")
    public R deleteAssessmentRecord(@RequestBody Integer[] ids, HttpServletRequest request){
        OwnershipGuard.assertAdminOnly(request);
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        List<AssessmentRecordEntity> examrecordE = examrecordService.listByIds(Arrays.asList(ids));//考试记录表
        List<String> joinIds = new ArrayList<>();
        for (AssessmentRecordEntity examrecord:examrecordE) {
            joinIds.add(examrecord.getRecordNo());
        }
        boolean deleteSuccess = examredetailsService.remove(new QueryWrapper<AssessmentAnswerEntity>().in("record_no", joinIds));
        if(!deleteSuccess){
            return R.error();
        }
        boolean b = examrecordService.removeByIds(Arrays.asList(ids));//删除当前表
        if(!b){
            return R.error();
        }
        return R.ok();
    }
}
