-- 原子拼团参团 Lua 脚本
-- KEYS[1] = marketing:group:{groupOrderId}         -- Hash: currentNumber, targetNumber, status
-- KEYS[2] = marketing:group_users:{groupOrderId}    -- Set: 已参团用户ID
-- KEYS[3] = marketing:activity_users:{activityId}   -- Set: 活动已参团用户ID（防重复参团）
-- ARGV[1] = userId
-- ARGV[2] = activityId
-- ARGV[3] = perUserLimit (每人限参团次数，此脚本仅检查是否已参过)
-- ARGV[4] = expireSeconds

-- 1. 检查团是否还在进行中
local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'PENDING' then
    return {-1, 'GROUP_NOT_PENDING'}
end

-- 2. 检查用户是否已在此团中
local isMember = redis.call('SISMEMBER', KEYS[2], ARGV[1])
if isMember == 1 then
    return {-2, 'USER_ALREADY_IN_GROUP'}
end

-- 3. 检查用户是否已参加过此活动
local isInActivity = redis.call('SISMEMBER', KEYS[3], ARGV[1])
if isInActivity == 1 then
    return {-3, 'USER_ALREADY_JOINED_ACTIVITY'}
end

-- 4. 获取当前人数和目标人数
local currentNumber = tonumber(redis.call('HGET', KEYS[1], 'currentNumber'))
local targetNumber = tonumber(redis.call('HGET', KEYS[1], 'targetNumber'))

if currentNumber >= targetNumber then
    return {-4, 'GROUP_FULL'}
end

-- 5. 原子操作：增加人数 + 加入用户集合
redis.call('HINCRBY', KEYS[1], 'currentNumber', 1)
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('SADD', KEYS[3], ARGV[1])

-- 6. 判断是否成团
local newNumber = currentNumber + 1
if newNumber >= targetNumber then
    redis.call('HSET', KEYS[1], 'status', 'SUCCESS')
    return {1, 'GROUP_SUCCESS'}
else
    return {0, 'JOINED'}
end
