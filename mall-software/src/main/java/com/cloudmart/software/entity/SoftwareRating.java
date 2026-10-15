package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 软件评分（方案 4.5，P1）：每用户每软件一条，1~5 星可修改/撤销；分享者不能给自己评分 */
@Getter
@Setter
@TableName("software_rating")
public class SoftwareRating {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long userId;

    /** 1~5 星 */
    private Integer score;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
