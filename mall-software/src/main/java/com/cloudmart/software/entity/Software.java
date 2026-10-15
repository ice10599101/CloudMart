package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.SoftwareLifecycle;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 软件主体（方案 6.2）：归属与运营状态；内容在快照，
 * 双指针 published/working 指向 software_revision。
 */
@Getter
@Setter
@TableName("software")
public class Software {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分享者（mall-user 前台会员 ID） */
    private Long ownerId;

    private SoftwareLifecycle lifecycle;

    /** 当前公开快照（须属于本软件，应用层校验） */
    private Long publishedRevisionId;

    /** 唯一工作快照（须属于本软件，应用层校验） */
    private Long workingRevisionId;

    /** 成功上线序号 */
    private Integer publicationSeq;

    /** 主体并发更新乐观锁 */
    private Integer lockVersion;

    /** 首页精选排序，0=不精选 */
    private Integer featuredRank;

    private Integer commentCount;

    private Integer favoriteCount;

    /** P1 有效评分人数 */
    private Integer ratingCount;

    /** P1 有效评分星数和 */
    private Integer ratingSum;

    /** 去重获取链接累计数（非外部下载量） */
    private Long linkAccessCount;

    private LocalDateTime publishedAt;

    private LocalDateTime lastReleaseAt;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
