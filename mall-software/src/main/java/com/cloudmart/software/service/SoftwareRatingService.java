package com.cloudmart.software.service;

import com.cloudmart.software.vo.software.SoftwareRatingVO;

/**
 * 软件评分（方案 4.5，P1）：登录用户 1~5 星；每用户每软件一条，可修改/撤销；
 * 分享者不能给自己评分；评分不要求"下载完成"凭证；计数与评分行同事务维护。
 */
public interface SoftwareRatingService {

    /** 新建/改分（幂等 upsert；expectedVersion 非空时 CAS 既有评分行） */
    SoftwareRatingVO rate(Long userId, Long softwareId, Integer score, Integer expectedVersion);

    /** 撤销评分（幂等：无记录也成功；expectedVersion 非空时 CAS） */
    SoftwareRatingVO revoke(Long userId, Long softwareId, Integer expectedVersion);

    /** 我的评分（未评分 null；详情个性化用） */
    Integer myScore(Long userId, Long softwareId);
}
