package com.cloudmart.risk.service;

import com.cloudmart.risk.dto.RiskCheckRequest;
import com.cloudmart.risk.vo.RiskCheckVO;

public interface RiskCheckService {

    RiskCheckVO check(RiskCheckRequest request);
}
