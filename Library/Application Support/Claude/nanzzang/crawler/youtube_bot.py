"""
NANZZANG YouTube Shorts 자동 생성 및 업로드 봇
- NANZZANG API 핫 토픽 → 영상 생성 (PIL + ffmpeg + gTTS) → YouTube Shorts 업로드
"""

import os
import json
import base64
import tempfile
import subprocess
import requests
from datetime import datetime
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from gtts import gTTS

from google.oauth2.credentials import Credentials
from google.auth.transport.requests import Request
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

# ── 환경변수 ──────────────────────────────────────────────────
NANZZANG_API_URL = os.getenv("NANZZANG_API_URL", "https://nanzzang-api-production.up.railway.app")
NANZZANG_EMAIL   = os.getenv("NANZZANG_EMAIL", "bot@nanzzang.com")
NANZZANG_PASS    = os.getenv("NANZZANG_PASSWORD", "")
YOUTUBE_CLIENT_SECRET_JSON = os.getenv("YOUTUBE_CLIENT_SECRET_JSON", "")  # base64 인코딩된 client_secret.json
YOUTUBE_TOKEN_JSON         = os.getenv("YOUTUBE_TOKEN_JSON", "")          # base64 인코딩된 token.json
YOUTUBE_CHANNEL_ID = "UCvA2htvJ2DyhIYpsUwTeCMg"

SCOPES = ["https://www.googleapis.com/auth/youtube.upload"]

# ── 영상 설정 ─────────────────────────────────────────────────
W, H = 1080, 1920          # Shorts 9:16
FPS  = 30
FONT_PATH = "/usr/share/fonts/truetype/noto/NotoSansKR-Bold.ttf"
FONT_PATH_FALLBACK = str(Path(__file__).parent / "NotoSansKR-Bold.ttf")

BG_COLOR      = (10, 10, 10)
CYAN          = (0, 245, 255)
RED           = (255, 0, 60)
PURPLE        = (180, 0, 255)
WHITE         = (255, 255, 255)
GRAY          = (80, 80, 80)


# ── 폰트 로드 ─────────────────────────────────────────────────
def load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in [FONT_PATH, FONT_PATH_FALLBACK]:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


