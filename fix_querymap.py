# -*- coding: utf-8 -*-
"""修复 @SpringQueryMap + record 出参展开失效（OpenFeign 端 record 无法通过 JavaBeans 内省展开 → 筛选参数从未到达下游）"""
import io, os

BASE = 'mall-admin/src/main/java/com/cloudmart/admin/feign/'

def patch(path, subs, drop_imports=()):
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    for old, new in subs:
        assert old in s, "MISSING in %s:\n%s" % (path, old[:100])
        s = s.replace(old, new)
    for imp in drop_imports:
        s = s.replace(imp + "\n", "")
    with io.open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(s)
    print("OK", os.path.basename(path))

patch(BASE + 'ProductFeignClient.java', [
    ("    ApiResponse<ProductSearchResultDTO> searchProducts(@SpringQueryMap ProductSearchRequest request);",
     """    ApiResponse<ProductSearchResultDTO> searchProducts(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "categoryId", required = false) java.math.BigDecimal minPriceKey, placeholder)""" if False else
     """    ApiResponse<ProductSearchResultDTO> searchProducts(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                       @RequestParam(value = "minPrice", required = false) java.math.BigDecimal minPrice,
                                                       @RequestParam(value = "maxPrice", required = false) java.math.BigDecimal maxPrice,
                                                       @RequestParam(value = "sort", required = false) String sort,
                                                       @RequestParam(value = "status", required = false) Integer status,
                                                       @RequestParam("page") Integer page,
                                                       @RequestParam("size") Integer size);"""),
], drop_imports=("import org.springframework.cloud.openfeign.SpringQueryMap;",))

patch(BASE + 'MarketingFeignClient.java', [
    ("    ApiResponse<Object> listGroupActivities(@SpringQueryMap GroupActivitySearchRequest request);",
     """    ApiResponse<Object> listGroupActivities(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam("page") Integer page,
                                            @RequestParam("size") Integer size);"""),
    ("    ApiResponse<Object> listGroupOrders(@SpringQueryMap GroupOrderSearchRequest request);",
     """    ApiResponse<Object> listGroupOrders(@RequestParam(value = "activityId", required = false) Long activityId,
                                        @RequestParam(value = "status", required = false) String status,
                                        @RequestParam("page") Integer page,
                                        @RequestParam("size") Integer size);"""),
    ("    ApiResponse<Object> listTieredPromotions(@SpringQueryMap TieredPromotionSearchRequest request);",
     """    ApiResponse<Object> listTieredPromotions(@RequestParam(value = "status", required = false) String status,
                                             @RequestParam("page") Integer page,
                                             @RequestParam("size") Integer size);"""),
], drop_imports=("import org.springframework.cloud.openfeign.SpringQueryMap;",))

patch(BASE + 'LiveFeignClient.java', [
    ("    ApiResponse<Object> listRooms(@SpringQueryMap LiveRoomSearchRequest request);",
     """    ApiResponse<Object> listRooms(@RequestParam(value = "status", required = false) String status,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("size") Integer size);"""),
], drop_imports=("import org.springframework.cloud.openfeign.SpringQueryMap;",))

patch(BASE + 'WmsFeignClient.java', [
    ("    ApiResponse<Object> listPickOrders(@SpringQueryMap WmsSearchRequest request);",
     """    ApiResponse<Object> listPickOrders(@RequestParam(value = "status", required = false) String status,
                                       @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                                       @RequestParam("page") Integer page,
                                       @RequestParam("size") Integer size);"""),
    ("    ApiResponse<Object> listInboundOrders(@SpringQueryMap WmsSearchRequest request);",
     """    ApiResponse<Object> listInboundOrders(@RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                                          @RequestParam("page") Integer page,
                                          @RequestParam("size") Integer size);"""),
    ("    ApiResponse<Object> listShipping(@SpringQueryMap WmsSearchRequest request);",
     """    ApiResponse<Object> listShipping(@RequestParam(value = "status", required = false) String status,
                                     @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                                     @RequestParam("page") Integer page,
                                     @RequestParam("size") Integer size);"""),
], drop_imports=("import org.springframework.cloud.openfeign.SpringQueryMap;",))

patch(BASE + 'WishFeignClient.java', [
    ("    ApiResponse<Object> listWishes(@SpringQueryMap AdminWishSearchRequest request);",
     """    ApiResponse<Object> listWishes(@RequestParam(value = "userId", required = false) Long userId,
                                   @RequestParam(value = "categoryId", required = false) Long categoryId,
                                   @RequestParam(value = "status", required = false) String status,
                                   @RequestParam(value = "auditStatus", required = false) String auditStatus,
                                   @RequestParam(value = "visibility", required = false) String visibility,
                                   @RequestParam(value = "keyword", required = false) String keyword,
                                   @RequestParam("page") Integer page,
                                   @RequestParam("pageSize") Integer pageSize);"""),
    ("    ApiResponse<Object> listInteractions(@SpringQueryMap AdminInteractionSearchRequest request);",
     """    ApiResponse<Object> listInteractions(@RequestParam(value = "wishId", required = false) Long wishId,
                                         @RequestParam(value = "userId", required = false) Long userId,
                                         @RequestParam(value = "type", required = false) String type,
                                         @RequestParam(value = "startTime", required = false) String startTime,
                                         @RequestParam(value = "endTime", required = false) String endTime,
                                         @RequestParam("page") Integer page,
                                         @RequestParam("pageSize") Integer pageSize);"""),
    ("    ApiResponse<Object> listComments(@SpringQueryMap AdminCommentSearchRequest request);",
     """    ApiResponse<Object> listComments(@RequestParam(value = "wishId", required = false) Long wishId,
                                     @RequestParam(value = "userId", required = false) Long userId,
                                     @RequestParam(value = "sensitiveHit", required = false) Boolean sensitiveHit,
                                     @RequestParam(value = "status", required = false) String status,
                                     @RequestParam("page") Integer page,
                                     @RequestParam("pageSize") Integer pageSize);"""),
], drop_imports=("import org.springframework.cloud.openfeign.SpringQueryMap;",))

