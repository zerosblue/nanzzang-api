FROM python:3.11-slim

RUN apt-get update && apt-get install -y \
    ffmpeg \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY crawler.py .
COPY blog_bot.py .
COPY youtube_bot.py .
COPY run_scheduled.py .

CMD ["python", "run_scheduled.py"]
