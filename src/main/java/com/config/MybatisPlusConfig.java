package com.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * MyBatis-Plus 配置
 *
 * <p>迁移说明（Phase 2 / Step 2）：MP 2.x 的 {@code PaginationInterceptor} 在 3.x 中
 * 已被 {@link MybatisPlusInterceptor} + 内部拦截器 {@link PaginationInnerInterceptor} 取代。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件（MP 3.x 写法）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

}
