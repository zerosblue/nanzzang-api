"""
NANZZANG 통합 스케줄러
- 크롤러 봇: CRAWLER_INTERVAL_HOURS마다 NANZZANG 토픽 자동 생성
- 블로그 봇: BLOG_INTERVAL_HOURS마다 Ghost 블로그 글 자동 게시
Railway에서 상시 실행되는 서비스로 동작
"""

import os
import time
import threading
import subprocess
from datetime import datetime

CRAWLER_INTERVAL_HOURS = int(os.getenv("CRAWLER_INTERVAL_HOURS", "24"))
BLOG_INTERVAL_HOURS = int(os.getenv("BLOG_INTERVAL_HOURS", "12"))
BLOG_POST_COUNT = int(os.getenv("BLOG_POST_COUNT", "2"))
GHOST_URL = os.getenv("GHOST_URL", "")


def now() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def run_crawler():
    print(f"[{now()}] 크롤러 시작", flush=True)
    result = subprocess.run(
        ["python", "crawler.py", "--count", "5"],
        capture_output=False,
    )
    status = "완료" if result.returncode == 0 else f"실패 (exit {result.returncode})"
    print(f"[{now()}] 크롤러 {status}", flush=True)


def run_blog():
    if not GHOST_URL:
        return  # GHOST_URL 미설정 시 블로그 봇 스킵
    print(f"[{now()}] 블로그 봇 시작 ({BLOG_POST_COUNT}개)", flush=True)
    result = subprocess.run(
        ["python", "blog_bot.py", "--count", str(BLOG_POST_COUNT)],
        capture_output=False,
    )
    status = "완료" if result.returncode == 0 else f"실패 (exit {result.returncode})"
    print(f"[{now()}] 블로그 봇 {status}", flush=True)


def crawler_loop():
    run_crawler()
    while True:
        time.sleep(CRAWLER_INTERVAL_HOURS * 3600)
        run_crawler()


def blog_loop():
    if not GHOST_URL:
        print(f"[{now()}] GHOST_URL 미설정 — 블로그 봇 비활성화", flush=True)
        return
    time.sleep(60)  # 크롤러보다 1분 늦게 시작 (NANZZANG 토픽 먼저 생성 후 링크)
    run_blog()
    while True:
        time.sleep(BLOG_INTERVAL_HOURS * 3600)
        run_blog()


if __name__ == "__main__":
    print(f"통합 스케줄러 시작", flush=True)
    print(f"  크롤러: {CRAWLER_INTERVAL_HOURS}시간 간격", flush=True)
    if GHOST_URL:
        print(f"  블로그봇: {BLOG_INTERVAL_HOURS}시간 간격, {BLOG_POST_COUNT}개/회", flush=True)
    else:
        print(f"  블로그봇: 비활성화 (GHOST_URL 환경변수 필요)", flush=True)

    t_crawler = threading.Thread(target=crawler_loop, daemon=True)
    t_blog = threading.Thread(target=blog_loop, daemon=True)

    t_crawler.start()
    t_blog.start()

    t_crawler.join()
    t_blog.join()
