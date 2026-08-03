package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminConfigRequest;
import com.cloudmart.admin.dto.AdminConfigResponse;

import java.util.List;

public interface AdminConfigService {

    List<AdminConfigResponse> list();

    AdminConfigResponse getById(Long id);

    AdminConfigResponse getByKey(String configKey);

    void create(AdminConfigRequest request);

    void update(Long id, AdminConfigRequest request);

    void delete(Long id);

    void refreshCache();
}
