package com.cloudmart.risk.service;

import com.cloudmart.risk.dto.RiskRecordDTO;
import com.cloudmart.risk.vo.RiskRecordVO;

import java.util.List;

public interface RiskRecordService {

    RiskRecordVO createRecord(RiskRecordDTO dto);

    List<RiskRecordVO> listRecords(Long userId, Integer page, Integer pageSize);

    RiskRecordVO getRecord(Long id);
}
