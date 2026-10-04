-- 参团预筛（只读，T11）：提前拒绝明显重复/满员，不产生任何 Redis 写。
-- KEYS[1] = marketing:group:{groupOrderId}          Hash: currentNumber, targetNumber, status
-- KEYS[2] = marketing:group_users:{groupOrderId}    Set: 已参团用户ID
-- KEYS[3] = marketing:activity_users:{activityId}   Set: 活动已参团用户ID
-- ARGV[1] = userId
-- 返回 {-1,'GROUP_NOT_PENDING'} / {-2,'USER_ALREADY_IN_GROUP'} /
--       {-3,'USER_ALREADY_JOINED_ACTIVITY'} / {-4,'GROUP_FULL'} / {1,'OK'}
-- 说明：写操作（HINCRBY/SADD/状态流转）移至 DB 事务提交后的投影重建——
-- 旧实现在 DB 事实前写 Redis，回滚后残留集合会永久拒绝合法参团（T11 缺陷）。
local status = redis.call('HGET', KEYS[1], 'status')
if status ~= false and status ~= 'PENDING' then
    return {-1, 'GROUP_NOT_PENDING'}
end

local isMember = redis.call('SISMEMBER', KEYS[2], ARGV[1])
if isMember == 1 then
    return {-2, 'USER_ALREADY_IN_GROUP'}
end

local isInActivity = redis.call('SISMEMBER', KEYS[3], ARGV[1])
if isInActivity == 1 then
    return {-3, 'USER_ALREADY_JOINED_ACTIVITY'}
end

local currentNumber = tonumber(redis.call('HGET', KEYS[1], 'currentNumber') or '0')
local targetNumber = tonumber(redis.call('HGET', KEYS[1], 'targetNumber') or '999999')
if currentNumber >= targetNumber then
    return {-4, 'GROUP_FULL'}
end

return {1, 'OK'}
