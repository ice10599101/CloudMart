package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/** 评分请求（P1 4.5）：score 1~5；expectedVersion 可选（修改/撤销时 CAS） */
@Getter
@Setter
public class SoftwareRatingRequest {

    /** 1~5 星 */
    private Integer score;

    /** 既有评分行的 lockVersion（可选；不匹配返回 SOFTWARE_VERSION_CONFLICT） */
    private Integer expectedVersion;
}
