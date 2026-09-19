package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 宠物房间（家园）。每宠物一间，首次进入家园时懒创建（{@code uk_pet_room} 兜底并发）。
 *
 * <p>{@code comfort} 是已摆放家具舒适度之和，达到阈值后休息/进入房间有额外收益；
 * {@code visitCount/likeCount} 由来访与点赞累加，服务端维护，前端只读。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_room")
public class PetRoom {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 宠物 ID */
    private Long petId;

    /** 用户 ID（访问查询冗余） */
    private Long userId;

    /** 墙纸编码（pet_furniture_config.code，NULL = 默认） */
    private String wallCode;

    /** 地板编码（pet_furniture_config.code，NULL = 默认） */
    private String floorCode;

    /** 欢迎语（来访者可见） */
    private String welcomeMessage;

    /** 是否允许来访（1 公开/0 仅自己） */
    private Boolean isPublic;

    /** 当前舒适度（已摆放家具之和） */
    private Integer comfort;

    /** 累计来访次数 */
    private Integer visitCount;

    /** 累计点赞数 */
    private Integer likeCount;

    /** 乐观锁版本号（摆放/主题并发写保护） */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
