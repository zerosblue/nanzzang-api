"""
NANZZANG 크롤러 봇
- 네이버 뉴스 랭킹 크롤링
- Claude AI로 NANZZANG 대결 토픽 자동 생성
- NANZZANG API에 자동 등록

사용법:
  python crawler.py              # 5개 토픽 생성 (기본)
  python crawler.py --count 3   # 3개 토픽 생성
  python crawler.py --dry-run   # API 등록 없이 생성 결과만 출력
"""

import os
import json
import time
import random
import argparse
import requests
import xml.etree.ElementTree as ET
from datetime import datetime
from bs4 import BeautifulSoup
from anthropic import Anthropic
from dotenv import load_dotenv

load_dotenv()

API_URL = os.getenv("NANZZANG_API_URL", "http://localhost:8080")
ELECTION_MODE = os.getenv("ELECTION_MODE", "false").lower() == "true"
BOT_EMAIL = os.getenv("NANZZANG_EMAIL", "bot@nanzzang.com")
BOT_NICKNAME = os.getenv("NANZZANG_NICKNAME", "난장봇")
ADMIN_EMAIL = os.getenv("ADMIN_EMAIL", "test1@nanzzang.com")
ADMIN_NICKNAME = os.getenv("ADMIN_NICKNAME", "키보드워리어")

BOT_A_EMAIL = os.getenv("BOT_A_EMAIL", "bot_a@nanzzang.com")
BOT_A_NICKNAME = os.getenv("BOT_A_NICKNAME", "🤖 AI-A팀논객")
BOT_B_EMAIL = os.getenv("BOT_B_EMAIL", "bot_b@nanzzang.com")
BOT_B_NICKNAME = os.getenv("BOT_B_NICKNAME", "🤖 AI-B팀논객")

META_GRAPH_BASE = "https://graph.facebook.com/v21.0"
META_PAGE_ACCESS_TOKEN = os.getenv("META_PAGE_ACCESS_TOKEN", "")
META_PAGE_ID = os.getenv("META_PAGE_ID", "")
INSTAGRAM_ACCOUNT_ID = os.getenv("INSTAGRAM_ACCOUNT_ID", "")
INSTAGRAM_IMAGE_URL = os.getenv("INSTAGRAM_IMAGE_URL", "")
NANZZANG_URL = "https://nanzzang-web.vercel.app"

TWITTER_API_KEY = os.getenv("TWITTER_API_KEY", "")
TWITTER_API_SECRET = os.getenv("TWITTER_API_SECRET", "")
TWITTER_ACCESS_TOKEN = os.getenv("TWITTER_ACCESS_TOKEN", "")
TWITTER_ACCESS_TOKEN_SECRET = os.getenv("TWITTER_ACCESS_TOKEN_SECRET", "")

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    )
}

CATEGORY_MAP = {
    "정치": "politics",
    "경제": "economy",
    "사회": "social",
    "문화": "culture",
    "연예": "culture",
    "스포츠": "culture",
    "일상": "daily",
    "연애": "love",
    "직장": "work",
}

TITLE_PATTERNS = [
    "~가 옳냐 vs ~가 옳냐",
    "~편이냐 vs ~편이냐",
    "~가 정상이냐 vs 비정상이냐",
    "~해야 하냐 vs 말아야 하냐",
    "이해돼? vs 말도 안 돼",
    "~가 문제냐 vs 쟤가 문제냐",
    "니편은? A vs B",
    "~한 거 당연하냐 vs 어이없냐",
]

CATEGORY_HASHTAGS = {
    "politics": ["#정치갈등", "#여야대결", "#정치이슈"],
    "economy": ["#경제이슈", "#돈문제", "#경제갈등"],
    "social": ["#사회이슈", "#핫이슈", "#사회갈등"],
    "culture": ["#문화이슈", "#트렌드", "#요즘이슈"],
    "daily": ["#일상갈등", "#공감포인트", "#일상이슈"],
    "love": ["#연애갈등", "#연애이슈", "#썸타는중"],
    "work": ["#직장인갈등", "#직장이슈", "#퇴근후고민"],
    "story": ["#실화이슈", "#갈등스토리", "#공감스토리"],
}


# ─── 크롤링 ─────────────────────────────────────────────────────────────────


def fetch_naver_ranking_headlines() -> list[str]:
    """네이버 뉴스 많이 본 뉴스 랭킹 크롤링"""
    url = "https://news.naver.com/main/ranking/popularDay.naver"
    try:
        resp = requests.get(url, headers=HEADERS, timeout=10)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, "lxml")

        headlines = []
        # 랭킹 기사 제목 추출 (여러 선택자 시도)
        for selector in [
            "a.rankingnews_box_head",
            ".rankingnews_list a",
            ".ranking_headline",
            "a[class*='news_tit']",
        ]:
            items = soup.select(selector)
            for item in items:
                text = item.get_text(strip=True)
                if text and len(text) > 5:
                    headlines.append(text)
            if headlines:
                break

        # 선택자 실패시 모든 기사 링크 텍스트 파싱
        if not headlines:
            for a in soup.find_all("a", href=True):
                text = a.get_text(strip=True)
                if 10 < len(text) < 80 and "article" in a.get("href", ""):
                    headlines.append(text)

        print(f"[네이버 랭킹] {len(headlines)}개 헤드라인 수집")
        return headlines[:20]
    except Exception as e:
        print(f"[네이버 랭킹 실패] {e}")
        return []


