import asyncio
from create_producer import get_producer
from connect_websocket import connect

async def main():
    producer = None
    try:

        print(f"### producer 생성 시도")
        # create producer
        producer = get_producer()
        print(f"### producer 생성 성공")

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

