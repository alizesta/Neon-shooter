from pathlib import Path
import re

ROOT = Path("NeonShooter")
APP = ROOT / "app"
JAVA = APP / "src/main/java/com/claude/neonshooter"
GAME = JAVA / "GameView.kt"
MAIN = JAVA / "MainActivity.kt"
APP_CLASS = JAVA / "App.kt"
PLAY_CLASS = JAVA / "Play.kt"
MANIFEST = APP / "src/main/AndroidManifest.xml"
STRINGS = APP / "src/main/res/values/strings.xml"
GRADLE = APP / "build.gradle.kts"
SFX = JAVA / "Sfx.kt"
README = ROOT / "README.md"


def replace_regex(path: Path, pattern: str, replacement: str, label: str, min_count: int = 0):
    if not path.exists():
        raise RuntimeError(f"Required file missing for {label}: {path}")
    content = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, content, flags=re.MULTILINE)
    if count < min_count:
        raise RuntimeError(f"Expected at least {min_count} match(es) for {label}, found {count}")
    path.write_text(updated, encoding="utf-8")
    return count


def strip_google_play_games():
    replace_regex(GAME, r'^\s*"lb" -> Play\.showLeaderboard\(act\)\s*\n', "", "leaderboard action")
    replace_regex(GAME, r'^\s*"ach" -> Play\.showAchievements\(act\)\s*\n', "", "achievement action")
    replace_regex(GAME, r'^\s*"signin" -> Play\.signIn\(act\)\s*\n', "", "Google sign-in action")
    replace_regex(GAME, r'^\s*add\("lb", "LEADERBOARD", y \+ 30f\)\s*\n', "", "leaderboard menu button")
    replace_regex(GAME, r'^\s*add\("ach", "ACHIEVEMENTS", y \+ 60f\)\s*\n', "", "achievements menu button")
    replace_regex(GAME, r'^\s*add\("signin", if \(Play\.signedIn\) "GOOGLE: ONLINE" else "GOOGLE SIGN-IN", y \+ 90f\)\s*\n', "", "Google sign-in menu button")
    replace_regex(
        GAME,
        r'add\("sound", if \(sfx\.enabled\) "SOUND: ON" else "SOUND: OFF", y \+ 120f\)',
        'add("sound", if (sfx.enabled) "SOUND: ON" else "SOUND: OFF", y + 30f)',
        "sound menu placement",
    )
    replace_regex(
        GAME,
        r'add\("ship", "SHIP: " \+ shipName\(\), y \+ 150f\)',
        'add("ship", "SHIP: " + shipName(), y + 60f)',
        "ship menu placement",
    )
    replace_regex(GAME, r'^\s*private val unlocked = HashSet<Int>\(\)\s*\n', "", "achievement tracking state")
    replace_regex(GAME, r'^\s*private fun ach\(resId: Int\) \{ if \(unlocked\.add\(resId\)\) Play\.unlock\(act, resId\) \}\s*\n', "", "achievement helper")
    for pattern, label in [
        (r'^\s*if \(wave >= 10\) ach\(R\.string\.achievement_wave_10\)\s*\n', "wave achievement"),
        (r'^\s*if \(kills == 1\) ach\(R\.string\.achievement_first_kill\)\s*\n', "first kill achievement"),
        (r'^\s*if \(score >= 1000\) ach\(R\.string\.achievement_score_1000\)\s*\n', "score achievement"),
        (r'^\s*if \(e\.type == 2\) \{ ach\(R\.string\.achievement_boss_slayer\); bullets\.removeAll \{ !it\.mine \} \}\s*\n', "boss achievement"),
    ]:
        replace_regex(GAME, pattern, "", label)
    replace_regex(
        GAME,
        r'^\s*act\.runOnUiThread \{\s*try \{\s*Play\.submit\(act, score\)\s*\}\s*catch \(e: Exception\) \{\s*Log\.e\("NeonShooter", "Score submit skipped", e\)\s*\}\s*\}\s*\n',
        "",
        "online score submission",
    )

    # Archive revisions differ in which online menu items they contain.
    # Strip any remaining Google Games calls without assuming all IDs exist.
    replace_regex(
        GAME,
        r'^\s*.*Play\.(?:showLeaderboard|showAchievements|signIn|check|submit|unlock)\(.*\)\s*\n',
        "",
        "remaining Google Games call sites",
    )
    replace_regex(
        GAME,
        r'ach\(R\.string\.achievement_[A-Za-z0-9_]+\);?\s*',
        "",
        "achievement calls embedded in gameplay lines",
    )
    replace_regex(
        GAME,
        r'^\s*add\("(?:lb|ach|signin)",\s*.*(?:LEADERBOARD|ACHIEVEMENTS|GOOGLE SIGN-IN).*?\)\s*\n',
        "",
        "remaining online menu buttons",
    )

    replace_regex(MAIN, r'^\s*Play\.check\(this\)\s*\n', "", "automatic Google Games check")
    replace_regex(MANIFEST, r'^\s*<meta-data android:name="com\.google\.android\.gms\.games\.APP_ID"[^>]*/>\s*\n', "", "Google Games manifest metadata")
    replace_regex(MANIFEST, r'^\s*android:name="\.App"\s*\n', "", "custom Google Games Application class")
    replace_regex(GRADLE, r'^\s*implementation\("com\.google\.android\.gms:play-services-games-v2:[^"]+"\)\s*\n', "", "Google Play Games dependency")
    replace_regex(
        STRINGS,
        r'^\s*<!-- Replace with values from Google Play Console \(Play Games Services\) -->\s*\n'
        r'(?:\s*<string name="(?:app_id|leaderboard_high_score|achievement_first_kill|achievement_score_1000|achievement_boss_slayer|achievement_wave_10)"[^>]*>.*?</string>\s*\n)+',
        "",
        "Google Games resource IDs",
    )

    APP_CLASS.unlink(missing_ok=True)
    PLAY_CLASS.unlink(missing_ok=True)

    # Final line-level cleanup for source-archive variants. Preserve the
    # boss projectile-clear effect when it shares a line with an achievement.
    game_lines = GAME.read_text(encoding="utf-8").splitlines()
    cleaned_lines = []
    for line in game_lines:
        if "ach(" in line:
            if "bullets.removeAll" in line:
                cleaned_lines.append("        if (e.type == 2) { bullets.removeAll { !it.mine } }")
            continue
        if "Play." in line:
            continue
        cleaned_lines.append(line)
    GAME.write_text("\n".join(cleaned_lines) + "\n", encoding="utf-8")

    game_text = GAME.read_text(encoding="utf-8")
    for forbidden in ("Play.", "ach(", "R.string.achievement_", "LEADERBOARD", "ACHIEVEMENTS", "GOOGLE SIGN-IN"):
        if forbidden in game_text:
            raise RuntimeError(f"Google Play Games reference remains in GameView.kt: {forbidden}")
    for path in (MAIN, MANIFEST, GRADLE, STRINGS):
        content = path.read_text(encoding="utf-8")
        if "com.google.android.gms.games" in content or "PlayGames" in content:
            raise RuntimeError(f"Google Play Games reference remains in {path}")
    for path in JAVA.rglob("*.kt"):
        content = path.read_text(encoding="utf-8")
        if "PlayGames" in content or "Play." in content:
            raise RuntimeError(f"Google Play Games reference remains in {path}")


