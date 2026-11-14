# Neon Shooter (Android, Kotlin)
Pixel-art neon space shooter. Drag to move, auto-fire, waves + boss every 5th wave,
Atari-style generated sounds, Google Play Games sign-in / leaderboard / achievements.

## Setup
1. Open the folder in Android Studio (Gradle sync will fetch the wrapper).
2. In Play Console > Play Games Services create: 1 leaderboard + 4 achievements
   (first kill, score 1000, boss slayer, wave 10). Put the IDs in app/src/main/res/values/strings.xml
   (app_id, leaderboard_high_score, achievement_*).
3. Add your SHA-1 (debug + release) to the Play Games credentials, add yourself as tester.
Without real IDs the game still runs; Google features just stay inactive.

## Update log (Gregorian date, Iran time)
- 2026-11-14 11:39 IRST (UTC+3:30) — v1.0 first version
- 2026-11-14 11:45 IRST (UTC+3:30) — added GitHub Actions workflow to build the debug APK in the cloud
