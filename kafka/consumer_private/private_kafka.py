import os
import time
import json
from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable

# 카프카 연결 에러 핸들러
def get_env_or_raise(key):
    """환경 변수를 읽어오되, 없으면 명확한 메시지와 함께 예외를 발생시킵니다."""
    value = os.environ.get(key)
    if not value:
        raise ValueError(f"[Configuration Error] 필수 환경 변수 '{key}'가 누락")
    return value

# Kafka Consumer 설정 (orders 처리용)
def create_consumer():
    broker = get_env_or_raise('KAFKA_BROKER')
    kafka_topic = get_env_or_raise("KAFKA_ORDER_TOPIC")
    while True:
        try:
            print(f"Connecting to Kafka broker at {broker}...")
            
            consumer = KafkaConsumer(
                kafka_topic,
                bootstrap_servers=broker,
                auto_offset_reset='latest',
                group_id='stock-order-group',
                enable_auto_commit=True,
                api_version=(3,0,0),
                value_deserializer=lambda x: json.loads(x.decode('utf-8'))
            )
            
            print("Successfully connected to Kafka broker")
            return consumer
            
        except NoBrokersAvailable:
            print(f"Kafka broker ({broker}) is not available yet. Retrying in 3 seconds...")
            time.sleep(3)
        except Exception as e:
            print(f"Unexpected error while connecting to Kafka: {e}. Retrying in 3 seconds...")
            time.sleep(3)

# Kafka Producer 설정 (orders completed 처리용)
def create_producer():
    broker = get_env_or_raise('KAFKA_BROKER')
    producer_topic = get_env_or_raise("KAFKA_COMPLETED_TOPIC") 
    while True:
        try:
            producer = KafkaProducer(
                bootstrap_servers=broker,
                api_version=(3, 0, 0),
                value_serializer=lambda v: json.dumps(v).encode('utf-8')
            )
            print("Successfully connected to Kafka Producer")
            return producer_topic, producer
        except NoBrokersAvailable:
            print(f"Kafka broker ({broker}) is not available yet for Producer. Retrying in 3 seconds...")
            time.sleep(3)
