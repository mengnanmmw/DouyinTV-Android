# DouyinTV (纯原生接口 + Media3 ExoPlayer 版)

专为 Android TV 打造的抖音客户端，**完全不使用 WebView**，采用原生 HTTP 接口解析 + **AndroidX Media3 ExoPlayer** 硬件解码播放，兼容老旧电视/盒子（Android 5.0+）。

## 架构特点

- **零 WebView 依赖**：直接调用抖音 Web API（`tab/feed`、`channel/feed`、`follow/feed`、`general/search/single`），内置纯 Kotlin `XBogus` 签名与访客 `ttwid` 自动注册。
- **原生 Media3 ExoPlayer**：通过 `OkHttpDataSource` 注入 `Referer` 与 `User-Agent` 直连 CDN 视频流，支持硬解与倍速播放。
- **BBLL 风格 TV 交互**：左侧导航导轨 + 4 列视频封面网格（`Coil` 加载 + 焦点放大动画），全屏播放器支持遥控器 `↑/↓` 切视频、`←/→` 快进退 10 秒、`OK` 播放/暂停、`菜单键` 切换倍速。
- **手机扫码导入 Cookie（局域网 8088 端口）**：当遇到风控或需要看「关注」列表时，在「凭据/设置」中用手机微信/浏览器扫描二维码，即可通过局域网网页将浏览器 Cookie 或自建解析代理地址发送到电视，无需遥控器打字。

## GitHub Actions 云端编译

1. 将本项目全部文件推送到你的 GitHub 仓库 `main` 分支。
2. 打开仓库 **Actions** 页面，等待 `Build DouyinTV Native APK` 完成。
3. 在构建详情页 **Artifacts** 中下载 `DouyinTV-Native-debug-apk` 或 `DouyinTV-Native-release-apk` 安装到电视。
