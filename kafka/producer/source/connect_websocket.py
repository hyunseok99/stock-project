import os
import websockets
import requests
import asyncio
import time
import json
import pytz
from datetime import datetime
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
from base64 import b64decode
from market_utils import get_market_status

# 환경 변수 세팅
kafka_topic = os.getenv('KAFKA_TOPIC', 'default_topic')
key_bytes = 32

# 인메모리 캐싱용 딕셔너리 : 마지막 가격 저장용
last_prices = {}

# 주식 티커 리스트 return: list
def get_ticker_list():
    tickers = os.getenv('STOCK_TICKERS', '')

    if not tickers:
        return []
    ticker_list = [s.strip() for s in tickers.split(',')]

    return ticker_list

# AES256 DECODE
def aes_cbc_base64_dec(key, iv, cipher_text):
    """
    :param key:  str type AES256 secret key value
    :param iv: str type AES256 Initialize Vector
    :param cipher_text: Base64 encoded AES256 str
    :return: Base64-AES256 decodec str
    """
    cipher = AES.new(key.encode('utf-8'), AES.MODE_CBC, iv.encode('utf-8'))
    return bytes.decode(unpad(cipher.decrypt(b64decode(cipher_text)), AES.block_size))

# 웹소켓 접속키 발급
def get_approval(key, secret):
    # url = https://openapivts.koreainvestment.com:29443' # 모의투자계좌
    url = 'https://openapi.koreainvestment.com:9443' # 실전투자계좌
    headers = {"content-type": "application/json"}
    body = {"grant_type": "client_credentials",
            "appkey": key,
            "secretkey": secret}
    PATH = "oauth2/Approval"
    URL = f"{url}/{PATH}"
    time.sleep(0.05)
    res = requests.post(URL, headers=headers, data=json.dumps(body))
    approval_key = res.json()["approval_key"]
    return approval_key

# 주식체결처리 출력 및 출력 가격과 기존 카프카 가격 비교하여 변동 있는 경우 카프카에 전송
def stockspurchase(producer, data, today_date):
    print("============================================")
    menulist = "유가증권단축종목코드|주식체결시간|주식현재가|전일대비부호|전일대비|전일대비율|가중평균주식가격|주식시가|주식최고가|주식최저가|매도호가1|매수호가1|체결거래량|누적거래량|누적거래대금|매도체결건수|매수체결건수|순매수체결건수|체결강도|총매도수량|총매수수량|체결구분|매수비율|전일거래량대비등락율|시가시간|시가대비구분|시가대비|최고가시간|고가대비구분|고가대비|최저가시간|저가대비구분|저가대비|영업일자|신장운영구분코드|거래정지여부|매도호가잔량|매수호가잔량|총매도호가잔량|총매수호가잔량|거래량회전율|전일동시간누적거래량|전일동시간누적거래량비율|시간구분코드|임의종료구분코드|정적VI발동기준가"
    menustr = menulist.split('|')
    pValue = data.split('^')

    ticker = pValue[0]
    price = pValue[2]
    diff = pValue[4]
    diffRate = pValue[5]
    volume = pValue[13]
    startPrice = pValue[7]
    highPrice = pValue[8]
    lowPrice = pValue[9]

    # 글로벌 딕셔너리의 기존 가격과 비교하여 변동이 있으면 카프카 전송 및 최신 가격 업데이트
    if last_prices.get(ticker) != price:
        last_prices[ticker] = price
        producer.send(kafka_topic, key=ticker.encode('utf-8'), value={"date": today_date, "price": price, "diff":diff, "diffRate": diffRate, "startPrice": startPrice, "highPrice": highPrice, "lowPrice": lowPrice, "volume": volume})
        print("updated produce: %-13s[%s]" % (ticker, price))
    # producer.flush() #flush를 통해 즉시 kafka에 전송

