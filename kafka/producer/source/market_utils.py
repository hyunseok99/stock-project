import holidays
import pytz
from datetime import datetime, time

kr_holidays = holidays.KR(language='ko')

def get_market_status():
    kst = pytz.timezone('Asia/Seoul')
    now = datetime.now(kst)
    today = now.date()
    now_time = now.time()
    print(f"get_market_status의 현재 시각: {now.strftime('%Y-%m-%d %H:%M:%S')}")
    market_open = time(9,0)
    market_close = time(15,30)
    # 1. 공휴일 체크
    if today in kr_holidays:
        return "CLOSED", f"공휴일({kr_holidays.get(today)})로 인한 휴장입니다."

    # 2. 주말 체크
    if now.weekday() >= 5:
        return "CLOSED", "주말이어서 장이 열리지 않습니다."

    # 3. 평일 장 종료 체크
    if now_time < market_open:
        return "CLOSED", "장 시작 전입니다."
    elif now_time <= market_close:
        return "OPEN", "장이 열려 있습니다."
    else:
        return "CLOSED", "장이 종료되었습니다."



if __name__ == "__main__":
    print(kr_holidays.get(datetime.now()))
    state, reason = get_market_status()
    print(f"Market Status: {state}, Reason: {reason}")