def fetch_naver_rss_headlines() -> list[str]:
    """뉴스 RSS 헤드라인 수집 (폴백)"""
    rss_sources = [
        ("https://www.yonhapnewstv.co.kr/browse/feed/", "연합뉴스TV"),
        ("https://www.hankyung.com/feed/all-news", "한국경제"),
    ]
    headlines = []
    for url, name in rss_sources:
        try:
            resp = requests.get(url, headers=HEADERS, timeout=8)
            root = _parse_rss_safely(resp.content)
            if root is None:
                continue
            for item in root.findall(".//item")[:5]:
                title = item.findtext("title", "").strip()
                if title and len(title) > 5:
                    headlines.append(title)
        except Exception as e:
            print(f"[RSS 실패] {name}: {e}")
    print(f"[RSS 폴백] {len(headlines)}개 헤드라인 수집")
    return headlines


def _parse_rss_safely(content: bytes) -> ET.Element | None:
    """RSS XML 파싱 — 특수문자/인코딩 오류 허용"""
    import re
    try:
        return ET.fromstring(content)
    except ET.ParseError:
        pass
    try:
        # 잘못된 XML 문자 제거 후 재시도
        text = content.decode("utf-8", errors="replace")
        text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", "", text)
        return ET.fromstring(text.encode("utf-8"))
    except Exception:
        return None


def fetch_political_headlines() -> list[str]:
    """정치 뉴스 전문 소스 크롤링 — 보수/진보 매체 균형 수집"""
    sources = [
        ("https://www.chosun.com/arc/outboundfeeds/rss/category/politics/?outputType=xml", "조선일보"),  # 보수
        ("https://www.khan.co.kr/rss/rssdata/politic_news.xml", "경향신문"),    # 진보
        ("https://www.hani.co.kr/rss/politics/", "한겨레"),                     # 진보
        ("https://rss.ohmynews.com/rss/politics.xml", "오마이뉴스"),            # 진보
    ]
    headlines = []
    for url, name in sources:
        try:
            resp = requests.get(url, headers=HEADERS, timeout=8)
            root = _parse_rss_safely(resp.content)
            if root is None:
                print(f"[정치뉴스 파싱실패] {name}")
                continue
            for item in root.findall(".//item")[:4]:
                title = item.findtext("title", "").strip()
                if title and len(title) > 5:
                    headlines.append(f"[{name}] {title}")
        except Exception as e:
            print(f"[정치뉴스 실패] {name}: {e}")
    print(f"[정치뉴스] {len(headlines)}개 헤드라인 수집")
    return headlines


def fetch_daum_headlines() -> list[str]:
    """다음 뉴스 핫 이슈 크롤링 (추가 소스)"""
    url = "https://news.daum.net/"
    try:
        resp = requests.get(url, headers=HEADERS, timeout=10)
        soup = BeautifulSoup(resp.text, "lxml")
        headlines = []
        for selector in [".rank_news a", ".list_news2 a.link_txt", ".tit_thumb a"]:
            items = soup.select(selector)
            for item in items:
                text = item.get_text(strip=True)
                if 10 < len(text) < 80:
                    headlines.append(text)
            if headlines:
                break
        print(f"[다음] {len(headlines)}개 헤드라인 수집")
        return headlines[:10]
    except Exception as e:
        print(f"[다음 실패] {e}")
        return []


def collect_headlines() -> list[str]:
    """여러 소스에서 헤드라인 수집 — 정치 매체 우선 포함"""
    headlines = []

    # 1. 정치 전문 소스 (보수/진보 균형) — 항상 수집
    political = fetch_political_headlines()
    headlines += political

    # 2. 네이버 랭킹 (일반 인기 뉴스)
    headlines += fetch_naver_ranking_headlines()

    # 3. 다음 (보충)
    if len(headlines) < 10:
        headlines += fetch_daum_headlines()

    # 4. 폴백
    if len(headlines) < 5:
        headlines += fetch_naver_rss_headlines()

    # 중복 제거
    seen = set()
    unique = []
    for h in headlines:
        key = h[:20]
        if key not in seen:
            seen.add(key)
            unique.append(h)

    return unique[:30]


# ─── AI 토픽 생성 ─────────────────────────────────────────────────────────────

ALL_CATEGORIES = ["politics", "economy", "social", "culture", "daily", "love", "work", "story"]


