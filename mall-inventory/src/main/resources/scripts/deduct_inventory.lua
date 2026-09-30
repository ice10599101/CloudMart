-- CloudMart 库存原子预扣减 Lua 脚本
-- KEYS[1]: 库存 Key (inventory:product:{skuId}) — String 类型，存储可用库存数量
-- ARGV[1]: 扣减数量
--
-- 返回值:
--   0 = 库存不足
--   1 = 预扣减成功
--   2 = Key 不存在（缓存未预热/已过期）——调用方应回源 DB 后重试，
--       不能与"库存不足"混为一谈，否则 Redis 失效期间的首次下单会被误判为售罄

local key = KEYS[1]
local quantity = tonumber(ARGV[1])

if quantity == nil or quantity <= 0 then
    return 0
end

local stock = tonumber(redis.call('GET', key))
if stock == nil then
    return 2
end

if stock < quantity then
    return 0
end

redis.call('DECRBY', key, quantity)
return 1
