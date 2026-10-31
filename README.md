# YTMP3 Android v1.0.2

YouTube / Instagram 视频转 MP3 / MP4 的安卓客户端，搭配自建私有服务端使用。

## 功能

**本地下载**
- 手机端直接下载、转码（yt-dlp + FFmpeg），无需服务器
- MP3（128K / 192K / 256K / 320K）/ MP4（360p ~ 2160p）
- 下载完成后自动保存到 `Download/YTMP3`，列表实时显示文件大小
- 支持本地更新yt-dlp

**服务器模式**
- 添加、管理多个下载服务器，一键检测连通性（`GET /api/health`）
- 服务器列表支持点选切换、上下拖动排序
- 服务端转码完成后一键保存到手机

**其他**
- 深色 / 新安卓配色（Material You 浅色）一键切换，右上角按钮

## 服务端

搭配 Docker 镜像使用：

```bash
docker run -d --name ytmp3 -p 8080:8080 weleone/ytmp3:latest
```

服务端项目：https://github.com/weleone/ytmp3-docker

## 构建

推送到 `main` 分支后 GitHub Actions 自动构建，APK 在 Actions 页的 Artifacts 下载。

```bash
git add -A && git commit -m "xxx" && git push
```

## 要求

- Android 8.0+
- 本地转码需要 arm64 / armeabi-v7a 设备

## 免责声明

本站仅供个人学习研究使用，请勿用于商业用途。视频版权归原作者所有，下载内容请在 24 小时内删除。
<img src="[https://raw.githubusercontent.com/weleone/images/refs/heads/main/ytmp3_app_zh_screenshot.jpg]" width="800" height="600" alt="ytmp3 app">
