
package com.controller;


import java.util.Arrays;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.service.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.annotation.IgnoreAuth;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.entity.AdminUserEntity;
import com.service.TokenService;
import com.utils.MPUtil;
import com.utils.PageUtils;
import com.security.OwnershipGuard;
import com.security.CurrentUserProvider;
import com.utils.R;

/**
 * 登录相关
 */
@RequestMapping("users")
@RestController
public class AdminUserController {
	
	@Autowired
	private AdminUserService usersService;
	
	@Autowired
	private TokenService tokenService;

	/**
	 * 登录
	 */
	@IgnoreAuth
	@PostMapping(value = "/login")
	public R login(String username, String password, String captcha, HttpServletRequest request) {
		AdminUserEntity user = usersService.getOne(new QueryWrapper<AdminUserEntity>().eq("username", username));
		if(user==null || !user.getPassword().equals(password)) {
			return R.error("账号或密码不正确");
		}
		String token = tokenService.generateToken(user.getId(),username, "admin_user", user.getRole());
		R r = R.ok();
		r.put("token", token);
		r.put("role",user.getRole());
		r.put("userId",user.getId());
		return r;
	}
	
	/**
	 * 注册
	 */
	@IgnoreAuth
	@PostMapping(value = "/register")
	public R register(@RequestBody AdminUserEntity user){
//    	ValidatorUtils.validateEntity(user);
    	if(usersService.getOne(new QueryWrapper<AdminUserEntity>().eq("username", user.getUsername())) !=null) {
    		return R.error("学生已存在");
    	}
        usersService.save(user);
        return R.ok();
    }

	/**
	 * 退出
	 */
	@GetMapping(value = "logout")
	public R logout(HttpServletRequest request) {
		request.getSession().invalidate();
		return R.ok("退出成功");
	}
	
	// ── Step 5 / D7（独立安全小批次）：已【下线】匿名密码重置端点 {@code /users/resetPass} ──
	// 下线依据（批 3A §6 调用面核查，全部为零）：
	//   Java：仅本方法这一处定义，无内部调用；用户端 0；管理端 src 0；管理端 dist 0；
	//         无 scripts/bin/deploy/docker 目录引用；配置 0；业务说明 0。
	// 风险定性：该端点为 @IgnoreAuth（免鉴权）+ 传入 username 即可把密码重置为 123456，
	//         而 users 表是【管理端账号】表 → 匿名即可重置管理员密码（账户接管）。
	// 影响面：管理端「重置密码」按钮走的是 /yonghu/resetPassword、/xinlilaoshi/resetPassword
	//         （批 2 已限管理员），与本端点无关；用户/老师端的「忘记密码」走各自的 /resetPass（按 D3 不动）。
	// 回归：见 IdentityAuthorizationRegressionTest「D7」用例 —— 断言该路径返回 404。
	
	/**
     * 列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,AdminUserEntity user){
        QueryWrapper<AdminUserEntity> ew = new QueryWrapper<AdminUserEntity>();
    	PageUtils page = usersService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.allLike(ew, user), params), params));
        return R.ok().put("data", page);
    }

	/**
     * 列表
     */
    @RequestMapping("/list")
    public R list( AdminUserEntity user){
       	QueryWrapper<AdminUserEntity> ew = new QueryWrapper<AdminUserEntity>();
      	ew.allEq(MPUtil.allEQMapPre( user, "user")); 
        return R.ok().put("data", usersService.selectListView(ew));
    }

    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") String id, HttpServletRequest request){
        // Step 5 批1：归属授权 —— 管理端账号详情仅管理员可访问（学生/老师一律拒绝）
        OwnershipGuard.assertAdminOnly(request);
        AdminUserEntity user = usersService.getById(id);
        return R.ok().put("data", user);
    }
    
    /**
     * 获取学生的session学生信息
     */
    @RequestMapping("/session")
    public R getCurrUser(HttpServletRequest request){
    	Integer id = CurrentUserProvider.currentUserIdOrNull(request);
        AdminUserEntity user = usersService.getById(id);
        return R.ok().put("data", user);
    }

    /**
     * 保存
     */
    @PostMapping("/save")
    public R save(@RequestBody AdminUserEntity user){
//    	ValidatorUtils.validateEntity(user);
    	if(usersService.getOne(new QueryWrapper<AdminUserEntity>().eq("username", user.getUsername())) !=null) {
    		return R.error("学生已存在");
    	}
    	user.setPassword("123456");
        usersService.save(user);
        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody AdminUserEntity user){
//        ValidatorUtils.validateEntity(user);
        usersService.updateById(user);//全部更新
        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        usersService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }
}
