package com.cloudmart.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件业务引用（FILE-01）：消费方登记对 file_asset 的引用，删除被引用文件返回 409。
 */
@Data
@TableName("file_reference")
public class FileReference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;

    /** 业务类型（POST/AVATAR/AFTER_SALE 等） */
    private String bizType;

    private String bizId;

    private LocalDateTime createdAt;
}
