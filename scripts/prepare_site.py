#!/usr/bin/env python3
"""Build download site into site/: versioned APK + wordbooks + index.html."""
from __future__ import annotations

import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "https://kylebing.github.io/bbq10-vocabulary/"


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

    books_src = ROOT / "web" / "wordbooks"
    books_dst = site / "wordbooks"
    if books_src.is_dir():
        books_dst.mkdir(parents=True, exist_ok=True)
        for db in sorted(books_src.glob("*.db")):
            shutil.copy2(db, books_dst / db.name)

    build_info = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    sha = ""
    if len(sys.argv) > 1:
        sha = sys.argv[1][:7]
    if sha:
        build_info = f"{build_info} · {sha}"

    # Absolute URLs avoid broken relative paths on GitHub project pages / BB10.
    apk_href = BASE + apk_name

    html = (ROOT / "web" / "index.html").read_text(encoding="utf-8")
    html = (
        html.replace("__VERSION__", ver)
        .replace("__APK__", apk_href)
        .replace("__BASE__", BASE)
        .replace("__BUILD_INFO__", build_info)
    )
    (site / "index.html").write_text(html, encoding="utf-8")
    (site / "index.htm").write_text(html, encoding="utf-8")
    n = len(list(books_dst.glob("*.db"))) if books_dst.is_dir() else 0
    print(f"site ready: {apk_href}  version={ver}  wordbooks={n}")


if __name__ == "__main__":
    main()
