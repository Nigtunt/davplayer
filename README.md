# DavPlayer

一个基于 WebDAV 协议的视频播放器（Android），可配合 OpenList / Alist 等 WebDAV 服务使用。

## 功能

1. **WebDAV 配置**：在「设置」中配置服务器地址、用户名、密码，浏览目录并播放视频。
2. **播放历史**：记录每个视频的播放进度，历史页可查看并从上次位置续播。
3. **封面抽帧**：视频开始播放后自动抽取当前帧作为历史列表封面（未播放不产生任何缓存）。
4. **倍速播放**：播放中可切换 0.5x–3x 倍速；长按屏幕任意位置临时加速（默认 2x，可在设置中配置为 1.5x / 2x / 2.5x / 3x / 4x），松开恢复。

## 技术栈

- Kotlin + Jetpack Compose (Material3)
- Media3 / ExoPlayer 播放内核
- OkHttp 手写 WebDAV PROPFIND 协议解析
- MediaMetadataRetriever 远程抽帧
- JSON 本地文件存储播放历史

## 构建

GitHub Actions 推送后自动构建 debug APK（Artifacts 中下载）。

本地构建：

```bash
# 需要 JDK 17+ 与 Android SDK (platform-34, build-tools 34.0.0)
./gradlew assembleDebug
```
