"""
NANZZANG 블로그 봇
- 네이버/다음 뉴스 핫이슈 크롤링
- Claude AI로 SEO 최적화 블로그 글 자동 생성
- Ghost 블로그에 자동 게시
- 글 말미에 관련 NANZZANG 토픽 링크 삽입 (자연스러운 홍보)

사용법:
  python blog_bot.py              # 1개 글 생성/게시
  python blog_bot.py --count 3   # 3개 글 생성/게시
  python blog_bot.py --dry-run   # 게시 없이 미리보기만

환경변수:
  GHOST_URL              Ghost 블로그 URL (예: https://myblog.com)
  GHOST_ADMIN_API_KEY    Ghost Admin API Key (id:secret 형식)
  NANZZANG_API_URL       NANZZANG 백엔드 URL
  ANTHROPIC_API_KEY      Claude API 키
"""

import os
import json
import time
import argparse
import struct
import hmac
import hashlib
import base64
import requests
from datetime import datetime
from anthropic import Anthropic
from dotenv import load_dotenv
from crawler import collect_headlines

load_dotenv()

GHOST_URL = os.getenv("GHOST_URL", "http://localhost:2368").rstrip("/")
GHOST_ADMIN_API_KEY = os.getenv("GHOST_ADMIN_API_KEY", "")
NANZZANG_URL = os.getenv("NANZZANG_URL", "https://nanzzang-web.vercel.app")
NANZZANG_API_URL = os.getenv("NANZZANG_API_URL", "http://localhost:8080")


# ─── Ghost Admin API 인증 ─────────────────────────────────────────────────────


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def get_ghost_jwt() -> str:
    """Ghost Admin API용 JWT 토큰 생성 (PyJWT 없이 직접 구현)"""
    if not GHOST_ADMIN_API_KEY or ":" not in GHOST_ADMIN_API_KEY:
        raise ValueError("GHOST_ADMIN_API_KEY가 설정되지 않았습니다 (id:secret 형식)")

    key_id, secret_hex = GHOST_ADMIN_API_KEY.split(":", 1)
    secret = bytes.fromhex(secret_hex)

    now = int(datetime.utcnow().timestamp())
    header = _b64url(json.dumps({"alg": "HS256", "typ": "JWT", "kid": key_id}).encode())
    payload = _b64url(json.dumps({"iat": now, "exp": now + 300, "aud": "/admin/"}).encode())

    sig_input = f"{header}.{payload}".encode()
    sig = _b64url(hmac.new(secret, sig_input, hashlib.sha256).digest())

    return f"{header}.{payload}.{sig}"


# ─── NANZZANG 토픽 조회 ───────────────────────────────────────────────────────


def fetch_nanzzang_topics(limit: int = 20) -> list[dict]:
    """최근 NANZZANG 토픽 조회 (블로그 글 말미 링크용)"""
    try:
        resp = requests.get(
            f"{NANZZANG_API_URL}/api/v1/topics",
            params={"sort": "hot", "size": limit},
            timeout=10,
        )
        resp.raise_for_status()
        topics = resp.json().get("content", [])
        # 진행 중인 토픽만
        return [t for t in topics if not t.get("isClosed", False)]
    except Exception as e:
        print(f"[NANZZANG] 토픽 조회 실패: {e}")
        return []


# ─── AI 블로그 글 생성 ────────────────────────────────────────────────────────


