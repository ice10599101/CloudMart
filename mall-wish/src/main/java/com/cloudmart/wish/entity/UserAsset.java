package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** ㉟ 用户虚拟资产（Sprint 3.6）：uk(user,asset) 防重复拥有；is_active_skin/bgm 同类型互斥。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_user_asset")
public class UserAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long assetId;

    /** 获取来源：EXCHANGE 星光兑换 / COLLECT 星火收藏 */
    private String source;

    /** OWNED / REFUNDED */
    private String status;

    private Boolean isActiveSkin;

    private Boolean isActiveBgm;

    /** 关联心愿（星火收藏品） */
    private Long refWishId;

    private LocalDateTime acquiredAt;
}
