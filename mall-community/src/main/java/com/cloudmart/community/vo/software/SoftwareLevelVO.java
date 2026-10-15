package com.cloudmart.community.vo.software;

import java.time.Instant;

/**
 * 用户社区等级查询结果（方案 9.6）：本次请求实时读取，checkedAt 仅供调用方
 * 记录核对时点，不得当长期授权票据使用。
 */
public record SoftwareLevelVO(
        Long userId,
        Integer level,
        String levelTitle,
        Instant checkedAt) {
}
