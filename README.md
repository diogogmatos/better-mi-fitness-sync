# <img src="branding/app-icon.svg" alt="" width="40" height="40" align="absmiddle" /> Better Mi Fitness Sync

Sync your **Mi Fitness** data to **Health Connect** (Android) or **Apple Health** (iOS): steps, heart rate, sleep, workouts, and more.

## Download

[![Download Android APK](https://img.shields.io/github/v/release/ilyasaftr/better-mi-fitness-sync?label=Download%20APK&logo=android&color=3DDC84)](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.apk)
[![Download iOS IPA](https://img.shields.io/github/v/release/ilyasaftr/better-mi-fitness-sync?label=Download%20IPA&logo=apple&color=000000)](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.ipa)
[![All releases](https://img.shields.io/github/v/release/ilyasaftr/better-mi-fitness-sync?label=All%20releases&logo=github)](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest)

| | Latest build |
|--|--|
| **Android** | [BetterMiFitnessSync.apk](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.apk) |
| **iOS** | [BetterMiFitnessSync.ipa](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.ipa) |

## Install on Android

1. Download the APK above.
2. Install it (allow **Install unknown apps** if asked).
3. Open the app, grant **Health Connect** access, sign in with your Mi account, then sync.

Needs [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) (or the built-in version on newer phones).

## Install on iOS

1. Download the IPA above.
2. Re-sign and install on your iPhone (e.g. [Sideloadly](https://sideloadly.io/) or Xcode).
3. Allow **Health** access, sign in with your Mi account, then sync.

**Note:** The IPA is unsigned. To sync to **Apple Health** you need a **paid Apple Developer** account with **HealthKit** enabled (a free Apple ID is not enough). Syncing to **Google Health** does not require HealthKit and works without a paid developer account.

## What it does

- Signs in to your Mi Fitness account
- Syncs steps, heart rate, sleep, SpO₂, weight, workouts (including GPS routes when available), and other metrics
- Writes data to Health Connect (Android) or Apple Health / Google Health (iOS)
- Optional auto-sync on a schedule
- Keeps login and health data on your device only

### Google Health (iOS)

On iOS you can choose to sync to **Google Health** instead of Apple Health. Google Health currently accepts only **workouts, sleep, and weight & body fat**; the other metrics are read-only there and show as "Unsupported" during a sync. Sign in with your Google account when prompted — the app stores only the OAuth tokens on your device.

## Privacy

- Your Mi login stays on the phone.
- Health data goes from Mi Fitness → your phone’s health app.
- Everything stays on your phone.
