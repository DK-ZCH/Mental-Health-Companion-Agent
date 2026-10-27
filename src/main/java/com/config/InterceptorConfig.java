package com.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import com.interceptor.AuthorizationInterceptor;

@Configuration
public class InterceptorConfig extends WebMvcConfigurationSupport{
	
	@Bean
    public AuthorizationInterceptor getAuthorizationInterceptor() {
        return new AuthorizationInterceptor();
    }
	
	@Override
    public void addInterceptors(InterceptorRegistry registry) {
		// 注意：必须排除 /error（Spring Boot 内部错误分派路径）。
		// 否则异常解析器写好正确的 HTTP 状态（400/404/500）后转发到 /error，
		// 鉴权拦截器会在 ERROR 分派上再次生效，把响应体改写成 {"code":401,"msg":"请先登录"}，
		// 导致「服务器异常」被误报为「未登录」。详见 docs/PHASE2-T4-EXCEPTION-AUDIT.md §4.1（P0）。
        registry.addInterceptor(getAuthorizationInterceptor()).addPathPatterns("/**").excludePathPatterns("/static/**", "/error");
        super.addInterceptors(registry);
	}
	
	/**
	 * springboot 2.0配置WebMvcConfigurationSupport之后，会导致默认配置被覆盖，要访问静态资源需要重写addResourceHandlers方法
	 */
	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// 🔒 已移除 classpath:/admin/（2026-10-27 收尾批次）：
		//    该 location 会把 resources/admin/admin/ 整棵旧 Vue2 源码工程（30,476 文件，
		//    含 node_modules/、package.json、*.bat、src/**）匿名暴露为 /admin/**。
		//    取证：/admin/package.json、/admin/node_modules/vue/package.json 等均返回 200。
		//    同时该 dist 构建于 2022-03-30、调用的是改名前的旧路径（/yonghu/*、/exampaper/*）
		//    ⇒ 管理端实际不可用（POST /yonghu/login = 404），保留它只剩披露价值。
		//    ⚠️ 若将来重建管理端（Step 10 / Legacy Admin Build Recovery），
		//    应挂载【新的构建产物目录】，不要恢复本行 —— 否则源码会再次整体可读。
		registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/resources/")
        .addResourceLocations("classpath:/static/")
        .addResourceLocations("classpath:/img/")
        .addResourceLocations("classpath:/front/")
        .addResourceLocations("classpath:/public/");
		super.addResourceHandlers(registry);
    }
}