def fetch_recent_topics(token: str, size: int = 50) -> list[dict]:
    """최근 토픽 목록 조회 (중복 방지용)"""
    try:
        resp = requests.get(
            f"{API_URL}/api/v1/topics",
            params={"sort": "latest", "size": size},
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        resp.raise_for_status()
        return resp.json().get("content", [])
    except Exception as e:
        print(f"[최근 토픽 조회 실패] {e}")
        return []


def pick_target_categories(recent_topics: list[dict], count: int, election_mode: bool = False) -> list[str]:
    """정치 40% 고정, 나머지는 덜 쓰인 카테고리 우선 배정.
    election_mode=True면 절반 이상을 politics로 강제 배정."""
    from collections import Counter
    recent_cats = [t.get("category", "") for t in recent_topics[:10]]
    cat_count = Counter(recent_cats)

    if election_mode:
        politics_slots = (count + 1) // 2
        other_slots = count - politics_slots
        others = sorted([c for c in ALL_CATEGORIES if c != "politics"], key=lambda c: cat_count.get(c, 0))
        targets = ["politics"] * politics_slots
        for i in range(other_slots):
            targets.append(others[i % len(others)])
        return targets

    # 정치 40~50% (5개 기준 2개, 10개 기준 5개)
    politics_slots = max(2, int(count * 0.45 + 0.5))
    other_slots = count - politics_slots
    others = sorted([c for c in ALL_CATEGORIES if c != "politics"], key=lambda c: cat_count.get(c, 0))

    # 연속 같은 카테고리 방지: politics를 분산 배치
    others_assigned = [others[i % len(others)] for i in range(other_slots)]
    # politics와 others를 번갈아 배치
    targets = []
    pi, oi = 0, 0
    while pi < politics_slots or oi < other_slots:
        if pi < politics_slots:
            targets.append("politics")
            pi += 1
        if oi < other_slots:
            targets.append(others_assigned[oi])
            oi += 1
    return targets[:count]


def _pick_pattern_order(count: int) -> list[int]:
    """패턴 인덱스 배정 — 연속 중복 없이, 골고루 회전"""
    n = len(TITLE_PATTERNS)
    base = list(range(n))
    random.shuffle(base)
    full = []
    while len(full) < count:
        chunk = base[:]
        random.shuffle(chunk)
        # 이전 끝 패턴과 다음 시작 패턴 충돌 방지
        if full and chunk[0] == full[-1]:
            if len(chunk) > 1:
                chunk[0], chunk[1] = chunk[1], chunk[0]
        full.extend(chunk)
    result = full[:count]
    # 혹시 남은 연속 중복 제거
    for i in range(1, len(result)):
        if result[i] == result[i - 1]:
            swap_with = next((j for j in range(i + 1, len(result)) if result[j] != result[i - 1]), None)
            if swap_with:
                result[i], result[swap_with] = result[swap_with], result[i]
    return result


def generate_topics_with_ai(
    headlines: list[str], count: int = 5, recent_topics: list[dict] = None,
    election_mode: bool = False,
) -> list[dict]:
    """Claude AI로 NANZZANG 스타일 대결 토픽 생성"""
    client = Anthropic()

    headlines_text = "\n".join(f"- {h}" for h in headlines[:20])
    today = datetime.now().strftime("%Y년 %m월 %d일")

    # 최근 토픽 컨텍스트 구성
    recent_topics = recent_topics or []
    target_categories = pick_target_categories(recent_topics, count, election_mode=election_mode)
    pattern_order = _pick_pattern_order(count)

    recent_titles_text = ""
    if recent_topics:
        titles = [t.get("title", "") for t in recent_topics[:10] if t.get("title")]
        recent_titles_text = f"""
[최근 등록된 토픽 — 이와 유사한 주제 절대 금지]
{chr(10).join(f"- {t}" for t in titles)}
"""

    # 각 토픽에 카테고리 + 제목 패턴 지정
    category_assignments = "\n".join(
        f"- 토픽 {i+1}번: 카테고리 = \"{cat}\", 제목패턴 = \"{TITLE_PATTERNS[pattern_order[i]]}\""
        for i, cat in enumerate(target_categories)
    )

    election_guide = ""
    if election_mode:
        election_guide = """
[선거 모드 — politics 카테고리 토픽 작성 특별 지침]
- 지금은 선거 기간이다. 정치 토픽은 실제 선거 이슈·후보·정당 갈등을 직접 건드려라
- 예시 소재: 공약 비교, 후보 자질 논란, 지지층 충돌, 정권 심판론 vs 안정론, 투표 인증 논란
- 특정 정당/후보를 일방적으로 비하하는 건 금지, 하지만 진영 대립 구도는 최대한 날카롭게
- A팀/B팀 이름은 실제 갈등 진영을 반영해라 (예: "심판해야" vs "지켜야", "정권교체" vs "정권유지")
"""

    prompt = f"""오늘은 {today}이다. 아래는 오늘 조선일보/한겨레/경향신문/오마이뉴스/중앙일보/네이버 랭킹 뉴스 헤드라인이다.

{headlines_text}
{recent_titles_text}{election_guide}
위 뉴스를 기반으로 NANZZANG(익명 갈등 대결 커뮤니티)에 올릴 폭발적인 대결 토픽 {count}개를 만들어라.

NANZZANG의 정체성:
- 대한민국의 모든 갈등, 분노, 적대감을 폭발시키는 전쟁터다
- 중립은 죽음이다. 반드시 A팀 아니면 B팀이다
- 읽는 순간 "씨X 저거 맞지" 또는 "저건 진짜 아니지" 반응이 터져야 한다
- 온라인 커뮤니티에서 캡처해서 퍼날라야 할 만큼 자극적이어야 한다
- A팀과 B팀은 완전히 반대 극단에 위치해야 한다 — 중간 입장 없음

[제목 패턴 사용 규칙 — 반드시 엄수]
각 토픽마다 지정된 패턴을 정확히 적용해라. "~" 부분에 해당 내용을 채워서 완성해라.

패턴 예시:
- "~가 옳냐 vs ~가 옳냐" → "민주당 옹호가 옳냐 vs 국힘 옹호가 옳냐"
- "~편이냐 vs ~편이냐" → "검찰편이냐 vs 야당편이냐"
- "~가 정상이냐 vs 비정상이냐" → "탄핵 집착이 정상이냐 vs 비정상이냐"
- "~해야 하냐 vs 말아야 하냐" → "지지 철회해야 하냐 vs 말아야 하냐"
- "이해돼? vs 말도 안 돼" → "이재명 무죄 이해돼? vs 말도 안 돼"
- "~가 문제냐 vs 쟤가 문제냐" → "여당이 문제냐 vs 쟤가 문제냐"
- "니편은? A vs B" → "니편은? 탄핵파 vs 사수파"
- "~한 거 당연하냐 vs 어이없냐" → "이탈표 던진 거 당연하냐 vs 어이없냐"

[정치 토픽 특별 지침 — politics 카테고리 필수 적용]
핵심 원칙: 조선일보 독자(보수)와 한겨레/오마이뉴스 독자(진보)가 각자 "저 새끼들 진짜 X같네" 하며 달려드는 구도를 만들어라.
한쪽이 일방적으로 욕먹는 구도 X → 양쪽 다 격하게 싸울 수 있는 구도 O

활용할 갈등 소재 (뉴스와 연결해서 사용):
- 탄핵/반탄핵 진영 충돌 — 서로가 서로를 매국노·내로남불로 보는 구도
- 정권 심판 vs 야당 심판 — "이 정권이 더 X" vs "야당이 집권하면 더 X"
- 여당 발언 vs 야당 발언 — 정치인 실언/망언 진영별 해석 충돌
- 586운동권 vs MZ보수 — 세대+이념 복합 갈등
- 친미 vs 친중 외교 노선 충돌
- 검찰개혁 찬반 — "검찰공화국" vs "방탄방패"
- 언론 편향 논란 — "조중동 쓰레기" vs "한겨레 좌빨"
- 노조·파업 갈등 — "귀족노조" vs "노동탄압"
- 부동산 정책 — "집값 올린 건 민주당" vs "집값 올린 건 국힘"

A/B팀 이름 예시 (이 수준의 날카로움 필수):
- "탄핵당연" vs "탄핵역적"
- "정권심판" vs "야당심판"
- "검찰해체" vs "방탄해체"
- "운동권OUT" vs "MZ꼴통"
- "조중동폐간" vs "한겨레폐간"
- "이재명무죄" vs "이재명유죄"
- "국힘해산해" vs "민주당해산"

정치인 실명·정당명 직접 언급 가능, 단 허위사실 적시 금지.

제목 작성 규칙:
1. 지정된 패턴을 반드시 사용 — 패턴 없는 제목 절대 금지
2. 읽자마자 욕 나오거나 격하게 공감해야 함
3. 뉴스를 한국인의 실제 분노 포인트로 재해석 (계층갈등, 세대갈등, 정치혐오 등)
4. 제목만 봐도 댓글 1000개 달릴 것 같아야 함
5. 혐오·차별(인종, 성별 비하 등)은 절대 금지, 갈등·분노·논쟁은 최대한 날카롭게
6. 최근 등록된 토픽과 주제·소재가 겹치면 절대 안 됨

body 작성 규칙:
- 독자의 분노 또는 공감을 즉시 폭발시켜야 한다
- 핵심 쟁점을 날카롭게 정리하되, 양쪽 진영 모두 격하게 반응할 수 있도록
- 마지막 문장은 반드시 극단적 편 가르기 유도 ("넌 어느 편이냐", "X같지 않냐" 식으로)
- 감정 자극 단어 적극 활용: 배신, 내로남불, 적폐, 빨갱이, 꼴통, 위선, 기만 등

A팀/B팀 이름 규칙:
- 읽는 순간 즉각 한 팀에 감정이입이 되어야 한다
- 짧고 강렬하게, 최대 8자 이내
- "찬성" "반대" "지지" 같은 무색무취 표현 절대 금지
- 두 팀은 완전히 반대 극단 — 중간 입장 없음

카테고리 배분 + 제목 패턴 — 반드시 아래 지시를 정확히 따를 것:
{category_assignments}

아래 JSON 형식으로만 반환해라. 절대 다른 텍스트 없이 JSON만:
[
  {{
    "title": "지정된 패턴을 적용한 폭발적 대결 제목 (최대 50자, 마침표 없이)",
    "body": "분노와 공감을 즉시 폭발시키는 배경 설명 2-3문장. 마지막은 극단적 편 가르기 유도.",
    "category": "카테고리",
    "teamAName": "극단적 A진영 이름 (최대 8자)",
    "teamBName": "극단적 B진영 이름 (최대 8자)",
    "durationDays": 7
  }}
]"""

    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=2000,
        messages=[{"role": "user", "content": prompt}],
    )

    raw = response.content[0].text.strip()
    if "```" in raw:
        raw = raw.split("```")[1]
        if raw.startswith("json"):
            raw = raw[4:]
    raw = raw.strip()

    topics = json.loads(raw)
    print(f"[AI] {len(topics)}개 토픽 생성 완료 (카테고리: {[t['category'] for t in topics]})")
    return topics


