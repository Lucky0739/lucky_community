-- 评论计数加减：key 存在才动它，缺 key 不创建（返回 -1，调用方回源 count(*) 重算）。
-- 裸 HINCRBY 会把缺失的 key 从 delta 建起来，历史计数直接丢，所以先 EXISTS 守卫。
-- TTL 不在这里刷新：只在创建/回填时设一次，活跃帖的 key 也会周期性过期，计数才有机会自愈。
-- KEYS[1] 计数 Hash key
-- ARGV[1] Hash field   ARGV[2] delta
-- 返回：-1 key 不存在；>=0 加减后的值（下限 0）

if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end

local v = redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
if v < 0 then
    redis.call('HSET', KEYS[1], ARGV[1], '0')
    v = 0
end
return v
