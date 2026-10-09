# Charge Alert (Android)

Alerts you when your phone reaches a battery percentage you choose while charging, then
keeps alerting at a fixed interval until you unplug the charger.

## How it works

1. Open the app, pick the **alert percentage** (20–100%, default 80%) and the **repeat
   interval** (1–60 minutes, default 5), and switch **Monitoring enabled** on.
2. A small ongoing notification shows the app is watching the battery (Android requires
   this for anything that runs in the background).
3. When the phone is charging and the level reaches your percentage, the app plays your alert
   sound on the alarm volume and shows a notification with vibration.
4. The alert repeats every *N* minutes until you unplug. Unplugging stops it right away.
   Tapping **Stop until unplugged** on the alert silences it for the current charge.
5. Monitoring restarts automatically after the phone reboots.

## Alert sound and voice message

- **Alert sound:** choose *Ringtone, then voice message*, *Ringtone only* or *Voice message only*.
- **Ringtone:** *Phone tones* opens Android's tone picker (alarms, ringtones, notification
  sounds). *Audio file* lets you pick any song or recording from storage. Tones are cut off after
  15 seconds.
- **Voice message:** tap one of the ready-made messages (e.g. "Unplug your phone now.") or
  write your own in the note box. The phone reads it aloud with text-to-speech each time the alert
  fires. Write `{level}` to have the battery percentage spoken, e.g.
  "Battery is at {level} percent, unplug now".
- **Test alert** plays the current setup immediately so you can check it.

## Code

| File | Purpose |
| --- | --- |
| `AlertPlayer.kt` | Plays the ringtone and speaks the voice message on the alarm stream |
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
- The alert plays on the **alarm volume**, so it's heard even when ringer or notification volume
  is low. If it's too quiet, turn up the alarm volume.
- The voice message uses your phone's text-to-speech engine (usually Google Speech Services).