# ── 텍스트 줄 자동 wrap ───────────────────────────────────────
def wrap_text(text: str, font: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    words = text.split()
    lines, current = [], ""
    for word in words:
        test = (current + " " + word).strip()
        bbox = font.getbbox(test)
        if bbox[2] - bbox[0] <= max_width:
            current = test
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_centered_text(draw: ImageDraw.Draw, text: str, y: int, font: ImageFont.FreeTypeFont,
                        color: tuple, max_width: int = W - 80) -> int:
    lines = wrap_text(text, font, max_width)
    for line in lines:
        bbox = font.getbbox(line)
        x = (W - (bbox[2] - bbox[0])) // 2
        draw.text((x, y), line, font=font, fill=color)
        y += (bbox[3] - bbox[1]) + 14
    return y


# ── 프레임 이미지 생성 ────────────────────────────────────────
def make_frame(topic: dict, frame_type: str) -> Image.Image:
    img  = Image.new("RGB", (W, H), BG_COLOR)
    draw = ImageDraw.Draw(img)

    font_logo  = load_font(72)
    font_title = load_font(54 if len(topic["title"]) > 20 else 64)
    font_team  = load_font(52)
    font_vs    = load_font(96)
    font_small = load_font(36)
    font_cta   = load_font(44)

    # 배경 그라디언트 효과 (간단한 원형 글로우)
    for r, alpha in [(400, 12), (300, 8), (200, 5)]:
        overlay = Image.new("RGB", (W, H), BG_COLOR)
        od = ImageDraw.Draw(overlay)
        od.ellipse([(W//2 - r, H//2 - r), (W//2 + r, H//2 + r)],
                   fill=(30, 0, 60))
        img = Image.blend(img, overlay, alpha / 100)
        draw = ImageDraw.Draw(img)

    if frame_type == "intro":
        # NANZZANG 로고
        y = H // 2 - 120
        draw.text(((W - font_logo.getbbox("⚔️ NANZZANG")[2]) // 2, y),
                  "⚔️ NANZZANG", font=font_logo, fill=PURPLE)
        y += 120
        draw.text(((W - font_small.getbbox("세상 모든 갈등의 대결장")[2]) // 2, y),
                  "세상 모든 갈등의 대결장", font=font_small, fill=GRAY)

    elif frame_type == "topic":
        # 카테고리 + 제목
        y = 280
        draw.text(((W - font_small.getbbox("🔥 지금 난장판 벌어지는 중")[2]) // 2, y),
                  "🔥 지금 난장판 벌어지는 중", font=font_small, fill=RED)
        y += 80
        y = draw_centered_text(draw, topic["title"], y, font_title, WHITE)

    elif frame_type == "versus":
        # A vs B
        a_name = topic["teamAName"]
        b_name = topic["teamBName"]

        # A팀 박스
        draw.rounded_rectangle([(40, 380), (W//2 - 20, 760)], radius=24,
                                fill=(0, 30, 35), outline=CYAN, width=3)
        draw.text(((W//2 - 20 + 40) // 2 - font_small.getbbox("A팀")[2]//2, 420),
                  "A팀", font=font_small, fill=CYAN)
        draw_centered_text(draw, a_name, 490, font_team, CYAN, max_width=W//2 - 80)

        # VS
        vs_bbox = font_vs.getbbox("VS")
        draw.text(((W - (vs_bbox[2] - vs_bbox[0])) // 2, 530),
                  "VS", font=font_vs, fill=PURPLE)

        # B팀 박스
        draw.rounded_rectangle([(W//2 + 20, 380), (W - 40, 760)], radius=24,
                                fill=(35, 0, 12), outline=RED, width=3)
        draw.text(((W//2 + 20 + W - 40) // 2 - font_small.getbbox("B팀")[2]//2, 420),
                  "B팀", font=font_small, fill=RED)
        draw_centered_text(draw, b_name, 490, font_team, RED, max_width=W//2 - 80)

        # 투표 현황
        total = (topic.get("teamAVotes", 0) or 0) + (topic.get("teamBVotes", 0) or 0)
        if total > 0:
            a_pct = topic["teamAVotes"] / total
            bar_y, bar_h = 820, 28
            # A팀 바
            draw.rounded_rectangle([(40, bar_y), (40 + int((W - 80) * a_pct), bar_y + bar_h)],
                                    radius=6, fill=CYAN)
            # B팀 바
            draw.rounded_rectangle([(40 + int((W - 80) * a_pct), bar_y), (W - 40, bar_y + bar_h)],
                                    radius=6, fill=RED)
            a_txt = f"{a_pct*100:.0f}%"
            b_txt = f"{(1-a_pct)*100:.0f}%"
            draw.text((50, bar_y + 44), a_txt, font=font_small, fill=CYAN)
            draw.text((W - 50 - font_small.getbbox(b_txt)[2], bar_y + 44), b_txt, font=font_small, fill=RED)
            draw.text(((W - font_small.getbbox(f"총 {total}명 참전")[2]) // 2, bar_y + 44),
                      f"총 {total}명 참전", font=font_small, fill=GRAY)

    elif frame_type == "cta":
        y = H // 2 - 200
        draw.text(((W - font_cta.getbbox("당신의 선택은?")[2]) // 2, y),
                  "당신의 선택은?", font=font_cta, fill=WHITE)
        y += 100
        draw.text(((W - font_small.getbbox("지금 바로 참전하세요")[2]) // 2, y),
                  "지금 바로 참전하세요", font=font_small, fill=GRAY)
        y += 100
        draw.rounded_rectangle([(80, y), (W - 80, y + 90)], radius=20,
                                fill=PURPLE)
        url = "nanzzang.co.kr"
        draw.text(((W - font_cta.getbbox(url)[2]) // 2, y + 22),
                  url, font=font_cta, fill=WHITE)
        y += 140
        draw.text(((W - font_logo.getbbox("⚔️ NANZZANG")[2]) // 2, y),
                  "⚔️ NANZZANG", font=font_logo, fill=PURPLE)

    return img


# ── TTS 생성 ─────────────────────────────────────────────────
def make_tts(text: str, path: str):
    tts = gTTS(text=text, lang="ko")
    tts.save(path)


# ── 영상 조합 (ffmpeg) ────────────────────────────────────────
def build_video(topic: dict, output_path: str) -> bool:
    with tempfile.TemporaryDirectory() as tmp:
        # 각 씬 이미지 생성
        scenes = [
            ("intro",   3,  f"⚔️ 낭짱! 세상 모든 갈등의 대결장"),
            ("topic",   6,  f"{topic['title']}"),
            ("versus",  10, f"{topic['teamAName']} 대 {topic['teamBName']}, 과연 누가 이길까요?"),
            ("cta",     5,  "지금 바로 낭짱에서 참전하세요!"),
        ]

        video_parts = []
        for i, (frame_type, duration, tts_text) in enumerate(scenes):
            img_path   = f"{tmp}/frame_{i}.png"
            audio_path = f"{tmp}/audio_{i}.mp3"
            part_path  = f"{tmp}/part_{i}.mp4"

            # 이미지 저장
            make_frame(topic, frame_type).save(img_path)

            # TTS
            make_tts(tts_text, audio_path)

            # 이미지 + 오디오 → 영상 클립
            result = subprocess.run([
                "ffmpeg", "-y",
                "-loop", "1", "-i", img_path,
                "-i", audio_path,
                "-c:v", "libx264", "-c:a", "aac",
                "-shortest",
                "-t", str(duration),
                "-vf", f"scale={W}:{H}",
                "-pix_fmt", "yuv420p",
                part_path,
            ], capture_output=True)

            if result.returncode != 0:
                print(f"[YouTube] ffmpeg 실패: {result.stderr.decode()}")
                return False

            video_parts.append(part_path)

        # 클립 합치기
        concat_list = f"{tmp}/concat.txt"
        with open(concat_list, "w") as f:
            for p in video_parts:
                f.write(f"file '{p}'\n")

        result = subprocess.run([
            "ffmpeg", "-y",
            "-f", "concat", "-safe", "0", "-i", concat_list,
            "-c", "copy",
            output_path,
        ], capture_output=True)

        if result.returncode != 0:
            print(f"[YouTube] 합치기 실패: {result.stderr.decode()}")
            return False

    return True


# ── YouTube 인증 ──────────────────────────────────────────────
def get_youtube_client():
    if not YOUTUBE_CLIENT_SECRET_JSON:
        raise RuntimeError("YOUTUBE_CLIENT_SECRET_JSON 환경변수 없음")

    with tempfile.TemporaryDirectory() as tmp:
        secret_path = f"{tmp}/client_secret.json"
        token_path  = f"{tmp}/token.json"

        with open(secret_path, "w") as f:
            f.write(base64.b64decode(YOUTUBE_CLIENT_SECRET_JSON).decode())

        creds = None
        if YOUTUBE_TOKEN_JSON:
            token_data = base64.b64decode(YOUTUBE_TOKEN_JSON).decode()
            with open(token_path, "w") as f:
                f.write(token_data)
            creds = Credentials.from_authorized_user_file(token_path, SCOPES)

        if not creds or not creds.valid:
            if creds and creds.expired and creds.refresh_token:
                creds.refresh(Request())
            else:
                flow = InstalledAppFlow.from_client_secrets_file(secret_path, SCOPES)
                creds = flow.run_local_server(port=0)

        return build("youtube", "v3", credentials=creds)


# ── YouTube 업로드 ────────────────────────────────────────────
def upload_to_youtube(youtube, video_path: str, topic: dict) -> str | None:
    title       = f"[난장판] {topic['title'][:80]}"
    description = (
        f"{topic['teamAName']} vs {topic['teamBName']}\n\n"
        f"{topic.get('body', '')}\n\n"
        f"⚔️ 지금 참전하기 → https://nanzzang.co.kr/topic/{topic['id']}\n"
        f"#난짱 #NANZZANG #찬반 #대결 #Shorts"
    )

    body = {
        "snippet": {
            "title": title,
            "description": description,
            "tags": ["난짱", "NANZZANG", "찬반", "대결", "편가르기", "Shorts"],
            "categoryId": "22",  # People & Blogs
            "defaultLanguage": "ko",
        },
        "status": {
            "privacyStatus": "public",
            "selfDeclaredMadeForKids": False,
        },
    }

    media = MediaFileUpload(video_path, mimetype="video/mp4", resumable=True)
    request = youtube.videos().insert(part="snippet,status", body=body, media_body=media)

    response = None
    while response is None:
        _, response = request.next_chunk()

    video_id = response.get("id")
    print(f"[YouTube] 업로드 완료: https://youtube.com/shorts/{video_id}")
    return video_id


# ── 토픽 가져오기 ─────────────────────────────────────────────
def fetch_hot_topics(n: int = 3) -> list[dict]:
    res = requests.get(f"{NANZZANG_API_URL}/api/v1/topics",
                       params={"sort": "hot", "size": n}, timeout=10)
    res.raise_for_status()
    return res.json().get("content", [])


# ── 이미 업로드한 토픽 추적 (로컬 파일) ──────────────────────
UPLOADED_FILE = Path(__file__).parent / ".youtube_uploaded.json"

def load_uploaded() -> set:
    if UPLOADED_FILE.exists():
        return set(json.loads(UPLOADED_FILE.read_text()))
    return set()

def save_uploaded(ids: set):
    UPLOADED_FILE.write_text(json.dumps(list(ids)))


# ── 메인 ─────────────────────────────────────────────────────
def run(count: int = 1):
    print(f"[YouTube] 봇 시작 ({count}개)", flush=True)

    uploaded = load_uploaded()
    topics   = fetch_hot_topics(10)
    new_topics = [t for t in topics if t["id"] not in uploaded][:count]

    if not new_topics:
        print("[YouTube] 새로운 토픽 없음, 스킵", flush=True)
        return

    try:
        youtube = get_youtube_client()
    except Exception as e:
        print(f"[YouTube] 인증 실패: {e}", flush=True)
        return

    for topic in new_topics:
        print(f"[YouTube] 영상 생성 중: {topic['title'][:50]}", flush=True)
        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as f:
            output_path = f.name

        try:
            if not build_video(topic, output_path):
                continue
            video_id = upload_to_youtube(youtube, output_path, topic)
            if video_id:
                uploaded.add(topic["id"])
                save_uploaded(uploaded)
        except Exception as e:
            print(f"[YouTube] 오류: {e}", flush=True)
        finally:
            Path(output_path).unlink(missing_ok=True)

    print("[YouTube] 완료", flush=True)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=1)
    args = parser.parse_args()
    run(args.count)
