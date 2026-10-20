-- 取出并清空待刷库的 postId 列表
-- KEYS[1] 待刷库集合

local members = redis.call('SMEMBERS', KEYS[1])
if #members > 0 then
    redis.call('DEL', KEYS[1])
end
return members
