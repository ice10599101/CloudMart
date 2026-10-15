package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/** 授权撤销请求 */
@Getter
@Setter
public class AdminGrantRevokeRequest {

    private String reason;
}
