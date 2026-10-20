-- 取消点赞 MQ 发送失败时回滚 Redis
-- KEYS[1] 布隆  KEYS[2] 用户 Hash  KEYS[3] 计数  KEYS[4] 取消集合
-- ARGV[1] postId  ARGV[2] 布隆 TTL  ARGV[3] Hash TTL  ARGV[4] 计数 TTL

local postId = ARGV[1]
local bloomTtl = tonumber(ARGV[2])
local dataTtl = tonumber(ARGV[3])
local countTtl = tonumber(ARGV[4])

redis.call('SREM', KEYS[4], postId)
redis.call('HSET', KEYS[2], postId, '1')
redis.call('EXPIRE', KEYS[2], dataTtl)
if redis.call('EXISTS', KEYS[1]) == 1 then
    redis.call('EXPIRE', KEYS[1], bloomTtl)
end
redis.call('INCR', KEYS[3])
redis.call('EXPIRE', KEYS[3], countTtl)
return 0
