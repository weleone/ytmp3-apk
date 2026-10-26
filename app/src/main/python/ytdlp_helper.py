"""Chaquopy 侧: 本机 yt-dlp 纯下载 (转码/合并由 Java 侧 ffmpeg-kit 做).

本机 yt-dlp 更新说明 (与服务端 pip 更新不同, 这里不能用 yt-dlp -U):
  Chaquopy 的 pip 包是编译期打进 APK 的, 运行时无法 pip 升级, -U 只对
  独立二进制发行版有效. 所以本机更新走 wheel 覆盖: 从 PyPI 下载最新
  yt-dlp wheel, 解压到应用私有目录 ytdlp_update/, 下次启动时把该目录
  插到 sys.path 最前面, 优先于 APK 内置的 yt_dlp. 更新后必须重启 App
  才能生效 (同一进程里已导入的旧模块不会自动换掉).
"""
import os
import re
import sys


def _norm(v):
    """归一化比对: "2026.08.19" 与 "2026.8.19" 视为相同 (与服务端一致)."""
    return ".".join(str(int(x)) for x in str(v).split(".") if x.isdigit())


def _apply_update_path(update_dir):
    """若私有目录里有覆盖版 yt_dlp, 把它插到 sys.path 最前面."""
    if not update_dir:
        return
    if os.path.isdir(os.path.join(update_dir, "yt_dlp")):
        if update_dir not in sys.path:
            sys.path.insert(0, update_dir)


def _version_from_dir(update_dir):
    """直接读覆盖目录里 yt_dlp/version.py 的 __version__, 不导入模块."""
    try:
        with open(os.path.join(update_dir, "yt_dlp", "version.py"),
                  encoding="utf-8") as f:
            m = re.search(r'__version__\s*=\s*["\']([^"\']+)["\']', f.read())
            return m.group(1) if m else None
    except OSError:
        return None


def get_version(update_dir=None):
    """当前生效版本: 有覆盖版时返回覆盖版, 否则返回 APK 内置版."""
    if update_dir:
        v = _version_from_dir(update_dir)
        if v:
            return v
    _apply_update_path(update_dir)
    import yt_dlp
    return yt_dlp.version.__version__


def _fetch_pypi():
    """返回 (最新版, wheel 下载地址)."""
    import json
    import urllib.request
    req = urllib.request.Request(
        "https://pypi.org/pypi/yt-dlp/json",
        headers={"User-Agent": "YTMP3-Android/1.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        d = json.load(resp)
    latest = d["info"]["version"]
    wheel_url = None
    for u in d.get("urls", []):
        if u.get("packagetype") == "bdist_wheel" \
                and u.get("filename", "").endswith(".whl"):
            wheel_url = u["url"]
            if "py3-none-any" in u["filename"]:
                break
    if not wheel_url and d.get("urls"):
        wheel_url = d["urls"][0]["url"]
    return latest, wheel_url


def check_update(update_dir=None):
    """返回 "当前|最新|是否有更新(0/1)|wheel地址". Java 侧按 | 切分."""
    current = get_version(update_dir)
    latest, wheel_url = _fetch_pypi()
    available = 1 if _norm(current) != _norm(latest) else 0
    return "%s|%s|%d|%s" % (current, latest, available, wheel_url or "")


def update_yt_dlp(update_dir, wheel_url=None):
    """下载最新 wheel 并覆盖到 update_dir, 返回 "旧版|新版".

    原子化: 先解压到 staging 目录并验出 yt_dlp/version.py, 通过后
    再替换旧覆盖目录; 中途失败不破坏旧覆盖版 (仍可用内置版兜底).
    """
    import shutil
    import urllib.request
    import zipfile

    if not update_dir:
        raise RuntimeError("未指定更新目录")
    try:
        old = get_version(update_dir)
    except Exception:
        old = "未知"
    if not wheel_url:
        _, wheel_url = _fetch_pypi()
    if not wheel_url:
        raise RuntimeError("未找到 yt-dlp wheel 下载地址")

    parent = os.path.dirname(update_dir.rstrip("/")) or "."
    os.makedirs(parent, exist_ok=True)
    staging = update_dir.rstrip("/") + "_new"
    tmp_wheel = update_dir.rstrip("/") + ".whl"
    if os.path.isdir(staging):
        shutil.rmtree(staging)
    try:
        req = urllib.request.Request(
            wheel_url, headers={"User-Agent": "YTMP3-Android/1.0"})
        with urllib.request.urlopen(req, timeout=120) as resp, \
                open(tmp_wheel, "wb") as f:
            while True:
                chunk = resp.read(8192)
                if not chunk:
                    break
                f.write(chunk)
        if os.path.getsize(tmp_wheel) < 100000:
            raise RuntimeError("下载的 wheel 过小, 可能下载失败")
        with zipfile.ZipFile(tmp_wheel) as z:
            names = z.namelist()
            if "yt_dlp/__init__.py" not in names:
                raise RuntimeError("wheel 里没有 yt_dlp 包")
            z.extractall(staging)
        new = _version_from_dir(staging)
        if not new:
            raise RuntimeError("新版 version.py 解析失败")
        if os.path.isdir(update_dir):
            shutil.rmtree(update_dir)
        os.rename(staging, update_dir)
        return "%s|%s" % (old, new)
    finally:
        try:
            if os.path.exists(tmp_wheel):
                os.remove(tmp_wheel)
        except OSError:
            pass
        try:
            if os.path.isdir(staging):
                shutil.rmtree(staging)
        except OSError:
            pass


def _download_one(url, fmt_expr, out_dir, prefix, update_dir=None):
    """下载单个流，返回文件路径。"""
    _apply_update_path(update_dir)
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


def download(url, fmt, out_dir, resolution="1080", update_dir=None):
    _apply_update_path(update_dir)
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
                return _download_one(url, expr, out_dir, "raw", update_dir)
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
            video_path = _download_one(url, expr, out_dir, "video", update_dir)
            last_err = None
            break
        except DownloadError as e:
            last_err = e
    if video_path is None:
        raise last_err if last_err else RuntimeError("视频流下载失败")
    audio_path = None
    for expr in audio_exprs:
        try:
            audio_path = _download_one(url, expr, out_dir, "audio", update_dir)
            break
        except DownloadError:
            continue
    # 音频可选：没有也行，返回视频单路径
    if audio_path:
        return video_path + "|" + audio_path
    return video_path


def get_title(url, update_dir=None):
    _apply_update_path(update_dir)
    from yt_dlp import YoutubeDL
    with YoutubeDL({"quiet": True, "no_warnings": True, "noplaylist": True}) as ydl:
        info = ydl.extract_info(url, download=False)
        return (info.get("title") or "audio").strip()
