FROM python:3.9-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY source/ .

ENV PYTHONPATH=/app

# main 파일 실행
CMD ["python", "main.py"]
