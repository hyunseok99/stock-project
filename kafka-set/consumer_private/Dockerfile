FROM python:3.9-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY private_kafka.py . 
COPY db_connection.py .
COPY private_redis.py . 
COPY metrics.py .
COPY settlement.py . 
COPY main.py .

EXPOSE 8000

CMD ["python", "main.py"]
