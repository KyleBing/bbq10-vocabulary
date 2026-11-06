#!/usr/bin/env bash
# Build debug APK and refresh local dist/ for python http.server.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(python3 - <<'PY'
import re
from pathlib import Path
t = Path("app/build.gradle").read_text()
m = re.search(r'versionName\s+"([^"]+)"', t)
print(m.group(1) if m else "0.0.0.0")
PY
)"

./gradlew :app:assembleDebug --no-daemon -q
SRC="app/build/outputs/apk/debug/app-debug.apk"
NAME="Vocabulary-${VERSION}.apk"
mkdir -p dist/wordbooks
cp -f "$SRC" "dist/${NAME}"
cp -f "$SRC" "dist/cet4.apk"
if ls web/wordbooks/*.db >/dev/null 2>&1; then
  cp -f web/wordbooks/*.db dist/wordbooks/
fi

python3 - <<PY
from pathlib import Path
from datetime import datetime, timezone
ver = "${VERSION}"
apk = "Vocabulary-${VERSION}.apk"
html = Path("web/index.html").read_text(encoding="utf-8")
block = """      <table width="72%" border="0" cellpadding="0" cellspacing="0">
        <tr>
          <td align="center" bgcolor="#2E7D32">
            <a href="%s" style="display:block;width:100%%;padding:12px 8px;text-decoration:none;color:#FFFFFF;">
              <font face="Helvetica, Arial, sans-serif" color="#FFFFFF" size="4"><b>下载 App v%s</b></font>
            </a>
          </td>
        </tr>
      </table>""" % (apk, ver)
html = (
    html.replace("__DOWNLOAD_BLOCK__", block)
    .replace("__VERSION__", ver)
    .replace("__APK__", apk)
    .replace("__BASE__", "")
    .replace("__BUILD_INFO__", "本地 dist · " + datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"))
)
Path("dist/index.html").write_text(html, encoding="utf-8")
n = len(list(Path("dist/wordbooks").glob("*.db")))
print(f"Published dist/{apk} (+ dist/cet4.apk)  v{ver}  wordbooks={n}")
PY
