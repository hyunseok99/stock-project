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
    # 0단계: 정산 범위(Order ID Checkpoint) 설정
    # =========================================================================
    with engine.connect() as conn:
        # A. 이전 정산 시 저장되었던 가장 마지막 last_settled_order_id, last_settled_cash_id 조회 (없으면 0)
        checkpoint_row = conn.execute(
            text("SELECT COALESCE(MAX(last_settled_order_id), 0), COALESCE(MAX(last_settled_cash_id), 0) FROM settlement_checkpoint")
        ).fetchone()

        last_settled_order_id = checkpoint_row[0] if checkpoint_row else 0
        last_settled_cash_id = checkpoint_row[1] if checkpoint_row else 0

        # B. 현재 시점 order_history, cash_Transaction_history의 가장 마지막 id 조회 (Auto Increment PK)
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

    if not has_new_orders:
        print("ℹ️ 신규 체결 및 입출금 내역이 없어 1~2단계 정산을 스킵하고 3단계 캐시 재워밍으로 진행합니다.")

    # =========================================================================
    # 1단계: 원장(주문+입출금) vs Redis 잔고 정합성 검증 & DB users 동기화
    # =========================================================================
    if has_new_orders:
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

        # E. DB 적재 (users.balance 업데이트 및 settlement_checkpoint 2건 추가)
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
    # 2단계: 체크포인트 구간 order_history 기반 user_stocks (보유주식/평단가) 정산
    # =========================================================================
    if has_new_orders:
        print("2️⃣ 체크포인트 범위 체결 내역 기반 보유 주식 및 평단가 정산 중...")

        fetch_orders_sql = text("""
            SELECT user_id, ticker, order_type, price, quantity
            FROM order_history
            WHERE status = 'SUCCESS' 
              AND id > :last_settled_id 
              AND id <= :current_max_id
            ORDER BY id ASC
        """)

        with engine.connect() as conn:
            today_orders = conn.execute(
                fetch_orders_sql, 
                {"last_settled_id": last_settled_id, "current_max_id": current_max_id}
            ).fetchall()

        user_stock_map = {}
        for row in today_orders:
            key = (str(row.user_id), str(row.ticker))
            if key not in user_stock_map:
                user_stock_map[key] = []
            user_stock_map[key].append(row)

        # N+1 최적화: 기존 주식 잔고 1회 미리 조회
        existing_stocks_map = {}
        if user_stock_map:
            with engine.connect() as conn:
                existing_rows = conn.execute(
                    text("SELECT user_id, ticker, quantity, average_price FROM user_stocks")
                ).fetchall()
                for r in existing_rows:
                    existing_stocks_map[(str(r.user_id), str(r.ticker))] = (r.quantity, r.average_price)

        processed_stock_count = 0

        with engine.begin() as conn:
            for (u_id, ticker), orders in user_stock_map.items():
                existing_stock = existing_stocks_map.get((u_id, ticker), (0, 0))
                cur_qty, cur_avg = existing_stock[0], existing_stock[1]

                for order in orders:
                    o_type, price, qty = order.order_type, order.price, order.quantity
                    if o_type == 'BUY':
                        new_qty = cur_qty + qty
                        cur_avg = ((cur_qty * cur_avg) + (qty * price)) // new_qty
                        cur_qty = new_qty
                    elif o_type == 'SELL':
                        cur_qty = max(0, cur_qty - qty)

                if cur_qty > 0:
                    upsert_sql = text("""
                        INSERT INTO user_stocks (user_id, ticker, quantity, average_price)
                        VALUES (:u_id, :ticker, :qty, :avg_price)
                        ON DUPLICATE KEY UPDATE quantity = :qty, average_price = :avg_price
                    """)
                    conn.execute(upsert_sql, {"u_id": u_id, "ticker": ticker, "qty": cur_qty, "avg_price": cur_avg})
                else:
                    delete_sql = text("DELETE FROM user_stocks WHERE user_id = :u_id AND ticker = :ticker")
                    conn.execute(delete_sql, {"u_id": u_id, "ticker": ticker})

                processed_stock_count += 1

        print(f"✅ [2단계 완료] 총 {processed_stock_count}건의 유저 주식 보유 상태 정산 완료.")

    # =========================================================================
    # 3단계: DB 최신 검증 잔고 및 보유 주식 ➔ Redis 캐시 재워밍 (무조건 실행!)
    # =========================================================================
    print("3️⃣ 다음 날 장 개장을 위한 Redis 잔고 및 보유 주식 캐시 재워밍 중...")

    with engine.connect() as conn:
        all_users = conn.execute(text("SELECT user_id, balance FROM users")).fetchall()
        all_stocks = conn.execute(text("SELECT user_id, ticker, quantity FROM user_stocks WHERE quantity > 0")).fetchall()

    old_stock_keys = redis_client.keys("user:stock:*")
    if old_stock_keys:
        redis_client.delete(*old_stock_keys)

    pipe = redis_client.pipeline()

    for u in all_users:
        pipe.set(f"user:balance:{u.user_id}", u.balance)

    for s in all_stocks:
        pipe.set(f"user:stock:{s.user_id}:{s.ticker}", s.quantity)

    pipe.execute()

    print(f"✅ [3단계 완료] 잔고 {len(all_users)}건, 보유 주식 {len(all_stocks)}건 Redis 캐시 재워밍 완료.")

    elapsed_time = time.time() - start_time
    print(f"🎉 [Settlement 완료] 전체 정산 및 정합성 검증 소요 시간: {elapsed_time:.2f}초\n")


if __name__ == "__main__":
    run_settlement()