# ─── 유사 토픽 정리 ───────────────────────────────────────────────────────────


def clean_similar_topics(_token: str = None):
    """Claude AI로 유사/중복 토픽 탐지 후 자동 삭제 (어드민 계정 사용)"""
    # 어드민 로그인
    try:
        resp = requests.post(
            f"{API_URL}/api/v1/auth/login",
            json={"email": ADMIN_EMAIL, "nickname": ADMIN_NICKNAME},
            timeout=10,
        )
        resp.raise_for_status()
        admin_token = resp.json()["accessToken"]
        print(f"[정리] 어드민 로그인 성공")
    except Exception as e:
        print(f"[정리] 어드민 로그인 실패: {e}")
        return

    print("\n[정리] 전체 토픽 조회 중...")

    # 공개 API로 최대 100개 조회 (페이지 순회)
    all_topics = []
    for page in range(10):
        try:
            resp = requests.get(
                f"{API_URL}/api/v1/topics",
                params={"sort": "latest", "size": 10, "page": page},
                timeout=10,
            )
            resp.raise_for_status()
            data = resp.json()
            content = data.get("content", [])
            all_topics.extend(content)
            if data.get("last", True):
                break
        except Exception as e:
            print(f"[정리] 토픽 조회 실패: {e}")
            break

    token = admin_token

    if len(all_topics) < 2:
        print("[정리] 토픽이 2개 미만, 스킵")
        return

    print(f"[정리] 총 {len(all_topics)}개 토픽 분석 중...")

    topic_list_text = "\n".join(
        f"{i+1}. [{t.get('category','')}] {t.get('title','')} (id: {t.get('id','')})"
        for i, t in enumerate(all_topics)
    )

    client = Anthropic()
    prompt = f"""아래는 NANZZANG 커뮤니티에 등록된 토픽 목록이다.

{topic_list_text}

다음 기준으로 삭제해야 할 토픽의 id 목록을 골라라:
1. 주제나 소재가 거의 동일한 토픽이 여러 개면 → 가장 나중에 등록된 것만 남기고 나머지 삭제
2. 카테고리가 동일하고 갈등 구도가 비슷한 토픽이 연속으로 있으면 → 덜 자극적인 쪽 삭제
3. 제목이 너무 비슷하거나 A/B팀 구도가 거의 같은 토픽 → 하나만 남기고 삭제

삭제할 토픽 id 목록을 JSON 배열로만 반환해라. 삭제할 게 없으면 빈 배열 [].
절대 다른 텍스트 없이 JSON 배열만:
["id1", "id2", ...]"""

    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=500,
        messages=[{"role": "user", "content": prompt}],
    )

    raw = response.content[0].text.strip()
    if "```" in raw:
        raw = raw.split("```")[1]
        if raw.startswith("json"):
            raw = raw[4:]
    raw = raw.strip()

    try:
        ids_to_delete = json.loads(raw)
    except Exception:
        print(f"[정리] AI 응답 파싱 실패: {raw}")
        return

    if not ids_to_delete:
        print("[정리] 삭제할 유사 토픽 없음")
        return

    print(f"[정리] 유사 토픽 {len(ids_to_delete)}개 삭제 중...")
    deleted = 0
    for tid in ids_to_delete:
        try:
            resp = requests.delete(
                f"{API_URL}/api/v1/topics/admin/{tid}",
                headers={"Authorization": f"Bearer {token}"},
                timeout=10,
            )
            resp.raise_for_status()
            title = next((t.get("title","?") for t in all_topics if str(t.get("id","")) == str(tid)), tid)
            print(f"  ✓ 삭제: {title}")
            deleted += 1
        except Exception as e:
            print(f"  ✗ 삭제 실패 ({tid}): {e}")

    print(f"[정리] {deleted}개 삭제 완료")


