package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.vo.CheckInResultVO;
import com.cloudmart.community.vo.ExpLogVO;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserDecorationVO;
import com.cloudmart.community.vo.UserLevelVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface GrowthService {

    CheckInResultVO checkIn(Long userId);

    boolean isCheckedInToday(Long userId);

    UserLevelVO getUserLevel(Long userId);

    /**
     * 设置用户头像框（Lv2+ 权益）。frame 须为合法 key，否则抛出业务异常。
     */
    void setAvatarFrame(Long userId, String frame);

    /**
     * 批量查询用户头像装饰信息（无记录的用户按 Lv1 默认）。
     */
    Map<Long, UserDecorationVO> getUserDecorations(List<Long> userIds);

    void addExp(Long userId, int exp, String source, Long bizId, String description);

    /**
     * R21 经验发放（带真实到账结果）：日配额按 (用户, 业务日) 行锁原子占用，
     * credited=min(requested, 当日剩余额度)；(source,bizId) 事实幂等；零收益也留事实。
     * 排行榜投影仅在事务提交后按 credited 累加（回滚不留残影）。
     */
    ExpGrantResult grantExp(Long userId, int exp, String source, Long bizId, String description);

    Page<ExpLogVO> getExpLogs(Long userId, int page, int size);

    List<LevelConfigVO> getLevelConfigs();

    List<LocalDate> getCheckInCalendar(Long userId, int year, int month);

    int getContinuousDays(Long userId);
}
