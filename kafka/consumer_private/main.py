import os
import time
import socket
import uuid
from datetime import datetime, timezone, timedelta
import metrics
from sqlalchemy import text
from private_kafka import create_consumer, create_producer
from db_connection import get_engine, get_all_valid_tickers, get_user_balance_from_db, get_user_stock_quantity_from_db, is_flush_required, bulk_insert_order_history, sync_user_stock_after_fill
from private_redis import create_redis_client, process_user_order

KST = timezone(timedelta(hours=9))

POD_NAME = socket.gethostname()
VALID_TICKERS = set()

def main():
    global VALID_TICKERS

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
    # 티커 set 로드
    VALID_TICKERS = get_all_valid_tickers(engine)
    print(f"✅ [인메모리 캐싱 완료] 총 {len(VALID_TICKERS)}개의 유효 티커를 메모리에 로드했습니다.")

    # redis client 생성
    redis_client = create_redis_client()
    
    # consumer, producer 생성 
    consumer = create_consumer()
    completed_topic, retry_topic, producer = create_producer()

    print("[Main] 모든 인프라 연결 완료. 주문 메시지 수신 대기 중...")

    # DB 벌크 버퍼 및 플러시 타이머 초기화
    order_buffer = []
    last_flush_time = time.time()

    def process_flush():
        """db_connection 모듈을 통해 DB 플러시 후, 성공 시 메트릭 기록 및 카프카 오프셋을 커밋합니다."""
        nonlocal last_flush_time
        if not order_buffer:
            return

        max_retries = 3
        delays = [0.5, 1.0, 2.0]

        for attempt in range(max_retries):
            # DB 벌크 적재 실행
            success, duration, count = bulk_insert_order_history(engine, order_buffer)
            
            if success:
                if count > 0:
                    metrics.DB_FLUSH_LATENCY.observe(duration)
                    metrics.DB_BATCH_SIZE.observe(count)
                
                # DB 저장 완료 직후 카프카 오프셋 커밋 및 버퍼 비우기
                consumer.commit()
                order_buffer.clear()
                last_flush_time = time.time()
                return

            print(f"⚠️ [DB 벌크 플러시 실패] 재시도 {attempt + 1}/{max_retries} ({delays[attempt]}초 대기)...")
            time.sleep(delays[attempt])

        print(f"🚨 [CRITICAL] DB Bulk Insert 3회 재시도 모두 실패! (버퍼 크기: {len(order_buffer)})")
        print(f"📦 [보상 로직 발동] 미적재 주문 {len(order_buffer)}건을 {retry_topic} 토픽으로 안전하게 격리 발행합니다.")

        for failed_order in order_buffer:
            failed_order["retry_count"] = failed_order.get("retry_count", 0) + 1
            producer.send(retry_topic, value=failed_order)

        # 💡 안전하게 토픽으로 위임 완료했으므로, 메인 오프셋을 커밋하고 버퍼를 비워 파이프라인 무한정체를 방지
        consumer.commit()
        order_buffer.clear()
        last_flush_time = time.time()
    

    # 3. 카프카 이벤트 루프 가동 (non-blocking 폴링 구조로 변경)
    try:
        while True:
            # 1초 동안 신규 주문 메시지가 없어도 타임아웃 후 다음 코드로 진행됨
            records_dict = consumer.poll(timeout_ms=1000)

            for tp, messages in records_dict.items():
                for message in messages:
                    try:
                        assigned_partition = message.partition

                        order_dto = message.value
                        order_id = order_dto.get('orderId')
                        user_id = order_dto.get('userId')
                        
                        # 🚨 [검증] orderId가 없는 비정상 메시지는 멱등성과 추적성을 위해 즉시 거절
                        if not order_id:
                            print(f"❌ [비정상 메시지] orderId가 누락되어 주문을 거절합니다: {order_dto}")
                            metrics.ORDER_PROCESS_TOTAL.labels(order_type=order_dto.get('orderType') or "UNKNOWN", status="FAILED_MISSING_ORDER_ID").inc()
                            
                            # 유저 ID가 존재할 때만 프론트엔드로 실패 알림 발행 (스피너 무한 대기 방지)
                            if user_id:
                                fail_event = {
                                    "orderId": None,
                                    "userId": user_id,
                                    "ticker": order_dto.get('ticker'),
                                    "orderType": order_dto.get('orderType'),
                                    "status": "FAILED",
                                    "message": "주문 식별자(orderId)가 누락되어 주문 처리가 거절되었습니다.",
                                    "processedPartition": assigned_partition
                                }
                                producer.send(completed_topic, value=fail_event)
                            continue

                        ticker = order_dto.get('ticker')
                        order_type = order_dto.get('orderType')
                        price = int(order_dto.get('price', 0))
                        quantity = int(order_dto.get('quantity', 0))
                        ordered_at = order_dto.get('orderedAt') or datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S")

                        now_ms = int(time.time() * 1000)
                        requested_at_ms = int(order_dto.get('requestedAt') or now_ms)
                        published_at_ms = int(order_dto.get('publishedAt') or requested_at_ms)
                        polled_at_ms = now_ms
                        kafka_lag_ms = max(0, polled_at_ms - published_at_ms)
                        
                        total_order_price = price * quantity
                        
                        start_time = time.time()
                        
                        # [WORK 0] 티커 검증 
                        if ticker not in VALID_TICKERS:
                            print(f"❌ [잘못된 티커] 존재하지 않는 종목: {ticker}")
                            metrics.ORDER_PROCESS_TOTAL.labels(order_type=order_type, status="FAILED_INVALID_TICKER").inc()
                            
                            fail_event = {
                                "orderId": order_id,
                                "userId": user_id, "ticker": ticker, "orderType": order_type,
                                "status": "FAILED", "message": "존재하지 않는 주식 종목입니다.",
                                "processedPartition": assigned_partition
                            }
                            producer.send(completed_topic, value=fail_event)
                            continue

                        # [WORK 1] Redis 루아 스크립트로 1ms 실시간 체결 연산 실행
                        res_balance = process_user_order(redis_client, user_id, ticker, total_order_price, order_type, quantity)

                        # [WORK 2] Cache Miss 발생 시 DB Fallback
                        if res_balance == -2:
                            print(f"⚠️ Cache Miss: user_id={user_id}. DB 잔고 조회 후 레디스 워밍 시도...")
                            db_balance = get_user_balance_from_db(engine, user_id)
                            db_stock_qty = get_user_stock_quantity_from_db(engine, user_id, ticker)

                            if db_balance is not None:
                                redis_client.set(f"user:balance:{user_id}", db_balance)
                                redis_client.set(f"user:stock:{user_id}:{ticker}", db_stock_qty)

                                res_balance = process_user_order(redis_client, user_id, ticker, total_order_price, order_type, quantity)
                            else:
                                print(f"❌ DB에도 존재하지 않는 회원: user_id={user_id}")
                        
                        # 실시간 체결(Redis) 소요 시간 관측
                        execution_time = time.time() - start_time
                        metrics.ORDER_EXECUTION_LATENCY.labels(order_type=order_type).observe(execution_time)
                        
                        # [WORK 3] 루아 처리 결과 상태 매핑
                        if res_balance >= 0:
                            status_str = "SUCCESS"
                            msg = f"{ticker} {quantity}주 {order_type} 체결 완료"
                        elif res_balance == -1:
                            status_str = "FAILED_BALANCE"
                            msg = "잔액이 부족합니다."
                        elif res_balance == -2:
                            status_str = "FAILED_NO_USER"
                            msg = "존재하지 않는 회원입니다."
                        elif res_balance == -4:
                            status_str = "FAILED_SELL_STOCK"
                            msg = "보유 주식이 부족합니다."
                        else:
                            status_str = "FAILED_ERROR"
                            msg = "알 수 없는 체결 오류가 발생했습니다."
                        
                        metrics.ORDER_PROCESS_TOTAL.labels(order_type=order_type, status=status_str).inc()

                        # [WORK 3.5] 체결 성공 시 user_stocks 즉시 반영 (실시간 포지션 동기화)
                        # - SSE 알림 발행보다 '먼저' 실행되어야 프론트가 알림 수신 후
                        #   /api/portfolio 재조회할 때 최신 보유분을 받을 수 있음
                        # - 본 함수 실패 시 settlement cronjob이 자정에 보정하므로 체결 처리는 계속 진행
                        if res_balance >= 0:
                            synced = sync_user_stock_after_fill(engine, user_id, ticker, price, quantity, order_type)
                            if not synced:
                                print(f"⚠️ [동기화 지연] 자정 settlement가 보정 예정 - User: {user_id}, Ticker: {ticker}")

                        # [알림 전송] 카프카 체결 완료/실패 이벤트 토픽 발행
                        completed_event = {
                            "orderId": order_id,
                            "userId": user_id,
                            "ticker": ticker,
                            "orderType": order_type,
                            "status": "SUCCESS" if res_balance >= 0 else "FAILED",
                            "message": msg,
                            "processedPartition": assigned_partition
                        }
                        producer.send(completed_topic, value=completed_event)

                        executed_at_ms = int(time.time() * 1000)
                        executed_at = datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S")
                        total_latency_ms = max(0, executed_at_ms - requested_at_ms)

                        # [WORK 4] DB 벌크 적재용 메모리 버퍼 적재
                        order_buffer.append({
                            "order_id": order_id,
                            "user_id": user_id,
                            "ticker": ticker,
                            "amount": total_order_price,
                            "price": price,
                            "quantity": quantity,
                            "order_type": order_type,
                            "status": status_str,
                            "ordered_at": ordered_at,
                            "executed_at": executed_at,
                            "kafka_lag_ms": kafka_lag_ms,
                            "total_latency_ms": total_latency_ms,
                            "retry_count": 0
                        })
                            
                    except Exception as e:
                        print(f"❌ 개별 메시지 처리 중 오류 발생 (Skipping): {e}")
                        metrics.ORDER_PROCESS_TOTAL.labels(order_type="UNKNOWN", status="FAILED_ERROR").inc()

            # [WORK 5] 배치 플러시 조건 검사 (버퍼에 데이터가 있을 때만 500개 누적 OR 5초 경과 체크)
            if order_buffer and is_flush_required(len(order_buffer), last_flush_time):
                process_flush()

    except KeyboardInterrupt:
        print("🛑 프로세스 정지 요청 감지 - 메모리 버퍼 잔여 데이터 DB 플러시 중...")
        if order_buffer:
            process_flush()
    finally:
        # 종료 시 커넥션 자원 정리
        consumer.close()
        producer.close()


if __name__ == "__main__":
    main()    