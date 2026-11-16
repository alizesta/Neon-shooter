# Monetization setup for Neon Shooter

## SDK integration

Neon Shooter integrates the official **Tapsell Mediation Android SDK 1.4.0-alpha04** for rewarded and interstitial video.

The daily 100-coin reward is granted only from the rewarded SDK's `onRewarded()` callback. Skipping or merely closing a rewarded video never grants coins. Interstitials are used only at the existing pause between waves.

Official references:
- Android setup: https://developer.tapsell.ir/fa/docs/sdk/platforms/android/quick-start/
- Rewarded video API: https://developer.tapsell.ir/fa/docs/sdk/platforms/android/ad-formats/rewarded/
- Interstitial API: https://developer.tapsell.ir/fa/docs/sdk/platforms/android/ad-formats/interstitial/
- Test credentials and zones: https://developer.tapsell.ir/fa/docs/sdk/platforms/android/test/
- Publisher dashboard: https://app.tapsell.ir/

## Test defaults and live publisher credentials

GitHub Actions uses Tapsell's **official test App ID**, **test Rewarded Zone ID** and **test Interstitial Zone ID** by default. Test ads do not generate revenue.

To monetize the game, add these GitHub repository Actions secrets:
- `TAPSELL_APP_ID`: the App ID for Neon Shooter from the Tapsell publisher dashboard.
- `TAPSELL_REWARDED_ZONE_ID`: the Rewarded Video Zone ID created for Neon Shooter.
- `TAPSELL_INTERSTITIAL_ZONE_ID`: the Interstitial Zone ID created for Neon Shooter.

The build uses the secrets when provided and otherwise falls back to Tapsell's documented test IDs. No live identifiers are committed to Git.
