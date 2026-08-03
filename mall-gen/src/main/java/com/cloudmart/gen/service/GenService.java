package com.cloudmart.gen.service;

import com.cloudmart.gen.dto.GenConfigRequest;
import com.cloudmart.gen.dto.GenPreviewResponse;
import com.cloudmart.gen.dto.GenTableColumnResponse;
import com.cloudmart.gen.dto.GenTableResponse;

import java.util.List;

public interface GenService {
    List<GenTableResponse> listTables();
    GenTableResponse getTable(String tableName);
    List<GenTableColumnResponse> getTableColumns(String tableName);
    List<GenPreviewResponse> preview(GenConfigRequest config);
    byte[] generateCode(GenConfigRequest config);
}
