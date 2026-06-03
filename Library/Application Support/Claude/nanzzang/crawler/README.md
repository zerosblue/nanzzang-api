# NANZZANG 크롤러 봇

네이버/다음 뉴스를 크롤링하고 Claude AI가 NANZZANG 대결 토픽을 자동 생성합니다.

## 설치

```bash
python3 -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## 설정

```bash
cp .env.example .env
# .env 파일에 값 입력:
# ANTHROPIC_API_KEY=sk-ant-...
# NANZZANG_API_URL=http://localhost:8080  (or 배포 URL)
```

## 실행

```bash
# 기본 실행 (5개 토픽 생성 + API 등록)
python crawler.py

# 개수 지정
python crawler.py --count 3

# 미리보기만 (API 등록 없이)
python crawler.py --dry-run
```

## 자동 실행 (cron)

매일 오전 9시 자동 실행:
```bash
# crontab -e
0 9 * * * cd /path/to/nanzzang/crawler && source venv/bin/activate && python crawler.py >> cron.log 2>&1
```

## 크롤링 소스
- 네이버 뉴스 많이 본 뉴스 (1순위)
- 네이버 뉴스 RSS (폴백)
- 다음 뉴스 (폴백)
