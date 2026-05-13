import holidays
from datetime import datetime

kr_holidays = holidays.KR()

def get_market_status():
    now = datetime.now()

    # 1. 공휴일 체크
    if now in kr_holidays:
        return "CLOSED", f"공휴일({kr_holidays.get(now)})로 인한 휴장입니다."

    # 2. 주말 체크
    if now.weekday() >= 5:
        return "CLOSED", "주말이어서 장이 열리지 않습니다."

    # 3. 평일 장 종료 체크
    current_time = now.hour * 100 + now.minute
    if current_time < 900:
        return "CLOSED", "장 시작 전입니다."
    elif current_time > 1530:
        return "CLOSED", "장 종료 후입니다."

    return "OPEN", "장이 열려 있습니다."
