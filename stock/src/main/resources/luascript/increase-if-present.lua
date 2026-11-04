if redis.call('EXISTS', KEYS[1]) == 0 then
	return nil
end
return redis.call('INCRBY', KEYS[1], ARGV[1])