# ─── API 등록 ─────────────────────────────────────────────────────────────────


def get_bot_token() -> str:
    """봇 계정 로그인 후 JWT 토큰 획득"""
    resp = requests.post(
        f"{API_URL}/api/v1/auth/login",
        json={"email": BOT_EMAIL, "nickname": BOT_NICKNAME},
        timeout=10,
    )
    resp.raise_for_status()
    token = resp.json()["accessToken"]
    print(f"[API] 봇 로그인 성공 (닉네임: {BOT_NICKNAME})")
    return token


def post_topic(topic: dict, token: str) -> dict:
    """토픽을 NANZZANG API에 등록"""
    resp = requests.post(
        f"{API_URL}/api/v1/topics",
        json=topic,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        timeout=10,
    )
    resp.raise_for_status()
    result = resp.json()
    print(f"  ✓ 등록: [{result['category']}] {result['title']}")
    return result


# ─── Meta Graph API 포스팅 ────────────────────────────────────────────────────


def _build_meta_message(topic_result: dict) -> str:
    title = topic_result.get("title", "")
    team_a = topic_result.get("teamAName", "A팀")
    team_b = topic_result.get("teamBName", "B팀")
    votes_a = topic_result.get("teamAVotes", 0)
    votes_b = topic_result.get("teamBVotes", 0)
    total = votes_a + votes_b
    pct_a = round(votes_a / total * 100) if total > 0 else 50
    pct_b = 100 - pct_a
    topic_id = topic_result.get("id", "")
    url = f"{NANZZANG_URL}/topic/{topic_id}" if topic_id else NANZZANG_URL

    category = topic_result.get("category", "social")
    base_tags = CATEGORY_HASHTAGS.get(category, ["#사회이슈", "#갈등"])
    common_tags = ["#난장", "#NANZZANG", "#대결투표", "#갈등"]
    # 제목 앞 6자로 키워드 해시태그
    keyword = title[:6].replace(" ", "")
    all_tags = ([f"#{keyword}"] if keyword else []) + base_tags + common_tags
    hashtags = " ".join(all_tags[:8])

    body = topic_result.get("body", "").strip()
    body_section = f"\n\n💬 {body}" if body else ""

    return (
        f"🔥 지금 난장에서 대결 중!\n\n"
        f"『 {title} 』"
        f"{body_section}\n\n"
        f"🔵 {team_a}  {pct_a}%\n"
        f"🔴 {team_b}  {pct_b}%\n\n"
        f"⚔️ 당신의 선택은?\n"
        f"👉 {url}\n\n"
        f"{hashtags}"
    )


