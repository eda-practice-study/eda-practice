-- KEYS[1] = stock:{productId}
-- ARGV[1] = 늘릴 수량
-- return 늘린 뒤 available, 키가 없으면 nil (키는 만들지 않는다)
if redis.call('EXISTS', KEYS[1]) == 0 then
  return nil
end

return redis.call('HINCRBY', KEYS[1], 'available', ARGV[1])
