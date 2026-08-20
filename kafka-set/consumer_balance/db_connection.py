import os
import time
from datetime import datetime
from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError

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
        pool_size=10, 
        max_overflow=20, 
        pool_recycle=3600
    )
    return engine;

def is_flush_required(buffer_size: int, last_flush_time: float) -> bool:
    """배치 플러시 조건(500개 누적 OR 5초 경과)을 충족했는지 검사합니다."""
    return buffer_size >= BATCH_SIZE or (time.time() - last_flush_time) >= BATCH_TIMEOUT

def bulk_insert_cash_transactions_history(engine, records: list):
    """
    메모리 버퍼에 모인 입출금 이력을 MariaDB cash_transactions_history 테이블에 벌크로 적재합니다.
    
    :param engine: SQLAlchemy Engine 객체
    :param records: [{ "event_id": ..., "user_id": ..., "transaction_type": ..., "amount": ... }]
    :return: (성공 여부: bool, 소요 시간(초): float, 적재 건수: int)
    """
    if not records:
        return True, 0.0, 0

    batch_count = len(records)
    flush_start = time.time();

    sql = text("""
    INSERT INTO cash_transactions_history (event_id, user_id, transaction_type, amount, created_at)
    VALUES (:event_id, :user_id, :transaction_type, :amount, :created_at)
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