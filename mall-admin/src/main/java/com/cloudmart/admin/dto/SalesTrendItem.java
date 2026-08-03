package com.cloudmart.admin.dto;

import java.math.BigDecimal;

public record SalesTrendItem(
    String date,
    BigDecimal sales,
    long orders
) {}
