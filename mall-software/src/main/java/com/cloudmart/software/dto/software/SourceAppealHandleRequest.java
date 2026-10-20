package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * SF08 补批：管理员处理作者来源申诉。ACCEPTED 仅把来源恢复为 UNKNOWN 待重检。
 */
@Getter
@Setter
public class SourceAppealHandleRequest {

    /** ACCEPTED/REJECTED */
    private String decision;

    /** 处理说明（1~500 字，审计留痕） */
    private String note;
}
