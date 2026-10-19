package com.cloudmart.software.dto.software;

import lombok.Data;

import java.util.List;

/** UX01：区块排序提交（完整集合；校验重复/缺失后原子重排） */
@Data
public class AdminHomeSectionReorderRequest {

    /** 全部区块 ID 的目标顺序（必须与现有启用集合完整一致） */
    private List<Long> sectionIds;
}
