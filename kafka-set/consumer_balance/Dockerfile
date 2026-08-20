FROM python:3.9-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY db_connection.py . 
COPY private_kafka.py . 
COPY main.py .

CMD ["python", "main.py"]
