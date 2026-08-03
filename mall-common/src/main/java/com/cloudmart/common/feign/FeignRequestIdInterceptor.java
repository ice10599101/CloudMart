package com.cloudmart.common.feign;

import com.cloudmart.common.constant.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

public class FeignRequestIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            template.header(SecurityConstants.X_REQUEST_ID_HEADER, requestId);
        }
    }
}
