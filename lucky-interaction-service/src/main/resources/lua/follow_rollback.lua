-- 关注 MQ 发送失败时回滚 Redis（撤销 follow.lua 的 ZADD + INCR）
-- KEYS[1] 当前用户的关注 ZSet
-- KEYS[2] 当前用户的关注数
-- ARGV[1] 被关注贴吧 ID（member）
-- 返回：0 成功或已是未关注状态（幂等）

if redis.call('ZSCORE', KEYS[1], ARGV[1]) == false then
    return 0
end

redis.call('ZREM', KEYS[1], ARGV[1])

local count = redis.call('GET', KEYS[2])
if count ~= false then
    count = tonumber(count)
    if count > 0 then
        redis.call('DECR', KEYS[2])
    end
else
    redis.call('SET', KEYS[2], redis.call('ZCARD', KEYS[1]))
end

return 0
