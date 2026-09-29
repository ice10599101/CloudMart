package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 食物道具配置（F1 配置化）：商城上架 + 喂养效果（价格/效果服务端权威，管理端可编辑）。
 * 服务端 60 秒 TTL 缓存，管理端保存后本实例即时生效。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_food_config")
public class PetFoodConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 食物编码（唯一，喂食接口入参） */
    private String code;

    private String name;

    private String icon;

    private String description;

    private Integer priceStarlight;

    /** 饱食恢复（0~100） */
    private Integer hunger;

    /** 心情恢复（0~100） */
    private Integer happiness;

    /** 生命恢复（受 max_hp 封顶） */
    private Integer hp;

    /** 1 上架 / 0 下架 */
    private Integer enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 数据库 ON UPDATE 维护，业务代码禁止写入 */
    private LocalDateTime updatedAt;
}
