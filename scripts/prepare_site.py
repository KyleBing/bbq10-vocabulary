#!/usr/bin/env python3
"""Build download site into site/: versioned APK + index.html."""
from __future__ import annotations

import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def version_name() -> str:
    text = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    m = re.search(r'versionName\s+"([^"]+)"', text)
    if not m:
        raise SystemExit("versionName not found in app/build.gradle")
    return m.group(1)


def main() -> None:
    ver = version_name()
    apk_name = f"Vocabulary-{ver}.apk"
    apk_src = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    if not apk_src.is_file():
        raise SystemExit(f"APK missing: {apk_src} (run assembleDebug first)")

    site = ROOT / "site"
    if site.exists():
        shutil.rmtree(site)
    site.mkdir(parents=True)

    shutil.copy2(apk_src, site / apk_name)
    shutil.copy2(apk_src, site / "cet4.apk")  # stable alias

    build_info = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    sha = ""
    if len(sys.argv) > 1:
        sha = sys.argv[1][:7]
    if sha:
        build_info = f"{build_info} · {sha}"

    html = (ROOT / "web" / "index.html").read_text(encoding="utf-8")
    html = (
        html.replace("__VERSION__", ver)
        .replace("__APK__", apk_name)
        .replace("__BUILD_INFO__", build_info)
    )
    (site / "index.html").write_text(html, encoding="utf-8")
    print(f"site ready: {apk_name}  version={ver}")


if __name__ == "__main__":
    main()
