package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.SoftwareChannel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 版本（方案 6.4）：快照内完整展示版本列表；复制快照保留 release_key；
 * 排序由服务端 release_order 决定，禁止按版本字符串字典序判断新旧。
 */
@Getter
@Setter
@TableName("software_release")
public class SoftwareRelease {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long revisionId;

    /** 跨快照稳定版本键（服务端 UUID，客户端不可伪造） */
    private String releaseKey;

    /** 版本号（1~40 字，不强制 SemVer） */
    private String versionName;

    private SoftwareChannel channel;

    /** 服务端排序值 */
    private Integer releaseOrder;

    /** 发布日期（发布者声明） */
    private LocalDate releaseDate;

    /** 更新日志（≤10000 字符） */
    private String changelog;

    /** 发布者推荐版本（每渠道最多一个） */
    private Boolean recommended;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
