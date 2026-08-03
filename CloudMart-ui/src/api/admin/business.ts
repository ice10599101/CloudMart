import request from '@/utils/request'

export function getProducts(params?: Record<string, any>) {
  return request.get('/admin/business/products', { params })
}

export function getProduct(id: number | string) {
  return request.get(`/admin/business/products/${id}`)
}

export function createProduct(data: Record<string, any>) {
  return request.post('/admin/business/products', data)
}

export function updateProduct(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/products/${id}`, data)
}

export function deleteProduct(id: number | string) {
  return request.delete(`/admin/business/products/${id}`)
}

export function getCategories(params?: Record<string, any>) {
  return request.get('/admin/business/categories', { params })
}

export function createCategory(data: Record<string, any>) {
  return request.post('/admin/business/categories', data)
}

export function updateCategory(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/categories/${id}`, data)
}

export function deleteCategory(id: number | string) {
  return request.delete(`/admin/business/categories/${id}`)
}

export function getOrders(params?: Record<string, any>) {
  return request.get('/admin/business/orders', { params })
}

export function getOrder(id: number | string) {
  return request.get(`/admin/business/orders/${id}`)
}

export function shipOrder(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/orders/${id}/ship`, data)
}

export function cancelOrder(id: number | string, data?: Record<string, any>) {
  return request.put(`/admin/business/orders/${id}/cancel`, data)
}

export function approveRefund(id: number | string, data?: Record<string, any>) {
  return request.put(`/admin/business/orders/${id}/refund/approve`, data)
}

export function rejectRefund(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/orders/${id}/refund/reject`, data)
}

export function getTodayOrderStats() {
  return request.get('/admin/business/orders/stats/today')
}

export function getMembers(params?: Record<string, any>) {
  return request.get('/admin/business/members', { params })
}

export function getMember(id: number | string) {
  return request.get(`/admin/business/members/${id}`)
}

export function updateMember(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/members/${id}`, data)
}

export function updateMemberStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/members/${id}/status`, data)
}

export function getCoupons(params?: Record<string, any>) {
  return request.get('/admin/business/coupons', { params })
}

export function getCoupon(id: number | string) {
  return request.get(`/admin/business/coupons/${id}`)
}

export function createCoupon(data: Record<string, any>) {
  return request.post('/admin/business/coupons', data)
}

export function updateCoupon(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/coupons/${id}`, data)
}

export function enableCoupon(id: number | string) {
  return request.put(`/admin/business/coupons/${id}/enable`)
}

export function disableCoupon(id: number | string) {
  return request.put(`/admin/business/coupons/${id}/disable`)
}

export function deleteCoupon(id: number | string) {
  return request.delete(`/admin/business/coupons/${id}`)
}

export function getSeckillActivities(params?: Record<string, any>) {
  return request.get('/admin/business/seckill/activities', { params })
}

export function createSeckillActivity(data: Record<string, any>) {
  return request.post('/admin/business/seckill/activities', data)
}

export function updateSeckillActivity(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/seckill/activities/${id}`, data)
}

export function getSeckillActivity(activityId: number | string) {
  return request.get(`/admin/business/seckill/activities/${activityId}`)
}

export function updateSeckillActivityStatus(activityId: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/seckill/activities/${activityId}/status`, data)
}

export function deleteSeckillActivity(id: number | string) {
  return request.delete(`/admin/business/seckill/activities/${id}`)
}

export function getSeckillProducts(activityId: number | string, params?: Record<string, any>) {
  return request.get(`/admin/business/seckill/products/activity/${activityId}`, { params })
}

export function getSeckillProduct(productId: number | string) {
  return request.get(`/admin/business/seckill/products/${productId}`)
}

export function createSeckillProduct(activityId: number | string, data: Record<string, any>) {
  return request.post(`/admin/business/seckill/products/${activityId}`, data)
}

export function deleteSeckillProduct(id: number | string) {
  return request.delete(`/admin/business/seckill/products/${id}`)
}

export function getCart(userId: number | string) {
  return request.get(`/admin/business/carts/${userId}`)
}

export function removeCartItem(userId: number | string, skuId: number | string) {
  return request.delete(`/admin/business/carts/${userId}/items/${skuId}`)
}

export function clearCart(userId: number | string) {
  return request.delete(`/admin/business/carts/${userId}/clear`)
}

export function getReviews(params?: Record<string, any>) {
  return request.get('/admin/business/reviews', { params })
}

export function updateReviewStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/reviews/${id}/status`, data)
}

export function deleteReview(id: number | string) {
  return request.delete(`/admin/business/reviews/${id}`)
}

export function getInventory(params?: Record<string, any>) {
  return request.get('/admin/business/inventory', { params })
}

export function getInventoryDetail(skuId: number | string) {
  return request.get(`/admin/business/inventory/${skuId}`)
}

export function initInventory(data: Record<string, any>) {
  return request.post('/admin/business/inventory/init', data)
}

export function getPayments(params?: Record<string, any>) {
  return request.get('/admin/business/payments', { params })
}

export function getPaymentByOrder(orderId: number | string) {
  return request.get(`/admin/business/payments/order/${orderId}`)
}

export function refundPayment(id: number | string, data?: Record<string, any>) {
  return request.post(`/admin/business/payments/${id}/refund`, data)
}

export function getNotifications(params?: Record<string, any>) {
  return request.get('/admin/business/notifications', { params })
}

export function sendNotification(data: Record<string, any>) {
  return request.post('/admin/business/notifications', data)
}

export function getGroupActivities(params?: Record<string, any>) {
  return request.get('/admin/business/marketing/group/activities', { params })
}

export function createGroupActivity(data: Record<string, any>) {
  return request.post('/admin/business/marketing/group/activities', data)
}

