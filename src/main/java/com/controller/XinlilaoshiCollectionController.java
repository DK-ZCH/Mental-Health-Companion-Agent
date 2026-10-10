
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

import com.service.DictionaryService;
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
 * 心理老师收藏
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/xinlilaoshiCollection")
public class XinlilaoshiCollectionController {
    private static final Logger logger = LoggerFactory.getLogger(XinlilaoshiCollectionController.class);

    @Autowired
    private XinlilaoshiCollectionService xinlilaoshiCollectionService;


    @Autowired
    private TokenService tokenService;
    @Autowired
    private DictionaryService dictionaryService;

    //级联表service
    @Autowired
    private YonghuService yonghuService;
    @Autowired
    private XinlilaoshiService xinlilaoshiService;



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
        PageUtils page = xinlilaoshiCollectionService.queryPage(params);

        //字典表数据转换
        List<XinlilaoshiCollectionView> list =(List<XinlilaoshiCollectionView>)page.getList();
        for(XinlilaoshiCollectionView c:list){
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
        XinlilaoshiCollectionEntity xinlilaoshiCollection = xinlilaoshiCollectionService.getById(id);
        // Step 5 批1：归属授权 —— 学生匹配 studentId；老师匹配 counselorId；管理员放行
        OwnershipGuard.assertOwnership(request, xinlilaoshiCollection,
                xinlilaoshiCollection == null ? null : xinlilaoshiCollection.getStudentId(),
                xinlilaoshiCollection == null ? null : xinlilaoshiCollection.getCounselorId());
        if(xinlilaoshiCollection !=null){
            //entity转view
            XinlilaoshiCollectionView view = new XinlilaoshiCollectionView();
            BeanUtils.copyProperties( xinlilaoshiCollection , view );//把实体数据重构到view中

                //级联表
                YonghuEntity yonghu = yonghuService.getById(xinlilaoshiCollection.getStudentId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createdAt", "favoritedAt", "repliedAt"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setStudentId(yonghu.getId());
                }
                //级联表
                XinlilaoshiEntity xinlilaoshi = xinlilaoshiService.getById(xinlilaoshiCollection.getCounselorId());
                if(xinlilaoshi != null){
                    BeanUtils.copyProperties( xinlilaoshi , view ,new String[]{ "id", "createdAt", "favoritedAt", "repliedAt"});//把级联的数据添加到view中,并排除id和创建时间字段
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
    public R save(@RequestBody XinlilaoshiCollectionEntity xinlilaoshiCollection, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,xinlilaoshiCollection:{}",this.getClass().getName(),xinlilaoshiCollection.toString());

        String role = CurrentUserProvider.currentRole(request);
        if(false)
            return R.error(511,"永远不会进入");
        else if("心理老师".equals(role))
            xinlilaoshiCollection.setCounselorId(CurrentUserProvider.requireCurrentUserId(request));
        else if("学生".equals(role))
            xinlilaoshiCollection.setStudentId(CurrentUserProvider.requireCurrentUserId(request));

        QueryWrapper<XinlilaoshiCollectionEntity> queryWrapper = new QueryWrapper<XinlilaoshiCollectionEntity>()
            .eq("counselor_id", xinlilaoshiCollection.getCounselorId())
            .eq("student_id", xinlilaoshiCollection.getStudentId())
            .eq("favorite_type", xinlilaoshiCollection.getFavoriteType())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        XinlilaoshiCollectionEntity xinlilaoshiCollectionEntity = xinlilaoshiCollectionService.getOne(queryWrapper);
        if(xinlilaoshiCollectionEntity==null){
            xinlilaoshiCollection.setFavoritedAt(new Date());
            xinlilaoshiCollection.setCreatedAt(new Date());
            xinlilaoshiCollectionService.save(xinlilaoshiCollection);
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody XinlilaoshiCollectionEntity xinlilaoshiCollection, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,xinlilaoshiCollection:{}",this.getClass().getName(),xinlilaoshiCollection.toString());

        // Step 5 批3B：写路径归属授权（此前整个 role 分支被注释 → /update 完全采信客户端实体）
        // ① 目标记录必须可写：学生仅限自己的记录（否则 403）；管理员放行；心理老师暂保持现状（→ 批 4）
        XinlilaoshiCollectionEntity existing = xinlilaoshiCollection.getId() == null ? null
                : xinlilaoshiCollectionService.getById(xinlilaoshiCollection.getId());
        OwnershipGuard.assertWritableTarget(request, existing,
                existing == null ? null : existing.getStudentId(),
                existing == null ? null : existing.getCounselorId());
        // ② 归属由服务端决定：学生强制本人（客户端伪造的 studentId 被忽略）；管理员保留其显式目标
        //    注：counselorId 属 C 类「页面选择的目标老师」，本批保留客户端值（批 4 一并处理）
        xinlilaoshiCollection.setStudentId(OwnershipGuard.resolveWriteOwner(request,
                OwnershipGuard.AdminWriteOperation.UPDATE_FAVORITE, xinlilaoshiCollection.getStudentId()));
        //根据字段查询是否有相同数据
        QueryWrapper<XinlilaoshiCollectionEntity> queryWrapper = new QueryWrapper<XinlilaoshiCollectionEntity>()
            .notIn("id",xinlilaoshiCollection.getId()).and(w -> w
            .eq("counselor_id", xinlilaoshiCollection.getCounselorId())
            .eq("student_id", xinlilaoshiCollection.getStudentId())
            .eq("favorite_type", xinlilaoshiCollection.getFavoriteType())
            );

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        XinlilaoshiCollectionEntity xinlilaoshiCollectionEntity = xinlilaoshiCollectionService.getOne(queryWrapper);
        if(xinlilaoshiCollectionEntity==null){
            xinlilaoshiCollectionService.updateById(xinlilaoshiCollection);//根据id更新
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
        xinlilaoshiCollectionService.removeByIds(Arrays.asList(ids));
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
            List<XinlilaoshiCollectionEntity> xinlilaoshiCollectionList = new ArrayList<>();//上传的东西
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
                            XinlilaoshiCollectionEntity xinlilaoshiCollectionEntity = new XinlilaoshiCollectionEntity();
//                            xinlilaoshiCollectionEntity.setCounselorId(Integer.valueOf(data.get(0)));   //心理老师 要改的
//                            xinlilaoshiCollectionEntity.setStudentId(Integer.valueOf(data.get(0)));   //学生 要改的
//                            xinlilaoshiCollectionEntity.setFavoriteType(Integer.valueOf(data.get(0)));   //类型 要改的
//                            xinlilaoshiCollectionEntity.setFavoritedAt(date);//时间
//                            xinlilaoshiCollectionEntity.setCreatedAt(date);//时间
                            xinlilaoshiCollectionList.add(xinlilaoshiCollectionEntity);


                            //把要查询是否重复的字段放入map中
                        }

                        //查询是否重复
                        xinlilaoshiCollectionService.saveBatch(xinlilaoshiCollectionList);
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
        PageUtils page = xinlilaoshiCollectionService.queryPage(params);

        //字典表数据转换
        List<XinlilaoshiCollectionView> list =(List<XinlilaoshiCollectionView>)page.getList();
        for(XinlilaoshiCollectionView c:list)
            dictionaryService.dictionaryConvert(c, request); //修改对应字典表字段
        return R.ok().put("data", page);
    }

    /**
    * 前端详情
    */
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("detail方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        XinlilaoshiCollectionEntity xinlilaoshiCollection = xinlilaoshiCollectionService.getById(id);
            if(xinlilaoshiCollection !=null){


                //entity转view
                XinlilaoshiCollectionView view = new XinlilaoshiCollectionView();
                BeanUtils.copyProperties( xinlilaoshiCollection , view );//把实体数据重构到view中

                //级联表
                    YonghuEntity yonghu = yonghuService.getById(xinlilaoshiCollection.getStudentId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setStudentId(yonghu.getId());
                }
                //级联表
                    XinlilaoshiEntity xinlilaoshi = xinlilaoshiService.getById(xinlilaoshiCollection.getCounselorId());
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
    public R add(@RequestBody XinlilaoshiCollectionEntity xinlilaoshiCollection, HttpServletRequest request){
        logger.debug("add方法:,,Controller:{},,xinlilaoshiCollection:{}",this.getClass().getName(),xinlilaoshiCollection.toString());
        // Step 5 批3B：归属由服务端决定 —— 学生强制本人（客户端伪造的 studentId 被忽略）
        // 注意：必须在「重复校验」之前解析，否则查重会用客户端伪造的归属
        xinlilaoshiCollection.setStudentId(OwnershipGuard.resolveWriteOwner(request,
                OwnershipGuard.AdminWriteOperation.ADD_FAVORITE, xinlilaoshiCollection.getStudentId()));
        QueryWrapper<XinlilaoshiCollectionEntity> queryWrapper = new QueryWrapper<XinlilaoshiCollectionEntity>()
            .eq("counselor_id", xinlilaoshiCollection.getCounselorId())
            .eq("student_id", xinlilaoshiCollection.getStudentId())
            .eq("favorite_type", xinlilaoshiCollection.getFavoriteType())
            ;
        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        XinlilaoshiCollectionEntity xinlilaoshiCollectionEntity = xinlilaoshiCollectionService.getOne(queryWrapper);
        if(xinlilaoshiCollectionEntity==null){
            xinlilaoshiCollection.setFavoritedAt(new Date());
            xinlilaoshiCollection.setCreatedAt(new Date());
        xinlilaoshiCollectionService.save(xinlilaoshiCollection);
            return R.ok();
        }else {
            return R.error(511,"您已经收藏过了");
        }
    }


}
