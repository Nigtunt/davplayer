# iOS 3.0 UI 改造清单

> 依据 Android 端 3.0 第一轮改造整理，目标是在不回归播放 / Komga / 文件操作的前提下，把 iOS 做成同一套视觉和交互。

## 0. 范围锁定

### 保留

- WebDAV 多源、路径栈、文件播放
- 文件左滑：删除 / 移动
- Komga 漫画、阅读列表、书库、服务端进度
- 视频历史、漫画历史
- 主题默认跟随系统
- iPad 双栏

### 不做

- 音乐播放、音频迷你条
- CBZ 漫画包直接阅读
- 离线缓存下载
- NFO / TMDB 刮削
- DLNA / AirPlay 投屏
- 播放器内字幕目录浏览器

### 视频元数据

- 大小：OpenList API
- 修改时间：WebDAV PROPFIND `getlastmodified`
- 质量 / 编码 / HDR / 音轨：只解析文件名
- 解析不到不显示，不伪造 115 字段

---

## 1. 设计令牌

参考 Android `Theme.kt`：

| 令牌 | 浅色 | 深色 |
|---|---|---|
| background | `#F7F8FA` | `#0E1116` |
| surface / card | `#FFFFFF` | `#171B23` |
| surfaceContainer | `#F0F2F5` | `#1B2029` |
| primary | `#4F46E5` | `#A5B4FC` |
| 进度 coral | `#FF4D4F` | `#FF4D4F` |
| text primary | `#1D2129` | `#F2F3F5` |
| text secondary | `#4E5969` | `#A6ADBB` |
| text tertiary | `#86909C` | `#8B93A1` |
| hairline | `0x0F000000` | `0x14FFFFFF` |
| 卡片圆角 | 16–20dp | 同左 |

要求：

- 浅色是主视觉，深色做同层级映射
- 页面底色不能是纯白；卡片才是纯白
- 进度统一珊瑚红，不再使用粉紫渐变
- 阴影只做轻微环境光，避免 iOS Compose 大面积 blur

对应文件：

- `ios/composeApp/src/commonMain/kotlin/com/davplayer/app/ios/theme/Theme.kt`

---

## 2. 数据层

### 2.1 DavEntry

Android 已增加：

```kotlin
data class DavEntry(
    val name: String,
    val href: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long = 0L
)
```

iOS 需要：

- `DavClient.list()` 的 PROPFIND 增加 `<D:getlastmodified/>`
- `DavEntry` 增加 `lastModified: Long`
- 解析 RFC1123 / ISO 时间
- 列表显示“今天 HH:mm / MM-dd HH:mm”，解析不到就隐藏

### 2.2 文件名视频解析

Android 新增 `VideoMeta.kt`：

```kotlin
data class VideoMeta(
    val resolution: String?,
    val hdr: String?,
    val codec: String?,
    val source: String?,
    val audio: String?
)
```

iOS 直接移植到 `commonMain`，解析：

- `2160p / 4K / UHD` → 4K
- `1080p / FHD` → 1080P
- `720p` → 720P
- `480p / 576p` → SD
- `HDR10 / HDR / Dolby Vision`
- `HEVC / H.265 / H.264 / AV1`
- `BluRay / WEB-DL / REMUX`
- `DTS-HD / TrueHD / Atmos / DDP / AAC`

规则：

- 只显示解析到的标签
- 年份 `2024` 不能误判成 4K
- 没有标签就不显示

### 2.3 浏览偏好

Android 新增持久化：

```text
browseGridView: Boolean
browseSortMode: name | size | modified
```

iOS 建议放 `SourceStore` 或通用 settings：

- 文件夹列表 / 图标视图
- 排序：名称 / 大小 / 时间

---

## 3. 浏览页 `BrowserScreen`

### 3.1 顶部

- 返回圆钮（根目录禁用）
- 源名 + 状态点 + 当前目录
- 标题「媒体与文件库」+ 已连接胶囊
- 右侧三个圆形按钮：视图切换 / 排序 / 刷新
- 源名点击可切换源

### 3.2 面包屑

- `根目录 > xxx > xxx`
- 横向滚动
- 每一级可点击跳转
- 当前层级主色 / 浅主色底
- 根目录图标 Home

### 3.3 筛选

- 全部
- 文件夹
- 视频
- 字幕

不再显示音频、漫画包、NFO 等空分类。

### 3.4 文件夹

- 列表：白卡、左侧主色文件夹图标、名称、修改时间、右侧更多
- 图标：两列卡片，图标 + 名称 + 修改时间
- 文件夹数量拿不到就不显示，不伪造
- 点击进入，更多打开操作面板

### 3.5 视频

- 白色圆角卡片
- 左侧 16:9 封面（历史封面 / 占位）
- 标题：文件名两行省略
- 标签：文件名解析的画质/编码/HDR/来源
- 大小 + 修改时间
- 进度：珊瑚红文字 + 封面底部进度条
- 右上更多按钮
- 点击播放

### 3.6 字幕

- 白色卡片
- 字幕图标 + 文件名 + 格式 + 大小 + 修改时间
- 点击打开操作面板
- 不直接播放

### 3.7 滑动操作

- 左滑露出右侧「删除 / 移动」
- 按钮 64dp 宽、12sp
- 操作区 152dp，右端留 8dp
- 外层 `clipToBounds`
- 右滑不打开；已展开时右滑回弹

### 3.8 文件操作面板

