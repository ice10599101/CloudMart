package com.cloudmart.wish.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 *
 * <p>拦截器链（顺序敏感）：</p>
 * <ol>
 *   <li>{@link OptimisticLockerInnerInterceptor}：启用 {@code @Version} 乐观锁，
 *       用于 {@code WishProgress} 进度更新的 CAS 防并发覆盖</li>
 *   <li>{@link PaginationInnerInterceptor}：分页插件，管理后台 offset 分页使用</li>
 * </ol>
 *
 * <p>注意：用户端列表使用 cursor 分页（LambdaQueryWrapper + last("id &lt; ?")），
 * 不依赖分页插件，避免深分页性能问题。</p>
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁：必须放在分页之前，确保 UPDATE 语句的 version 条件不被分页插件干扰
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页：管理后台 offset 分页使用
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
                // WishProgress.version 初始值设为 0
                this.strictInsertFill(metaObject, "version", () -> 0, Integer.class);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
            }
        };
    }
}
