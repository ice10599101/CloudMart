package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 心愿进度实体（1:1 with wish）。
 *
 * <p>业务事实表，不采用 Redis Write-Behind。用户主动修改进度必须同步写 MySQL，
 * 并使用 {@link Version} 乐观锁/CAS 防止并发覆盖（离线打卡/BLE 数据重放场景）。
 * 需配合 {@code OptimisticLockerInnerInterceptor}（见 MyBatisPlusConfig）生效。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_progress")
public class WishProgress {

    @TableId(type = IdType.INPUT)
    private Long wishId;

    private Integer currentValue;

    private Integer targetValue;

    private Integer currentStreak;

    private Integer maxStreak;

    /**
     * 乐观锁版本号。MyBatis-Plus 更新时自动追加 {@code version = version + 1} 条件。
     * 失配时 {@code affectedRows = 0}，业务层抛出 {@code WISH_VERSION_CONFLICT}。
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
