package com.cloudmart.order.dto;

import java.math.BigDecimal;

/** R12：购物篮满减匹配请求（mall-order → mall-marketing 内部契约镜像）。 */
public record TieredMatchRequest(BigDecimal orderAmount) {
}
