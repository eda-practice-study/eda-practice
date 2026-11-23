-- TCC Try: 가용 재고를 예약한다 (available → reserved)
-- KEYS[1]   = reservation:{reservationId}  (Hash: status + productId → 예약 수량)
-- KEYS[2]   = reservations                 (ZSet: 진행 중인 예약, score = 예약 시각 ms)
-- KEYS[2+i] = stock:{productId}
-- ARGV[1] = reservationId, ARGV[2] = 예약 시각 ms
-- ARGV[1+2i] = i 번째 productId, ARGV[2+2i] = i 번째 수량
-- return  0 = 예약함 (이미 예약 · 확정된 ID 여도 0 — 재시도해도 두 번 차감하지 않는다)
--        -1 = 이미 취소된 ID 라 거부함 (Cancel 뒤에 늦게 도착한 Try 를 막는다)
--         i (> 0) = i 번째 상품의 재고가 없거나 부족해 아무것도 바꾸지 않음
local status = redis.call('HGET', KEYS[1], 'status')
if status == 'CANCELLED' then
  return -1
end
if status then
  return 0
end

local count = #KEYS - 2

-- 1단계: 검증 — 하나라도 부족하면 아무것도 바꾸지 않는다
for i = 1, count do
  local available = tonumber(redis.call('HGET', KEYS[2 + i], 'available'))
  if available == nil or available < tonumber(ARGV[2 + 2 * i]) then
    return i
  end
end

-- 2단계: available → reserved 로 옮기고, 누구의 예약인지 기록한다
redis.call('HSET', KEYS[1], 'status', 'RESERVED')
for i = 1, count do
  local quantity = tonumber(ARGV[2 + 2 * i])
  redis.call('HINCRBY', KEYS[2 + i], 'available', -quantity)
  redis.call('HINCRBY', KEYS[2 + i], 'reserved', quantity)
  redis.call('HSET', KEYS[1], ARGV[1 + 2 * i], quantity)
end
redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])

return 0
