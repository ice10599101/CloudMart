package com.cloudmart.admin.feign;

import com.cloudmart.admin.config.FileServiceTokenConfig;
import com.cloudmart.admin.dto.AdminFileAssetPage;
import com.cloudmart.admin.dto.AdminFileAssetRow;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-file 管理端内部 Feign（ADMIN-04）。
 *
 * <p>下游 /internal/assets/admin/** 由服务令牌保护（iss=mall-admin、scope=file:internal），
 * 仅读写 owner_type=ADMIN 的管理员域资产。</p>
 */
@FeignClient(contextId = "fileFeignClient", name = "mall-file",
        configuration = FileServiceTokenConfig.class, path = "/internal/assets",
        fallbackFactory = FileFeignClientFallbackFactory.class)
public interface FileFeignClient {

    @GetMapping("/admin")
    ApiResponse<AdminFileAssetPage> pageAdminAssets(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "purpose", required = false) String purpose);

    @GetMapping("/admin/{id}")
    ApiResponse<AdminFileAssetRow> adminAsset(@PathVariable("id") Long id);

    @DeleteMapping("/admin/{id}")
    ApiResponse<Void> deleteAdminAsset(@PathVariable("id") Long id);
}
