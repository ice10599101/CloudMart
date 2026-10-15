package com.cloudmart.software;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 小答软件微服务启动类。
 *
 * <p>独立 mall-software 微服务，负责站内软件分享与交流领域的全部业务事实：
 * 软件目录、内容快照、版本与外部下载来源、发布资格、付款查看授权、
 * 评论互动、失效反馈、软件举报与运营审核。</p>
 *
 * <p>数据归属：独立 mall_software 数据库（独立 Flyway 历史，禁止写入/引用 mall_community）；
 * 账号、社区等级、拉黑、素材与通知复用现有微服务的受保护内部 API。</p>
 *
 * <p>Mapper 扫描除本服务 repository 外，显式纳入 mall-common 通用异步组件 Mapper
 * （outbox_event / inbox_record / compensation_task 建在本服务库内，
 * 由 {@code cloudmart.async.enabled=true} 触发公共 AsyncAutoConfiguration 装配）。</p>
 */
@SpringBootApplication
@MapperScan({"com.cloudmart.software.repository", "com.cloudmart.common.async.mapper"})
@EnableFeignClients(basePackages = "com.cloudmart.software.feign")
@EnableScheduling
@Import(GlobalExceptionHandler.class)
public class SoftwareApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoftwareApplication.class, args);
    }
}
