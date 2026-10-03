package com.cloudmart.pet.config;

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
 * MyBatis-Plus 配置（与 mall-wish 同构）。
 *
 * <p>拦截器链（顺序敏感）：</p>
 * <ol>
 *   <li>{@link OptimisticLockerInnerInterceptor}：启用 {@code @Version} 乐观锁，
 *       用于宠物状态懒更新的 CAS 防并发覆盖</li>
 *   <li>{@link PaginationInnerInterceptor}：分页插件，管理后台/对战历史 offset 分页使用</li>
 * </ol>
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁：必须放在分页之前，确保 UPDATE 语句的 version 条件不被分页插件干扰
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * R20：createdAt/updatedAt 自动填充统一经注入的 {@link PetClock}（UTC）——
     * 原实现用主机 {@code LocalDateTime.now()}，宿主时区非 UTC 时所有自动填充时间整体漂移，
     * 与接口层"按 UTC 加 Z"的口径冲突（业务日归属/财务顺序被污染）。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler(PetClock petClock) {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", petClock::nowUtc, LocalDateTime.class);
                this.strictInsertFill(metaObject, "updatedAt", petClock::nowUtc, LocalDateTime.class);
                this.strictInsertFill(metaObject, "version", () -> 0, Integer.class);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", petClock::nowUtc, LocalDateTime.class);
            }
        };
    }
}
