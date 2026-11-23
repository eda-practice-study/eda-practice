-- TCC Cancel: 예약한 수량만 reserved → available 로 되돌린다
-- KEYS[1] = reservation:{reservationId}, KEYS[2] = reservations
-- ARGV[1] = reservationId, ARGV[2] = 확정 · 취소된 예약 기록을 남겨 둘 시간 (초)
-- return  1 = 예약을 취소함
--         0 = 되돌릴 예약이 없음 (이미 취소됐거나, Try 가 아직 도착하지 않음 — 이때는 취소 표식만 남긴다)
--        -1 = 이미 확정돼 취소할 수 없음
-- 예약 기록에 있는 수량만 되돌리므로 다른 주문의 reserved 를 건드리지 않는다
-- stock:{productId} 키는 예약 기록에서 만든다 (단일 노드 전제 — 클러스터라면 KEYS 로 넘겨야 한다)
local status = redis.call('HGET', KEYS[1], 'status')
if status == 'CONFIRMED' then
  return -1
end
if status == 'CANCELLED' then
  return 0
end

if status == false then
  -- 빈 롤백: Try 보다 Cancel 이 먼저 왔다. 표식을 남겨 늦게 오는 Try 를 거부하게 한다
  redis.call('HSET', KEYS[1], 'status', 'CANCELLED')
  redis.call('EXPIRE', KEYS[1], ARGV[2])
  redis.call('ZREM', KEYS[2], ARGV[1])
  return 0
end

local fields = redis.call('HGETALL', KEYS[1])
for i = 1, #fields, 2 do
  if fields[i] ~= 'status' then
    local quantity = tonumber(fields[i + 1])
    redis.call('HINCRBY', 'stock:' .. fields[i], 'reserved', -quantity)
    redis.call('HINCRBY', 'stock:' .. fields[i], 'available', quantity)
  end
end

redis.call('HSET', KEYS[1], 'status', 'CANCELLED')
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('ZREM', KEYS[2], ARGV[1])

return 1
