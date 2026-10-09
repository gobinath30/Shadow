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

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_ENABLED = "enabled"

        const val DEFAULT_THRESHOLD = 80
        const val DEFAULT_INTERVAL = 5
        const val MIN_THRESHOLD = 20
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 60
    }
}
