-- 取消关注：从 ZSet 删除 member，关注数 -1
-- KEYS[1] 当前用户的关注 ZSet
-- KEYS[2] 当前用户的关注数
-- ARGV[1] 被关注贴吧 ID（member）
-- 返回：0 成功，1 尚未关注

if redis.call('ZSCORE', KEYS[1], ARGV[1]) == false then
    return 1
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
