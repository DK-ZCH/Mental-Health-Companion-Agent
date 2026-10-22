
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
import com.service.AuthTokenService;
import com.utils.MPUtil;
import com.utils.PageUtils;
import com.security.OwnershipGuard;
import com.security.CurrentUserProvider;
import com.security.PasswordHasher;
import com.utils.R;

/**
 * 登录相关
 */
@RequestMapping("adminUser")
@RestController
public class AdminUserController {
	
	@Autowired
	private AdminUserService usersService;
	
	@Autowired
	private AuthTokenService tokenService;

	/**
	 * 登录
	 */
	@IgnoreAuth
	@PostMapping(value = "/login")
	public R login(String username, String password, String captcha, HttpServletRequest request) {
		AdminUserEntity user = usersService.getOne(new QueryWrapper<AdminUserEntity>().eq("username", username));
		// S2-F-4-1：双格式校验（BCrypt 优先，历史明文兜底；空值/非法哈希一律 false）
		if(user==null || !PasswordHasher.matches(password, user.getPassword())) {
			return R.error("账号或密码不正确");
		}
		String token = tokenService.generateToken(user.getId(),username, "admin_user", user.getRole());
		R r = R.ok();
		r.put("token", token);
		r.put("role",user.getRole());
		r.put("userId",user.getId());
		return r;
	}
	
	// ── S2-D1：已【下线】匿名管理员注册端点 /adminUser/register ──
	// 下线依据（S2-D 审计 §1）：@IgnoreAuth 匿名注册 admin，role 客户端可控 ⇒ 匿名提权入口。
	// 处理：删除方法（管理员由部署初始化/管理端 /save 创建，无开放注册管理员业务需求）。

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
	//         （批 2 已限管理员），与本端点无关；用户/老师端的 /resetPass 亦已于 S2-F-1-1 下线。
	// 回归：见 IdentityAuthorizationRegressionTest「D7」用例 —— 断言该路径返回 404。
	
	/**
     * 列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,AdminUserEntity user, HttpServletRequest request){
        // Step 5b-A：管理端账号列表仅管理员可访问（学生/老师一律拒绝）—— 与 /info/{id} 同策略
        // 取证依据：学生端 front/ 不调用 users/*；仅管理端 users/list.vue 调用
        OwnershipGuard.assertAdminOnly(request);
        QueryWrapper<AdminUserEntity> ew = new QueryWrapper<AdminUserEntity>();
    	PageUtils page = usersService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.allLike(ew, user), params), params));
        return R.ok().put("data", page);
    }

	/**
     * 列表
     */
    @RequestMapping("/list")
    public R list( AdminUserEntity user, HttpServletRequest request){
        // Step 5b-A：同上 —— 管理端账号列表仅管理员可访问
        OwnershipGuard.assertAdminOnly(request);
       	QueryWrapper<AdminUserEntity> ew = new QueryWrapper<AdminUserEntity>();
      	ew.allEq(MPUtil.allEQMapPre( user, "u"), false); 
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
        // S2-E-1：身份一致性守卫 —— 必须早于 getById（跨表 id 空间重合，仅按 userId 查表会串角色）
        OwnershipGuard.assertCurrentIdentity(request, OwnershipGuard.ROLE_ADMIN, OwnershipGuard.TABLE_ADMIN_USER);
    	Integer id = CurrentUserProvider.currentUserIdOrNull(request);
        AdminUserEntity user = usersService.getById(id);
        return R.ok().put("data", user);
    }

    /**
     * 保存
     */
    @PostMapping("/save")
    public R save(@RequestBody AdminUserEntity user, HttpServletRequest request){
        // S2-D1：管理端创建管理员账号 → 仅管理员；role 服务端固定「管理员」（防客户端注入异常 role）
        OwnershipGuard.assertAdminOnly(request);
    	if(usersService.getOne(new QueryWrapper<AdminUserEntity>().eq("username", user.getUsername())) !=null) {
    		return R.error("学生已存在");
    	}
        user.setRole("管理员");
        user.setPassword(PasswordHasher.hash("123456"));
        usersService.save(user);
        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody AdminUserEntity user, HttpServletRequest request){
        // S2-D1：编辑管理员账号 → 仅管理员；role 服务端恢复库中现值（防提权，前端不提交 role）；password 保留（正常改密）
        OwnershipGuard.assertAdminOnly(request);
        AdminUserEntity existing = user.getId() == null ? null : usersService.getById(user.getId());
        if (existing != null) {
            user.setRole(existing.getRole());
        }
        com.security.UpdateGuard.assertHasUpdatableFields(user); // T1：空更新防护（400，不进入 DAO）
        usersService.updateById(user);//全部更新
        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids, HttpServletRequest request){
        // S2-D1：删除管理员账号 → 仅管理员
        OwnershipGuard.assertAdminOnly(request);
        usersService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }
}
