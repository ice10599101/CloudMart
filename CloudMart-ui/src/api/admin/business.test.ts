import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getProducts,
  getProduct,
  createProduct,
  updateProduct,
  deleteProduct,
  getCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  getOrders,
  getOrder,
  shipOrder,
  cancelOrder,
  approveRefund,
  rejectRefund,
  getTodayOrderStats,
  getMembers,
  getMember,
  updateMember,
  updateMemberStatus,
  getCoupons,
  getCoupon,
  createCoupon,
  enableCoupon,
  disableCoupon,
  getSeckillActivities,
  createSeckillActivity,
  getSeckillActivity,
  updateSeckillActivityStatus,
  getSeckillProducts,
  getSeckillProduct,
  createSeckillProduct,
  getCart,
  removeCartItem,
  clearCart,
  getReviews,
  updateReviewStatus,
  deleteReview,
  getInventory,
  getInventoryDetail,
  initInventory,
  getPayments,
  getPaymentByOrder,
  refundPayment,
  getNotifications,
  sendNotification,
  getGroupActivities,
  createGroupActivity,
  enableGroupActivity,
  disableGroupActivity,
  getGroupOrders,
  getTieredPromotions,
  createTieredPromotion,
  enableTieredPromotion,
  disableTieredPromotion,
  getLiveRooms,
  createLiveRoom,
  startLive,
  endLive,
  getPickOrders,
  getPickOrder,
  startPick,
  confirmPicked,
  confirmPacked,
  getInboundOrders,
  getBlacklist,
  addToBlacklist,
  removeFromBlacklist,
  checkBlacklist,
  triggerFullVectorSync,
  syncProductVector,
  deleteProductVector,
  getBrands,
  getBrand,
  createBrand,
  updateBrand,
  getRiskRecords,
  getRiskRecord,
  getRiskRules,
  createRiskRule,
  updateRiskRule,
  deleteRiskRule,
  getShipping,
  updateShippingStatus,
  getWarehouses,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
  uploadFile,
  deleteFile,
} from './business'

describe('admin business API - Product Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getProducts() calls GET /admin/business/products', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getProducts({ page: 1, size: 10 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/products', { params: { page: 1, size: 10 } })
  })

  it('getProduct() calls GET /admin/business/products/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getProduct(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/products/1')
  })

  it('createProduct() calls POST /admin/business/products', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createProduct({ name: 'Phone' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/products', { name: 'Phone' })
  })

  it('updateProduct() calls PUT /admin/business/products/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateProduct(1, { name: 'Phone Pro' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/products/1', { name: 'Phone Pro' })
  })

  it('deleteProduct() calls DELETE /admin/business/products/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteProduct(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/products/1')
  })
})

describe('admin business API - Category Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getCategories() calls GET /admin/business/categories', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getCategories({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/categories', { params: { page: 1 } })
  })

  it('createCategory() calls POST /admin/business/categories', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createCategory({ name: 'Electronics' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/categories', { name: 'Electronics' })
  })

  it('updateCategory() calls PUT /admin/business/categories/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateCategory(1, { name: 'Updated' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/categories/1', { name: 'Updated' })
  })

  it('deleteCategory() calls DELETE /admin/business/categories/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteCategory(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/categories/1')
  })
})

describe('admin business API - Order Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getOrders() calls GET /admin/business/orders', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getOrders({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/orders', { params: { page: 1 } })
  })

  it('getOrder() calls GET /admin/business/orders/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getOrder(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/orders/1')
  })

  it('shipOrder() calls PUT /admin/business/orders/:id/ship', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await shipOrder(1, { trackingNo: 'SF123' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/orders/1/ship', { trackingNo: 'SF123' })
  })

  it('cancelOrder() calls PUT /admin/business/orders/:id/cancel', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await cancelOrder(1, { reason: 'out of stock' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/orders/1/cancel', { reason: 'out of stock' })
  })

  it('approveRefund() calls PUT /admin/business/orders/:id/refund/approve', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await approveRefund(1, { amount: 100 })
    expect(request.put).toHaveBeenCalledWith('/admin/business/orders/1/refund/approve', { amount: 100 })
  })

  it('rejectRefund() calls PUT /admin/business/orders/:id/refund/reject', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await rejectRefund(1, { reason: 'invalid' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/orders/1/refund/reject', { reason: 'invalid' })
  })

  it('getTodayOrderStats() calls GET /admin/business/orders/stats/today', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getTodayOrderStats()
    expect(request.get).toHaveBeenCalledWith('/admin/business/orders/stats/today')
  })
})

