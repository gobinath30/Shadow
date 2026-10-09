package com.shadow.chargealert

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var testPlayer: AlertPlayer
    private lateinit var batteryText: TextView
    private lateinit var thresholdLabel: TextView
    private lateinit var intervalLabel: TextView
    private lateinit var ringtoneName: TextView
    private lateinit var noteInput: TextInputEditText

    private var batteryLevel = -1

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickRingtone =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            if (result.resultCode == RESULT_OK && uri != null) {
                prefs.ringtoneUri = uri.toString()
                updateRingtoneName()
            }
        }

    private val pickAudioFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            // Keep access to the file after a reboot so the background alert can still play it.
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs.ringtoneUri = uri.toString()
            updateRingtoneName()
        }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (raw < 0 || scale <= 0) return
            batteryLevel = raw * 100 / scale
            batteryText.text = getString(
                if (plugged) R.string.battery_charging else R.string.battery_not_charging,
                batteryLevel,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        testPlayer = AlertPlayer(this)

        // Keep content clear of the status bar, navigation bar and keyboard.
        val root = findViewById<android.view.View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        batteryText = findViewById(R.id.battery_text)
        thresholdLabel = findViewById(R.id.threshold_label)
        intervalLabel = findViewById(R.id.interval_label)
        ringtoneName = findViewById(R.id.ringtone_name)
        noteInput = findViewById(R.id.note_input)

        setUpMonitoringControls()
        setUpSoundControls()
        setUpVoiceControls()

        updateLabels()
        updateRingtoneName()
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
        testPlayer.stop()
        super.onStop()
    }

    override fun onDestroy() {
        testPlayer.release()
        super.onDestroy()
    }

    private fun setUpMonitoringControls() {
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
    }

    private fun setUpSoundControls() {
        val group = findViewById<RadioGroup>(R.id.sound_mode_group)
        group.check(
            when (prefs.soundMode) {
                Prefs.SOUND_RINGTONE -> R.id.sound_ringtone
                Prefs.SOUND_VOICE -> R.id.sound_voice
                else -> R.id.sound_both
            },
        )
        group.setOnCheckedChangeListener { _, checkedId ->
            prefs.soundMode = when (checkedId) {
                R.id.sound_ringtone -> Prefs.SOUND_RINGTONE
                R.id.sound_voice -> Prefs.SOUND_VOICE
                else -> Prefs.SOUND_BOTH
            }
        }

        findViewById<Button>(R.id.pick_ringtone).setOnClickListener {
            val current = prefs.ringtoneUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_ringtone_title))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            pickRingtone.launch(intent)
        }
        findViewById<Button>(R.id.pick_file).setOnClickListener {
            pickAudioFile.launch(arrayOf("audio/*"))
        }
    }

    private fun setUpVoiceControls() {
        noteInput.setText(prefs.voiceMessage)
        noteInput.doAfterTextChanged { prefs.voiceMessage = it?.toString().orEmpty() }

        val presets = findViewById<ChipGroup>(R.id.preset_group)
        Prefs.PRESET_MESSAGES.forEach { message ->
            val chip = Chip(this).apply {
                text = AlertPlayer.formatMessage(message, prefs.threshold)
                setOnClickListener {
                    noteInput.setText(message)
                    noteInput.setSelection(message.length)
                }
            }
            presets.addView(chip)
        }

        findViewById<Button>(R.id.test_alert).setOnClickListener {
            testPlayer.play(if (batteryLevel >= 0) batteryLevel else prefs.threshold)
        }
        findViewById<Button>(R.id.stop_test).setOnClickListener { testPlayer.stop() }
    }

    private fun updateLabels() {
        thresholdLabel.text = getString(R.string.threshold_label, prefs.threshold)
        intervalLabel.text = resources.getQuantityString(
            R.plurals.interval_label,
            prefs.intervalMinutes,
            prefs.intervalMinutes,
        )
    }

    private fun updateRingtoneName() {
        val uriString = prefs.ringtoneUri
        val name = if (uriString == null) {
            getString(R.string.ringtone_default)
        } else {
            val uri = Uri.parse(uriString)
            displayName(uri)
                ?: runCatching { RingtoneManager.getRingtone(this, uri)?.getTitle(this) }.getOrNull()
                ?: getString(R.string.ringtone_unknown)
        }
        ringtoneName.text = getString(R.string.ringtone_current, name)
    }

    /** File name of a document picked from storage, or null for system ringtones. */
    private fun displayName(uri: Uri): String? = runCatching {
        if (uri.authority == "media" || uri.authority == "settings") return null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

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
