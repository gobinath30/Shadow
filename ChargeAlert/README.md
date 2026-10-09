# Charge Alert (Android)

Alerts you when your phone reaches a battery percentage you choose while charging, then
keeps alerting at a fixed interval until you unplug the charger.

## How it works

1. Open the app, pick the **alert percentage** (20–100%, default 80%) and the **repeat
   interval** (1–60 minutes, default 5), and switch **Monitoring enabled** on.
2. A small ongoing notification shows the app is watching the battery (Android requires
   this for anything that runs in the background).
3. When the phone is charging and the level reaches your percentage, you get a loud alarm-style
   notification with vibration.
4. The alert repeats every *N* minutes until you unplug. Unplugging stops it right away.
   Tapping **Stop until unplugged** on the alert silences it for the current charge.
5. Monitoring restarts automatically after the phone reboots.

## Code

| File | Purpose |
| --- | --- |
| `ChargeMonitorService.kt` | Foreground service: listens for battery changes and unplug events, runs the repeat timer, posts alerts |
| `MainActivity.kt` | Settings screen: percentage, interval, on/off switch, live battery level |
| `Prefs.kt` | Stores the settings |
| `BootReceiver.kt` | Restarts monitoring after reboot or app update |

Requirements: Android 8.0 (API 26) or newer.

## Build

- **Android Studio:** open the `ChargeAlert` folder and press Run.
- **Command line:** `./gradlew assembleDebug` (needs the Android SDK) and the APK appears in
  `app/build/outputs/apk/debug/`.
- **GitHub Actions:** every push that touches `ChargeAlert/` builds the APK; download it from
  the workflow run's *Artifacts* section.

## Tips

- Some phone makers (Xiaomi, Oppo, Vivo, Samsung, etc.) kill background apps aggressively. If
  alerts stop arriving, set Charge Alert's battery usage to *Unrestricted* in system settings.
- The alert uses the alarm sound, so it's audible even when notification volume is low. You can
  change the sound or vibration in Android's notification settings for "Charge level alerts".
