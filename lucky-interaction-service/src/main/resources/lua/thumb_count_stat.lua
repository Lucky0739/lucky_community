-- 点赞计数变更统计：脏帖集合 + 全局事件计数
-- KEYS[1] 待刷库 postId 集合  KEYS[2] 全局变更事件计数
-- ARGV[1] postId  ARGV[2] 阈值（如 500）
-- 返回：1 达到阈值应立即刷库，0 否

redis.call('SADD', KEYS[1], ARGV[1])
local n = redis.call('INCR', KEYS[2])
if n >= tonumber(ARGV[2]) then
    redis.call('SET', KEYS[2], '0')
    return 1
end
return 0
