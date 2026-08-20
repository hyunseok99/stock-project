import os
import time
import socket
import metrics
from sqlalchemy import text
from private_kafka import create_consumer, create_producer
from db_connection import get_engine

### =======================================
#   기존의 모든 구매 요청을 db에 write 하던 코드
### =======================================
POD_NAME = socket.gethostname()

def main():
    print(f"[{POD_NAME}] [Main] 주식 체결 가동 및 인프라 초기화 시스템 시동...")

    # 1. 프로메테우스 익스포터 셋업
    metrics.start_metrics_server(port=8000)

    # 2. 인프라 레이어 셋업 (엔진 및 카프카 커넥션 확보)
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

    
    # consumer, producer 생성 
    consumer = create_consumer()
    completed_topic, producer = create_producer()

    print("[Main] 모든 인프라 연결 완료. 주문 메시지 수신 대기 중...")

    # 3. 카프카 이벤트 루프 가동
    for message in consumer:
        assigned_partition = message.partition

        order_dto = message.value
        user_id = order_dto.get('userId')
        ticker = order_dto.get('ticker')
        price = int(order_dto.get('price', 0))
        quantity = int(order_dto.get('quantity', 0))
        
        total_order_price = price * quantity
        
        start_time = time.time()
        
        # 커넥션 풀에서 연결을 하나 빌려오고 트랜잭션 자동 시작
        with engine.begin() as conn:
            try:
                # [WORK 1] users 테이블 비관적 락 조회를 할 필요가 없음 why? 이미 user id를 partition key로 이용중
                #lock_user_sql = text("SELECT balance FROM users WHERE user_id = :user_id FOR UPDATE;")
                get_user_sql = text("SELECT balance FROM users WHERE user_id = :user_id;")
                user_result = conn.execute(get_user_sql, {"user_id": user_id}).fetchone()
                
                if not user_result:
                    print(f"❌ [주문 실패] 존재하지 않는 회원: {user_id}")
                    
                    # metric: 존재하지 않는 회원
                    duration = time.time() - start_time
                    metrics.ORDER_PROCESS_TOTAL.labels(status="FAILED_NO_USER").inc()
                    metrics.ORDER_PROCESS_DURATION.labels(status="FAILED_NO_USER").observe(duration)
                    continue

                current_balance = user_result[0]
                
                # 주식 티커 존재 여부 검증
                check_ticker_sql = text("SELECT 1 FROM livestock WHERE ticker = :ticker;")
                ticker_result = conn.execute(check_ticker_sql, {"ticker": ticker}).fetchone()

                if not ticker_result:
                    print(f"❌ [주문 실패] 존재하지 않는 종목 티커: {ticker}")

                    fail_event = {"userId": user_id, "ticker": ticker, "status": "FAILED", "message": "존재하지 않는 주식 종목입니다.", "processedPartition": assigned_partition}
                    producer.send(completed_topic, value=fail_event)

                    # metric: 존재하지 않는 종목
                    duration = time.time() - start_time
                    metrics.ORDER_PROCESS_TOTAL.labels(status="FAILED_INVALID_TICKER").inc()
                    metrics.ORDER_PROCESS_DURATION.labels(status="FAILED_INVALID_TICKER").observe(duration)
                    continue


                # 잔고 검증
                if current_balance < total_order_price:
                    print(f"❌ [주문 실패] 잔액 부족 - User: {user_id}, 현재잔액: {current_balance}")
                    
                    fail_event = {"userId": user_id, "ticker": ticker, "status": "FAILED", "message": "잔액이 부족합니다.", "processedPartition": assigned_partition}
                    producer.send(completed_topic, value=fail_event)

                    # metric: 잔액 부족
                    duration = time.time() - start_time
                    metrics.ORDER_PROCESS_TOTAL.labels(status="FAILED_BALANCE").inc()
                    metrics.ORDER_PROCESS_DURATION.labels(status="FAILED_BALANCE").observe(duration)
                    continue
                    
                # [WORK 2] users 테이블 잔고 차감
                update_balance_sql = text("UPDATE users SET balance = balance - :total_price WHERE user_id = :user_id;")
                conn.execute(update_balance_sql, {"total_price": total_order_price, "user_id": user_id})
                
                # [WORK 3] user_stocks 테이블 보유 주식 및 평단가 갱신
                select_stock_sql = text("SELECT quantity, average_price FROM user_stocks WHERE user_id = :user_id AND ticker = :ticker;")
                stock_result = conn.execute(select_stock_sql, {"user_id": user_id, "ticker": ticker}).fetchone()
                
                if stock_result:
                    old_quantity = stock_result[0]
                    old_avg_price = stock_result[1]
                    
                    new_quantity = old_quantity + quantity
                    new_avg_price = ((old_quantity * old_avg_price) + (quantity * price)) // new_quantity
                    
                    update_stock_sql = text("""
                        UPDATE user_stocks 
                        SET quantity = :quantity, average_price = :avg_price 
                        WHERE user_id = :user_id AND ticker = :ticker;
                    """)
                    conn.execute(update_stock_sql, {"quantity": new_quantity, "avg_price": new_avg_price, "user_id": user_id, "ticker": ticker})
                else:
                    insert_stock_sql = text("""
                        INSERT INTO user_stocks (user_id, ticker, quantity, average_price) 
                        VALUES (:user_id, :ticker, :quantity, :price);
                    """)
                    conn.execute(insert_stock_sql, {"user_id": user_id, "ticker": ticker, "quantity": quantity, "price": price})
                    
                # [WORK 4] order_history 테이블에 이력 적재
                insert_history_sql = text("""
                    INSERT INTO order_history (user_id, ticker, order_type, price, quantity) 
                    VALUES (:user_id, :ticker, 'BUY', :price, :quantity);
                """)
                conn.execute(insert_history_sql, {"user_id": user_id, "ticker": ticker, "price": price, "quantity": quantity})
                
                print(f"[체결 성공] User: {user_id} -> {ticker} {quantity}주 매수 완료.")
                
                # [알림 전송] broker에 체결 완료 토픽에 전달 
                success_event = {"userId": user_id, "ticker": ticker, "status": "SUCCESS", "message": f"{ticker} 주식이 체결되었습니다.", "processedPartition": assigned_partition}
                producer.send(completed_topic, value=success_event)
                
                # metric: 체결 성공
                duration = time.time() - start_time
                metrics.ORDER_PROCESS_TOTAL.labels(status="SUCCESS").inc()
                metrics.ORDER_PROCESS_DURATION.labels(status="SUCCESS").observe(duration)
            except Exception as e:
                # 블록 안에서 터지면 engine.begin()이 감지해 자동 rollback을 수행합니다.
                print(f"[트랜잭션 에러] 롤백 처리 완료: {e}")
                # metric: 시스템/DB 예외 실패
                duration = time.time() - start_time
                metrics.ORDER_PROCESS_TOTAL.labels(status="FAILED_ERROR").inc()
                metrics.ORDER_PROCESS_DURATION.labels(status="FAILED_ERROR").observe(duration)

if __name__ == "__main__":
    main()    