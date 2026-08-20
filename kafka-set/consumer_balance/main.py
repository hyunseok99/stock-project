import os
import time
from sqlalchemy import text
from private_kafka import create_consumer
from db_connection import get_engine, is_flush_required, bulk_insert_cash_transactions_history

def main():
    print("[Main] 입출금 시스템 가동 및 인프라 초기화 시스템 시동...")


    # 1. 인프라 레이어 셋업 (엔진 및 카프카 커넥션 확보)
    engine = get_engine()

    # db연결 대기
    while True:
        try:
            print("Connecting to MariaDB...")
            # 실제 연결을 맺고 가벼운 쿼리 하나를 던져봅니다.
            with engine.connect() as test_conn:
                test_conn.execute(text("SELECT 1"))
            print("Successfully connected to MariaDB!")
            break # 연결 성공 시 무한루프 탈출!
        except Exception as e:
            print(f"MariaDB is not available yet. Retrying in 3 seconds... (Reason: {e})")
            time.sleep(3)
   
    
    # consumer 생성 
    consumer = create_consumer()

    # DB 벌크 버퍼 및 플러시 타이머 초기화
    balance_buffer = []
    last_flush_time = time.time()

    def process_flush():
            """db_connection 모듈을 통해 DB 플러시 후, 성공 시 메트릭 기록 및 카프카 오프셋을 커밋합니다."""
            nonlocal last_flush_time
            
            # DB 벌크 적재 실행
            success, duration, count = bulk_insert_cash_transactions_history(engine, balance_buffer)
            
            if success:
                
                # DB 저장 완료 직후 카프카 오프셋 커밋 및 버퍼 비우기
                consumer.commit()
                balance_buffer.clear()
                last_flush_time = time.time()
            else:
                print("DB Bulk Insert 실패로 인해 카프카 오프셋 커밋을 보류합니다.")
    

    # 3. 카프카 이벤트 루프 가동 - non-blocking 구조 
    try:
        while True:
            # 💡 1초 동안 메시지가 들어오지 않아도 타임아웃 후 다음 코드로 진행됨
            records_dict = consumer.poll(timeout_ms=1000)

            for tp, messages in records_dict.items():
                for message in messages:
                    try:
                        balance_dto = message.value
                        event_id = balance_dto.get('eventId')
                        user_id = balance_dto.get('userId')
                        transaction_type = balance_dto.get('type') # DEPOSIT / WITHDRAWAL
                        amount = int(balance_dto.get('amount', 0))
                        created_at = balance_dto.get('timestamp')

                        # [WORK 1] DB 벌크 적재용 메모리 버퍼 적재
                        balance_buffer.append({
                            "event_id": event_id,
                            "user_id": user_id,
                            "transaction_type": transaction_type,
                            "amount": amount,
                            "created_at": created_at
                        })

                    except Exception as e:
                        print(f"❌ 개별 메시지 처리 중 오류 발생 (Skipping): {e}")

            # [WORK 2] 배치 플러시 조건 검사 (버퍼에 데이터가 있을 때만 500개 누적 OR 5초 경과 체크)
            if balance_buffer and is_flush_required(len(balance_buffer), last_flush_time):
                process_flush()
    except KeyboardInterrupt:
        print("🛑 프로세스 정지 요청 감지 - 메모리 버퍼 잔여 데이터 DB 플러시 중...")
        process_flush()
    finally:
        # 종료 시 커넥션 자원 정리
        consumer.close()

if __name__ == "__main__":
    main()    