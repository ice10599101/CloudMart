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
 * 宠物主表。多宠物（一用户多宠，{@code uk_pet_user_active} 保证至多一只主宠；
 * 日常养成/互动/任务全部作用于主宠）；养成值 0-100、成长属性上限 999。
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

    /** 性别：MALE/FEMALE（领养时选择；仅展示语义，不影响养成数值） */
    private String gender;

    /** 外观 JSON：{"color":"orange","accessory":"bell"}（服务端白名单校验） */
    private String appearance;

    /** 性格：LIVELY/GENTLE/TSUNDERE/SIMPLE/COOL/CHATTERBOX（决定 AI 说话风格） */
    private String personality;

    /** 当前职业编码（pet_career_config.code，NULL = 未入职；见三期宠物职业） */
    private String careerCode;

    /** 等级（1 起） */
    private Integer level;

    /** 当前等级内经验 */
    private Integer exp;

    /** 成长阶段：BABY/YOUNG/ADULT（升级自动推进） */
    private String growthStage;

    /** 进化阶段（0 未进化/1 一阶/2 二阶；由 pet_evolution_config 驱动，见 §89 宠物进化） */
    private Integer evolutionStage;

    /** 当前穿戴皮肤编码（pet_skin_config.code，NULL=原生外观） */
    private String skinCode;

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

    /** 原始自定义外观 JSON（穿戴皮肤前保存，卸皮肤恢复原值；NULL=从无自定义外观） */
    private String baseAppearance;

    /** 饱食变化小数余量（B04：独立累计，高频查询不丢小数） */
    private Double hungerFrac;

    /** 心情变化小数余量（B04） */
    private Double happinessFrac;

    /** 精力变化小数余量（B04） */
    private Double energyFrac;

    /** 清洁变化小数余量（B04） */
    private Double cleanlinessFrac;

    /** 力量 */
    private Integer strength;

    /** 智力 */
    private Integer intelligence;

    /** 敏捷 */
    private Integer agility;

    /** 魅力 */
    private Integer charm;

    /** 与主人的亲密度（只增不减；等级阈值见 PetProperties.Intimacy） */
    private Integer intimacy;

    /** 累计陪伴时长（秒，心跳累加，日上限见配置） */
    private Long companionSeconds;

    /** 累计陪伴天数（去重日期数） */
    private Integer companionDays;

    /** 连续陪伴天数（断签重置） */
    private Integer companionStreak;

    /** 最近一次陪伴日期（UTC，连续天数判定） */
    private java.time.LocalDate lastCompanionDate;

    /** 今日陪伴秒数（跨天惰性重置） */
    private Integer todayCompanionSeconds;

    /** 状态快照（展示冗余；权威状态由 pet_activity 合成） */
    private String status;

    /** 是否在个人主页公开（默认公开，用户可关） */
    private Boolean isPublic;

    /** 是否当前主宠（每用户至多一只；多宠物切换见 §89 多种宠物） */
    private Boolean isActive;

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
