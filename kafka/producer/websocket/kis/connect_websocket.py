# 웹 소켓 모듈을 선언한다.
import sys
import traceback
from datetime import datetime

import websockets
import json
import requests
import os
import asyncio
import time

from kafka_config import producer, kafka_topic

from dotenv import load_dotenv
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
from base64 import b64decode

from kis.kafka_config import get_latest_prices
from market_utils import get_market_status

# .env파일 로드
load_dotenv("kis.env")

# 환경 변수
developer = os.getenv('DEVELOPER')
appKey =os.getenv('APP_KEY')
appSecret = os.getenv('APP_SECRET')

clearConsole = lambda: os.system('cls' if os.name in ('nt', 'dos') else 'clear')

key_bytes = 32

# 날짜를 yyyy-mm-dd 형식으로 변환
today_date = datetime.now().strftime('%Y-%m-%d')

# 최초 1번 가장 설정 티커들의 최신 저장된 가격을 가져온다
latest_prices = get_latest_prices()
# latest_prices = {}
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

# 주식체결 출력라이브러리
def stockhoka(data):

    """ 넘겨받는데이터가 정상인지 확인
    print("stockhoka[%s]"%(data))
    """
    recvvalue = data.split('^')  # 수신데이터를 split '^'
    ticker = recvvalue[0]
    expect_price = recvvalue[47]
    # print("티커(tr_key):", ticker)  # tr_key를 출력하는 부분 추가
    # print("예상 체결가            [%s]" % (expect_price))

    print("유가증권 단축 종목코드 [" + recvvalue[0] + "]")
    # print("영업시간 [" + recvvalue[1] + "]" + "시간구분코드 [" + recvvalue[2] + "]")
    # print("======================================")
    # print("매도호가10 [%s]    잔량10 [%s]" % (recvvalue[12], recvvalue[32]))
    # print("매도호가09 [%s]    잔량09 [%s]" % (recvvalue[11], recvvalue[31]))
    # print("매도호가08 [%s]    잔량08 [%s]" % (recvvalue[10], recvvalue[30]))
    # print("매도호가07 [%s]    잔량07 [%s]" % (recvvalue[9], recvvalue[29]))
    # print("매도호가06 [%s]    잔량06 [%s]" % (recvvalue[8], recvvalue[28]))
    # print("매도호가05 [%s]    잔량05 [%s]" % (recvvalue[7], recvvalue[27]))
    # print("매도호가04 [%s]    잔량04 [%s]" % (recvvalue[6], recvvalue[26]))
    # print("매도호가03 [%s]    잔량03 [%s]" % (recvvalue[5], recvvalue[25]))
    # print("매도호가02 [%s]    잔량02 [%s]" % (recvvalue[4], recvvalue[24]))
    # print("매도호가01 [%s]    잔량01 [%s]" % (recvvalue[3], recvvalue[23]))
    # print("--------------------------------------")
    # print("매수호가01 [%s]    잔량01 [%s]" % (recvvalue[13], recvvalue[33]))
    # print("매수호가02 [%s]    잔량02 [%s]" % (recvvalue[14], recvvalue[34]))
    # print("매수호가03 [%s]    잔량03 [%s]" % (recvvalue[15], recvvalue[35]))
    # print("매수호가04 [%s]    잔량04 [%s]" % (recvvalue[16], recvvalue[36]))
    # print("매수호가05 [%s]    잔량05 [%s]" % (recvvalue[17], recvvalue[37]))
    # print("매수호가06 [%s]    잔량06 [%s]" % (recvvalue[18], recvvalue[38]))
    # print("매수호가07 [%s]    잔량07 [%s]" % (recvvalue[19], recvvalue[39]))
    # print("매수호가08 [%s]    잔량08 [%s]" % (recvvalue[20], recvvalue[40]))
    # print("매수호가09 [%s]    잔량09 [%s]" % (recvvalue[21], recvvalue[41]))
    # print("매수호가10 [%s]    잔량10 [%s]" % (recvvalue[22], recvvalue[42]))
    # print("======================================")
    # print("총매도호가 잔량        [%s]" % (recvvalue[43]))
    # print("총매도호가 잔량 증감   [%s]" % (recvvalue[54]))
    # print("총매수호가 잔량        [%s]" % (recvvalue[44]))
    # print("총매수호가 잔량 증감   [%s]" % (recvvalue[55]))
    # print("시간외 총매도호가 잔량 [%s]" % (recvvalue[45]))
    # print("시간외 총매수호가 증감 [%s]" % (recvvalue[46]))
    # print("시간외 총매도호가 잔량 [%s]" % (recvvalue[56]))
    # print("시간외 총매수호가 증감 [%s]" % (recvvalue[57]))
    print("예상 체결가            [%s]" % (recvvalue[47]))
    # print("예상 체결량            [%s]" % (recvvalue[48]))
    # print("예상 거래량            [%s]" % (recvvalue[49]))
    # print("예상체결 대비          [%s]" % (recvvalue[50]))
    # print("부호                   [%s]" % (recvvalue[51]))
    # print("예상체결 전일대비율    [%s]" % (recvvalue[52]))
    # print("누적거래량             [%s]" % (recvvalue[53]))
    # print("주식매매 구분코드      [%s]" % (recvvalue[58]))


