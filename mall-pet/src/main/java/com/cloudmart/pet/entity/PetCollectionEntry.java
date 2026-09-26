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


@Getter
@Setter
@NoArgsConstructor
@TableName("pet_collection_entry")
public class PetCollectionEntry {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 收藏图鉴配置（N07）：类别/编码/资源/稀有度/解锁条件；隐藏彩蛋不泄漏正文。 */
    private String category;
    private String itemCode;
    private String resourceKey;
    private String rarity;
    private String unlockCondition;
    private Boolean hidden;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
