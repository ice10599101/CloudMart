package com.cloudmart.job.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XxlJobConfig {

    @Value("${xxl-job.admin.addresses:http://localhost:48080/xxl-job-admin}")
    private String adminAddresses;

    @Value("${xxl-job.accessToken:}")
    private String accessToken;

    @Value("${xxl-job.executor.appname:mall-job-executor}")
    private String appname;

    @Value("${xxl-job.executor.address:}")
    private String address;

    @Value("${xxl-job.executor.ip:}")
    private String ip;

    @Value("${xxl-job.executor.port:9999}")
    private int port;

    @Value("${xxl-job.executor.logpath:/data/applogs/xxl-job/jobhandler}")
    private String logPath;

    @Value("${xxl-job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
