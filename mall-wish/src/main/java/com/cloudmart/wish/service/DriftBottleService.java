package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.vo.DriftBottleVO;

import java.util.List;

/**
 * 漂流瓶服务：投瓶 / 捞瓶 / 我的漂流瓶 / 匿名回应。
 *
 * <p>漂流瓶匿名随机漂流（非 LBS）：投瓶人投出后进入全局海面池，捞起者随机捞取，
 * 全程不暴露投瓶人身份；关联心愿的漂流瓶支持匿名祝福/点亮回应。</p>
 */
public interface DriftBottleService {

    /** 投瓶（content 与 wishId 二选一），返回新漂流瓶（role=THROWN） */
    DriftBottleVO throwBottle(Long userId, ThrowBottleRequest request);

    /** 捞瓶：随机捞取一个非自己的漂流瓶；海里无瓶返回 null */
    DriftBottleVO fishBottle(Long userId);

    /** 我的漂流瓶（我投出的 + 我捞到的，倒序） */
    List<DriftBottleVO> listMine(Long userId);

    /** 匿名回应（仅捞起人可回应关联心愿漂流瓶）：BLESS 免费 / LIGHT 扣星光 2 点亮 */
    DriftBottleVO interact(Long userId, Long bottleId, String type);
}