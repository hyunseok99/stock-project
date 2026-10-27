import os
import time
from datetime import datetime
from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError
from metrics import USER_STOCK_SYNC_TOTAL, USER_STOCK_SYNC_LATENCY

# Batch 
BATCH_SIZE = 500
BATCH_TIMEOUT = 5.0

# db 연결 에러 핸들러
def get_env_or_raise(key):
    """환경 변수를 읽어오되, 없으면 명확한 메시지와 함께 예외를 발생시킵니다."""
    value = os.environ.get(key)
    if not value:
        raise ValueError(f"[Configuration Error] 필수 환경 변수 '{key}'가 누락")
    return value

# 어제까지의 주식 정보와 지수 정보 업데이트
def get_engine():

    DB_HOST = get_env_or_raise("DB_HOST")
    DB_PORT = get_env_or_raise("DB_PORT")
    DB_USER = get_env_or_raise("DB_USER")
    DB_PASSWORD = get_env_or_raise("DB_PASSWORD")
    DB_NAME = get_env_or_raise("DB_NAME")
    
    MARIADB_URL = f"mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}?charset=utf8mb4"
   
    engine = create_engine(
        MARIADB_URL, 
        connect_args={"init_command": "SET time_zone='+09:00'"},
        pool_size=10, 
        max_overflow=20, 
        pool_recycle=3600
    )
    return engine

def get_all_valid_tickers(engine) -> set:
    """[앱 시작 시 1회 실행] DB(livestock)에서 유효한 모든 주식 티커 목록을 가져옵니다."""
    sql = text("SELECT DISTINCT ticker FROM livestock")
    try:
        with engine.connect() as conn:
            results = conn.execute(sql).fetchall()
            # set 자료구조로 변환하여 O(1) 탐색 보장
            return {row[0] for row in results}
    except Exception as e:
        print(f"❌ [DB Error] 전체 티커 목록 로드 실패: {e}")
        return set()

def get_user_balance_from_db(engine, user_id: str):
    """
    [Cache Miss 대비] MariaDB에서 유저의 최신 잔고를 조회합니다.
    
    :param engine: SQLAlchemy Engine 객체
    :param user_id: 조회할 유저 ID
    :return: 잔고 금액(int/float) 또는 유저가 없을 경우 None
    """
    sql = text("SELECT balance FROM users WHERE user_id = :user_id")
    try:
        with engine.connect() as conn:
            result = conn.execute(sql, {"user_id": str(user_id)}).fetchone()
            return result[0] if result else None
    except SQLAlchemyError as e:
        print(f"[DB Error] 유저 잔고 조회 실패 (user_id={user_id}): {e}")
        return None

def get_user_stock_quantity_from_db(engine, user_id: str, ticker: str) -> int:
    """[Cache Miss 대비] MariaDB에서 유저의 특정 종목 보유 수량을 조회합니다."""
    sql = text("SELECT quantity FROM user_stocks WHERE user_id = :user_id AND ticker = :ticker")
    try:
        with engine.connect() as conn:
            result = conn.execute(sql, {"user_id": str(user_id), "ticker": ticker}).fetchone()
            return result[0] if result else 0
    except SQLAlchemyError as e:
        print(f"❌ [DB Error] 유저 주식 수량 조회 실패 (user_id={user_id}, ticker={ticker}): {e}")
        return 0

def is_flush_required(buffer_size: int, last_flush_time: float) -> bool:
    """배치 플러시 조건(500개 누적 OR 5초 경과)을 충족했는지 검사합니다."""
    return buffer_size >= BATCH_SIZE or (time.time() - last_flush_time) >= BATCH_TIMEOUT

