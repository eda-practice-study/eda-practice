-- TCC Confirm: Stock DB 차감이 커밋된 뒤, 예약한 수량을 reserved 에서 뺀다 (available 은 Try 때 이미 줄었음)
-- KEYS[1] = reservation:{reservationId}, KEYS[2] = reservations
-- ARGV[1] = reservationId, ARGV[2] = 확정 · 취소된 예약 기록을 남겨 둘 시간 (초)
-- return  1 = 확정함, 0 = 이미 확정함
--        -1 = 예약이 없거나 이미 취소됨 (만료 취소 뒤에 주문 이벤트가 늦게 온 경우)
-- stock:{productId} 키는 예약 기록에서 만든다 (단일 노드 전제 — 클러스터라면 KEYS 로 넘겨야 한다)
local status = redis.call('HGET', KEYS[1], 'status')
if status == 'CONFIRMED' then
  return 0
end
if status ~= 'RESERVED' then
  return -1
end

local fields = redis.call('HGETALL', KEYS[1])
for i = 1, #fields, 2 do
  if fields[i] ~= 'status' then
    redis.call('HINCRBY', 'stock:' .. fields[i], 'reserved', -tonumber(fields[i + 1]))
  end
end

-- 기록은 남겨 둬서, 늦게 도착한 같은 ID 의 Try 가 다시 차감하지 못하게 한다
redis.call('HSET', KEYS[1], 'status', 'CONFIRMED')
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('ZREM', KEYS[2], ARGV[1])

return 1
