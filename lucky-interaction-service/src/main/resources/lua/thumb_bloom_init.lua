-- 初始化用户点赞布隆；ARGV[1]=TTL秒 ARGV[2]=误判率 ARGV[3]=容量，ARGV[4..] 为已点赞 postId

local ttl = tonumber(ARGV[1])
local errorRate = ARGV[2]
local capacity = tonumber(ARGV[3])

if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('BF.RESERVE', KEYS[1], errorRate, capacity)
end

for i = 4, #ARGV do
    redis.call('BF.ADD', KEYS[1], ARGV[i])
end

redis.call('EXPIRE', KEYS[1], ttl)
return 0
