import os

from dotenv import load_dotenv
from kafka import KafkaProducer
from kafka import KafkaConsumer
import json

###############################
# producer를 통해 가격 정보 전달 (가격 차이가 존재 한다면)
###############################


# .env파일 로드
load_dotenv("kafka.env")

# 환경 변수
broker = os.getenv('KAFKA_BROKER')
kafka_topic =os.getenv('KAFKA_TOPIC')

# 기존 가격을 저장할 dictionary
last_prices = {}

# kafka producer 생성
def create_producer():
    producer = KafkaProducer(bootstrap_servers=broker,
                             client_id='stock-producer',
                             value_serializer=lambda v: json.dumps(v).encode('utf-8'))
    return producer
# kafka producer 생성
producer = create_producer()

# kafka에서 해당하는 티커들 가장 마지막 가격 가져와 초기화
def get_latest_prices():
    consumer = KafkaConsumer(
        'stock_prices',
        bootstrap_servers=broker,
        auto_offset_reset='latest',  # 최신 데이터 가져오기
        enable_auto_commit=False,  # 자동 커밋 비활성화
        value_deserializer=lambda v: json.loads(v.decode('utf-8'))
    )

    latest_prices = {}

    # 일정 시간만 소비하고 종료
    records = consumer.poll(timeout_ms=3000, max_records=100)  # 3초 동안 최대 100개 메시지 가져옴

    if not records:  # 데이터가 없으면 빈 딕셔너리 반환
        consumer.close()
        return latest_prices

    # 가져온 데이터만 순회
    for topic_partition, messages in records.items():
        for message in messages:
            stock_data = message.value
            ticker = stock_data["ticker"]
            price = stock_data["price"]
            latest_prices[ticker] = price

    consumer.close()
    return latest_prices