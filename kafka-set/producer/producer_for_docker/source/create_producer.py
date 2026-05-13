import os
import json
from kafka import KafkaProducer

def get_producer():
    broker = os.getenv('KAFKA_BROKER', 'localhost:9092')
    return KafkaProducer(
        bootstrap_servers=broker,
        client_id='stock-producer',
        value_serializer=lambda v: json.dumps(v).encode('utf-8')
    )