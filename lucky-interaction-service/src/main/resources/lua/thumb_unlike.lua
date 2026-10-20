-- 取消点赞
-- KEYS[1] 布隆  KEYS[2] 用户 Hash  KEYS[3] 计数  KEYS[4] 取消集合
-- ARGV[1] postId  ARGV[2] 布隆 TTL  ARGV[3] Hash TTL  ARGV[4] 计数 TTL  ARGV[5] 是否已由 DB 确认（1/0）
-- 返回：-1 布隆未初始化，0 成功，1 未点赞，2 需查库，3 冲突（查库期间又点了赞）

if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end

local postId = ARGV[1]
local bloomTtl = tonumber(ARGV[2])
local dataTtl = tonumber(ARGV[3])
local countTtl = tonumber(ARGV[4])
local dbConfirmed = ARGV[5] == '1'

if redis.call('SISMEMBER', KEYS[4], postId) == 1 then
    return 1
end

local hashHit = redis.call('HEXISTS', KEYS[2], postId) == 1
local bloomHit = redis.call('BF.EXISTS', KEYS[1], postId) == 1

if dbConfirmed then
    if hashHit then
        return 3
    end
    if not bloomHit then
        return 1
    end
else
    if hashHit then
        redis.call('HDEL', KEYS[2], postId)
    elseif bloomHit then
        return 2
    else
        return 1
    end
end

redis.call('SADD', KEYS[4], postId)
redis.call('EXPIRE', KEYS[4], dataTtl)
redis.call('EXPIRE', KEYS[1], bloomTtl)

local count = tonumber(redis.call('GET', KEYS[3]))
if count == nil then
    count = 0
end
if count > 0 then
    redis.call('DECR', KEYS[3])
end
redis.call('EXPIRE', KEYS[3], countTtl)
return 0