describe('admin business API - Member Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getMembers() calls GET /admin/business/members', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getMembers({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/members', { params: { page: 1 } })
  })

  it('getMember() calls GET /admin/business/members/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getMember(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/members/1')
  })

  it('updateMember() calls PUT /admin/business/members/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateMember(1, { nickname: 'Updated' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/members/1', { nickname: 'Updated' })
  })

  it('updateMemberStatus() calls PUT /admin/business/members/:id/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateMemberStatus(1, { status: 0 })
    expect(request.put).toHaveBeenCalledWith('/admin/business/members/1/status', { status: 0 })
  })
})

describe('admin business API - Coupon Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getCoupons() calls GET /admin/business/coupons', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getCoupons({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/coupons', { params: { page: 1 } })
  })

  it('getCoupon() calls GET /admin/business/coupons/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getCoupon(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/coupons/1')
  })

  it('createCoupon() calls POST /admin/business/coupons', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createCoupon({ name: 'Discount' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/coupons', { name: 'Discount' })
  })

  it('enableCoupon() calls PUT /admin/business/coupons/:id/enable', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await enableCoupon(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/coupons/1/enable')
  })

  it('disableCoupon() calls PUT /admin/business/coupons/:id/disable', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await disableCoupon(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/coupons/1/disable')
  })
})

describe('admin business API - Seckill Activity Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getSeckillActivities() calls GET /admin/business/seckill/activities', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getSeckillActivities({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/seckill/activities', { params: { page: 1 } })
  })

  it('createSeckillActivity() calls POST /admin/business/seckill/activities', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createSeckillActivity({ name: 'Flash Sale' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/seckill/activities', { name: 'Flash Sale' })
  })

  it('getSeckillActivity() calls GET /admin/business/seckill/activities/:activityId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getSeckillActivity(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/seckill/activities/1')
  })

  it('updateSeckillActivityStatus() calls PUT /admin/business/seckill/activities/:activityId/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateSeckillActivityStatus(1, { status: 1 })
    expect(request.put).toHaveBeenCalledWith('/admin/business/seckill/activities/1/status', { status: 1 })
  })
})

describe('admin business API - Seckill Product Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getSeckillProducts() calls GET /admin/business/seckill/products/activity/:activityId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getSeckillProducts(1, { page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/seckill/products/activity/1', { params: { page: 1 } })
  })

  it('getSeckillProduct() calls GET /admin/business/seckill/products/:productId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getSeckillProduct(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/seckill/products/1')
  })

  it('createSeckillProduct() calls POST /admin/business/seckill/products/:activityId', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createSeckillProduct(1, { productId: 10 })
    expect(request.post).toHaveBeenCalledWith('/admin/business/seckill/products/1', { productId: 10 })
  })
})

describe('admin business API - Cart Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getCart() calls GET /admin/business/carts/:userId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getCart(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/carts/1')
  })

  it('removeCartItem() calls DELETE /admin/business/carts/:userId/items/:skuId', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await removeCartItem(1, 2)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/carts/1/items/2')
  })

  it('clearCart() calls DELETE /admin/business/carts/:userId/clear', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await clearCart(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/carts/1/clear')
  })
})

describe('admin business API - Review Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getReviews() calls GET /admin/business/reviews', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getReviews({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/reviews', { params: { page: 1 } })
  })

  it('updateReviewStatus() calls PUT /admin/business/reviews/:id/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateReviewStatus(1, { status: 1 })
    expect(request.put).toHaveBeenCalledWith('/admin/business/reviews/1/status', { status: 1 })
  })

  it('deleteReview() calls DELETE /admin/business/reviews/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteReview(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/reviews/1')
  })
})

describe('admin business API - Inventory Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getInventory() calls GET /admin/business/inventory', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getInventory({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/inventory', { params: { page: 1 } })
  })

  it('getInventoryDetail() calls GET /admin/business/inventory/:skuId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getInventoryDetail(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/inventory/1')
  })

  it('initInventory() calls POST /admin/business/inventory/init', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await initInventory({ skuId: 1, quantity: 100 })
    expect(request.post).toHaveBeenCalledWith('/admin/business/inventory/init', { skuId: 1, quantity: 100 })
  })
})

describe('admin business API - Payment Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getPayments() calls GET /admin/business/payments', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getPayments({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/payments', { params: { page: 1 } })
  })

  it('getPaymentByOrder() calls GET /admin/business/payments/order/:orderId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getPaymentByOrder(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/payments/order/1')
  })

  it('refundPayment() calls POST /admin/business/payments/:id/refund', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await refundPayment(1, { amount: 50 })
    expect(request.post).toHaveBeenCalledWith('/admin/business/payments/1/refund', { amount: 50 })
  })
})