建议使用 `ModalBottomSheet`：

- 打开 / 播放
- 复制直链
- 文件信息
- 移动
- 删除

保留现有 WebDAV DELETE / MOVE，不改协议。

---

## 4. 字幕

### 浏览页

- 直接把字幕文件列出来
- 管理动作：复制直链 / 文件信息 / 移动 / 删除

### 播放器

- 删除 `SubtitleBrowserView`
- 删除 `PlayerBridge.directoryLister` 相关 UI 入口
- 不再在播放器内浏览目录
- 播放器只接收浏览页找到的**同名外挂字幕**
- 同目录、同文件名主体、扩展名 `srt/ass/ssa/vtt`
- 找不到就无外挂字幕
- 内嵌字幕仍由播放器轨道自动选择

iOS 播放器需要：

- `BrowserScreen` 点击视频时，在当前目录的 `entries` 中找到同名字幕
- 把 `subtitleHref` 传给 `InlinePlayerHost` / VLC 播放请求
- 初始化播放器时直接挂载外挂字幕
- 不新增播放器内字幕目录选择

---

## 5. 漫画首页 `KomgaTab`

对齐 Android：

- 顶部标题「漫画书架」
- 继续阅读：横向 142dp 大卡，3:4，书脊，底部进度和页码
- 最新入库：三列 3:4 书架
- 阅读列表：横向 132dp 卡片
- 书库：白色卡片行
- 所有封面使用 Komga 实际封面；失败用低饱和渐变占位
- 保留原有阅读列表 / 书库 / 服务端进度逻辑

---

## 6. 历史页

- 标题「历史」+ 副标题
- 视频 / 漫画分段胶囊，显示数量
- 视频行：白卡 + 16:9 封面 + 标题 + 已看百分比 + 时间 + 「继续」
- 长按仍保留：跳转目录 / 删除记录
- 漫画行：白卡 + 3:4 封面 + 标题 + 系列 + 页数 + 时间
- 点击直接续播 / 续读

---

## 7. 我的页

- 顶部 `ProfileHeader`：
  - 头像字母 `D`
  - DavPlayer
  - WebDAV 源名
  - 已连接 / 未配置状态
  - Komga 已连接提示
- 设置分组卡片：
  - 白色卡片 + 18dp 圆角 + 轻阴影
  - 现有分组全部保留，不缩减
- 主题文案改为“默认跟随系统，可手动切换浅色 / 深色”

---

## 8. 底部导航

- 白色浮层，不再硬分割线
- 选中项：主色图标 + 主色文字 + 浅主色圆底
- 未选中：弱化文字
- 高度 60dp + 安全区
- 四个 Tab 不变

---

## 9. 播放页（P3）

Android 本轮只完成了：

- 移除播放器内字幕目录选择
- 浏览页同名外挂字幕随视频挂载

尚未完成：

- 视频详情页（标题、标签、同目录视频、字幕轨道卡）
- 播放器控制层完全对齐参考图

iOS 下一步建议：

1. 先复用现有 VLC 播放器，不改解码
2. 播放器上方保留 16:9
3. 下方增加可滚动详情区：
   - 标题 / 关闭
   - 文件名解析标签
   - 已加载字幕轨道卡
   - 同目录其它视频
4. 不伪造类型 / 导演 / 评分 / NFO
5. 不做投屏 / 离线缓存死按钮

---

## 10. iPad / 横屏

- 不能直接把手机稿拉伸
- 保留当前双栏：左侧目录，右侧详情/播放
- 列表卡片宽度自适应
- 漫画 3 列在 iPad 可提升到 4–5 列
- 播放器横屏保持现有全屏逻辑

---

## 11. 验收清单

### 功能不回归

- [ ] WebDAV 多源切换
- [ ] 目录返回 / 刷新
- [ ] 视频播放、续播、进度
- [ ] 文件删除 / 移动
- [ ] 同名外挂字幕自动挂载
- [ ] 播放器无字幕目录入口
- [ ] Komga 首页、系列、阅读列表
- [ ] Komga 服务端进度
- [ ] 视频/漫画历史
- [ ] iPad 双栏

### 视觉

- [ ] 浅色：灰底 + 白卡 + 靛蓝主色 + 珊瑚进度
- [ ] 深色：同层级映射，不纯反相
- [ ] 文件夹列表 / 图标切换
- [ ] 面包屑可点击
- [ ] 筛选四类
- [ ] 视频画质标签只显示文件名解析结果
- [ ] 无数据的标签不显示
- [ ] 左滑按钮完整露出，不越界
- [ ] 空状态 / 加载状态 / 错误状态统一

### 性能

- [ ] 封面/缩略图继续缓存
- [ ] 列表滚动不触发重复网络请求
- [ ] 避免大面积 blur / 重阴影
- [ ] 文件数较多时文件夹网格不卡顿

---

## 12. Android 对应文件

- `app/src/main/java/com/davplayer/app/Theme.kt`
- `app/src/main/java/com/davplayer/app/VideoMeta.kt`
- `app/src/main/java/com/davplayer/app/Dav.kt`
- `app/src/main/java/com/davplayer/app/Store.kt`
- `app/src/main/java/com/davplayer/app/MainActivity.kt`
- `app/src/main/java/com/davplayer/app/KomgaUi.kt`
- `app/src/main/java/com/davplayer/app/PlayerActivity.kt`
- `.github/workflows/ios.yml`（Android 改造期间已关闭 iOS push）
