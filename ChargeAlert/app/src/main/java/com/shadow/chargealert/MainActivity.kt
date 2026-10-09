package com.shadow.chargealert

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var batteryText: TextView
    private lateinit var thresholdLabel: TextView
    private lateinit var intervalLabel: TextView

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (raw < 0 || scale <= 0) return
            val pct = raw * 100 / scale
            batteryText.text = getString(
                if (plugged) R.string.battery_charging else R.string.battery_not_charging,
                pct,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        batteryText = findViewById(R.id.battery_text)
        thresholdLabel = findViewById(R.id.threshold_label)
        intervalLabel = findViewById(R.id.interval_label)

        val thresholdBar = findViewById<SeekBar>(R.id.threshold_bar)
        thresholdBar.max = 100 - Prefs.MIN_THRESHOLD
        thresholdBar.progress = prefs.threshold - Prefs.MIN_THRESHOLD
        thresholdBar.setOnSeekBarChangeListener(onChange { progress ->
            prefs.threshold = progress + Prefs.MIN_THRESHOLD
            updateLabels()
        })

        val intervalBar = findViewById<SeekBar>(R.id.interval_bar)
        intervalBar.max = Prefs.MAX_INTERVAL - Prefs.MIN_INTERVAL
        intervalBar.progress = prefs.intervalMinutes - Prefs.MIN_INTERVAL
        intervalBar.setOnSeekBarChangeListener(onChange { progress ->
            prefs.intervalMinutes = progress + Prefs.MIN_INTERVAL
            updateLabels()
        })

        val enabledSwitch = findViewById<MaterialSwitch>(R.id.enabled_switch)
        enabledSwitch.isChecked = prefs.enabled
        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            if (checked) {
                askForNotificationPermission()
                ChargeMonitorService.start(this)
            } else {
                ChargeMonitorService.stop(this)
            }
        }

        updateLabels()
        ChargeMonitorService.createChannels(this)
        if (prefs.enabled) {
            askForNotificationPermission()
            ChargeMonitorService.start(this)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }

    private fun updateLabels() {
        thresholdLabel.text = getString(R.string.threshold_label, prefs.threshold)
        intervalLabel.text = resources.getQuantityString(
            R.plurals.interval_label,
            prefs.intervalMinutes,
            prefs.intervalMinutes,
        )
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** SeekBar listener that applies [onProgress] live and tells the service once the user lets go. */
    private fun onChange(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onProgress(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (prefs.enabled) ChargeMonitorService.notifySettingsChanged(this@MainActivity)
        }
    }
}
