package com.cloudmart.software.dto.software;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** UX04：教程元数据保存（articleId 唯一；关联软件≤3 且至少关联一个软件或专题） */
@Data
public class AdminGuideSaveRequest {

    /** INSTALL_GUIDE/WORKFLOW/COMPARISON/TROUBLESHOOTING */
    private String guideType;

    private String title;

    private String summary;

    /** 社区帖子 ID（正文与审核真源；一篇帖子只挂一个教程） */
    private Long articleId;

    private Long coverAssetId;

    /** 关联软件 ID（≤3） */
    private List<Long> relatedSoftwareIds;

    private Long relatedTopicId;

    /** 适用平台（WINDOWS/MACOS/LINUX/WEB） */
    private List<String> platformScope;

    private String appliesToVersion;

    private LocalDate checkedAt;

    private String checkNote;

    private Boolean enabled;

    private Integer displayOrder;
}
