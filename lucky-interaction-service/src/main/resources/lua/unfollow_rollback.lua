-- 取消关注 MQ 发送失败时回滚（恢复 unfollow.lua 删除的关系）
-- KEYS[1] 当前用户的关注 ZSet
-- KEYS[2] 当前用户的关注数
-- ARGV[1] 被关注贴吧 ID（member）
-- ARGV[2] 关注时间 score（毫秒）
-- 返回：0

redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])

if redis.call('GET', KEYS[2]) ~= false then
    redis.call('INCR', KEYS[2])
else
    redis.call('SET', KEYS[2], redis.call('ZCARD', KEYS[1]))
end

return 0
