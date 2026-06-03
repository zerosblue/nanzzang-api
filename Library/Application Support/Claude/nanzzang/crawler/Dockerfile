FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY crawler.py .
COPY blog_bot.py .
COPY run_scheduled.py .

CMD ["python", "run_scheduled.py"]
