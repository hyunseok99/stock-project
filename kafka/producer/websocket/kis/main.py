from dotenv import load_dotenv
import os

# .env파일 로드
load_dotenv("kis.env")

# 환경 변수
developer = os.getenv('DEVELOPER')
appKey =os.getenv('APP_KEY')
appSecret = os.getenv('APP_SECRET')
