-- 关注关系原子写入。
-- KEYS[1] 当前用户的关注 ZSet
-- KEYS[2] 当前用户的关注数
-- ARGV[1] 当前用户 ID
-- ARGV[2] 被关注贴吧 ID
-- ARGV[3] score：关注时间毫秒
-- ARGV[4] 关注人数上限
-- 返回：0 成功，1 已关注，3 关注人数已达上限

if redis.call('ZSCORE', KEYS[1], ARGV[2]) ~= false then
    return 1
end

local count = tonumber(redis.call('GET', KEYS[2]))
if count == nil then
    count = redis.call('ZCARD', KEYS[1])
    redis.call('SET', KEYS[2], count)
end
if count >= tonumber(ARGV[4]) then
    return 3
end

redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
redis.call('INCR', KEYS[2])
return 0
