package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日获取链接去重事实（方案 6.9）：同用户同来源同上海自然日最多一条；
 * 复合主键去重；不保存完整链接、IP 或密码；首次 INSERT 成功才增累计计数。
 */
@Getter
@Setter
@TableName("software_link_access_daily")
public class SoftwareLinkAccessDaily {

    private Long softwareId;

    private String sourceKey;

    private Long userId;

    /** 获取日期（Asia/Shanghai 切日） */
    private LocalDate accessDate;

    private LocalDateTime createdAt;
}
