package com.cloudmart.seckill.service;

import com.cloudmart.seckill.dto.CreateActivityRequest;
import com.cloudmart.seckill.dto.SeckillActivityDTO;

import java.util.List;

public interface SeckillActivityService {

    SeckillActivityDTO createActivity(CreateActivityRequest request);

    SeckillActivityDTO getActivity(Long activityId);

    List<SeckillActivityDTO> listActivities(String status);

    SeckillActivityDTO updateActivityStatus(Long activityId, String status);

    void refreshActivityStatuses();
}
