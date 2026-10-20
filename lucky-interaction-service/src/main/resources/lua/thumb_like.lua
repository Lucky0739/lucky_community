-- 点赞：布隆 + 用户 Hash + 帖子计数 + 取消集合（布隆位不可删）
-- KEYS[1] 用户点赞布隆  KEYS[2] 用户点赞 Hash  KEYS[3] 帖子点赞计数  KEYS[4] 取消点赞集合
-- ARGV[1] postId  ARGV[2] 布隆 TTL  ARGV[3] Hash TTL  ARGV[4] 计数 TTL
-- 返回：-1 布隆未初始化，0 成功，1 已点赞

if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end

local postId = ARGV[1]
local bloomTtl = tonumber(ARGV[2])
local dataTtl = tonumber(ARGV[3])
local countTtl = tonumber(ARGV[4])

if redis.call('SISMEMBER', KEYS[4], postId) == 0 then
    if redis.call('BF.EXISTS', KEYS[1], postId) == 1 and redis.call('HEXISTS', KEYS[2], postId) == 1 then
        return 1
    end
else
    redis.call('SREM', KEYS[4], postId)
end

redis.call('BF.ADD', KEYS[1], postId)
redis.call('HSET', KEYS[2], postId, '1')
redis.call('EXPIRE', KEYS[2], dataTtl)
redis.call('EXPIRE', KEYS[1], bloomTtl)
redis.call('EXPIRE', KEYS[4], dataTtl)

redis.call('INCR', KEYS[3])
redis.call('EXPIRE', KEYS[3], countTtl)
return 0
