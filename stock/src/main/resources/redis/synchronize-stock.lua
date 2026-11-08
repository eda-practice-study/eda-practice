local kind = redis.call('TYPE', KEYS[1]).ok
if kind == 'hash' then
    local currentVersion = redis.call('HGET', KEYS[1], 'version')
    local incomingVersion = ARGV[2]
    -- Compare nonnegative decimal strings without Lua floating-point rounding.
    if currentVersion and (#currentVersion > #incomingVersion
            or (#currentVersion == #incomingVersion and currentVersion > incomingVersion)) then
        return nil
    end
elseif kind ~= 'none' and kind ~= 'string' then
    return redis.error_reply('Unexpected stock key type: ' .. kind)
end

if kind == 'string' then
    redis.call('DEL', KEYS[1])
end
redis.call('HSET', KEYS[1], 'quantity', ARGV[1], 'version', ARGV[2])
return tonumber(ARGV[1])
