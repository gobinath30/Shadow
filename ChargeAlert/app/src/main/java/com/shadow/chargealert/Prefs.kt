package com.shadow.chargealert

import android.content.Context

/** User settings, stored in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("charge_alert", Context.MODE_PRIVATE)

    /** Battery percentage at which alerts start. */
    var threshold: Int
        get() = sp.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = sp.edit().putInt(KEY_THRESHOLD, value).apply()

    /** Minutes between repeated alerts while the phone stays plugged in. */
    var intervalMinutes: Int
        get() = sp.getInt(KEY_INTERVAL, DEFAULT_INTERVAL)
        set(value) = sp.edit().putInt(KEY_INTERVAL, value).apply()

    /** Whether background monitoring is switched on. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** One of [SOUND_BOTH], [SOUND_RINGTONE] or [SOUND_VOICE]. */
    var soundMode: Int
        get() = sp.getInt(KEY_SOUND_MODE, SOUND_BOTH)
        set(value) = sp.edit().putInt(KEY_SOUND_MODE, value).apply()

    /** Chosen ringtone or audio file; null means the phone's default alarm sound. */
    var ringtoneUri: String?
        get() = sp.getString(KEY_RINGTONE_URI, null)
        set(value) = sp.edit().putString(KEY_RINGTONE_URI, value).apply()

    /** What the voice reminder says. "{level}" is replaced with the battery percentage. */
    var voiceMessage: String
        get() = sp.getString(KEY_VOICE_MESSAGE, null)?.takeIf { it.isNotBlank() } ?: PRESET_MESSAGES.first()
        set(value) = sp.edit().putString(KEY_VOICE_MESSAGE, value).apply()

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SOUND_MODE = "sound_mode"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_VOICE_MESSAGE = "voice_message"

        const val DEFAULT_THRESHOLD = 80
        const val DEFAULT_INTERVAL = 5
        const val MIN_THRESHOLD = 20
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 60

        const val SOUND_BOTH = 0
        const val SOUND_RINGTONE = 1
        const val SOUND_VOICE = 2

        /** Ready-made voice reminders the user can pick and then edit. */
        val PRESET_MESSAGES = listOf(
            "Battery is at {level} percent. Please unplug your phone now.",
            "Unplug your phone now.",
            "Charging complete. Remove the charger to protect your battery.",
            "Hey! Your phone has reached {level} percent. Time to unplug the charger.",
            "Attention please. Battery charged. Unplug the charger now.",
        )
    }
}
