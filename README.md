# BBQ10 Vocabulary

BlackBerry Q10（720×720 / Android 4.3）英语词库 App。

## 本地构建

```bash
./gradlew :app:assembleDebug
# 本地下载页（只挂本机构建的 APK，样式/内容与线上 Pages 不同）：
./scripts/serve_local.sh          # 默认 http://127.0.0.1:8765/
# 或：python3 scripts/prepare_site.py --local && python3 -m http.server 8765 --directory dist-local --bind 127.0.0.1
```

APK 文件名：`Vocabulary-<versionName>.apk`（见 `app/build.gradle`）。本地页输出在 `dist-local/`；线上 Pages 仍用 `site/`（Release 链接 + 扩展词库）。

## CI

- **Build Android APK**：PR / push `main` 编译并上传 APK artifact  
- **Build & Deploy Site**：push `main` 后编译 APK，写入带版本号下载链接的站点，部署到 **GitHub Pages**
- **Release**：push `main` 时读取 `app/build.gradle` 的 `versionName`；若尚无对应 tag（如 `v1.0.0.26`），则自动创建 GitHub Release，附带 APK / `cet4.apk` / 扩展词库，正文取自 `CHANGELOG.md`。已有同名 release 时会跳过；可在 Actions 里手动 `workflow_dispatch` 并勾选 force 重建。

首次部署请在仓库 Settings → Pages → Source 选择 **GitHub Actions**。

站点地址：`https://kylebing.github.io/bbq10-vocabulary/`  
页面 APK 下载链到 **GitHub Release**；扩展词库仍由 Pages 提供。本机调试下载用 `./scripts/serve_local.sh`（`dist-local/`，仅 APK）。内置词库用 App 内「切换词库」，扩展库用「从文件导入」。
