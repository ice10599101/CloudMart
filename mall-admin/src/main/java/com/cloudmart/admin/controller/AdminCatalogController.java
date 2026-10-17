package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.CategoryDTO;
import com.cloudmart.admin.dto.feign.CreateProductRequest;
import com.cloudmart.admin.dto.feign.ProductDTO;
import com.cloudmart.admin.dto.feign.ProductSearchResultDTO;
import com.cloudmart.admin.dto.feign.UpdateProductRequest;
import com.cloudmart.admin.feign.BrandFeignClient;
import com.cloudmart.admin.feign.CategoryFeignClient;
import com.cloudmart.admin.feign.ProductEsFeignClient;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务管理-商品目录域：品牌、商品、分类、商品 ES 索引。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：商品目录（品牌/商品/分类/ES索引）")
public class AdminCatalogController {

    private final BrandFeignClient brandFeignClient;
    private final ProductFeignClient productFeignClient;
    private final CategoryFeignClient categoryFeignClient;
    private final ProductEsFeignClient productEsFeignClient;

    public AdminCatalogController(BrandFeignClient brandFeignClient,
                                  ProductFeignClient productFeignClient,
                                  CategoryFeignClient categoryFeignClient,
                                  ProductEsFeignClient productEsFeignClient) {
        this.brandFeignClient = brandFeignClient;
        this.productFeignClient = productFeignClient;
        this.categoryFeignClient = categoryFeignClient;
        this.productEsFeignClient = productEsFeignClient;
    }

    // ==================== 品牌 ====================

    @GetMapping("/brands")
    @RequiresPermission("business:brand:list")
    @Operation(summary = "品牌列表", description = "查询品牌列表")
    public ApiResponse<Object> listBrands(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> params = new HashMap<>();
        if (name != null) params.put("name", name);
        if (status != null) params.put("status", status);
        params.put("page", page);
        params.put("size", pageSize);
        return brandFeignClient.listBrands(params);
    }

    @GetMapping("/brands/{id}")
    @RequiresPermission("business:brand:query")
    @Operation(summary = "品牌详情", description = "查询品牌详情")
    public ApiResponse<Object> getBrand(@PathVariable Long id) {
        return brandFeignClient.getBrand(id);
    }

    @PostMapping("/brands")
    @OperLog(title = "品牌管理", businessType = 1)
    @RequiresPermission("business:brand:add")
    @Operation(summary = "创建品牌", description = "创建新品牌")
    public ApiResponse<Object> createBrand(@RequestBody Map<String, Object> body) {
        return brandFeignClient.createBrand(body);
    }

    @PutMapping("/brands/{id}")
    @OperLog(title = "品牌管理", businessType = 2)
    @RequiresPermission("business:brand:edit")
    @Operation(summary = "更新品牌", description = "更新品牌信息")
    public ApiResponse<Object> updateBrand(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return brandFeignClient.updateBrand(id, body);
    }

    @DeleteMapping("/brands/{id}")
    @OperLog(title = "品牌管理", businessType = 3)
    @RequiresPermission("business:brand:remove")
    @Operation(summary = "删除品牌", description = "删除品牌")
    public ApiResponse<Void> deleteBrand(@PathVariable Long id) {
        return brandFeignClient.deleteBrand(id);
    }

    // ==================== 商品 ====================

    @GetMapping("/products")
    @RequiresPermission("business:product:list")
    @Operation(summary = "商品列表", description = "查询商品列表，支持搜索")
    public ApiResponse<List<ProductDTO>> listProducts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        ApiResponse<ProductSearchResultDTO> response = productFeignClient.searchProducts(keyword, categoryId, minPrice, maxPrice, sort, status, page, pageSize);
        if (response.success() && response.data() != null) {
            ProductSearchResultDTO result = response.data();
            return ApiResponse.ok(result.products(), result.page(), result.size(), result.total());
        }
        return ApiResponse.fail("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
    }

    @GetMapping("/products/{id}")
    @RequiresPermission("business:product:query")
    @Operation(summary = "商品详情", description = "查询商品详情")
    public ApiResponse<ProductDTO> getProduct(@PathVariable Long id) {
        return productFeignClient.getProductById(id);
    }

    @PostMapping("/products")
    @OperLog(title = "商品管理", businessType = 1)
    @RequiresPermission("business:product:add")
    @Operation(summary = "创建商品", description = "创建新商品")
    public ApiResponse<ProductDTO> createProduct(@RequestBody CreateProductRequest request) {
        return productFeignClient.createProduct(request);
    }

    @PutMapping("/products/{id}")
    @OperLog(title = "商品管理", businessType = 2)
    @RequiresPermission("business:product:edit")
    @Operation(summary = "更新商品", description = "更新商品信息")
    public ApiResponse<ProductDTO> updateProduct(@PathVariable Long id, @RequestBody UpdateProductRequest request) {
        return productFeignClient.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    @OperLog(title = "商品管理", businessType = 3)
    @RequiresPermission("business:product:remove")
    @Operation(summary = "删除商品", description = "删除商品")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        return productFeignClient.deleteProduct(id);
    }