describe('admin business API - Notification Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getNotifications() calls GET /admin/business/notifications', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getNotifications({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/notifications', { params: { page: 1 } })
  })

  it('sendNotification() calls POST /admin/business/notifications', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await sendNotification({ title: 'Sale' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/notifications', { title: 'Sale' })
  })
})

describe('admin business API - Group Activity Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getGroupActivities() calls GET /admin/business/marketing/group/activities', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getGroupActivities({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/marketing/group/activities', { params: { page: 1 } })
  })

  it('createGroupActivity() calls POST /admin/business/marketing/group/activities', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createGroupActivity({ name: 'Group Buy' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/marketing/group/activities', { name: 'Group Buy' })
  })

  it('enableGroupActivity() calls PUT /admin/business/marketing/group/activities/:id/enable', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await enableGroupActivity(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/marketing/group/activities/1/enable')
  })

  it('disableGroupActivity() calls PUT /admin/business/marketing/group/activities/:id/disable', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await disableGroupActivity(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/marketing/group/activities/1/disable')
  })

  it('getGroupOrders() calls GET /admin/business/marketing/group/orders', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getGroupOrders({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/marketing/group/orders', { params: { page: 1 } })
  })
})

describe('admin business API - Tiered Promotion Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getTieredPromotions() calls GET /admin/business/marketing/tiered/promotions', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getTieredPromotions({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/marketing/tiered/promotions', { params: { page: 1 } })
  })

  it('createTieredPromotion() calls POST /admin/business/marketing/tiered/promotions', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createTieredPromotion({ name: 'Tiered' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/marketing/tiered/promotions', { name: 'Tiered' })
  })

  it('enableTieredPromotion() calls PUT /admin/business/marketing/tiered/promotions/:id/enable', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await enableTieredPromotion(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/marketing/tiered/promotions/1/enable')
  })

  it('disableTieredPromotion() calls PUT /admin/business/marketing/tiered/promotions/:id/disable', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await disableTieredPromotion(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/marketing/tiered/promotions/1/disable')
  })
})

describe('admin business API - Live Room Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getLiveRooms() calls GET /admin/business/live/rooms', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getLiveRooms({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/live/rooms', { params: { page: 1 } })
  })

  it('createLiveRoom() calls POST /admin/business/live/rooms', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createLiveRoom({ title: 'Live Show' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/live/rooms', { title: 'Live Show' })
  })

  it('startLive() calls PUT /admin/business/live/rooms/:id/start', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await startLive(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/live/rooms/1/start')
  })

  it('endLive() calls PUT /admin/business/live/rooms/:id/end', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await endLive(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/live/rooms/1/end')
  })
})

describe('admin business API - WMS Pick Order Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getPickOrders() calls GET /admin/business/wms/pick-orders', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getPickOrders({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/wms/pick-orders', { params: { page: 1 } })
  })

  it('getPickOrder() calls GET /admin/business/wms/pick-orders/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getPickOrder(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/wms/pick-orders/1')
  })

  it('startPick() calls PUT /admin/business/wms/pick-orders/:id/start', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await startPick(1)
    expect(request.put).toHaveBeenCalledWith('/admin/business/wms/pick-orders/1/start')
  })

  it('confirmPicked() calls PUT /admin/business/wms/pick-orders/:id/picked', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await confirmPicked(1, { items: [] })
    expect(request.put).toHaveBeenCalledWith('/admin/business/wms/pick-orders/1/picked', { items: [] })
  })

  it('confirmPacked() calls PUT /admin/business/wms/pick-orders/:id/packed', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await confirmPacked(1, { weight: 2.5 })
    expect(request.put).toHaveBeenCalledWith('/admin/business/wms/pick-orders/1/packed', { weight: 2.5 })
  })
})

