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

    /** T10：本人参团查询——服务端从当前身份过滤（成员或团长），不暴露他人订单/地址 */
    IPage<GroupOrderDTO> listMyGroups(Long userId, String status, int page, int size);

    void handleGroupExpiration();
}