# 주식체결처리 출력 및 출력 가격과 기존 카프카 가격 비교하여 변동 있는 경우 카프카에 전송
def stockspurchase(data):
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


    # 티커 pValue[0], 체결 가격 pvalue[2]
    print("티커(tr_key):", ticker)  # tr_key를 출력하는 부분 추가
    print("%-13s[%s]" % (menustr[2], price))
    # 신 장운영 구분코드
    print("%-13s[%s]" % (menustr[34], pValue[34]))
    print("topic: ", kafka_topic)
    # 전일대비, 대비율  ex) 비율: -0.79, 0.74. 0.00 대비: -9000, 400, 0
    print("%-13s[%s]" % (menustr[4], pValue[4]))
    print("%-13s[%s]" % (menustr[5], pValue[5]))

    # 시가, 최고가, 최저가
    print("%-13s[%s]" % (menustr[7], pValue[7]))
    print("%-13s[%s]" % (menustr[8], pValue[8]))
    print("%-13s[%s]" % (menustr[9], pValue[9]))

    # 누적거래량
    print("%-13s[%s]" % (menustr[13], pValue[13]))

    # 날짜
    print("%-13s[%s]" % ("오늘 날짜", today_date))


    # 기존 가격과 비교하여 변동이 있으면 카프카 전송 및 최신 가격 업데이트
    # if ticker not in latest_prices or latest_prices[ticker] != price:
    #     latest_prices[ticker] = price
    # producer.send(kafka_topic, value={"ticker": ticker, "price": price})
    producer.send(kafka_topic, key=ticker.encode('utf-8'), value={"date": today_date, "price": price, "diff":diff, "diffRate": diffRate, "startPrice": startPrice, "highPrice": highPrice, "lowPrice": lowPrice, "volume": volume})
    print("produce: %-13s[%s]" % (ticker, price))
    # producer.flush() #flush를 통해 즉시 kafka에 전송

# 해외주식체결처리 출력라이브러리
def stockspurchase_overseas(data):
    print("============================================")
    menulist = "실시간종목코드|종목코드|수수점자리수|현지영업일자|현지일자|현지시간|한국일자|한국시간|시가|고가|저가|현재가|대비구분|전일대비|등락율|매수호가|매도호가|매수잔량|매도잔량|체결량|거래량|거래대금|매도체결량|매수체결량|체결강도|시장구분"
    menustr = menulist.split('|')
    pValue = data.split('^')

    ticker = pValue[1]
    price = pValue[11]
    diff = pValue[13]
    diffRate = pValue[14]
    volume = pValue[20]
    startPirce = pValue[8]
    highPrice = pValue[9]
    lowPrice = pValue[10]

    # 티커 pValue[0], 체결 가격 pvalue[2]
    print("티커(tr_key):", ticker)  # tr_key를 출력하는 부분 추가

    # 전일대비, 대비율  ex) 비율: -0.79, 0.74. 0.00 대비: -9000, 400, 0
    print("%-13s[%s]" % (menustr[13], pValue[13]))
    print("%-13s[%s]" % (menustr[14], pValue[14]))

    # 현재가, 시가, 최고가, 최저가
    print("%-13s[%s]" % (menustr[11], price))
    print("%-13s[%s]" % (menustr[8], pValue[8]))
    print("%-13s[%s]" % (menustr[9], pValue[9]))
    print("%-13s[%s]" % (menustr[10], pValue[10]))

    # 누적거래량
    print("%-13s[%s]" % (menustr[20], pValue[20]))

    # 날짜
    print("%-13s[%s]" % ("오늘 날짜", today_date))

    producer.send(kafka_topic, key=ticker.encode('utf-8'),
                  value={"date": today_date, "price": price, "diff": diff, "diffRate": diffRate,
                         "startPrice": startPirce, "highPrice": highPrice, "lowPrice": lowPrice, "volume": volume
                         })
    print("produce: %-13s[%s]" % (ticker, price))


