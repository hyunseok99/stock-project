import time
from prometheus_client import start_http_server, Counter, Histogram

# 1. 체결 요청 카운터 (매수/매도 및 처리 상태 구분)
ORDER_PROCESS_TOTAL = Counter(
    'order_process_requests_total',
    'Total number of processed stock orders',
    ['order_type', 'status']  # order_type: BUY, SELL / status: SUCCESS, FAILED_INSUFFICIENT_FUNDS, FAILED_SELL_STOCK ... 
)

# 2. 실시간 레디스 체결 연산 소요 시간 (초 단위 - 1ms~500ms 구간 세분화)
ORDER_EXECUTION_LATENCY = Histogram(
    'order_execution_latency_seconds',
    'Latency of real-time order execution via Redis Lua script',
    ['order_type'],
    buckets=[0.001, 0.005, 0.01, 0.05, 0.1, 0.5]
)

# 3. MariaDB 배치 적재(Bulk Insert) 소요 시간 (초 단위)
DB_FLUSH_LATENCY = Histogram(
    'db_bulk_flush_latency_seconds',
    'Time spent flushing batch orders into MariaDB',
    buckets=[0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0]
)

# 4. DB 1회 배치 적재 레코드 개수
DB_BATCH_SIZE = Histogram(
    'db_bulk_batch_size',
    'Number of orders in a single DB bulk insert batch',
    buckets=[1, 10, 50, 100, 250, 500]
)

# 6. 체결 직후 user_stocks 실시간 동기화 카운터 (성공/실패)
USER_STOCK_SYNC_TOTAL = Counter(
    'user_stock_sync_total',
    'Total number of real-time user_stocks upsert attempts after fill',
    ['order_type', 'result']  # order_type: BUY, SELL / result: SUCCESS, FAILED
)

# 7. user_stocks 실시간 동기화 소요 시간
USER_STOCK_SYNC_LATENCY = Histogram(
    'user_stock_sync_latency_seconds',
    'Latency of real-time user_stocks upsert after fill',
    ['order_type'],
    buckets=[0.001, 0.005, 0.01, 0.05, 0.1]
)

# 5. 프로메테우스 익스포터 서버 시작 & 라벨 선초기화 함수
def start_metrics_server(port=8000):
    start_http_server(port)
    print(f"[Metrics] Prometheus Exporter running on port {port}")

    # 그라파나 'No Data' 방지: 주요 라벨 타임시리즈를 초기값 0으로 생성
    order_types = ['BUY', 'SELL']
    statuses = ['SUCCESS', 'FAILED_BALANCE', 'FAILED_NO_USER', 'FAILED_INVALID_TICKER', 'FAILED_SELL_STOCK' , 'FAILED_ERROR']

    for o_type in order_types:
        for status in statuses:
            ORDER_PROCESS_TOTAL.labels(order_type=o_type, status=status).inc(0)

