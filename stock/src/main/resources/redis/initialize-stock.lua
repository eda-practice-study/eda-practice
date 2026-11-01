-- KEYS[1] = stock:{productId}
-- ARGV[1] = 초기 available 수량
-- return 1 = 생성함, 0 = 이미 있음
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end

redis.call('HSET', KEYS[1], 'available', ARGV[1], 'reserved', 0)
return 1
