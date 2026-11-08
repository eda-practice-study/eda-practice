if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('HSET', KEYS[1], 'quantity', '0', 'version', '0')
    return 1
end
return 0
