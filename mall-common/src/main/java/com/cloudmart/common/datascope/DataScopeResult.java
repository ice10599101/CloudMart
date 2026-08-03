package com.cloudmart.common.datascope;

import java.util.List;

public record DataScopeResult(DataScopeType type, List<Long> deptIds) {}
