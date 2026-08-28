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
 * 灰度比例配置（Sprint 2.8，文档 2.8 管理后台：灰度控制台/回滚操作）。
 *
 * <p>回滚 = gray_ratio 置 0（10 分钟内可回滚的验收语义由管理端一键操作
 * + 配置实时生效承载）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_grayscale_config")
public class GrayscaleConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 功能键（代码枚举白名单） */
    private String featureKey;

    /** 灰度比例 0-100（回滚=置 0） */
    private Integer grayRatio;

    /** 功能说明 */
    private String description;

    /** 最后修改人（管理后台用户 ID） */
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
