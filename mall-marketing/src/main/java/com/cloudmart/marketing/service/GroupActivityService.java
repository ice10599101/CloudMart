package com.cloudmart.marketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.marketing.dto.*;

public interface GroupActivityService {

    GroupActivityDTO createActivity(CreateGroupActivityRequest request);

    GroupActivityDTO enableActivity(Long id);

    GroupActivityDTO disableActivity(Long id);

    GroupActivityDTO getActivity(Long id);

    IPage<GroupActivityDTO> listActivities(String status, int page, int size);

    GroupOrderDTO joinGroup(Long userId, JoinGroupRequest request);

    GroupOrderDTO getGroupOrder(Long groupOrderId);

    IPage<GroupOrderDTO> listGroupOrders(Long activityId, String status, int page, int size);

    void handleGroupExpiration();
}