def generate_blog_post(headlines: list[str], nanzzang_topics: list[dict]) -> dict:
    """Claude AI로 SEO 최적화 블로그 글 생성"""
    client = Anthropic()
    today = datetime.now().strftime("%Y년 %m월 %d일")

    headlines_text = "\n".join(f"- {h}" for h in headlines[:10])
    topics_text = "\n".join(
        f"- {t['title']} | {t.get('teamAName','A')} vs {t.get('teamBName','B')} | id: {t['id']}"
        for t in nanzzang_topics[:10]
    )

    prompt = f"""오늘은 {today}이다. 아래 뉴스 헤드라인 중 가장 핫한 이슈를 골라 SEO 최적화 한국어 블로그 글을 작성해라.

[오늘 뉴스 헤드라인]
{headlines_text}

[현재 NANZZANG 대결 중인 토픽]
{topics_text}

작성 요구사항:
1. 제목: 핵심 키워드 포함, 사람들이 검색할 만한 표현으로 (40-60자)
2. 메타 디스크립션: 검색 결과에 뜨는 요약, 클릭 유도 (120자 이내)
3. 본문: 최소 1500자, H2/H3 소제목으로 구조화, 정보성+흥미로운 어투
4. 마지막에 NANZZANG 관련 토픽을 자연스럽게 연결 (위 토픽 목록에서 관련 있는 것 1-2개 선택)
5. 태그: SEO용 키워드 태그 3-5개

HTML 본문 작성 규칙:
- <h2>, <h3>, <p>, <ul>, <li>, <strong>, <em> 태그만 사용
- 코드블록, 테이블 사용 금지
- 자연스러운 한국어 문체

아래 JSON 형식으로만 반환 (다른 텍스트 절대 없이):
{{
  "title": "블로그 글 제목",
  "meta_description": "메타 디스크립션",
  "html": "<h2>소제목</h2><p>본문...</p>...",
  "tags": ["태그1", "태그2", "태그3"],
  "related_topic_ids": ["uuid1", "uuid2"],
  "chosen_headline": "선택한 헤드라인"
}}"""

    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=5000,
        tools=[{
            "name": "create_blog_post",
            "description": "SEO 최적화 블로그 포스트 데이터 반환",
            "input_schema": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "블로그 글 제목 (40-60자)"},
                    "meta_description": {"type": "string", "description": "메타 디스크립션 (120자 이내)"},
                    "html": {"type": "string", "description": "HTML 본문 (최소 1500자)"},
                    "tags": {"type": "array", "items": {"type": "string"}, "description": "SEO 태그 3-5개"},
                    "related_topic_ids": {"type": "array", "items": {"type": "string"}, "description": "관련 NANZZANG 토픽 UUID"},
                    "chosen_headline": {"type": "string", "description": "선택한 뉴스 헤드라인"},
                },
                "required": ["title", "meta_description", "html", "tags", "chosen_headline"],
            },
        }],
        tool_choice={"type": "tool", "name": "create_blog_post"},
        messages=[{"role": "user", "content": prompt}],
    )

    data = response.content[0].input
    print(f"  선택된 헤드라인: {data.get('chosen_headline', '?')}")
    return data


# ─── NANZZANG 홍보 섹션 ───────────────────────────────────────────────────────


def build_nanzzang_section(related_ids: list[str], all_topics: list[dict]) -> str:
    """글 말미 NANZZANG 토픽 링크 섹션 HTML"""
    matched = [t for t in all_topics if str(t.get("id", "")) in (related_ids or [])]

    if not matched:
        return (
            f'<h2>🔥 NANZZANG에서 직접 투표하기</h2>'
            f'<p>이 이슈에 대해 당신의 생각은? <strong>NANZZANG</strong>에서 편을 골라 대결에 참여해보세요!</p>'
            f'<p><a href="{NANZZANG_URL}" target="_blank" rel="noopener">👉 NANZZANG 바로가기</a></p>'
        )

    items = ""
    for t in matched:
        topic_url = f"{NANZZANG_URL}/topic/{t['id']}"
        team_a = t.get("teamAName", "A팀")
        team_b = t.get("teamBName", "B팀")
        votes_a = t.get("teamAVotes", 0)
        votes_b = t.get("teamBVotes", 0)
        total = votes_a + votes_b
        pct_a = round(votes_a / total * 100) if total > 0 else 50
        pct_b = 100 - pct_a

        items += (
            f'<li>'
            f'<strong>{t["title"]}</strong><br>'
            f'🔵 {team_a} {pct_a}% vs 🔴 {team_b} {pct_b}%<br>'
            f'<a href="{topic_url}" target="_blank" rel="noopener">⚔️ 지금 투표하러 가기</a>'
            f'</li>'
        )

    return (
        f'<h2>🔥 NANZZANG에서 직접 투표하기</h2>'
        f'<p>이 이슈, 당신은 어느 편인가요? <strong>NANZZANG</strong>에서 지금 대결이 펼쳐지고 있습니다!</p>'
        f'<ul>{items}</ul>'
        f'<p><a href="{NANZZANG_URL}" target="_blank" rel="noopener">👉 NANZZANG 더 많은 대결 보기</a></p>'
    )


