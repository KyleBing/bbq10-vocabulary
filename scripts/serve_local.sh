#!/usr/bin/env bash
# Build debug APK, write a local-only download page (not the Pages site), serve it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PORT="${1:-8765}"

VERSION="$(python3 - <<'PY'
import re
from pathlib import Path
t = Path("app/build.gradle").read_text()
m = re.search(r'versionName\s+"([^"]+)"', t)
print(m.group(1) if m else "0.0.0.0")
PY
)"

echo "==> assembleDebug (v${VERSION})"
./gradlew :app:assembleDebug --no-daemon -q

SRC="app/build/outputs/apk/debug/app-debug.apk"
NAME="Vocabulary-${VERSION}.apk"
OUT="dist-local"
rm -rf "$OUT"
mkdir -p "$OUT"
cp -f "$SRC" "${OUT}/${NAME}"
cp -f "$SRC" "${OUT}/cet4.apk"

python3 - <<PY
from pathlib import Path
from datetime import datetime, timezone
ver = "${VERSION}"
apk = "Vocabulary-${VERSION}.apk"
html = Path("web/local.html").read_text(encoding="utf-8")
html = (
    html.replace("__VERSION__", ver)
    .replace("__APK__", apk)
    .replace("__BUILD_INFO__", "本地 · " + datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"))
)
Path("dist-local/index.html").write_text(html, encoding="utf-8")
print(f"Ready {apk} (+ cet4.apk) in dist-local/")
PY

LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || true)"
echo "==> http://127.0.0.1:${PORT}/"
if [ -n "$LAN_IP" ]; then
  echo "==> http://${LAN_IP}:${PORT}/  (局域网)"
fi
echo "    Ctrl+C 结束"
exec python3 -m http.server "$PORT" --directory "$OUT" --bind 0.0.0.0
