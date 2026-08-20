import asyncio
import time
from create_producer import get_producer
from connect_websocket import connect

async def main():
    producer = None
    
    # 카프카 브로커 찾을때까지 무한 재시도
    while True:
      try:
          print(f"### producer 생성 시도")
          # create producer
          producer = get_producer()
          print(f"### producer 생성 성공")
          break;
      except Exception as e:
            print(f"### [경고] 브로커 연결 실패: {e}. 5초후 재연결 시도...")
            time.sleep(5)
    try:
      # websocket connection task 수행
      ws_task = asyncio.create_task(connect(producer))
      await ws_task
    except Exception as e:
        print(f"Main Exception: {e}")
    finally:
        # 프로그램 종료 시 producer close
        if producer:
            producer.flush()  # 남은 메시지 모두 전송
            producer.close()
            print("Producer closed.")

if __name__ == "__main__":
    asyncio.run(main())

