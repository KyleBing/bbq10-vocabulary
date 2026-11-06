# BBQ10 Vocabulary

BlackBerry Q10（720×720 / Android 4.3）英语词库 App。

## 本地构建

```bash
./gradlew :app:assembleDebug
python3 scripts/prepare_site.py
# 产物在 site/ ，可用 python3 -m http.server 8765 --directory site
```

APK 文件名：`Vocabulary-<versionName>.apk`（见 `app/build.gradle`）。

## CI

- **Build Android APK**：PR / push `main` 编译并上传 APK artifact  
- **Build & Deploy Site**：push `main` 后编译 APK，写入带版本号下载链接的站点，部署到 **GitHub Pages**

首次部署请在仓库 Settings → Pages → Source 选择 **GitHub Actions**。

站点地址：`https://kylebing.github.io/bbq10-vocabulary/`  
页面只提供 APK 下载；词库已内置在 App 中，用「切换词库」即可。
