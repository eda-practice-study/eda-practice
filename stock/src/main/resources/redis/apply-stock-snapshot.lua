local stored = redis.call('GET', KEYS[1])
local version = ARGV[1]
if stored then
    local current = string.match(stored, '^%d+')
    if #version < #current or (#version == #current and version <= current) then
        return 0
    end
end
redis.call('SET', KEYS[1], version .. ':' .. ARGV[2], 'EX', ARGV[3])
return 1