export function updateGroupActivity(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/marketing/group/activities/${id}`, data)
}

export function enableGroupActivity(id: number | string) {
  return request.put(`/admin/business/marketing/group/activities/${id}/enable`)
}

export function disableGroupActivity(id: number | string) {
  return request.put(`/admin/business/marketing/group/activities/${id}/disable`)
}

export function deleteGroupActivity(id: number | string) {
  return request.delete(`/admin/business/marketing/group/activities/${id}`)
}

export function getGroupOrders(params?: Record<string, any>) {
  return request.get('/admin/business/marketing/group/orders', { params })
}

export function getTieredPromotions(params?: Record<string, any>) {
  return request.get('/admin/business/marketing/tiered/promotions', { params })
}

export function createTieredPromotion(data: Record<string, any>) {
  return request.post('/admin/business/marketing/tiered/promotions', data)
}

export function getTieredPromotion(id: number | string) {
  return request.get(`/admin/business/marketing/tiered/promotions/${id}`)
}

export function updateTieredPromotion(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/marketing/tiered/promotions/${id}`, data)
}

export function enableTieredPromotion(id: number | string) {
  return request.put(`/admin/business/marketing/tiered/promotions/${id}/enable`)
}

export function disableTieredPromotion(id: number | string) {
  return request.put(`/admin/business/marketing/tiered/promotions/${id}/disable`)
}

export function deleteTieredPromotion(id: number | string) {
  return request.delete(`/admin/business/marketing/tiered/promotions/${id}`)
}

export function getLiveRooms(params?: Record<string, any>) {
  return request.get('/admin/business/live/rooms', { params })
}

export function createLiveRoom(data: Record<string, any>) {
  return request.post('/admin/business/live/rooms', data)
}

export function updateLiveRoom(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/live/rooms/${id}`, data)
}

export function startLive(id: number | string) {
  return request.put(`/admin/business/live/rooms/${id}/start`)
}

export function endLive(id: number | string) {
  return request.put(`/admin/business/live/rooms/${id}/end`)
}

export function deleteLiveRoom(id: number | string) {
  return request.delete(`/admin/business/live/rooms/${id}`)
}

export function getPickOrders(params?: Record<string, any>) {
  return request.get('/admin/business/wms/pick-orders', { params })
}

export function getPickOrder(id: number | string) {
  return request.get(`/admin/business/wms/pick-orders/${id}`)
}

export function startPick(id: number | string) {
  return request.put(`/admin/business/wms/pick-orders/${id}/start`)
}

export function confirmPicked(id: number | string, data?: Record<string, any>) {
  return request.put(`/admin/business/wms/pick-orders/${id}/picked`, data)
}

export function confirmPacked(id: number | string, data?: Record<string, any>) {
  return request.put(`/admin/business/wms/pick-orders/${id}/packed`, data)
}

export function getInboundOrders(params?: Record<string, any>) {
  return request.get('/admin/business/wms/inbound-orders', { params })
}

export function getInboundOrder(id: number | string) {
  return request.get(`/admin/business/wms/inbound-orders/${id}`)
}

export function getBlacklist(params?: Record<string, any>) {
  return request.get('/admin/business/risk/blacklist', { params })
}

export function addToBlacklist(data: Record<string, any>) {
  return request.post('/admin/business/risk/blacklist', data)
}

export function removeFromBlacklist(type: string, value: string) {
  return request.delete(`/admin/business/risk/blacklist/${type}/${value}`)
}

export function checkBlacklist(params?: Record<string, any>) {
  return request.get('/admin/business/risk/blacklist/check', { params })
}

export function triggerFullVectorSync() {
  return request.post('/admin/business/ai/vector-sync/full')
}

export function syncProductVector(id: number | string) {
  return request.post(`/admin/business/ai/vector-sync/product/${id}`)
}

export function deleteProductVector(id: number | string) {
  return request.delete(`/admin/business/ai/vector-sync/product/${id}`)
}

export function getBrands(params?: Record<string, any>) {
  return request.get('/admin/business/brands', { params })
}

export function getBrand(id: number | string) {
  return request.get(`/admin/business/brands/${id}`)
}

export function createBrand(data: Record<string, any>) {
  return request.post('/admin/business/brands', data)
}

export function updateBrand(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/brands/${id}`, data)
}

export function deleteBrand(id: number | string) {
  return request.delete(`/admin/business/brands/${id}`)
}

export function getRiskRecords(params?: Record<string, any>) {
  return request.get('/admin/business/risk/records', { params })
}

export function getRiskRecord(id: number | string) {
  return request.get(`/admin/business/risk/records/${id}`)
}

export function getRiskRules(params?: Record<string, any>) {
  return request.get('/admin/business/risk/rules', { params })
}

export function createRiskRule(data: Record<string, any>) {
  return request.post('/admin/business/risk/rules', data)
}

export function updateRiskRule(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/risk/rules/${id}`, data)
}

export function deleteRiskRule(id: number | string) {
  return request.delete(`/admin/business/risk/rules/${id}`)
}

export function getShipping(params?: Record<string, any>) {
  return request.get('/admin/business/wms/shipping', { params })
}

export function updateShippingStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/wms/shipping/${id}/status`, data)
}

export function getWarehouses(params?: Record<string, any>) {
  return request.get('/admin/business/wms/warehouses', { params })
}

export function createWarehouse(data: Record<string, any>) {
  return request.post('/admin/business/wms/warehouses', data)
}

export function updateWarehouse(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/business/wms/warehouses/${id}`, data)
}

export function deleteWarehouse(id: number | string) {
  return request.delete(`/admin/business/wms/warehouses/${id}`)
}

export function uploadFile(data: FormData) {
  return request.post('/file/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteFile(url: string) {
  return request.delete('/file/delete', { params: { url } })
}