def _post_to_facebook(message: str) -> bool:
    if not META_PAGE_ACCESS_TOKEN or not META_PAGE_ID:
        print("[Meta] Facebook 환경변수 미설정, 스킵")
        return False
    try:
        resp = requests.post(
            f"{META_GRAPH_BASE}/{META_PAGE_ID}/feed",
            data={"message": message, "access_token": META_PAGE_ACCESS_TOKEN},
            timeout=15,
        )
        resp.raise_for_status()
        print(f"  ✓ Facebook 포스팅 완료: {resp.json().get('id', '')}")
        return True
    except Exception as e:
        print(f"  ✗ Facebook 포스팅 실패: {e}")
        return False


def _post_to_instagram(message: str) -> bool:
    if not META_PAGE_ACCESS_TOKEN or not INSTAGRAM_ACCOUNT_ID:
        print("[Meta] Instagram 환경변수 미설정, 스킵")
        return False
    if not INSTAGRAM_IMAGE_URL:
        print("[Meta] Instagram: INSTAGRAM_IMAGE_URL 환경변수 없음, 스킵")
        return False
    try:
        # Step 1: 미디어 컨테이너 생성
        container = requests.post(
            f"{META_GRAPH_BASE}/{INSTAGRAM_ACCOUNT_ID}/media",
            data={
                "image_url": INSTAGRAM_IMAGE_URL,
                "caption": message,
                "access_token": META_PAGE_ACCESS_TOKEN,
            },
            timeout=15,
        )
        container.raise_for_status()
        creation_id = container.json().get("id")
        if not creation_id:
            print("  ✗ Instagram 컨테이너 생성 실패")
            return False

        time.sleep(3)  # IG 처리 대기

        # Step 2: 게시
        publish = requests.post(
            f"{META_GRAPH_BASE}/{INSTAGRAM_ACCOUNT_ID}/media_publish",
            data={"creation_id": creation_id, "access_token": META_PAGE_ACCESS_TOKEN},
            timeout=15,
        )
        publish.raise_for_status()
        print(f"  ✓ Instagram 포스팅 완료: {publish.json().get('id', '')}")
        return True
    except Exception as e:
        print(f"  ✗ Instagram 포스팅 실패: {e}")
        return False


def post_to_meta(topic_result: dict):
    """토픽 생성 후 Facebook 페이지 + Instagram 자동 포스팅"""
    if not META_PAGE_ACCESS_TOKEN:
        return  # 환경변수 미설정 시 조용히 스킵
    message = _build_meta_message(topic_result)
    _post_to_facebook(message)
    _post_to_instagram(message)


# ─── 트위터 자동 포스팅 ──────────────────────────────────────────────────────


def post_to_twitter(topic_result: dict):
    """토픽 생성 후 X(트위터) 자동 포스팅"""
    if not all([TWITTER_API_KEY, TWITTER_API_SECRET, TWITTER_ACCESS_TOKEN, TWITTER_ACCESS_TOKEN_SECRET]):
        return

    try:
        from requests_oauthlib import OAuth1Session

        title = topic_result.get("title", "")
        team_a = topic_result.get("teamAName", "A팀")
        team_b = topic_result.get("teamBName", "B팀")
        topic_id = topic_result.get("id", "")
        url = f"{NANZZANG_URL}/topic/{topic_id}" if topic_id else NANZZANG_URL
        category = topic_result.get("category", "social")
        hashtags = " ".join(CATEGORY_HASHTAGS.get(category, ["#갈등"])[:3])

        tweet = (
            f"🔥 {title}\n\n"
            f"🔵 {team_a} vs 🔴 {team_b}\n\n"
            f"⚔️ 당신의 선택은?\n"
            f"{url}\n\n"
            f"#난장 #NANZZANG {hashtags}"
        )

        if len(tweet) > 280:
            tweet = (
                f"🔥 {title[:60]}...\n\n"
                f"🔵 {team_a} vs 🔴 {team_b}\n\n"
                f"⚔️ {url}\n#난장 #NANZZANG"
            )

        oauth = OAuth1Session(
            TWITTER_API_KEY,
            client_secret=TWITTER_API_SECRET,
            resource_owner_key=TWITTER_ACCESS_TOKEN,
            resource_owner_secret=TWITTER_ACCESS_TOKEN_SECRET,
        )
        resp = oauth.post("https://api.twitter.com/2/tweets", json={"text": tweet})
        resp.raise_for_status()
        print(f"  ✓ 트위터 포스팅 완료")
    except Exception as e:
        print(f"  ✗ 트위터 포스팅 실패: {e}")


