import os
import time
import asyncio
import websockets
import json
from kafka import KafkaConsumer
from kafka.errors import NoBrokersAvailable

# 환경 변수
broker = os.getenv('KAFKA_BROKER')
kafka_topic = os.getenv('KAFKA_TOPIC')
kafka_topic_second = os.getenv("KAFKA_TOPIC_SECOND")

# 전역 상태 관리 
last_market_status = None
ticker_snapshot = {} # 티커별 가장 마지막 데이터 
connected_clients = set() # 접속중인 웹소켓  클라이언트 저장  

# Kafka Consumer 설정
def create_consumers():
    while True:
        try:
            print(f"Connecting to Kafka broker at {broker}...")
            
            consumer1 = KafkaConsumer(
                kafka_topic,
                bootstrap_servers=broker,
                auto_offset_reset='latest',
                group_id='consumer-group',
                enable_auto_commit=True,
                api_version=(3,0,0),
                value_deserializer=lambda x: json.loads(x.decode('utf-8'))
            )
            
            consumer2 = KafkaConsumer(
                kafka_topic_second,
                bootstrap_servers=broker,
                auto_offset_reset='latest',
                group_id='consumer-group2',
                enable_auto_commit=True,
                api_version=(3,0,0),
                value_deserializer=lambda x: json.loads(x.decode('utf-8'))
            )
            
            print("Successfully connected to Kafka broker")
            return consumer1, consumer2
            
        except NoBrokersAvailable:
            print(f"Kafka broker ({broker}) is not available yet. Retrying in 3 seconds...")
            time.sleep(3)
        except Exception as e:
            print(f"Unexpected error while connecting to Kafka: {e}. Retrying in 3 seconds...")
            time.sleep(3)

# Kafka 메시지 소비를 비동기로 처리하는 함수
# Kafka 메시지를 모든 클라이언트에 제공해주는 함수 
async def consume_and_send(consumer, topic):
    # 새로운 접속자에게 가장 최근의 상태 전송 위한 전역변수
    global last_market_status, ticker_snapshot
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
                    if ticker == "-1":
                      response['reason'] = data.get('reason')
                    else:
                      # ticker snapshot update
                      ticker_snapshot[ticker] = response
                    
                    last_market_status = response
                    
                    # broadcast for connected clients
                    if connected_clients:
                      message_json = json.dumps(response)
                      # using gather for not consider unconnected usr
                      await asyncio.gather(
                        *[client.send(message_json) for client in connected_clients],
                        return_exceptions = True
                      )
                      print(f"roadCast: Sent data from {topic}:{response}")
            await asyncio.sleep(0.1)  # CPU 과부하 방지
    except Exception as e:
        print(f"Error in broadcast consume_and_send for {topic}: {e}")

# 웹소켓 핸들러-> 접속 유저 관리 + 초기 데이터 전송 
async def handler(websocket):
    global last_market_status, ticker_snapshot # 전역 변수 사용 선언
    print("Client connected, starting Kafka consumers...")
    
    # 1. Add New Client
    connected_clients.add(websocket)
    
    # 새로운 접속자에게 저장된 마지막 상태가 있다면 즉시 전송+ 기존티커 스냅샷 제공
    if ticker_snapshot:
        try:
          snapshot_list = list(ticker_snapshot.values())
          print(f"Sending {len(snapshot_list)} cached tickers to new client")
          await websocket.send(json.dumps(snapshot_list))
        except Exception as e:
          print(f"Error sending initial cache: {e}")
    
    if last_market_status:
        try:
          print(f"Sending cached status to new client: {last_market_status}")
          await websocket.send(json.dumps(last_market_status))
        except Exception as e:
          print(f"Error sending initial cache: {e}")


    try:
        # 클라이언트 연결 끊어질 때까지 대기
        await websocket.wait_closed()
    finally:
        # 2. Remove Client
        connected_clients.remove(websocket)
        print("Cleint disconnected")


# 메인 서버 실행 함수
async def main(c1, c2):

    try:
        # 비동기적으로 task 2개 만들어 병렬로 데이터 처리 및 전송
        # 백그라운드 태스크로 실행 
        asyncio.create_task(consume_and_send(c1, kafka_topic))
        asyncio.create_task(consume_and_send(c2, kafka_topic_second))
    
        # 웹소켓 서버 시작
        server = await websockets.serve(handler, "0.0.0.0", 8765)
        print("WebSocket server started on ws://0.0.0.0:8765")
        await server.serve_forever()  # 서버가 계속 실행되도록 대기

    except Exception as e:
        print("Error in main:", e)



if __name__ == "__main__":
    consumer1 = None
    consumer2 = None
    try:
        # 컨슈머 2개 생성
        consumer1, consumer2 = create_consumers()
        asyncio.run(main(consumer1, consumer2))  # 메인 함수 실행
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
