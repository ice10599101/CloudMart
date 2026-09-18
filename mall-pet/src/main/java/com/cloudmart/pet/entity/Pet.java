package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 宠物主表。一期一用户一宠（uk_pet_user）；养成值 0-100、成长属性上限 999。
 *
 * <p>状态权威在服务端：{@code lastStateUpdateAt} 为懒更新游标，任何读/写入口
 * 先按 elapsed 推算自然衰减再落库；{@code version} 乐观锁防并发覆盖
 * （多人多端同时操作同一只宠物）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet")
public class Pet {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 主人用户 ID（网关 X-User-Id） */
    private Long userId;

    /** 宠物名（1-12 字符，30 天可改一次） */
    private String name;

    /** 种类：CAT/DOG/RABBIT/FOX/PANDA */
    private String species;

    /** 外观 JSON：{"color":"orange","accessory":"bell"}（服务端白名单校验） */
    private String appearance;

    /** 性格：LIVELY/GENTLE/TSUNDERE/SIMPLE/COOL/CHATTERBOX（决定 AI 说话风格） */
    private String personality;

    /** 等级（1 起） */
    private Integer level;

    /** 当前等级内经验 */
    private Integer exp;

    /** 成长阶段：BABY/YOUNG/ADULT（升级自动推进） */
    private String growthStage;

    /** 生命值 */
    private Integer hp;

    /** 生命上限（升级 +5） */
    private Integer maxHp;

    /** 饥饿度 0-100（越高越饱） */
    private Integer hunger;

    /** 心情 0-100 */
    private Integer happiness;

    /** 精力 0-100 */
    private Integer energy;

    /** 清洁度 0-100 */
    private Integer cleanliness;

    /** 力量 */
    private Integer strength;

    /** 智力 */
    private Integer intelligence;

    /** 敏捷 */
    private Integer agility;

    /** 魅力 */
    private Integer charm;

    /** 状态快照（展示冗余；权威状态由 pet_activity 合成） */
    private String status;

    /** 是否在个人主页公开（默认公开，用户可关） */
    private Boolean isPublic;

    /** 懒更新游标：上次状态自然变化结算时间（UTC） */
    private LocalDateTime lastStateUpdateAt;

    /** 最近一次改名时间（30 天冷却） */
    private LocalDateTime lastRenamedAt;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 软删除（放生/注销清理；核心数据物理删除禁止） */
    @TableLogic
    private LocalDateTime deletedAt;
}
