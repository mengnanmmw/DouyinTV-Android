# DouyinTV (Android TV 抖音客户端)

参考 [BBLL](https://github.com/xiaye13579/BBLL) 的电视端左侧导轨交互范式与 [douyin-tv](https://github.com/ygtec-org/douyin-tv) 的 WebView + JS 遥控器桥接方案开发的 Android TV 抖音客户端，支持通过 **GitHub Actions** 云端自动编译 APK。

## 功能特性

- **BBLL 风格左侧导轨**：推荐、精选、关注、热榜、扫码登录、设置，支持 TV 遥控器 D-pad 焦点高亮与沉浸全屏切换。
- **遥控器原生操控**：
  - `↑` / `↓`：切换上一个 / 下一个视频
  - `←` / `→`：快退 / 快进 10 秒（焦点在左侧导轨时按 `→` 进入视频区）
  - `OK` / `确认`：播放 / 暂停；**长按 OK** 或按 **菜单键** 打开设置面板
  - `返回键`：退出全屏 → 页面后退 → 双击退出应用
- **播完自动连播**：自动监听视频 `ended` 事件并切换下一条。
- **Cookie 持久化与崩溃自恢复**：扫码登录后自动保存状态；低内存电视盒子 WebView 渲染进程被回收时自动重建恢复。

## GitHub Actions 云端编译 APK

1. 在 GitHub 新建仓库（例如 `DouyinTV`），将本项目全部文件推送到 `main` 分支：
   ```bash
   git init
   git add .
   git commit -m "init: DouyinTV Android TV project"
   git branch -M main
   git remote add origin https://github.com/你的用户名/DouyinTV.git
   git push -u origin main
   ```
2. 进入仓库的 **Actions** 标签页，等待 `Build DouyinTV APK` 工作流运行完成。
3. 在构建详情页底部的 **Artifacts** 中下载 `DouyinTV-debug-apk` 或 `DouyinTV-release-apk`（已内置调试签名，可直接安装到电视或盒子）。
4. 如需自动生成 **Release**，推送 `v*` 标签即可：
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
