import os
import time
import redis

# 레디스 orders 증감 연산용 루아 스크립트 글로벌 객체 생성
# 1. 매수(BUY: 차감) / 매도(SELL: 증가) 통합 루아 스크립트 (원자적 연산)
# - BUY: 잔액이 충분하면 차감(DECRBY) 후 잔액 반환 (>= 0), 부족 시 -1 반환
# - SELL: 잔액 즉시 증가(INCRBY) 후 변경된 잔액 반환 (>= 0)
LUA_PROCESS_BALANCE = """
local balance_key = KEYS[1]
local stock_key = KEYS[2]

local amount = tonumber(ARGV[1])
local order_type = ARGV[2]
local quantity = tonumber(ARGV[3])

local balance_str = redis.call('GET', balance_key)

-- 1. 현금 잔고 키가 없으면 -2 (Cache Miss)
if not balance_str then
    return -2
end

local balance = tonumber(balance_str)
local stock_str = redis.call('GET', stock_key)

-- 2. 주식 키가 없으면 보유 수량 0 
local stock_qty = stock_str and tonumber(stock_str) or 0

if order_type == "BUY" then
    if balance < amount then
        return -1  -- 잔액 부족
    end
    redis.call('DECRBY', balance_key, amount)
    redis.call('INCRBY', stock_key, quantity)
    return tonumber(redis.call('GET', balance_key))

elseif order_type == "SELL" then
    if stock_qty < quantity then
        return -4  -- 보유 주식 수량 부족
    end
    redis.call('DECRBY', stock_key, quantity)
    redis.call('INCRBY', balance_key, amount)
    return tonumber(redis.call('GET', balance_key))
else
    return -3  -- 잘못된 order_type
end
"""

# 등록된 루아 스크립트 객체 캐싱 변수
_process_script = None


# 레디스 연결 에러 핸들러
def get_env_or_raise(key):
    """환경 변수를 읽어오되, 없으면 명확한 메시지와 함께 예외를 발생시킵니다."""
    value = os.environ.get(key)
    if not value:
        raise ValueError(f"[Configuration Error] 필수 환경 변수 '{key}'가 누락")
    return value

# redis client 설정 (stock_orders 처리용)
def create_redis_client():
    redis_host = get_env_or_raise('REDIS_HOST')
    redis_port = get_env_or_raise("REDIS_PORT")
    while True:
        try:
            print(f"Connecting to redis at {redis_host}...")
            
            redis_pool = redis.ConnectionPool(
                host=redis_host,
                port=redis_port,
                db=0,
                decode_responses=True,
                max_connections=20
            )
            
            redis_client = redis.Redis(connection_pool=redis_pool)

            redis_client.ping()
            print("Successfully connected to redis")
            return redis_client
            
        except (redis.ConnectionError, redis.TimeoutError) as e:
            print(f"Redis ({redis_host}) is not available yet. Retrying in 3 seconds...")
            time.sleep(3)
        except Exception as e:
            print(f"Unexpected error while connecting to redis: {e}. Retrying in 3 seconds...")
            time.sleep(3)


def process_user_order(redis_client, user_id: str, ticker: str, amount: int, order_type: str, quantity: int):
    """
    유저 잔고를 레디스 루아 스크립트로 원자적으로 처리(증가/감소)합니다.
    
    :param redis_client: 연결된 redis 객체
    :param user_id: 대상 유저 ID
    :param amount: 주문 금액
    :param order_type: 'BUY' (매수: 차감) 또는 'SELL' (매도: 증가)
    :return: 성공 시 변경 후 남은 잔액(>= 0), 잔액 부족 시 -1, 유저 정보 없으면 -2, 잘못된 order_type 시 -3
    """
    global _process_script
    
    if _process_script is None:
        _process_script = redis_client.register_script(LUA_PROCESS_BALANCE)

    balance_key = f"user:balance:{user_id}"
    stock_key = f"user:stock:{user_id}:{ticker}"
    # order_type을 대문자로 변환하여 인자로 전달
    result = _process_script(keys=[balance_key, stock_key], args=[amount, order_type.upper(), quantity])
    return int(result)