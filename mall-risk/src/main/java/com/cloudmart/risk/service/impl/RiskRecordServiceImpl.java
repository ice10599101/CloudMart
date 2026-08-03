package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.risk.converter.RiskConverter;
import com.cloudmart.risk.dto.RiskRecordDTO;
import com.cloudmart.risk.entity.RiskRecord;
import com.cloudmart.risk.repository.RiskRecordMapper;
import com.cloudmart.risk.service.RiskRecordService;
import com.cloudmart.risk.vo.RiskRecordVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskRecordServiceImpl implements RiskRecordService {

    private final RiskRecordMapper riskRecordMapper;
    private final RiskConverter riskConverter;

    public RiskRecordServiceImpl(RiskRecordMapper riskRecordMapper, RiskConverter riskConverter) {
        this.riskRecordMapper = riskRecordMapper;
        this.riskConverter = riskConverter;
    }

    @Override
    public RiskRecordVO createRecord(RiskRecordDTO dto) {
        RiskRecord entity = riskConverter.toEntity(dto);
        riskRecordMapper.insert(entity);
        return riskConverter.toRiskRecordVO(entity);
    }

    @Override
    public List<RiskRecordVO> listRecords(Long userId, Integer page, Integer pageSize) {
        Page<RiskRecord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<RiskRecord> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(RiskRecord::getUserId, userId);
        }
        wrapper.orderByDesc(RiskRecord::getId);

        Page<RiskRecord> result = riskRecordMapper.selectPage(pageParam, wrapper);
        return riskConverter.toRiskRecordVOList(result.getRecords());
    }

    @Override
    public RiskRecordVO getRecord(Long id) {
        RiskRecord entity = riskRecordMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("RISK_RECORD_NOT_FOUND", "风控记录不存在");
        }
        return riskConverter.toRiskRecordVO(entity);
    }
}