# ─── AI 논객 봇 ───────────────────────────────────────────────────────────────


def _debate_bot_login(email: str, nickname: str) -> str:
    resp = requests.post(
        f"{API_URL}/api/v1/auth/login",
        json={"email": email, "nickname": nickname},
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()["accessToken"]


def _get_debate_bot_tokens() -> tuple[str, str]:
    token_a = _debate_bot_login(BOT_A_EMAIL, BOT_A_NICKNAME)
    token_b = _debate_bot_login(BOT_B_EMAIL, BOT_B_NICKNAME)
    print(f"[봇] A팀논객 / B팀논객 로그인 완료")
    return token_a, token_b


def _generate_bot_comment(topic: dict, team_side: str, team_name: str, idx: int) -> str:
    """Claude Haiku로 해당 진영 댓글 생성"""
    client = Anthropic()
    opponent = topic["teamBName"] if team_side == "A" else topic["teamAName"]
    angle = "핵심 논거를 내세워" if idx == 1 else "다른 각도의 사례나 근거로 보충하며"

    prompt = f"""너는 익명 커뮤니티 NANZZANG에서 갈등 토론에 참여하는 일반 유저야.

현재 토픽: {topic['title']}
배경: {topic.get('body', '')}
A팀: {topic['teamAName']} / B팀: {topic['teamBName']}

너는 [{team_name}] 지지자야. {angle} 댓글 한 개를 써.

규칙:
- 상대방 [{opponent}] 입장을 직접 비판
- 감정적이고 직설적이되 논리 있게
- 100~200자 이내 한국어 구어체
- 인터넷 댓글 문체 (ㅋㅋ, ㄹㅇ, 솔직히 등 자연스럽게)
- 봇처럼 보이지 않게

댓글 내용만 출력 (따옴표·설명 없이):"""

    response = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=300,
        messages=[{"role": "user", "content": prompt}],
    )
    return response.content[0].text.strip()


def _generate_bot_reply(topic: dict, team_side: str, team_name: str, opponent_comment: str) -> str:
    """상대 댓글에 대한 반박 대댓글 생성"""
    client = Anthropic()
    opponent = topic["teamBName"] if team_side == "A" else topic["teamAName"]

    prompt = f"""너는 익명 커뮤니티 NANZZANG 토론 참여자야.

현재 토픽: {topic['title']}
A팀: {topic['teamAName']} / B팀: {topic['teamBName']}

상대방 [{opponent}]가 쓴 댓글:
"{opponent_comment}"

너는 [{team_name}] 지지자로서 위 댓글의 허점을 찌르는 대댓글을 써.

규칙:
- 상대 논리를 구체적으로 반박
- 감정적이고 직설적으로
- 80~150자 이내 한국어 구어체
- 인터넷 댓글 문체

대댓글 내용만 출력 (따옴표·설명 없이):"""

    response = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=200,
        messages=[{"role": "user", "content": prompt}],
    )
    return response.content[0].text.strip()


def _participate_as_bot(topic_id: str, team_side: str, token: str):
    resp = requests.post(
        f"{API_URL}/api/v1/topics/{topic_id}/participate",
        json={"teamSide": team_side},
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        timeout=10,
    )
    resp.raise_for_status()
    print(f"  ✓ {team_side}팀 투표 완료")


