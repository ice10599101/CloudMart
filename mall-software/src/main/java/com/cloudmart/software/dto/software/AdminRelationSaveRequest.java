package com.cloudmart.software.dto.software;

import lombok.Data;

import java.util.List;

/** UX03：同类关系全量替换请求（Items 详情管理；整体维护语义同专题清单） */
@Data
public class AdminRelationSaveRequest {

    private List<Item> items;

    @Data
    public static class Item {
        /** 关联软件 ID（不可为自身） */
        private Long relatedId;
        /** SIMILAR_USE / ALTERNATIVE_PLATFORM */
        private String relationType;
        /** 编辑解释（≤200 字，说明"同类用途/同平台替代"，不宣称优劣） */
        private String reason;
        private Integer displayOrder;
    }
}