def sync_user_stock_after_fill(engine, user_id: str, ticker: str, price: int, quantity: int, order_type: str) -> bool:
    """
    [실시간 포지션 반영] 주문 체결 성공 직후 user_stocks 테이블을 즉시 갱신합니다.
    
    - BUY : 신규 INSERT 또는 기존 row에 수량 추가 + 가중평균 평단 재계산
            (ON DUPLICATE KEY UPDATE 절 내 평단 계산 시 '기존 quantity'가 필요하므로
             average_price를 먼저 계산한 뒤 quantity를 증가시키는 순서)
    - SELL: 수량 차감, 전량 매도(quantity <= 0) 시 해당 row 삭제
    
    Redis는 이미 반영된 상태이며, 본 함수 실패 시 settlement cronjob이 자정에 보정합니다.
    
    :return: 성공 여부(bool)
    """
    success = False
    sync_start = time.time()
    try:
        with engine.begin() as conn:
            if order_type == "BUY":
                sql = text("""
                    INSERT INTO user_stocks (user_id, ticker, quantity, average_price)
                    VALUES (:user_id, :ticker, :quantity, :price)
                    ON DUPLICATE KEY UPDATE
                        average_price = ROUND((average_price * quantity + :price * VALUES(quantity))
                                              / (quantity + VALUES(quantity))),
                        quantity = quantity + VALUES(quantity)
                """)
                conn.execute(sql, {
                    "user_id": str(user_id),
                    "ticker": ticker,
                    "quantity": quantity,
                    "price": price,
                })
            else:  # SELL
                decrease_sql = text("""
                    UPDATE user_stocks
                    SET quantity = GREATEST(0, quantity - :quantity)
                    WHERE user_id = :user_id AND ticker = :ticker AND quantity > 0
                """)
                result = conn.execute(decrease_sql, {
                    "user_id": str(user_id),
                    "ticker": ticker,
                    "quantity": quantity,
                })

                # 차감 대상 row가 존재했을 때만 전량매도(수량 0) 삭제 검사 → no-op DELETE 방지
                if result.rowcount > 0:
                    delete_sql = text("""
                        DELETE FROM user_stocks
                        WHERE user_id = :user_id AND ticker = :ticker AND quantity <= 0
                    """)
                    conn.execute(delete_sql, {
                        "user_id": str(user_id),
                        "ticker": ticker,
                    })
        success = True
        return True

    except SQLAlchemyError as e:
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{now_str}] ❌ user_stocks 실시간 동기화 실패 (user_id={user_id}, ticker={ticker}, {order_type}): {e}")
        return False

    finally:
        elapsed = time.time() - sync_start
        USER_STOCK_SYNC_LATENCY.labels(order_type=order_type).observe(elapsed)
        USER_STOCK_SYNC_TOTAL.labels(order_type=order_type,
                                     result="SUCCESS" if success else "FAILED").inc()

def bulk_insert_order_history(engine, records: list):
    """
    메모리 버퍼에 모인 주문 이력을 MariaDB order_history 테이블에 벌크로 적재합니다.
    
    :param engine: SQLAlchemy Engine 객체
    :param records: [{ "user_id": ..., "ticker": ..., "amount": ..., "price": ..., "quantity": ..., "order_type": ..., "status": ... }, ...]
    :return: (성공 여부: bool, 소요 시간(초): float, 적재 건수: int)
    """
    if not records:
        return True, 0.0, 0

    batch_count = len(records)
    flush_start = time.time();

    for r in records:
        if not r.get("ordered_at"):
            r["ordered_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        if not r.get("executed_at"):
            r["executed_at"] = r["ordered_at"]
        if "kafka_lag_ms" not in r:
            r["kafka_lag_ms"] = 0
        if "total_latency_ms" not in r:
            r["total_latency_ms"] = 0
        if "retry_count" not in r:
            r["retry_count"] = 0

    sql = text("""
        INSERT IGNORE INTO order_history (
            order_id, user_id, ticker, amount, price, quantity, order_type, status,
            ordered_at, executed_at, kafka_lag_ms, total_latency_ms, retry_count
        )
        VALUES (
            :order_id, :user_id, :ticker, :amount, :price, :quantity, :order_type, :status,
            :ordered_at, :executed_at, :kafka_lag_ms, :total_latency_ms, :retry_count
        )
    """)

    try:
        # engine.begin()을 쓰면 블록 탈출 시 자동 커밋, 예외 발생 시 자동 롤백됩니다.
        with engine.begin() as conn:
            conn.execute(sql, records)

        flush_duration = time.time() - flush_start  
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        
        print(f"[{now_str}] ✅ DB Bulk INSERT 성공: 총 {len(records)}건 적재 완료 (소요시간: {flush_duration:.4f}초 / {flush_duration * 1000:.2f}ms)")
        return True, flush_duration, batch_count

    except SQLAlchemyError as e:
        flush_duration = time.time() - flush_start
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        
        print(f"[{now_str}] ❌ DB Bulk INSERT 실패 (자동 롤백됨, 소요시간: {flush_duration:.4f}초): {e}")
        return False, flush_duration, batch_count