    // ==================== 分类 ====================

    @GetMapping("/categories")
    @RequiresPermission("business:product:list")
    @Operation(summary = "分类列表", description = "查询商品分类列表")
    public ApiResponse<List<CategoryDTO>> listCategories() {
        return categoryFeignClient.listCategories();
    }

    @PostMapping("/categories")
    @OperLog(title = "分类管理", businessType = 1)
    @RequiresPermission("business:product:add")
    @Operation(summary = "创建分类", description = "创建商品分类")
    public ApiResponse<CategoryDTO> createCategory(@RequestParam String name, @RequestParam(required = false) Long parentId) {
        return categoryFeignClient.createCategory(name, parentId);
    }

    @PutMapping("/categories/{id}")
    @OperLog(title = "分类管理", businessType = 2)
    @RequiresPermission("business:product:edit")
    @Operation(summary = "更新分类", description = "更新商品分类信息")
    public ApiResponse<CategoryDTO> updateCategory(@PathVariable Long id, @RequestParam String name,
                                                    @RequestParam(required = false) Long parentId,
                                                    @RequestParam(required = false) Integer sortOrder,
                                                    @RequestParam(required = false) Integer status) {
        return categoryFeignClient.updateCategory(id, name, parentId, sortOrder, status);
    }

    @DeleteMapping("/categories/{id}")
    @OperLog(title = "分类管理", businessType = 3)
    @RequiresPermission("business:product:remove")
    @Operation(summary = "删除分类", description = "删除商品分类")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        return categoryFeignClient.deleteCategory(id);
    }

    // ==================== 商品 ES 索引管理（T08 蓝绿重建） ====================

    @GetMapping("/products/es/index/status")
    @RequiresPermission("business:product:edit")
    @Operation(summary = "ES 索引状态", description = "别名指向（含写索引）、mapping 与 settings")
    public ApiResponse<Map<String, Object>> esIndexStatus() {
        return productEsFeignClient.indexStatus();
    }

    @GetMapping("/products/es/index/versions")
    @RequiresPermission("business:product:edit")
    @Operation(summary = "ES 索引版本清单", description = "当前写索引与版本化实体索引")
    public ApiResponse<Map<String, Object>> esIndexVersions() {
        return productEsFeignClient.indexVersions();
    }

    @PostMapping("/products/es/index/full-rebuild")
    @OperLog(title = "ES 索引蓝绿重建", businessType = 1)
    @RequiresPermission("business:product:edit")
    @Operation(summary = "ES 索引蓝绿全量重建", description = "建新版本索引→全量写入→原子切换别名；旧版本保留回滚能力")
    public ApiResponse<Map<String, Object>> esIndexFullRebuild() {
        return productEsFeignClient.fullRebuild();
    }

    // ===== PRODUCT-01：商品问答管理（商城→商品问答） =====

    @GetMapping("/products/questions")
    @RequiresPermission("business:product:question")
    @Operation(summary = "问答管理分页", description = "PRODUCT-01：按商品/状态筛选（含隐藏项与全部答案）")
    public ApiResponse<Object> listQuestions(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return productFeignClient.adminQuestions(productId, status, page, pageSize);
    }

    @PostMapping("/products/questions/{questionId}/hide")
    @OperLog(title = "商品问答", businessType = 2)
    @RequiresPermission("business:product:question")
    @Operation(summary = "隐藏问题", description = "运营处置；隐藏后所有端不可见")
    public ApiResponse<Void> hideQuestion(@PathVariable Long questionId,
                                          @RequestBody(required = false) java.util.Map<String, String> body) {
        return productFeignClient.hideQuestion(questionId, body);
    }

    @PostMapping("/products/questions/{questionId}/restore")
    @OperLog(title = "商品问答", businessType = 2)
    @RequiresPermission("business:product:question")
    @Operation(summary = "恢复问题")
    public ApiResponse<Void> restoreQuestion(@PathVariable Long questionId) {
        return productFeignClient.restoreQuestion(questionId);
    }

    @PostMapping("/products/answers/{answerId}/hide")
    @OperLog(title = "商品问答", businessType = 2)
    @RequiresPermission("business:product:question")
    @Operation(summary = "隐藏答案")
    public ApiResponse<Void> hideAnswer(@PathVariable Long answerId,
                                        @RequestBody(required = false) java.util.Map<String, String> body) {
        return productFeignClient.hideAnswer(answerId, body);
    }

    @PostMapping("/products/answers/{answerId}/restore")
    @OperLog(title = "商品问答", businessType = 2)
    @RequiresPermission("business:product:question")
    @Operation(summary = "恢复答案")
    public ApiResponse<Void> restoreAnswer(@PathVariable Long answerId) {
        return productFeignClient.restoreAnswer(answerId);
    }
}
