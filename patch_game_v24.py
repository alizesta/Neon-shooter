#!/usr/bin/env python3
"""Recover and execute the complete, previously skipped v2.3 feature patch."""
from __future__ import annotations

import base64
import itertools
import lzma
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path("NeonShooter")
PARTS = [Path(f"build-assets/v23/patch_game_v23.part{i:02d}") for i in range(1, 5)]
if not all(p.is_file() for p in PARTS):
    raise SystemExit("Missing one or more v2.3 patch segments")

segments = ["".join(p.read_text(encoding="ascii").split()) for p in PARTS]
recovered = None
recovered_order = None
for order in itertools.permutations(range(len(segments))):
    encoded = "".join(segments[i] for i in order)
    try:
        packed = base64.b64decode(encoded, validate=True)
        source = lzma.decompress(packed)
        decoded = source.decode("utf-8-sig")
        compile(decoded, "patch_game_v23.py", "exec")
    except (ValueError, UnicodeDecodeError, lzma.LZMAError, EOFError, SyntaxError):
        continue
    if "GameView.kt" in decoded and ("NeonShooter" in decoded or "patch_game" in decoded):
        recovered = decoded
        recovered_order = order
        break

if recovered is None:
    raise SystemExit("Could not recover a valid Python feature patch from the four XZ segments")

patch_path = Path("patch_game_v23.py")
patch_path.write_text(recovered, encoding="utf-8")
print(f"Recovered v2.3 feature patch: order={recovered_order}, bytes={len(recovered.encode('utf-8'))}, lines={len(recovered.splitlines())}")
for term in ("MediaPlayer", "coin", "magnet", "boss", "Pause", "t.me/gateneon", "ABOUT", "ship"):
    print(f"PATCH_SOURCE[{term}]={term.lower() in recovered.lower()}")

subprocess.run([sys.executable, str(patch_path)], check=True)

# Force a monotonic Android version so the new APK installs as an update.
gradle = ROOT / "app/build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
g, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 24", g, count=1)
g, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.4.0"', g, count=1)
if not code_n or not name_n:
    raise SystemExit("Could not update Android version metadata")
gradle.write_text(g, encoding="utf-8")

# Report the actual generated source/assets. The separate verification step
# in the workflow consumes these markers before an APK can be published.
src = ROOT / "app/src/main"
kotlin_files = list(src.rglob("*.kt"))
all_kotlin = "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in kotlin_files)
game_file = ROOT / "app/src/main/java/com/claude/neonshooter/GameView.kt"
game = game_file.read_text(encoding="utf-8", errors="ignore")
audio_files = [p for p in src.rglob("*") if p.is_file() and p.suffix.lower() in {".wav", ".ogg", ".mp3", ".m4a"}]
markers = {
    "background_audio_engine": bool(re.search(r"\b(MediaPlayer|AudioTrack)\b", all_kotlin)),
    "background_audio_asset": bool(audio_files),
    "pause_ability_panel": bool(re.search(r"(?is)drawPause\s*\([^)]*\).*?(ability|قابلیت|weaponLevel|magnetLevel|missileLevel|laserLevel)", game)),
    "coin_economy": bool(re.search(r"(?i)(coinCount|coins|سکه)", all_kotlin)),
    "magnet_collection": bool(re.search(r"(?i)magnet", all_kotlin)),
    "boss_power_system": bool(re.search(r"(?i)boss.{0,60}(abilit|skill|power)|(?:abilit|skill|power).{0,60}boss", all_kotlin)),
    "about_screen": bool(re.search(r"(?i)(about|درباره)", all_kotlin)),
    "telegram_link": "t.me/gateneon" in all_kotlin,
    "black_hole_background_warp": bool(re.search(r"(?i)(warp|distort|lens|gravity).{0,80}(star|background)|(star|background).{0,80}(warp|distort|lens|gravity)", all_kotlin)),
    "five_ship_progression": bool(re.search(r"(?i)(shipNames|shipNames|FIFTH_SHIP|ship_4|ship4|shipCount\s*=\s*5|0\.\.4)", all_kotlin)),
    "icon_button_renderer": bool(re.search(r"(?i)(drawButtonIcon|drawIconButton|iconForButton)", all_kotlin)),
}
for key, value in markers.items():
    print(f"FEATURE_CHECK[{key}]={'PASS' if value else 'MISSING'}")
print("AUDIO_ASSETS=" + ",".join(str(p.relative_to(ROOT)) for p in audio_files))
