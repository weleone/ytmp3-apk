"""Chaquopy 侧: 本机 yt-dlp 纯下载 (转码/合并由 Java 侧 ffmpeg-kit 做)."""
import os


def _download_one(url, fmt_expr, out_dir, prefix):
    """下载单个流，返回文件路径。"""
    from yt_dlp import YoutubeDL
    from yt_dlp.utils import DownloadError
    ydl_opts = {
        "outtmpl": os.path.join(out_dir, prefix + ".%(ext)s"),
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "format": fmt_expr,
    }
    with YoutubeDL(ydl_opts) as ydl:
        ydl.download([url])
    files = [os.path.join(out_dir, f) for f in os.listdir(out_dir)
             if f.startswith(prefix + ".")
             and os.path.isfile(os.path.join(out_dir, f))]
    if not files:
        raise RuntimeError("下载失败: " + fmt_expr)
    files.sort(key=lambda p: os.path.getsize(p), reverse=True)
    return files[0]


def download(url, fmt, out_dir, resolution="1080"):
    from yt_dlp.utils import DownloadError

    os.makedirs(out_dir, exist_ok=True)

    def clean():
        for f in os.listdir(out_dir):
            fp = os.path.join(out_dir, f)
            try:
                if os.path.isfile(fp):
                    os.remove(fp)
            except OSError:
                pass

    clean()

    # 分辨率兜底：只接受纯数字
    res = "".join(c for c in str(resolution) if c.isdigit()) or "1080"

    if fmt == "mp3":
        # 音频：单个最佳音频流
        for expr in ["bestaudio/best"]:
            try:
                return _download_one(url, expr, out_dir, "raw")
            except DownloadError:
                continue
        raise RuntimeError("音频下载失败")

    # 视频：分离下载视频流 + 音频流，Java 侧用 ffmpeg 合并转码。
    # 不用单个 b/best——它有时是纯音频或纯视频，会导致黑屏。
    video_exprs = [
        "bv[ext=mp4][height<=%s]/bv[ext=mp4]/bv" % res,
        "bv[height<=%s]/bv" % res,
    ]
    audio_exprs = [
        "ba[ext=m4a]/ba",
    ]
    last_err = None
    video_path = None
    for expr in video_exprs:
        try:
            video_path = _download_one(url, expr, out_dir, "video")
            last_err = None
            break
        except DownloadError as e:
            last_err = e
    if video_path is None:
        raise last_err if last_err else RuntimeError("视频流下载失败")
    audio_path = None
    for expr in audio_exprs:
        try:
            audio_path = _download_one(url, expr, out_dir, "audio")
            break
        except DownloadError:
            continue
    # 音频可选：没有也行，返回视频单路径
    if audio_path:
        return video_path + "|" + audio_path
    return video_path


def get_title(url):
    from yt_dlp import YoutubeDL
    with YoutubeDL({"quiet": True, "no_warnings": True, "noplaylist": True}) as ydl:
        info = ydl.extract_info(url, download=False)
        return (info.get("title") or "audio").strip()
