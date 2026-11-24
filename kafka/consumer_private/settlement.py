import os
import time
from datetime import datetime
from sqlalchemy import text
from db_connection import get_engine
from private_redis import create_redis_client


def run_settlement():
    print(f"[{datetime.now()}] 🌙 [Settlement] 야간 정산, 정합성 검증 및 캐시 워밍업 개시")

    engine = get_engine()
    redis_client = create_redis_client()

    start_time = time.time()

    # =========================================================================
    # 0단계: 정산 범위(Order ID / Cash ID Checkpoint) 설정
    # =========================================================================
    with engine.connect() as conn:
        # A. 이전 정산 시 저장되었던 가장 마지막 last_settled_order_id, last_settled_cash_id 조회 (없으면 0)
        checkpoint_row = conn.execute(
            text("SELECT COALESCE(MAX(last_settled_order_id), 0), COALESCE(MAX(last_settled_cash_id), 0) FROM settlement_checkpoint")
        ).fetchone()

        last_settled_order_id = checkpoint_row[0] if checkpoint_row else 0
        last_settled_cash_id = checkpoint_row[1] if checkpoint_row else 0

        # B. 현재 시점 order_history, cash_transactions_history의 가장 마지막 id 조회 (Auto Increment PK)
        current_max_order_id = conn.execute(
            text("SELECT COALESCE(MAX(id), :last_id) FROM order_history"),
            {"last_id": last_settled_order_id}
        ).scalar() or last_settled_order_id

        current_max_cash_id = conn.execute(
            text("SELECT COALESCE(MAX(id), :last_id) FROM cash_transactions_history"),
            {"last_id": last_settled_cash_id}
        ).scalar() or last_settled_cash_id

    print(f"📌 [정산 범위 설정]")
    print(f"   - order_history.id: {last_settled_order_id} 초과 ~ {current_max_order_id} 이하 (총 {current_max_order_id - last_settled_order_id}건)")
    print(f"   - cash_transactions_history.id: {last_settled_cash_id} 초과 ~ {current_max_cash_id} 이하 (총 {current_max_cash_id - last_settled_cash_id}건)")

    # 신규 이벤트(주문 또는 입출금) 존재 여부 체크
    has_new_orders = last_settled_order_id < current_max_order_id
    has_new_cashes = last_settled_cash_id < current_max_cash_id
    has_new_activity = has_new_orders or has_new_cashes

    if not has_new_activity:
        print("ℹ️ 신규 체결 및 입출금 내역이 없어 1단계 원장 정산을 스킵하고 2~3단계 정합성 검증 및 캐시 재워밍으로 진행합니다.")

    # =========================================================================
    # 1단계: 원장(주문+입출금) vs Redis 잔고 정합성 검증 & DB users 동기화
    # =========================================================================
    if has_new_activity:
        print("1️⃣ Redis 잔고 vs 통합 원장(주문+입출금) 정합성 검증(Audit) 진행 중...")

        with engine.connect() as conn:
            # A. 전체 유저의 DB 시작 잔고 조회
            db_users = conn.execute(text("SELECT user_id, balance FROM users")).fetchall()
            user_db_balance_map = {str(u.user_id): u.balance for u in db_users}

            # B-1. 체크포인트 구간 내 체결 성공(SUCCESS) 내역 유저별 매수/매도 합산
            user_order_map = {}
            if has_new_orders:
                order_history_sql = text("""
                    SELECT 
                        user_id,
                        SUM(CASE WHEN order_type = 'BUY' THEN amount ELSE 0 END) AS total_buy,
                        SUM(CASE WHEN order_type = 'SELL' THEN amount ELSE 0 END) AS total_sell
                    FROM order_history
                    WHERE status = 'SUCCESS' 
                      AND id > :last_settled_order_id 
                      AND id <= :current_max_order_id
                    GROUP BY user_id
                """)
                order_rows = conn.execute(
                    order_history_sql, 
                    {"last_settled_order_id": last_settled_order_id, "current_max_order_id": current_max_order_id}
                ).fetchall()
                
                user_order_map = {
                    str(r.user_id): {"buy": int(r.total_buy or 0), "sell": int(r.total_sell or 0)} 
                    for r in order_rows
                }

            # B-2. 체크포인트 구간 내 입출금(DEPOSIT/WITHDRAWAL) 내역 유저별 입금/출금 합산
            user_cash_map = {}
            if has_new_cashes:
                cash_history_sql = text("""
                    SELECT 
                        user_id,
                        SUM(CASE WHEN transaction_type = 'DEPOSIT' THEN amount ELSE 0 END) AS total_deposit,
                        SUM(CASE WHEN transaction_type = 'WITHDRAWAL' THEN amount ELSE 0 END) AS total_withdrawal
                    FROM cash_transactions_history
                    WHERE id > :last_settled_cash_id 
                      AND id <= :current_max_cash_id
                    GROUP BY user_id
                """)
                cash_rows = conn.execute(
                    cash_history_sql, 
                    {"last_settled_cash_id": last_settled_cash_id, "current_max_cash_id": current_max_cash_id}
                ).fetchall()

                user_cash_map = {
                    str(r.user_id): {"deposit": int(r.total_deposit or 0), "withdrawal": int(r.total_withdrawal or 0)}
                    for r in cash_rows
                }

        # C. Redis 잔고 스캔
        balance_keys = redis_client.keys("user:balance:*")
        redis_balance_map = {}
        if balance_keys:
            pipe = redis_client.pipeline()
            for key in balance_keys:
                pipe.get(key)
            balances = pipe.execute()
            for key, bal in zip(balance_keys, balances):
                if bal is not None:
                    u_id = str(key.split("user:balance:")[-1])
                    redis_balance_map[u_id] = int(bal)

        # D. 정합성 검증 및 DB 반영용 데이터 준비
        all_target_user_ids = set(user_db_balance_map.keys()) | set(redis_balance_map.keys())
        
        user_final_balance_updates = []
        mismatch_count = 0

        for u_id in all_target_user_ids:
            start_bal = user_db_balance_map.get(u_id, 0)

            # 주식 거래 합산 (order_history)
            orders = user_order_map.get(u_id, {"buy": 0, "sell": 0})
            tot_buy = orders["buy"]
            tot_sell = orders["sell"]

            # 입출금 거래 합산 (cash_transactions_history)
            cashes = user_cash_map.get(u_id, {"deposit": 0, "withdrawal": 0})
            tot_deposit = cashes["deposit"]
            tot_withdrawal = cashes["withdrawal"]
            
            # 원장 통합 기준 예상 잔고 계산
            expected_bal = start_bal - tot_buy + tot_sell + tot_deposit - tot_withdrawal
            
            # Redis 실제 잔고
            redis_bal = redis_balance_map.get(u_id, expected_bal)
            diff = redis_bal - expected_bal
            
            if diff == 0:
                final_bal = redis_bal
            else:
                mismatch_count += 1
                print(f"⚠️ [정합성 불일치 감지] user_id={u_id} | Redis={redis_bal}원 vs DB원장계산={expected_bal}원 (차액={diff}원)")
                final_bal = expected_bal

            user_final_balance_updates.append({
                "user_id": u_id,
                "balance": final_bal
            })

        # E. DB 적재 (users.balance 업데이트 및 settlement_checkpoint 추가)
        with engine.begin() as conn:
            if user_final_balance_updates:
                update_user_sql = text("UPDATE users SET balance = :balance WHERE user_id = :user_id")
                conn.execute(update_user_sql, user_final_balance_updates)

            insert_checkpoint_sql = text("""
                INSERT INTO settlement_checkpoint (last_settled_order_id, last_settled_cash_id, updated_at)
                VALUES (:last_settled_order_id, :last_settled_cash_id, NOW())
            """)
            conn.execute(insert_checkpoint_sql, {
                "last_settled_order_id": current_max_order_id,
                "last_settled_cash_id": current_max_cash_id
            })

        print(f"✅ [1단계 완료] 총 {len(user_final_balance_updates)}명 검증 완료 (정상: {len(user_final_balance_updates)-mismatch_count}명, 불일치/복구: {mismatch_count}명)")

    # =========================================================================
    # 2단계: Redis 보유주식(Source of Truth) vs DB user_stocks 정합성 검증 및 보정
    # - main.py에서 체결 시 실시간으로 user_stocks를 갱신하지만, 비동기 DB 반영 중
    #   오류나 지연으로 DB와 Redis 데이터의 정합성이 깨진 경우 Redis 기준으로 DB를 맞춤
    # =========================================================================
    print("2️⃣ Redis 보유 주식(Source of Truth) vs DB user_stocks 정합성 검증 및 보정 중...")

    # A. Redis의 보유 주식 수량 스캔 (user:stock:{user_id}:{ticker})
    stock_keys = redis_client.keys("user:stock:*")
    redis_stock_map = {}
    if stock_keys:
        pipe = redis_client.pipeline()
        for key in stock_keys:
            pipe.get(key)
        stock_qtys = pipe.execute()
        for key, qty in zip(stock_keys, stock_qtys):
            if qty is not None:
                parts = key.split(":")
                if len(parts) >= 4:
                    u_id = ":".join(parts[2:-1])
                    ticker = parts[-1]
                    redis_stock_map[(u_id, ticker)] = int(qty)

    # B. DB user_stocks 현재 보유 수량 및 평단가 조회
    db_stock_map = {}
    with engine.connect() as conn:
        existing_rows = conn.execute(
            text("SELECT user_id, ticker, quantity, average_price FROM user_stocks")
        ).fetchall()
        for r in existing_rows:
            db_stock_map[(str(r.user_id), str(r.ticker))] = (int(r.quantity), int(r.average_price))

    # C. DB vs Redis 수량 비교 및 불일치 항목 추출 (Redis를 기준으로 정합성 맞춤)
    all_stock_pairs = set(redis_stock_map.keys()) | set(db_stock_map.keys())
    mismatched_pairs = []

    for pair in all_stock_pairs:
        u_id, ticker = pair
        db_qty, db_avg = db_stock_map.get(pair, (0, 0))
        # Redis에 키가 있으면 Redis 수량 기준, 없으면(캐시 미워밍 등) DB 수량 유지
        redis_qty = redis_stock_map.get(pair, db_qty)

        if redis_qty != db_qty:
            mismatched_pairs.append((u_id, ticker, redis_qty, db_qty, db_avg))

    # D. 불일치 발생 시에만 Redis 수량 기준으로 DB user_stocks 보정
    if mismatched_pairs:
        with engine.begin() as conn:
            for u_id, ticker, redis_qty, db_qty, db_avg in mismatched_pairs:
                print(f"⚠️ [주식 정합성 불일치 감지] user_id={u_id}, ticker={ticker} | Redis={redis_qty}주 vs DB={db_qty}주 ➔ Redis 기준으로 보정")

                if redis_qty > 0:
                    avg_price = db_avg
                    # DB에 기존 평단가가 없는 경우(신규 매수 DB 반영 누락 등), 최근 매수 체결가에서 복구
                    if avg_price <= 0:
                        latest_price = conn.execute(
                            text("""
                                SELECT price FROM order_history
                                WHERE user_id = :u_id AND ticker = :ticker
                                  AND status = 'SUCCESS' AND order_type = 'BUY'
                                ORDER BY id DESC LIMIT 1
                            """),
                            {"u_id": u_id, "ticker": ticker}
                        ).scalar()
                        avg_price = int(latest_price or 0)

                    upsert_sql = text("""
                        INSERT INTO user_stocks (user_id, ticker, quantity, average_price)
                        VALUES (:u_id, :ticker, :qty, :avg_price)
                        ON DUPLICATE KEY UPDATE quantity = :qty, average_price = :avg_price
                    """)
                    conn.execute(upsert_sql, {"u_id": u_id, "ticker": ticker, "qty": redis_qty, "avg_price": avg_price})
                else:
                    delete_sql = text("DELETE FROM user_stocks WHERE user_id = :u_id AND ticker = :ticker")
                    conn.execute(delete_sql, {"u_id": u_id, "ticker": ticker})

    print(f"✅ [2단계 완료] 총 {len(all_stock_pairs)}건 검증 완료 (정상: {len(all_stock_pairs) - len(mismatched_pairs)}건, Redis 기준 보정: {len(mismatched_pairs)}건).")

    # =========================================================================
    # 3단계: DB 최신 검증 잔고 및 보유 주식 ➔ Redis 캐시 재워밍 (무조건 실행!)
    # =========================================================================
    print("3️⃣ 다음 날 장 개장을 위한 Redis 잔고 및 보유 주식 캐시 재워밍 중...")

    with engine.connect() as conn:
        all_users = conn.execute(text("SELECT user_id, balance FROM users")).fetchall()
        all_stocks = conn.execute(text("SELECT user_id, ticker, quantity FROM user_stocks WHERE quantity > 0")).fetchall()

    # [개선] 기존 '전체 DELETE 후 SET' 방식은 DELETE~SET 사이에 체결이 들어오면
    #        보유 수량이 소실될 수 있어(체결 중 경합), '덮어쓰기 후 만료키 정리'로 변경:
    #        1) DB 기준값을 먼저 덮어써서 어떤 시점에도 유효한 수량이 존재하도록 보장
    #        2) 이후 보유 수량이 0이 되었거나 더 이상 유효하지 않은 구키만 정리
    pipe = redis_client.pipeline()

    new_stock_keys = set()
    for u in all_users:
        pipe.set(f"user:balance:{u.user_id}", u.balance)

    for s in all_stocks:
        stock_key = f"user:stock:{s.user_id}:{s.ticker}"
        pipe.set(stock_key, s.quantity)
        new_stock_keys.add(stock_key)

    pipe.execute()

    old_stock_keys = redis_client.keys("user:stock:*")
    stale_keys = [k for k in old_stock_keys if k not in new_stock_keys]
    if stale_keys:
        redis_client.delete(*stale_keys)

    print(f"✅ [3단계 완료] 잔고 {len(all_users)}건, 보유 주식 {len(new_stock_keys)}건 Redis 캐시 재워밍 완료 (정리된 만료키: {len(stale_keys)}건).")

    elapsed_time = time.time() - start_time
    print(f"🎉 [Settlement 완료] 전체 정산 및 정합성 검증 소요 시간: {elapsed_time:.2f}초\n")


if __name__ == "__main__":
    run_settlement()