def _post_comment_as_bot(
    topic_id: str, content: str, team_side: str, token: str, parent_id: str = None
) -> dict:
    body = {"content": content, "teamSide": team_side}
    if parent_id:
        body["parentId"] = parent_id
    resp = requests.post(
        f"{API_URL}/api/v1/comments/topic/{topic_id}",
        json=body,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()


def run_debate_bots(topic_result: dict):
    """A/B 논객 봇이 토픽에 댓글·대댓글·투표를 자동 생성"""
    topic_id = topic_result.get("id")
    if not topic_id:
        print("  [봇] topic ID 없음, 스킵")
        return

    print(f"\n  [봇댓글] 논객 봇 시작...")

    try:
        token_a, token_b = _get_debate_bot_tokens()
    except Exception as e:
        print(f"  [봇댓글] 봇 로그인 실패: {e}")
        return

    # 투표 참전
    for side, token in [("A", token_a), ("B", token_b)]:
        try:
            _participate_as_bot(str(topic_id), side, token)
        except Exception as e:
            print(f"  ✗ {side}팀 투표 실패: {e}")

    # A팀 댓글 2개
    a_comments = []
    for i in range(2):
        try:
            content = _generate_bot_comment(topic_result, "A", topic_result["teamAName"], i + 1)
            comment = _post_comment_as_bot(str(topic_id), content, "A", token_a)
            a_comments.append(comment)
            print(f"  ✓ A팀 댓글{i+1}: {content[:40]}...")
            time.sleep(1)
        except Exception as e:
            print(f"  ✗ A팀 댓글{i+1} 실패: {e}")

    # B팀 댓글 2개
    b_comments = []
    for i in range(2):
        try:
            content = _generate_bot_comment(topic_result, "B", topic_result["teamBName"], i + 1)
            comment = _post_comment_as_bot(str(topic_id), content, "B", token_b)
            b_comments.append(comment)
            print(f"  ✓ B팀 댓글{i+1}: {content[:40]}...")
            time.sleep(1)
        except Exception as e:
            print(f"  ✗ B팀 댓글{i+1} 실패: {e}")

    # A가 B의 첫 댓글에 반박 대댓글
    if a_comments and b_comments:
        try:
            reply = _generate_bot_reply(
                topic_result, "A", topic_result["teamAName"],
                b_comments[0].get("content", "")
            )
            _post_comment_as_bot(str(topic_id), reply, "A", token_a,
                                  parent_id=str(b_comments[0]["id"]))
            print(f"  ✓ A팀 반박 대댓글: {reply[:40]}...")
            time.sleep(1)
        except Exception as e:
            print(f"  ✗ A팀 대댓글 실패: {e}")

        # B가 A의 첫 댓글에 반박 대댓글
        try:
            reply = _generate_bot_reply(
                topic_result, "B", topic_result["teamBName"],
                a_comments[0].get("content", "")
            )
            _post_comment_as_bot(str(topic_id), reply, "B", token_b,
                                  parent_id=str(a_comments[0]["id"]))
            print(f"  ✓ B팀 반박 대댓글: {reply[:40]}...")
        except Exception as e:
            print(f"  ✗ B팀 대댓글 실패: {e}")


# ─── 메인 ─────────────────────────────────────────────────────────────────────


def run(count: int = 5, dry_run: bool = False, election_mode: bool = False):
    print("=" * 60)
    label = "  NANZZANG 크롤러 봇 시작" + (" [선거 모드 🗳️]" if election_mode else "")
    print(label)
    print("=" * 60)

    # 1. 헤드라인 수집
    print("\n[1단계] 뉴스 헤드라인 수집 중...")
    headlines = collect_headlines()
    if not headlines:
        print("헤드라인을 수집하지 못했습니다. 종료합니다.")
        return

    # 2. 최근 토픽 조회 (중복·카테고리 분산용)
    print("\n[2단계] 최근 토픽 조회 중...")
    token = get_bot_token()
    recent_topics = fetch_recent_topics(token)
    print(f"  최근 토픽 {len(recent_topics)}개 확인")

    # 3. AI 토픽 생성
    print(f"\n[3단계] Claude AI로 토픽 {count}개 생성 중...")
    topics = generate_topics_with_ai(headlines, count, recent_topics, election_mode=election_mode)

    if dry_run:
        print("\n[Dry Run] 생성된 토픽 미리보기:")
        print(json.dumps(topics, ensure_ascii=False, indent=2))
        return

    # 4. API 등록 (하드 중복 필터 적용)
    print(f"\n[4단계] NANZZANG API 등록 중 ({API_URL})...")
    recent_titles = [t.get("title", "") for t in recent_topics]

    def _is_too_similar(new_title: str, existing: list[str]) -> bool:
        """기존 제목과 6자 이상 겹치는 키워드가 있으면 중복으로 판단"""
        new_words = set(new_title.replace(" ", ""))
        for old in existing:
            old_words = set(old.replace(" ", ""))
            overlap = len(new_words & old_words)
            if overlap >= 8:
                return True
        return False

    success = 0
    for topic in topics:
        title = topic.get("title", "?")
        if _is_too_similar(title, recent_titles):
            print(f"  ⚠ 유사 토픽 건너뜀: {title}")
            continue
        try:
            result = post_topic(topic, token)
            recent_titles.append(title)  # 이번 배치 내 중복도 방지
            success += 1
            post_to_meta(result)
            post_to_twitter(result)
            run_debate_bots(result)
            time.sleep(0.5)
        except Exception as e:
            print(f"  ✗ 등록 실패: {title} — {e}")

    print(f"\n✅ 완료: {success}/{len(topics)}개 토픽 등록됨")

    # 5. 등록 후 기존 유사 토픽 AI 정리
    print("\n[5단계] 유사/중복 토픽 AI 정리 중...")
    clean_similar_topics(token)

    print("=" * 60)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="NANZZANG 크롤러 봇")
    parser.add_argument("--count", type=int, default=5, help="생성할 토픽 수 (기본값: 5)")
    parser.add_argument("--dry-run", action="store_true", help="API 등록 없이 미리보기만")
    parser.add_argument("--clean", action="store_true", help="유사/중복 토픽 일회성 정리 (수동 실행 전용)")
    parser.add_argument("--election", action="store_true", help="선거 모드: 정치 토픽 비중 절반 이상으로 증가")
    args = parser.parse_args()

    election = args.election or ELECTION_MODE

    if args.clean:
        print("=" * 60)
        print("  NANZZANG 유사 토픽 정리 (일회성)")
        print("=" * 60)
        token = get_bot_token()
        clean_similar_topics(token)
    else:
        run(count=args.count, dry_run=args.dry_run, election_mode=election)
