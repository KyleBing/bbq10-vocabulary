#!/usr/bin/env bash
# Build debug APK and publish to dist/ with a versioned filename.
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
mkdir -p dist
cp -f "$SRC" "dist/${NAME}"
cp -f "$SRC" "dist/cet4.apk"

python3 - <<PY
from pathlib import Path
import re
ver = "${VERSION}"
apk = "Vocabulary-${VERSION}.apk"
html = Path("dist/index.html")
text = html.read_text()
text = re.sub(r"<title>.*?</title>", f"<title>Vocabulary APK v{ver}</title>", text)
text = re.sub(r'(<p class="ver">).*?(</p>)', rf"\1版本 {ver}\2", text, count=1, flags=re.S)
text = re.sub(
    r'<a class="btn"[^>]*>.*?</a>',
    f'<a class="btn" href="{apk}">下载 App · v{ver}</a>',
    text,
    count=1,
    flags=re.S,
)
html.write_text(text)
print(f"Published dist/{apk} (+ dist/cet4.apk alias)  v{ver}")
PY
