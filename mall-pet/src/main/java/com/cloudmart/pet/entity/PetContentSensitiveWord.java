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
 * 内容安全敏感词（P0-1）：内存 Aho-Corasick 自动机的词库源，支持管理端热更新。
 *
 * <p>服务端按"指纹"（行数 + 最大 id + 最大 updated_at）轮询比对，
 * 词库变更后 ≤1 分钟内在所有实例生效；updated_at 由数据库 ON UPDATE 维护，
 * 业务代码禁止写入。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_content_sensitive_word")
public class PetContentSensitiveWord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 敏感词（匹配时忽略大小写） */
    private String word;

    /** POLITICS / ABUSE / AD / CRISIS */
    private String category;

    /** 1 启用 / 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 数据库 ON UPDATE 维护（热更新指纹依据），业务代码禁止写入 */
    private LocalDateTime updatedAt;
}