# ─── Ghost 게시 ───────────────────────────────────────────────────────────────


def post_to_ghost(post_data: dict, nanzzang_topics: list[dict]) -> dict:
    """Ghost Admin API로 블로그 글 게시"""
    token = get_ghost_jwt()

    nanzzang_html = build_nanzzang_section(
        post_data.get("related_topic_ids", []), nanzzang_topics
    )
    full_html = post_data["html"] + nanzzang_html

    tags = [{"name": tag} for tag in post_data.get("tags", [])]
    tags.append({"name": "NANZZANG"})
    tags.append({"name": "핫이슈"})

    payload = {
        "posts": [{
            "title": post_data["title"],
            "html": full_html,
            "status": "published",
            "meta_description": post_data.get("meta_description", ""),
            "tags": tags,
        }]
    }

    resp = requests.post(
        f"{GHOST_URL}/ghost/api/admin/posts/?source=html",
        json=payload,
        headers={
            "Authorization": f"Ghost {token}",
            "Content-Type": "application/json",
        },
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()["posts"][0]


# ─── 메인 ─────────────────────────────────────────────────────────────────────


def run(count: int = 1, dry_run: bool = False):
    print("=" * 60)
    print("  NANZZANG 블로그 봇 시작")
    print("=" * 60)

    print("\n[1단계] 뉴스 헤드라인 수집 중...")
    headlines = collect_headlines()
    if not headlines:
        print("헤드라인 수집 실패. 종료합니다.")
        return
    print(f"  {len(headlines)}개 헤드라인 수집 완료")

    print("\n[2단계] NANZZANG 핫 토픽 조회 중...")
    nanzzang_topics = fetch_nanzzang_topics()
    print(f"  {len(nanzzang_topics)}개 진행 중인 토픽 확인")

    success = 0
    used_headlines = list(headlines)

    for i in range(count):
        print(f"\n[블로그 {i+1}/{count}] Claude AI 글 생성 중...")
        try:
            post_data = generate_blog_post(used_headlines, nanzzang_topics)

            # 선택된 헤드라인 제거 (다음 글은 다른 헤드라인)
            chosen = post_data.get("chosen_headline", "")
            used_headlines = [h for h in used_headlines if h != chosen]

            if dry_run:
                print("\n--- 미리보기 ---")
                print(f"제목: {post_data['title']}")
                print(f"메타: {post_data['meta_description']}")
                print(f"태그: {post_data['tags']}")
                print(f"관련 NANZZANG: {post_data.get('related_topic_ids', [])}")
                print(f"HTML 길이: {len(post_data['html'])}자")
                print("----------------")
                success += 1
                continue

            result = post_to_ghost(post_data, nanzzang_topics)
            slug = result.get("slug", "")
            print(f"  ✓ 게시 완료: {result['title']}")
            print(f"    URL: {GHOST_URL}/{slug}")
            success += 1
            time.sleep(3)

        except Exception as e:
            print(f"  ✗ 실패: {e}")

    print(f"\n✅ 완료: {success}/{count}개 글 게시됨")
    print("=" * 60)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="NANZZANG 블로그 봇")
    parser.add_argument("--count", type=int, default=1, help="생성할 글 수 (기본값: 1)")
    parser.add_argument("--dry-run", action="store_true", help="게시 없이 미리보기만")
    args = parser.parse_args()

    run(count=args.count, dry_run=args.dry_run)
