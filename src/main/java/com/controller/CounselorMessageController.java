
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
import com.service.TokenService;
import com.utils.*;
import java.lang.reflect.InvocationTargetException;

import com.service.SysDictItemService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
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
import com.utils.R;
import com.alibaba.fastjson.*;

/**
 * 心理老师留言
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/xinlilaoshiLiuyan")
public class CounselorMessageController {
    private static final Logger logger = LoggerFactory.getLogger(CounselorMessageController.class);

    @Autowired
    private CounselorMessageService xinlilaoshiLiuyanService;


    @Autowired
    private TokenService tokenService;
    @Autowired
    private SysDictItemService dictionaryService;

    //级联表service
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
        if(params.get("orderBy")==null || params.get("orderBy")==""){
            params.put("orderBy","id");
        }
        PageUtils page = xinlilaoshiLiuyanService.queryPage(params);

        //字典表数据转换
        List<CounselorMessageView> list =(List<CounselorMessageView>)page.getList();
        for(CounselorMessageView c:list){
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
        CounselorMessageEntity xinlilaoshiLiuyan = xinlilaoshiLiuyanService.getById(id);
        // Step 5 批1：归属授权 —— 学生匹配 studentId；老师匹配 counselorId；管理员放行
        OwnershipGuard.assertOwnership(request, xinlilaoshiLiuyan,
                xinlilaoshiLiuyan == null ? null : xinlilaoshiLiuyan.getStudentId(),
                xinlilaoshiLiuyan == null ? null : xinlilaoshiLiuyan.getCounselorId());
        if(xinlilaoshiLiuyan !=null){
            //entity转view
            CounselorMessageView view = new CounselorMessageView();
            BeanUtils.copyProperties( xinlilaoshiLiuyan , view );//把实体数据重构到view中

                //级联表
                StudentEntity yonghu = yonghuService.getById(xinlilaoshiLiuyan.getStudentId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createdAt", "sentAt", "repliedAt"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setStudentId(yonghu.getId());
                }
                //级联表
                CounselorEntity xinlilaoshi = xinlilaoshiService.getById(xinlilaoshiLiuyan.getCounselorId());
                if(xinlilaoshi != null){
                    BeanUtils.copyProperties( xinlilaoshi , view ,new String[]{ "id", "createdAt", "sentAt", "repliedAt"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setCounselorId(xinlilaoshi.getId());
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
    public R save(@RequestBody CounselorMessageEntity xinlilaoshiLiuyan, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,xinlilaoshiLiuyan:{}",this.getClass().getName(),xinlilaoshiLiuyan.toString());

        String role = CurrentUserProvider.currentRole(request);
        if(false)
            return R.error(511,"永远不会进入");
        else if("心理老师".equals(role))
            xinlilaoshiLiuyan.setCounselorId(CurrentUserProvider.requireCurrentUserId(request));
        else if("学生".equals(role))
            xinlilaoshiLiuyan.setStudentId(CurrentUserProvider.requireCurrentUserId(request));

        xinlilaoshiLiuyan.setSentAt(new Date());
        xinlilaoshiLiuyan.setCreatedAt(new Date());
        xinlilaoshiLiuyanService.save(xinlilaoshiLiuyan);
        return R.ok();
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody CounselorMessageEntity xinlilaoshiLiuyan, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,xinlilaoshiLiuyan:{}",this.getClass().getName(),xinlilaoshiLiuyan.toString());

        // Step 5 批3B：写路径归属授权（此前整个 role 分支被注释 → /update 完全采信客户端实体）
        // ① 目标记录必须可写：学生仅限自己的记录（否则 403）；管理员放行；心理老师暂保持现状（→ 批 4）
        CounselorMessageEntity existing = xinlilaoshiLiuyan.getId() == null ? null
                : xinlilaoshiLiuyanService.getById(xinlilaoshiLiuyan.getId());
        OwnershipGuard.assertWritableTarget(request, existing,
                existing == null ? null : existing.getStudentId(),
                existing == null ? null : existing.getCounselorId());
        // ② 归属由服务端决定：学生强制本人（客户端伪造的 studentId 被忽略）；管理员保留其显式目标
        //    注：counselorId 属 C 类「页面选择的目标老师」，本批保留客户端值（批 4 一并处理）
        xinlilaoshiLiuyan.setStudentId(OwnershipGuard.resolveWriteOwner(request,
                OwnershipGuard.AdminWriteOperation.UPDATE_MESSAGE, xinlilaoshiLiuyan.getStudentId()));
        //根据字段查询是否有相同数据
        QueryWrapper<CounselorMessageEntity> queryWrapper = new QueryWrapper<CounselorMessageEntity>()
            .eq("id",0)
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        CounselorMessageEntity xinlilaoshiLiuyanEntity = xinlilaoshiLiuyanService.getOne(queryWrapper);
        xinlilaoshiLiuyan.setRepliedAt(new Date());
        if(xinlilaoshiLiuyanEntity==null){
            xinlilaoshiLiuyanService.updateById(xinlilaoshiLiuyan);//根据id更新
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        xinlilaoshiLiuyanService.removeByIds(Arrays.asList(ids));
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
            List<CounselorMessageEntity> xinlilaoshiLiuyanList = new ArrayList<>();//上传的东西
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
                            CounselorMessageEntity xinlilaoshiLiuyanEntity = new CounselorMessageEntity();
//                            xinlilaoshiLiuyanEntity.setCounselorId(Integer.valueOf(data.get(0)));   //心理老师 要改的
//                            xinlilaoshiLiuyanEntity.setStudentId(Integer.valueOf(data.get(0)));   //学生 要改的
//                            xinlilaoshiLiuyanEntity.setContent(data.get(0));                    //留言内容 要改的
//                            xinlilaoshiLiuyanEntity.setSentAt(date);//时间
//                            xinlilaoshiLiuyanEntity.setReplyContent(data.get(0));                    //回复内容 要改的
//                            xinlilaoshiLiuyanEntity.setRepliedAt(sdf.parse(data.get(0)));          //回复时间 要改的
//                            xinlilaoshiLiuyanEntity.setCreatedAt(date);//时间
                            xinlilaoshiLiuyanList.add(xinlilaoshiLiuyanEntity);


                            //把要查询是否重复的字段放入map中
                        }

                        //查询是否重复
                        xinlilaoshiLiuyanService.saveBatch(xinlilaoshiLiuyanList);
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
    @IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("list方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));

        // 没有指定排序字段就默认id倒序
        if(StringUtil.isEmpty(String.valueOf(params.get("orderBy")))){
            params.put("orderBy","id");
        }
        PageUtils page = xinlilaoshiLiuyanService.queryPage(params);

        //字典表数据转换
        List<CounselorMessageView> list =(List<CounselorMessageView>)page.getList();
        for(CounselorMessageView c:list)
            dictionaryService.dictionaryConvert(c, request); //修改对应字典表字段
        return R.ok().put("data", page);
    }

    /**
    * 前端详情
    */
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("detail方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        CounselorMessageEntity xinlilaoshiLiuyan = xinlilaoshiLiuyanService.getById(id);
            if(xinlilaoshiLiuyan !=null){


                //entity转view
                CounselorMessageView view = new CounselorMessageView();
                BeanUtils.copyProperties( xinlilaoshiLiuyan , view );//把实体数据重构到view中

                //级联表
                    StudentEntity yonghu = yonghuService.getById(xinlilaoshiLiuyan.getStudentId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setStudentId(yonghu.getId());
                }
                //级联表
                    CounselorEntity xinlilaoshi = xinlilaoshiService.getById(xinlilaoshiLiuyan.getCounselorId());
                if(xinlilaoshi != null){
                    BeanUtils.copyProperties( xinlilaoshi , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setCounselorId(xinlilaoshi.getId());
                }
                //修改对应字典表字段
                dictionaryService.dictionaryConvert(view, request);
                return R.ok().put("data", view);
            }else {
                return R.error(511,"查不到数据");
            }
    }


    /**
    * 前端保存
    */
    @RequestMapping("/add")
    public R add(@RequestBody CounselorMessageEntity xinlilaoshiLiuyan, HttpServletRequest request){
        logger.debug("add方法:,,Controller:{},,xinlilaoshiLiuyan:{}",this.getClass().getName(),xinlilaoshiLiuyan.toString());
        // Step 5 批3B：归属由服务端决定 —— 学生强制本人（客户端伪造的 studentId 被忽略）
        xinlilaoshiLiuyan.setStudentId(OwnershipGuard.resolveWriteOwner(request,
                OwnershipGuard.AdminWriteOperation.ADD_MESSAGE, xinlilaoshiLiuyan.getStudentId()));
        xinlilaoshiLiuyan.setSentAt(new Date());
        xinlilaoshiLiuyan.setCreatedAt(new Date());
        xinlilaoshiLiuyanService.save(xinlilaoshiLiuyan);
        return R.ok();
        }


}
