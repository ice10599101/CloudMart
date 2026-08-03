package com.cloudmart.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.CreateActivityRequest;
import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.service.SeckillActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillActivityServiceImpl implements SeckillActivityService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillConverter seckillConverter;

    public SeckillActivityServiceImpl(SeckillActivityMapper activityMapper, SeckillConverter seckillConverter) {
        this.activityMapper = activityMapper;
        this.seckillConverter = seckillConverter;
    }

    @Override
    @Transactional
    public SeckillActivityDTO createActivity(CreateActivityRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException("INVALID_TIME_RANGE", "结束时间必须晚于开始时间");
        }
        SeckillActivity entity = seckillConverter.toEntity(request);
        entity.setStatus("UPCOMING");
        activityMapper.insert(entity);
        return seckillConverter.toActivityDTO(entity);
    }

    @Override
    public SeckillActivityDTO getActivity(Long activityId) {
        SeckillActivity entity = activityMapper.selectById(activityId);
        if (entity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        return seckillConverter.toActivityDTO(entity);
    }

    @Override
    public List<SeckillActivityDTO> listActivities(String status) {
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(SeckillActivity::getStatus, status);
        }
        wrapper.orderByDesc(SeckillActivity::getStartTime);
        List<SeckillActivity> activities = activityMapper.selectList(wrapper);
        return seckillConverter.toActivityDTOList(activities);
    }

    @Override
    @Transactional
    public SeckillActivityDTO updateActivityStatus(Long activityId, String status) {
        SeckillActivity entity = activityMapper.selectById(activityId);
        if (entity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        entity.setStatus(status);
        activityMapper.updateById(entity);
        return seckillConverter.toActivityDTO(entity);
    }

    @Override
    @Transactional
    public void refreshActivityStatuses() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> upcoming = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, "UPCOMING")
                        .le(SeckillActivity::getStartTime, now)
        );
        for (SeckillActivity activity : upcoming) {
            activity.setStatus("ONGOING");
            activityMapper.updateById(activity);
        }

        List<SeckillActivity> ongoing = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, "ONGOING")
                        .le(SeckillActivity::getEndTime, now)
        );
        for (SeckillActivity activity : ongoing) {
            activity.setStatus("ENDED");
            activityMapper.updateById(activity);
        }
    }
}