def set_version_and_sound_persistence():
    gradle = GRADLE.read_text(encoding="utf-8")
    if 'versionCode = 12' not in gradle or 'versionName = "1.9"' not in gradle:
        raise RuntimeError("Expected v1.9 version metadata before applying v2.0 patch")
    gradle = gradle.replace("versionCode = 12", "versionCode = 13", 1)
    gradle = gradle.replace('versionName = "1.9"', 'versionName = "2.0"', 1)
    GRADLE.write_text(gradle, encoding="utf-8")

    sfx = SFX.read_text(encoding="utf-8")
    old = "    var enabled = true\n"
    new = """    private val prefs = ctx.getSharedPreferences("neon", 0)
    var enabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) { prefs.edit().putBoolean("sound_enabled", value).apply() }
"""
    if new not in sfx:
        if old not in sfx:
            raise RuntimeError("Sound preferences anchor missing")
        sfx = sfx.replace(old, new, 1)
        SFX.write_text(sfx, encoding="utf-8")


def update_project_readme():
    README.write_text(
        """# Neon Shooter 2.0

A portrait-mode neon arcade shooter for Android, built with Kotlin and Android Canvas.

## Gameplay
- Drag anywhere in the play area to move; the ship fires automatically.
- Use the on-screen Missile and Laser controls when they are ready.
- Fight escalating enemy waves and a boss every fifth wave.
- Choose upgrades between waves, unlock ships, and build a stronger loadout.
- Best score, upgrades, and sound preference are stored locally on the device.

## Build
Open this directory in Android Studio, sync Gradle, and build the release variant.
The GitHub Actions workflow builds and signs the release APK.

## Online services
The game does not require Google Play Games, Google sign-in, an online leaderboard, or achievements.
"""
    )


def main():
    if not GAME.exists():
        raise RuntimeError("Android project has not been extracted")
    strip_google_play_games()
    set_version_and_sound_persistence()
    update_project_readme()
    print("Neon Shooter 2.0 patch applied: Google Play Games removed; local progress retained.")


if __name__ == "__main__":
    main()
