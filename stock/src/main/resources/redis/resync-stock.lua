-- KEYS[1] = stock:{productId}
-- ARGV[1] = DB 실재고
-- return 1 = available 을 DB 값으로 맞춤 (키가 없으면 새로 만듦), 0 = 진행 중인 예약이 있어 보류
local reserved = tonumber(redis.call('HGET', KEYS[1], 'reserved') or '0')
if reserved ~= 0 then
  return 0
end

redis.call('HSET', KEYS[1], 'available', ARGV[1], 'reserved', 0)
return 1
