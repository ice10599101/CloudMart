package com.cloudmart.wish.entity;

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
 * 位置伪造可疑记录（Sprint 3.3：异常跳跃判定，仅存 geohash 无原始坐标）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_lbs_suspicious")
public class LbsSuspicious {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 推断速度（km/h） */
    private Integer speedKmh;

    /** 起点 geohash7（无原始坐标） */
    private String fromCell;

    /** 终点 geohash7（无原始坐标） */
    private String toCell;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