describe('admin business API - WMS Inbound & Shipping & Warehouse', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getInboundOrders() calls GET /admin/business/wms/inbound-orders', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getInboundOrders({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/wms/inbound-orders', { params: { page: 1 } })
  })

  it('getShipping() calls GET /admin/business/wms/shipping', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getShipping({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/wms/shipping', { params: { page: 1 } })
  })

  it('updateShippingStatus() calls PUT /admin/business/wms/shipping/:id/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateShippingStatus(1, { status: 'shipped' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/wms/shipping/1/status', { status: 'shipped' })
  })

  it('getWarehouses() calls GET /admin/business/wms/warehouses', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getWarehouses({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/wms/warehouses', { params: { page: 1 } })
  })

  it('createWarehouse() calls POST /admin/business/wms/warehouses', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createWarehouse({ name: 'WH-1' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/wms/warehouses', { name: 'WH-1' })
  })

  it('updateWarehouse() calls PUT /admin/business/wms/warehouses/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateWarehouse(1, { name: 'WH-2' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/wms/warehouses/1', { name: 'WH-2' })
  })

  it('deleteWarehouse() calls DELETE /admin/business/wms/warehouses/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteWarehouse(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/wms/warehouses/1')
  })
})

describe('admin business API - Risk Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getBlacklist() calls GET /admin/business/risk/blacklist', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getBlacklist({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/risk/blacklist', { params: { page: 1 } })
  })

  it('addToBlacklist() calls POST /admin/business/risk/blacklist', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await addToBlacklist({ type: 'ip', value: '1.2.3.4' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/risk/blacklist', { type: 'ip', value: '1.2.3.4' })
  })

  it('removeFromBlacklist() calls DELETE /admin/business/risk/blacklist/:type/:value', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await removeFromBlacklist('ip', '1.2.3.4')
    expect(request.delete).toHaveBeenCalledWith('/admin/business/risk/blacklist/ip/1.2.3.4')
  })

  it('checkBlacklist() calls GET /admin/business/risk/blacklist/check', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await checkBlacklist({ type: 'ip', value: '1.2.3.4' })
    expect(request.get).toHaveBeenCalledWith('/admin/business/risk/blacklist/check', { params: { type: 'ip', value: '1.2.3.4' } })
  })

  it('getRiskRecords() calls GET /admin/business/risk/records', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getRiskRecords({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/risk/records', { params: { page: 1 } })
  })

  it('getRiskRecord() calls GET /admin/business/risk/records/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getRiskRecord(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/risk/records/1')
  })

  it('getRiskRules() calls GET /admin/business/risk/rules', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getRiskRules({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/risk/rules', { params: { page: 1 } })
  })

  it('createRiskRule() calls POST /admin/business/risk/rules', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createRiskRule({ name: 'Rule1' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/risk/rules', { name: 'Rule1' })
  })

  it('updateRiskRule() calls PUT /admin/business/risk/rules/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateRiskRule(1, { name: 'Rule2' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/risk/rules/1', { name: 'Rule2' })
  })

  it('deleteRiskRule() calls DELETE /admin/business/risk/rules/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteRiskRule(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/risk/rules/1')
  })
})

describe('admin business API - AI Vector Sync', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('triggerFullVectorSync() calls POST /admin/business/ai/vector-sync/full', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await triggerFullVectorSync()
    expect(request.post).toHaveBeenCalledWith('/admin/business/ai/vector-sync/full')
  })

  it('syncProductVector() calls POST /admin/business/ai/vector-sync/product/:id', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await syncProductVector(1)
    expect(request.post).toHaveBeenCalledWith('/admin/business/ai/vector-sync/product/1')
  })

  it('deleteProductVector() calls DELETE /admin/business/ai/vector-sync/product/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteProductVector(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/business/ai/vector-sync/product/1')
  })
})

describe('admin business API - Brand Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getBrands() calls GET /admin/business/brands', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getBrands({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/business/brands', { params: { page: 1 } })
  })

  it('getBrand() calls GET /admin/business/brands/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getBrand(1)
    expect(request.get).toHaveBeenCalledWith('/admin/business/brands/1')
  })

  it('createBrand() calls POST /admin/business/brands', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createBrand({ name: 'Apple' })
    expect(request.post).toHaveBeenCalledWith('/admin/business/brands', { name: 'Apple' })
  })

  it('updateBrand() calls PUT /admin/business/brands/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateBrand(1, { name: 'Samsung' })
    expect(request.put).toHaveBeenCalledWith('/admin/business/brands/1', { name: 'Samsung' })
  })
})

describe('admin business API - File Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('uploadFile() calls POST /file/upload with multipart headers', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    const formData = new FormData()
    formData.append('file', new Blob(['test']), 'test.png')
    await uploadFile(formData)
    expect(request.post).toHaveBeenCalledWith('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  })

  it('deleteFile() calls DELETE /file/delete with url param', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteFile('https://cdn.example.com/a.png')
    expect(request.delete).toHaveBeenCalledWith('/file/delete', { params: { url: 'https://cdn.example.com/a.png' } })
  })
})
