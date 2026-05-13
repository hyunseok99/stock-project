import os
import asyncio
import websockets
import json
from kafka import KafkaConsumer

# 환경 변수
broker = os.getenv('KAFKA_BROKER')
kafka_topic = os.getenv('KAFKA_TOPIC')
kafka_topic_second = os.getenv("KAFKA_TOPIC_SECOND")


# Kafka Consumer 설정
def create_consumers():
    consumer1 = KafkaConsumer(
        kafka_topic,
        bootstrap_servers=broker,
        auto_offset_reset='latest',
        group_id='consumer-group',
        enable_auto_commit=True,
        value_deserializer=lambda x: json.loads(x.decode('utf-8'))
    )
    consumer2 = KafkaConsumer(
        kafka_topic_second,
        bootstrap_servers=broker,
        auto_offset_reset='latest',
        group_id='consumer-group2',
        enable_auto_commit=True,
        value_deserializer=lambda x: json.loads(x.decode('utf-8'))
    )
    return consumer1, consumer2

# Kafka 메시지 소비를 비동기로 처리하는 함수
async def consume_and_send(consumer, websocket, topic):
    try:
        while True:
            loop = asyncio.get_event_loop()
            messages = await loop.run_in_executor(None, lambda: consumer.poll(timeout_ms=100))
            for topic_partition, msgs in messages.items():
                for message in msgs:
                    ticker = message.key.decode('utf-8')  # bytes를 문자열로 디코딩
                    data = message.value

                    # ticker == "-1" 장 종료 상태
                    response = {
                        'date': data.get("date"),
                        'ticker': ticker,
                        'price': data.get('price', 0),
                        'diff': data.get('diff', 0),
                        'diffRate': data.get('diffRate', 0),
                        'startPrice': data.get('startPrice', 0),
                        'highPrice': data.get('highPrice', 0),
                        'lowPrice': data.get('lowPrice', 0),
                        'volume': data.get('volume', 0),
                    }
                    await websocket.send(json.dumps(response))
                    print(f"Sent data from {topic}: {response}")
            await asyncio.sleep(0.1)  # CPU 과부하 방지
    except Exception as e:
        print(f"Error in consume_and_send for {topic}: {e}")

# 웹소켓 핸들러
async def handler(websocket):
    print("Client connected, starting Kafka consumers...")

    # 비동기적으로 task 2개 만들어 병렬로 데이터 처리 및 전송
    task1 = asyncio.create_task(consume_and_send(consumer1, websocket, kafka_topic))
    task2 = asyncio.create_task(consume_and_send(consumer2, websocket, kafka_topic_second))

    try:
        # 클라이언트 연결 끊어질 때까지 대기
        await asyncio.gather(task1, task2)
    except websockets.exceptions.ConnectionClosedError as e:
        print("handler 웹소켓 연결 종료:", e)
    finally:
        task1.cancel()
        task2.cancel()


# 메인 서버 실행 함수
async def main():
    try:
        # 웹소켓 서버 시작
        server = await websockets.serve(handler, "0.0.0.0", 8765)
        print("WebSocket server started on ws://192.168.0.63:8765")
        await server.serve_forever()  # 서버가 계속 실행되도록 대기

    except Exception as e:
        print("Error in main:", e)


if __name__ == "__main__":
    consumer1 = None
    consumer2 = None
    try:
        # 컨슈머 2개 생성
        consumer1, consumer2 = create_consumers()
        asyncio.run(main())  # 메인 함수 실행
    except Exception as e:
        print("error in __main__:", e)
    finally:
        if consumer1:
            consumer1.close()
            print("Consumer 1 closed.")
        if consumer2:
            consumer2.close()
            print("Consumer 2 closed.")
        print("Consumer closed")
