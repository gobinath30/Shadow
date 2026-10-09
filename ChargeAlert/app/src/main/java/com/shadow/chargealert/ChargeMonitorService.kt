package com.shadow.chargealert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that watches the battery while monitoring is enabled.
 *
 * Once the phone is charging and the level reaches the user's threshold, it raises a loud
 * notification immediately and then again every [Prefs.intervalMinutes] minutes until the
 * charger is unplugged (or the user taps "Stop until unplugged").
 */
class ChargeMonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var notificationManager: NotificationManager
    private lateinit var alertPlayer: AlertPlayer
    private val handler = Handler(Looper.getMainLooper())

    private var level = -1
    private var charging = false
    private var alerting = false
    /** Set when the user silences alerts; cleared when the charger is unplugged. */
    private var silenced = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val repeatAlert = object : Runnable {
        override fun run() {
            if (!alerting) return
            postAlert()
            handler.postDelayed(this, intervalMillis())
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBatteryChanged(intent)
                Intent.ACTION_POWER_DISCONNECTED -> {
                    charging = false
                    evaluate()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        alertPlayer = AlertPlayer(this)
        createChannels(this)

        ServiceCompat.startForeground(
            this,
            ONGOING_ID,
            buildOngoingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // ACTION_BATTERY_CHANGED is sticky, so registering also delivers the current state.
        ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SILENCE -> {
                silenced = true
                alertPlayer.stop()
                evaluate()
            }
            ACTION_SETTINGS_CHANGED -> {
                // Restart the repeat timer so a new interval takes effect right away.
                if (alerting) {
                    handler.removeCallbacks(repeatAlert)
                    handler.postDelayed(repeatAlert, intervalMillis())
                }
                evaluate()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        stopAlerting()
        alertPlayer.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onBatteryChanged(intent: Intent) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (raw >= 0 && scale > 0) level = raw * 100 / scale
        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        evaluate()
    }

    /** Decides whether alerts should be running based on the latest battery state. */
    private fun evaluate() {
        if (!charging) silenced = false

        val shouldAlert = charging && !silenced && level >= prefs.threshold
        if (shouldAlert && !alerting) startAlerting()
        if (!shouldAlert && alerting) stopAlerting()

        notificationManager.notify(ONGOING_ID, buildOngoingNotification())
    }

    private fun startAlerting() {
        alerting = true
        // The phone is on the charger, so holding a wake lock costs nothing meaningful and
        // keeps the repeat timer firing on time while the screen is off.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChargeAlert:alerting")
            .apply { acquire(MAX_WAKE_LOCK_MILLIS) }
        handler.removeCallbacks(repeatAlert)
        handler.post(repeatAlert)
    }

    private fun stopAlerting() {
        alerting = false
        handler.removeCallbacks(repeatAlert)
        notificationManager.cancel(ALERT_ID)
        alertPlayer.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun postAlert() {
        val silenceIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ChargeMonitorService::class.java).setAction(ACTION_SILENCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(getString(R.string.alert_title, level))
            .setContentText(getString(R.string.alert_text, prefs.intervalMinutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.action_silence), silenceIntent)
            .setAutoCancel(true)
            .build()
        // Cancel first so the notification vibrates again even if the old one is still showing.
        notificationManager.cancel(ALERT_ID)
        notificationManager.notify(ALERT_ID, notification)
        alertPlayer.play(level)
    }

    private fun buildOngoingNotification(): Notification {
        val status = when {
            level < 0 -> getString(R.string.status_unknown)
            alerting -> getString(R.string.status_alerting, level)
            charging && silenced -> getString(R.string.status_silenced, level)
            charging -> getString(R.string.status_charging, level, prefs.threshold)
            else -> getString(R.string.status_not_charging, level)
        }
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(getString(R.string.ongoing_title))
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun intervalMillis(): Long = prefs.intervalMinutes * 60_000L

    companion object {
        const val CHANNEL_ONGOING = "monitor"
        // Version 1 of the alert channel had its own sound; sound is now played by AlertPlayer.
        private const val OLD_CHANNEL_ALERT = "charge_alert"
        const val CHANNEL_ALERT = "charge_alert_v2"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2
        private const val MAX_WAKE_LOCK_MILLIS = 12 * 60 * 60 * 1000L

        private const val ACTION_SILENCE = "com.shadow.chargealert.SILENCE"
        private const val ACTION_SETTINGS_CHANGED = "com.shadow.chargealert.SETTINGS_CHANGED"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ChargeMonitorService::class.java))
        }

        fun notifySettingsChanged(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ChargeMonitorService::class.java).setAction(ACTION_SETTINGS_CHANGED),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargeMonitorService::class.java))
        }

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ongoing = NotificationChannel(
                CHANNEL_ONGOING,
                context.getString(R.string.channel_ongoing),
                NotificationManager.IMPORTANCE_LOW,
            )
            val alert = NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
                // AlertPlayer plays the ringtone and voice, so the notification itself is silent.
                setSound(null, null)
            }
            nm.deleteNotificationChannel(OLD_CHANNEL_ALERT)
            nm.createNotificationChannels(listOf(ongoing, alert))
        }
    }
}