# 웹소켓 연결
async def connect(producer):
    global last_prices
    while True: # 무한 루프 구조
        try:
            # 1. KST 타임존 설정
            kst = pytz.timezone('Asia/Seoul')
            now_kst = datetime.now(kst)

            # connect 시작될 때 날짜 갱신 (다음날 재연결)
            today_date = now_kst.strftime('%Y-%m-%d') # 날짜를 yyyy-mm-dd 형식으로 변환

            # 웹소켓 연결 시도 전에 장이 열려 있는지 확인
            status, reason = get_market_status()

            # 장이 열려 있지 않다면 장이 열릴 때까지 대기모드로 전환
            if status != "OPEN":
                print(f"### Market is {status}. Reason: {reason}. Waiting...")

                producer.send(kafka_topic, key="-1".encode('utf-8'),
                              value={"date": today_date, "reason": reason})
                producer.flush()

                # 대기 시간 설정 (상태에 따라 유연하게)
                wait_time = 60  # 기본 1분
                now = datetime.now(pytz.timezone('Asia/Seoul'))
                if now.hour >= 16 or now.hour < 8:
                    wait_time = 3600  # 장 종료 후 새벽에는 1시간마다 체크
                elif now.hour == 8 and now.minute >= 40:
                    wait_time = 30  # 장 시작 직전에는 30초마다 체크

                await asyncio.sleep(wait_time)
                continue  # 다시 while문 처음으로 가서 상태 확인


            # 티커들
            kospi_tickers = get_ticker_list()

            # 웹 소켓에 접속.( 주석은 koreainvest test server for websocket)
            g_appkey = os.getenv('APP_KEY')
            g_appsecret = os.getenv('APP_SECRET')

            # 코스피 종목 리스트 가져오기
            custtype = 'P'  # customer type, 개인:'P' 법인 'B'
            # nxt_stocks = ["030000", "010950"] #, "032640", "030000"] #s-oil, lg-유플러스, 제일기획
            # nasdaq = ["PLTR", "AAPL", "NVDA"] # 팔란티어, 애플 , 엔비디아

            # url = 'ws://ops.koreainvestment.com:31000' # 모의투자계좌
            url = 'ws://ops.koreainvestment.com:21000'  # 실전투자계좌

            g_approval_key = get_approval(g_appkey, g_appsecret)
            print(f"approval_key [{g_approval_key}] (Date: {today_date})")

            async with websockets.connect(url, ping_interval=60) as websocket:

                # 실제로 실시간 시세만 확인하기 위해서 주식체결만 이용
                tr_id = 'H0STCNT0'
                tr_type = '1'

                # 여러 종목을 순차적으로 구독
                ## senddata_list = []
                for stockcode in kospi_tickers:
                    senddata = json.dumps({
                        "header": {
                            "approval_key": g_approval_key,
                            "custtype": custtype,
                            "tr_type": tr_type,
                            "content-type": "utf-8"
                        },
                        "body": {
                            "input": {
                                "tr_id": tr_id,
                                "tr_key": stockcode
                            }
                        }
                    })
                    ## senddata_list.append(senddata)
                    print('Input Command is :', senddata)
                    await websocket.send(senddata)
                    await asyncio.sleep(0.2)
                ## for senddata in senddata_list:
                    ## await websocket.send(senddata)

                # # 나스닥용
                # print("nasdap_stocks: ",nasdaq)
                # for stockcode in nasdaq:
                #     senddata = json.dumps({
                #         "header": {
                #             "approval_key": g_approval_key,
                #             "custtype": custtype,
                #             "tr_type": tr_type,
                #             "content-type": "utf-8"
                #         },
                #         "body": {
                #             "input": {
                #                 "tr_id": tr_id,
                #                 "tr_key": 'DNAS' + stockcode
                #             }
                #         }
                #     })
                #     senddata_list.append(senddata)
                #     print('Input Command is :', senddata)
                # for senddata in senddata_list:
                #     await websocket.send(senddata)

                # 무한히 데이터가 오기만 기다린다.
                while True:
                    data = await websocket.recv()
                    # print("Receive Command is :", data)

                    if data[0] == '0' or data[0] == '1':  # 실시간 데이터일 경우
                        if data[0] == '0':
                            recvstr = data.split('|')  # 수신데이터가 실데이터 이전은 '|'로 나뉘어져있어 split
                            trid0 = recvstr[1]
                            if trid0 == "H0STCNT0":  # 주식체결 데이터 처리
                                # print("#### 주식체결 ####")
                                #data_cnt = int(recvstr[2])  # 체결데이터 개수
                                stockspurchase(producer, recvstr[3], today_date)
                    else:
                        jsonObject = json.loads(data)
                        trid = jsonObject["header"]["tr_id"]

                        if trid != "PINGPONG":
                            rt_cd = jsonObject["body"]["rt_cd"]
                            if rt_cd == '1':  # 에러일 경우 처리
                                print("### ERROR RETURN CODE [ %s ] MSG [ %s ]" % (rt_cd, jsonObject["body"]["msg1"]))
                                pass
                            elif rt_cd == '0':  # 정상일 경우 처리
                                print("### RETURN CODE [ %s ] MSG [ %s ]" % (rt_cd, jsonObject["body"]["msg1"]))
                                # 체결통보 처리를 위한 AES256 KEY, IV 처리 단계
                                if trid == "H0STCNI0" or trid == "H0STCNI9":
                                    aes_key = jsonObject["body"]["output"]["key"]
                                    aes_iv = jsonObject["body"]["output"]["iv"]
                                    print("### TRID [%s] KEY[%s] IV[%s]" % (trid, aes_key, aes_iv))

                        elif trid == "PINGPONG":
                            print("### RECV [PINGPONG] [%s]" % (data))
                            await websocket.pong(data)
                            print("### SEND [PINGPONG] [%s]" % (data))
                            status, reason = get_market_status()
                            print("### Status: [%s],  Reason: [%s]" % (status, reason))
                            # 핑퐁 시점에 장 종료면 루프 탈출
                            if status == "CLOSED":
                                print("### Market Closed! Reason: [%s]" % reason)
                                break
        # ----------------------------------------
        # 모든 함수의 공통 부분(Exception 처리)
        # ----------------------------------------
        except Exception as e:
            print('Exception Raised!')
            print(e)
            print(' After 3 seconds Connect Again!')
            await asyncio.sleep(3) # 3초 후 재연결

            # 웹소켓 다시 시작 -> 어차피 while true 이므로 connect 함수 처음부터 다시 시작됨
            # await connect(producer)
