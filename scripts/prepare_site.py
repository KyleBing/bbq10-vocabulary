#!/usr/bin/env python3
"""Build download site into site/: wordbooks + index.html (+ APK only for --local)."""
from __future__ import annotations

import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGES_BASE = "https://kylebing.github.io/bbq10-vocabulary/"

DOWNLOAD_LOCAL = """      <table width="72%" border="0" cellpadding="0" cellspacing="0">
        <tr>
          <td align="center" bgcolor="#2E7D32">
            <a href="__APK__" style="display:block;width:100%;padding:12px 8px;text-decoration:none;color:#FFFFFF;">
              <font face="Helvetica, Arial, sans-serif" color="#FFFFFF" size="4"><b>下载 App v__VERSION__</b></font>
            </a>
          </td>
        </tr>
      </table>"""

DOWNLOAD_OFFLINE = """      <table width="84%" border="0" cellpadding="0" cellspacing="0">
        <tr>
          <td align="center" bgcolor="#333333" style="padding:12px 8px;">
            <font face="Helvetica, Arial, sans-serif" color="#FFAB91" size="3"><b>在线 APK 已下线</b></font>
            <br>
            <font face="Helvetica, Arial, sans-serif" color="#AAAAAA" size="2">
              Q10 上安装会报 Unable to install。<br>
              请用电脑本地 http 服务安装，或从 GitHub Release 下载。
            </font>
          </td>
        </tr>
      </table>"""


def version_name() -> str:
    text = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    m = re.search(r'versionName\s+"([^"]+)"', text)
    if not m:
        raise SystemExit("versionName not found in app/build.gradle")
    return m.group(1)


def main() -> None:
    args = [a for a in sys.argv[1:] if a]
    local = "--local" in args
    args = [a for a in args if a != "--local"]
    # Absolute URLs for GitHub Pages; relative for local http.server.
    base = "" if local else PAGES_BASE

    ver = version_name()
    apk_name = f"Vocabulary-{ver}.apk"
    apk_src = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"

    site = ROOT / "site"
    if site.exists():
        shutil.rmtree(site)
    site.mkdir(parents=True)

    if local:
        if not apk_src.is_file():
            raise SystemExit(f"APK missing: {apk_src} (run assembleDebug first)")
        shutil.copy2(apk_src, site / apk_name)
        shutil.copy2(apk_src, site / "cet4.apk")  # stable alias

    books_src = ROOT / "web" / "wordbooks"
    books_dst = site / "wordbooks"
    if books_src.is_dir():
        books_dst.mkdir(parents=True, exist_ok=True)
        for db in sorted(books_src.glob("*.db")):
            shutil.copy2(db, books_dst / db.name)

    build_info = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    if local:
        build_info = "本地 · " + build_info
    sha = args[0][:7] if args else ""
    if sha:
        build_info = f"{build_info} · {sha}"

    apk_href = apk_name if local else ""
    download_block = (
        DOWNLOAD_LOCAL.replace("__APK__", apk_href).replace("__VERSION__", ver)
        if local
        else DOWNLOAD_OFFLINE
    )

    html = (ROOT / "web" / "index.html").read_text(encoding="utf-8")
    html = (
        html.replace("__DOWNLOAD_BLOCK__", download_block)
        .replace("__VERSION__", ver)
        .replace("__APK__", apk_href)
        .replace("__BASE__", base)
        .replace("__BUILD_INFO__", build_info)
    )
    (site / "index.html").write_text(html, encoding="utf-8")
    (site / "index.htm").write_text(html, encoding="utf-8")
    n = len(list(books_dst.glob("*.db"))) if books_dst.is_dir() else 0
    mode = "local-apk" if local else "pages-no-apk"
    print(f"site ready: {mode}  version={ver}  wordbooks={n}")


if __name__ == "__main__":
    main()