async def connect():
    try:
        # 웹 소켓에 접속.( 주석은 koreainvest test server for websocket)
        ## 시세데이터를 받기위한 데이터를 미리 할당해서 사용한다.

        g_appkey = appKey
        g_appsecret = appSecret

        # 코스피 종목 리스트 가져오기
        custtype = 'P'  # customer type, 개인:'P' 법인 'B'
        # kospi_stocks = [ "005930", "000660", "373220", "207940", "005380", "068270", "000270", "035420", "012450", "329180"]
        # kospi_stocks = ["068270", "000270", "035420", "012450", "329180"]
        # kospi_stocks = ["005930", "272210", "035420", "000270", "012330", "010140", "323410", "105560",  "207940", "097950"]
        # kospi_stocks = ["005930", "207940", "005380", "000270", "035420"]
        # kospi_stocks = ["000660", "373220", "035720", "247540", "042660", "005930", "207940", "005380", "000270", "035420"]
        kospi_stocks = ["000660"]
        nxt_stocks = ["030000", "010950"] #, "032640", "030000"] #s-oil, lg-유플러스, 제일기획
        # kospi_stocks = nxt_stocks
        # nasdaq = ["PLTR", "AAPL", "NVDA"] # 팔란티어, 애플 , 엔비디아
        nasdaq = ["PLTR", "AAPL"] # 팔란티어, 애플 , 엔비디아

        # url = 'ws://ops.koreainvestment.com:31000' # 모의투자계좌
        url = 'ws://ops.koreainvestment.com:21000'  # 실전투자계좌

        g_approval_key = get_approval(g_appkey, g_appsecret)
        print("approval_key [%s]" % (g_approval_key))

        async with websockets.connect(url, ping_interval=60) as websocket:

            """" 주석처리는 더블쿼트 3개로 처리
            """
            # 실제로 실시간 시세만 확인하기 위해서 3.주식체결만 이용
            print("1.주식호가, 2.주식호가해제, 3.주식체결, 4.주식체결해제, 5.주식체결통보(고객), 6.주식체결통보해제(고객), 7.주식체결통보(모의), 8.주식체결통보해제(모의)")
            print("Input Command :")
            cmd = input()
            # cmd = '3'
            # 입력값 체크 step
            if cmd < '0' or cmd > '8':
                print("> Wrong Input Data", cmd)
            elif cmd == '0':
                print("Exit!!")

            # 입력값에 따라 전송 데이터셋 구분 처리
            if cmd == '3':  # 주식체결 등록
                tr_id = 'H0STCNT0'
                tr_type = '1'
            elif cmd == '1':
                tr_id = 'H0STASP0'
                tr_type = '1'
            elif cmd == '2':
                tr_id = 'H0NXCNT0' # nxt 실시간 호가
                tr_type = '1'
            elif cmd == '4':
                tr_id = 'HDFSCNT0'
                tr_type = '1' # 해외주식 실시간 지연 체결가
            # 여러 종목을 순차적으로 구독
            # print("kospi_stocks: ",kospi_stocks)

            senddata_list = []
            for stockcode in kospi_stocks:
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
                senddata_list.append(senddata)
                print('Input Command is :', senddata)

            for senddata in senddata_list:
                await websocket.send(senddata)

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
                print("Receive Command is :", data)
                print("Data[0]: ", data[0])

                if data[0] == '0' or data[0] == '1':  # 실시간 데이터일 경우
                    if data[0] == '0':
                        recvstr = data.split('|')  # 수신데이터가 실데이터 이전은 '|'로 나뉘어져있어 split
                        trid0 = recvstr[1]
                        if trid0 == "H0STASP0":  # 주식호가tr 일경우의 처리 단계
                            print("#### 주식호가 ####")
                            stockhoka(recvstr[3])
                            await asyncio.sleep(0.2)

                        elif trid0 == "H0STCNT0":  # 주식체결 데이터 처리
                            print("#### 주식체결 ####")
                            data_cnt = int(recvstr[2])  # 체결데이터 개수
                            stockspurchase(recvstr[3])
                            await asyncio.sleep(0.2)

                        elif trid0 == "H0NXCNT0": # nxt 체결 데이터 처리
                            stockspurchase(recvstr[3])
                            await asyncio.sleep(0.2)

                        elif trid0 == "HDFSCNT0": # 나스닥 실시간 체결 데이터
                            stockspurchase_overseas(recvstr[3])
                            await asyncio.sleep(0.2)


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
                        # 만약 장이 종료되었다면 연결 해제
                        if status == "CLOSED":
                            print("### Market Closed! Reason: [%s]" % reason)
                            producer.send(kafka_topic, key="-1".encode('utf-8'),
                                          value={"date": today_date, "reason": reason})
                            await websocket.close()
                            print("### Websocket Closed!")
                            break



    # ----------------------------------------
    # 모든 함수의 공통 부분(Exception 처리)
    # ----------------------------------------
    except Exception as e:
        print('Exception Raised!')
        print(e)
        print('Connect Again!')
        time.sleep(0.1)

        # 웹소켓 다시 시작
        await connect()

# # 비동기로 서버에 접속한다.
# asyncio.get_event_loop().run_until_complete(connect())
# asyncio.get_event_loop().close()

# -----------------------------------------------------------------------------
# - Name : main
# - Desc : 메인
# -----------------------------------------------------------------------------
async def main():
    try:
        # 웹소켓 시작
        await connect()

    except Exception as e:
        print('Exception Raised!')
        print(e)


if __name__ == "__main__":
    # kospi_stocks = stock.get_market_ticker_list(market="KOSPI")
    # print("kospi 갯수: ",len(kospi_stocks)) # 961개
    print("kospi_stocks: ",latest_prices)

    try:
        # ---------------------------------------------------------------------
        # Logic Start!
        # ---------------------------------------------------------------------
        # 웹소켓 시작
        asyncio.run(main())

    except KeyboardInterrupt:
        print("KeyboardInterrupt Exception 발생!")
        print(traceback.format_exc())
        sys.exit(-100)

    except Exception:
        print("Exception 발생!")
        print(traceback.format_exc())
        sys.exit(-200)