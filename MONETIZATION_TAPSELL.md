# Monetization setup for Neon Shooter

## Recommended network: Tapsell Mediation

Use the current **Tapsell Mediation** Android SDK, not the legacy Tapsell SDK or Tapsell Plus. Tapsell's current documentation marks the older SDK as unsupported and Tapsell Plus as deprecated for future support.

- Publisher dashboard: https://app.tapsell.ir/
- Android/Kotlin integration guide: https://developer.tapsell.ir/fa/docs/sdk/platforms/android/
- Official Android sample: https://github.com/tapsellorg/TapsellMediation-AndroidSample

## What to register

1. Create a publisher account in the Tapsell dashboard.
2. Register the Android application with package name `com.claude.neonshooter`.
3. Create a **Rewarded Video** placement and an **Interstitial** placement.
4. Keep the App ID and both placement/Zone IDs private. Do not commit credentials into Git.

## Recommended placements for this game

- Rewarded video: on Game Over, let the player voluntarily watch one ad to continue with one life. Grant the life only after the SDK confirms a completed and rewarded view.
- Interstitial: only at a natural break, such as after several completed waves or after a run ends. Use a frequency cap; never interrupt active gameplay.
- Avoid a permanent banner over the play field. It competes with the HUD and reduces usable screen space.

## Integration status

The game currently has **no live ad SDK or ad placements**. The publisher account's App ID and Zone IDs are needed before ads can be initialized and reward callbacks can be wired safely. The Google Play Games SDK has been removed independently; Tapsell Mediation does not require adding Google sign-in or a leaderboard to the game. Leave optional third-party ad adapters disabled unless their separate requirements are met.

## Revenue expectations

No network can guarantee "high" earnings. Revenue depends on active users, the share of users in each country, ad fill rate, completed views, the ad format, and the effective CPM/CPC. Start with rewarded video, measure actual revenue and retention for a few weeks, then decide whether to add interstitials or mediation adapters.
