package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/diagnostic")
public class FeignDiagnosticController {

    private final DiscoveryClient discoveryClient;
    private final ProductFeignClient productFeignClient;

    public FeignDiagnosticController(DiscoveryClient discoveryClient, ProductFeignClient productFeignClient) {
        this.discoveryClient = discoveryClient;
        this.productFeignClient = productFeignClient;
    }

    @GetMapping("/feign-test")
    public Map<String, Object> feignTest() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("step1_discoveryClient_type", discoveryClient.getClass().getName());

        List<String> services = discoveryClient.getServices();
        result.put("step2_registered_services", services);

        Map<String, Object> instanceMap = new LinkedHashMap<>();
        for (String service : services) {
            List<ServiceInstance> instances = discoveryClient.getInstances(service);
            instanceMap.put(service, instances.stream()
                    .map(si -> si.getHost() + ":" + si.getPort() + " [" + si.getUri() + "]")
                    .toList());
        }
        result.put("step3_service_instances", instanceMap);

        try {
            ApiResponse<?> response = productFeignClient.getProductCount();
            result.put("step4_feign_call_result", "SUCCESS");
            result.put("step4_feign_call_data", response);
        } catch (Exception e) {
            result.put("step4_feign_call_result", "FAILED");
            result.put("step4_feign_call_error_class", e.getClass().getName());
            result.put("step4_feign_call_error_message", e.getMessage());
            if (e.getCause() != null) {
                result.put("step4_feign_call_cause_class", e.getCause().getClass().getName());
                result.put("step4_feign_call_cause_message", e.getCause().getMessage());
            }
        }

        return result;
    }
}
