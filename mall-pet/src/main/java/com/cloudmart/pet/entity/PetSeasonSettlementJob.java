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
 * 赛季结算作业（R06）：checkpoint + 租约——进程 kill 后由租约到期接管，
 * 按快照 rank 游标推进，绝不重新冻榜、绝不提前 SETTLED。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_season_settlement_job")
public class PetSeasonSettlementJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 赛季 ID（唯一——一个赛季一个作业） */
    private Long seasonId;

    /** RUNNING / COMPLETED */
    private String status;

    /** 已处理到的名次游标（按 rank_no 升序，含 0=未开始） */
    private Integer cursorRank;

    /** 快照总人数 */
    private Integer totalCount;

    /** 累计发奖成功数 */
    private Integer successCount;

    /** 累计发奖失败数 */
    private Integer failureCount;

    /** 当前执行者租约标识 */
    private String leaseOwner;

    /** 租约版本（CAS 推进，防旧执行者覆盖新执行者） */
    private Long leaseVersion;

    /** 租约到期时间（UTC） */
    private LocalDateTime leaseUntil;

    /** 最近一次错误摘要 */
    private String lastError;

    /** 下次重试时间（UTC） */
    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
