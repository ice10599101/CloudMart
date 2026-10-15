package com.cloudmart.software.dto.software;

import java.time.Instant;

/**
 * 用户社区等级（方案 9.6）：checkedAt 仅作本次请求内的核对时点，
 * 不得当长期授权票据——resolve 每次实时重查。
 */
public record SoftwareLevelVO(
        Long userId,
        Integer level,
        String levelTitle,
        Instant checkedAt) {
}
