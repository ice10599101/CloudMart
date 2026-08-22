package com.cloudmart.job.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 负载均衡 RestClient 配置。
 *
 * <p>BusinessJobHandler 通过服务名调用各微服务（如 http://mall-wish/internal/jobs/...），
 * 服务名并非真实 DNS 主机名，必须由 Spring Cloud LoadBalancer 借助 Nacos 服务发现
 * 解析为实际 host:port。裸 RestClient.builder() 直接走 DNS 解析，
 * 会抛 java.net.UnknownHostException（不知道这样的主机）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    /**
     * 带 @LoadBalanced 的 Builder 会被 LoadBalancerRestClientBuilderBeanPostProcessor
     * 注入 DeferringLoadBalancerInterceptor，从而支持 lb:// 风格的服务名 URI。
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