patch(BASE + 'NotificationFeignClient.java', [
    ("import com.cloudmart.admin.dto.feign.NotificationSearchRequest;\n", ""),
    ("    @GetMapping\n    ApiResponse<Object> listNotifications(@SpringQueryMap NotificationSearchRequest request);\n\n", ""),
    ("    @GetMapping\n    ApiResponse<Object> listNotifications(@SpringQueryMap NotificationSearchRequest request);\n", ""),
], drop_imports=("import org.springframework.cloud.openfeign.SpringQueryMap;",))

# ---------- AdminBusinessController：8 处调用点透传 ----------
CTRL = 'mall-admin/src/main/java/com/cloudmart/admin/controller/AdminBusinessController.java'
patch(CTRL, [
    ("""        ProductSearchRequest request = new ProductSearchRequest(keyword, categoryId, minPrice, maxPrice, sort, status, page, pageSize);
        ApiResponse<ProductSearchResultDTO> response = productFeignClient.searchProducts(request);""",
     """        ApiResponse<ProductSearchResultDTO> response = productFeignClient.searchProducts(keyword, categoryId, minPrice, maxPrice, sort, status, page, pageSize);"""),
    ("""        GroupActivitySearchRequest request = new GroupActivitySearchRequest(status, page, pageSize);
        return marketingFeignClient.listGroupActivities(request);""",
     """        return marketingFeignClient.listGroupActivities(status, page, pageSize);"""),
    ("""        GroupOrderSearchRequest request = new GroupOrderSearchRequest(activityId, status, page, pageSize);
        return marketingFeignClient.listGroupOrders(request);""",
     """        return marketingFeignClient.listGroupOrders(activityId, status, page, pageSize);"""),
    ("""        TieredPromotionSearchRequest request = new TieredPromotionSearchRequest(status, page, pageSize);
        return marketingFeignClient.listTieredPromotions(request);""",
     """        return marketingFeignClient.listTieredPromotions(status, page, pageSize);"""),
    ("""        LiveRoomSearchRequest request = new LiveRoomSearchRequest(status, page, pageSize);
        return liveFeignClient.listRooms(request);""",
     """        return liveFeignClient.listRooms(status, page, pageSize);"""),
    ("""        WmsSearchRequest request = new WmsSearchRequest(status, warehouseId, page, pageSize);
        return wmsFeignClient.listPickOrders(request);""",
     """        return wmsFeignClient.listPickOrders(status, warehouseId, page, pageSize);"""),
    ("""        WmsSearchRequest request = new WmsSearchRequest(status, warehouseId, page, pageSize);
        return wmsFeignClient.listInboundOrders(request);""",
     """        return wmsFeignClient.listInboundOrders(status, warehouseId, page, pageSize);"""),
    ("""        WmsSearchRequest request = new WmsSearchRequest(status, warehouseId, page, pageSize);
        return wmsFeignClient.listShipping(request);""",
     """        return wmsFeignClient.listShipping(status, warehouseId, page, pageSize);"""),
])

# ---------- AdminWishController：入参保留 record（Spring MVC 7 支持构造器绑定），出参显式透传 ----------
WCTRL = 'mall-admin/src/main/java/com/cloudmart/admin/controller/AdminWishController.java'
with io.open(WCTRL, encoding='utf-8') as f:
    s = f.read()
for m in re.finditer(r'return (wishFeignClient\.list(?:Wishes|Interactions|Comments))\(request\);', s):
    pass
s = s.replace("return wishFeignClient.listWishes(request);",
    """return wishFeignClient.listWishes(request.userId(), request.categoryId(), request.status(),
                request.auditStatus(), request.visibility(), request.keyword(), request.page(), request.pageSize());""")
s = s.replace("return wishFeignClient.listInteractions(request);",
    """return wishFeignClient.listInteractions(request.wishId(), request.userId(), request.type(),
                request.startTime(), request.endTime(), request.page(), request.pageSize());""")
s = s.replace("return wishFeignClient.listComments(request);",
    """return wishFeignClient.listComments(request.wishId(), request.userId(), request.sensitiveHit(),
                request.status(), request.page(), request.pageSize());""")
with io.open(WCTRL, 'w', encoding='utf-8', newline='\n') as f:
    f.write(s)
print("OK AdminWishController")

# ---------- 死代码清理：不再被引用的 SearchRequest record ----------
import subprocess
for name in ["ProductSearchRequest", "GroupActivitySearchRequest", "GroupOrderSearchRequest",
             "TieredPromotionSearchRequest", "LiveRoomSearchRequest", "NotificationSearchRequest",
             "WmsSearchRequest", "AdminWishSearchRequest", "AdminInteractionSearchRequest", "AdminCommentSearchRequest"]:
    out = subprocess.run(["grep", "-rl", name, "mall-admin/src/main/java"], capture_output=True, text=True)
    refs = [p for p in out.stdout.strip().splitlines() if p and not p.replace('\\', '/').endswith("/%s.java" % name)]
    if not refs:
        os.remove("mall-admin/src/main/java/com/cloudmart/admin/dto/feign/%s.java" % name)
        print("DELETED", name)
    else:
        print("KEEP", name, "->", refs)
print("ALL DONE")
