package com.cloudmart.software.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置：分页插件（软件广场/版本/评论 offset 分页）+ 审计时间填充。
 *
 * <p>交互类表（评论/评分/举报/授权等）仍使用显式 lock_version 条件更新（方案 6.13），不使用 @Version；
 * 软件发布链路已取消版本号，改为服务端行锁 + 状态判断（SW01）。
 * 注解乐观锁——条件更新语句在 Service 层可读性更强，且与资格行/软件主行/
 * 快照行的锁协议一致。</p>
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 分页单页上限兜底（size 超限请求直达 LIMIT 的攻击面）
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
            }
        };
    }

    /** 短事务模板（9.7：submit 远程阶段在事务外，锁定复核阶段经模板进入事务） */
    @Bean
    public org.springframework.transaction.support.TransactionTemplate transactionTemplate(
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }
}
