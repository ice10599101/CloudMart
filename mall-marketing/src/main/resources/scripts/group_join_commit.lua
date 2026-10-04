-- 参团投影重建（只写，T11）：DB 事实提交后调用；缺失可由成员台账重建。
-- KEYS[1..3] 同上；ARGV[1]=userId ARGV[2]=activityId ARGV[3]=groupTtlSeconds
-- ARGV[4]=newStatus('PENDING'|'SUCCESS')
redis.call('HINCRBY', KEYS[1], 'currentNumber', 1)
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('SADD', KEYS[3], ARGV[1])
if ARGV[4] == 'SUCCESS' then
    redis.call('HSET', KEYS[1], 'status', 'SUCCESS')
end
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
redis.call('EXPIRE', KEYS[3], tonumber(ARGV[3]))
return 1
