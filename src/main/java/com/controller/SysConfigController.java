
package com.controller;


import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.annotation.IgnoreAuth;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.entity.SysConfigEntity;
import com.service.SysConfigService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.ValidatorUtils;

/**
 * 登录相关
 */
@RequestMapping("config")
@RestController
public class SysConfigController{
	
	@Autowired
	private SysConfigService configService;

	/**
     * 列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,SysConfigEntity config){
        QueryWrapper<SysConfigEntity> ew = new QueryWrapper<SysConfigEntity>();
    	PageUtils page = configService.queryPage(params);
        return R.ok().put("data", page);
    }
    
	/**
     * 列表
     */
    @IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,SysConfigEntity config){
        QueryWrapper<SysConfigEntity> ew = new QueryWrapper<SysConfigEntity>();
    	PageUtils page = configService.queryPage(params);
        return R.ok().put("data", page);
    }

    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") String id){
        SysConfigEntity config = configService.getById(id);
        return R.ok().put("data", config);
    }
    
    /**
     * 详情
     */
    @IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") String id){
        SysConfigEntity config = configService.getById(id);
        return R.ok().put("data", config);
    }
    
    /**
     * 根据name获取信息
     */
    @RequestMapping("/info")
    public R infoByName(@RequestParam String name){
        SysConfigEntity config = configService.getOne(new QueryWrapper<SysConfigEntity>().eq("config_key", "faceFile"));
        return R.ok().put("data", config);
    }
    
    /**
     * 保存
     */
    @PostMapping("/save")
    public R save(@RequestBody SysConfigEntity config){
//    	ValidatorUtils.validateEntity(config);
    	configService.save(config);
        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody SysConfigEntity config){
//        ValidatorUtils.validateEntity(config);
        configService.updateById(config);//全部更新
        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
    	configService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }
}
