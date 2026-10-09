package com.shadow.chargealert

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Plays the charge alert itself: the chosen ringtone, the spoken voice message, or both.
 *
 * Audio goes to the alarm stream so it is heard even when notification or ringer volume is
 * low, and it doesn't depend on the phone's notification sound settings.
 */
class AlertPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = Prefs(appContext)
    private val handler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var ttsReady = false
    /** Text waiting to be spoken once TextToSpeech finishes initialising. */
    private var pendingSpeech: String? = null

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            setUpTts()
            pendingSpeech?.let { speakNow(it) }
        }
        pendingSpeech = null
    }

    private val stopRingtone = Runnable { finishRingtone(speakAfter = true) }
    private var speechAfterRingtone: String? = null

    /** Plays one round of the alert for the given battery [level]. */
    fun play(level: Int) {
        stop()
        val message = formatMessage(prefs.voiceMessage, level)
        when (prefs.soundMode) {
            Prefs.SOUND_VOICE -> speak(message)
            Prefs.SOUND_RINGTONE -> playRingtone(thenSpeak = null)
            else -> playRingtone(thenSpeak = message)
        }
    }

    fun stop() {
        handler.removeCallbacks(stopRingtone)
        speechAfterRingtone = null
        mediaPlayer?.run {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null
        pendingSpeech = null
        if (ttsReady) tts.stop()
    }

    fun release() {
        stop()
        tts.shutdown()
    }

    private fun playRingtone(
        thenSpeak: String?,
        uri: Uri? = prefs.ringtoneUri?.let(Uri::parse) ?: defaultAlarmUri(),
    ) {
        speechAfterRingtone = thenSpeak
        if (uri == null) {
            // No alarm or notification sound exists on this phone: just speak the message.
            finishRingtone(speakAfter = true)
            return
        }
        val player = MediaPlayer()
        val started = runCatching {
            player.setAudioAttributes(ALARM_ATTRIBUTES)
            player.setDataSource(appContext, uri)
            player.setOnCompletionListener { finishRingtone(speakAfter = true) }
            player.prepare()
            player.start()
        }.isSuccess
        if (!started) {
            // The chosen sound can't be played (e.g. the file was deleted): fall back to the
            // default alarm, and if even that fails, go straight to the voice message.
            player.release()
            val fallback = defaultAlarmUri()
            if (fallback != null && uri != fallback) {
                playRingtone(thenSpeak, fallback)
            } else {
                finishRingtone(speakAfter = true)
            }
            return
        }
        mediaPlayer = player
        // Long songs or looping alarm tones are cut off so the voice message isn't delayed.
        handler.postDelayed(stopRingtone, MAX_RINGTONE_MILLIS)
    }

    private fun finishRingtone(speakAfter: Boolean) {
        handler.removeCallbacks(stopRingtone)
        mediaPlayer?.run {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null
        val speech = speechAfterRingtone
        speechAfterRingtone = null
        if (speakAfter && speech != null) speak(speech)
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        if (ttsReady) speakNow(text) else pendingSpeech = text
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun setUpTts() {
        tts.setAudioAttributes(ALARM_ATTRIBUTES)
        val result = tts.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }
    }

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    companion object {
        private const val UTTERANCE_ID = "charge_alert"
        private const val MAX_RINGTONE_MILLIS = 15_000L

        private val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** Replaces the {level} placeholder in the user's message with the battery percentage. */
        fun formatMessage(template: String, level: Int): String =
            template.replace("{level}", level.toString(), ignoreCase = true)
    }
}
