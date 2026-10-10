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
 * 赛季通行证进度（§6）：一赛季一用户一行；claimed_tiers 服务端 CAS 追加。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_season_pass")
public class PetSeasonPass {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seasonId;

    private Long userId;

    private Integer passExp;

    /** JSON 数组字符串（如 [1,2]） */
    private String claimedTiers;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
