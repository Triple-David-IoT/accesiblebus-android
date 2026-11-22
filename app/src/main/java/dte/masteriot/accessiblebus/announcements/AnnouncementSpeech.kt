
package dte.masteriot.accessiblebus.announcements

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class AnnouncementSpeech(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE)
                as AudioManager

    private val speechMutex = Mutex()
    private val utteranceCounter = AtomicLong(0)

    @Volatile
    private var activeUtteranceId: String? = null

    private val initialization = CompletableDeferred<Boolean>()

    private lateinit var tts: TextToSpeech
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    private var closed = false


    private val focusListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                if (::tts.isInitialized) {
                    tts.stop()
                }
            }
        }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->

            if (status != TextToSpeech.SUCCESS) {
                initialization.complete(false)
                return@TextToSpeech
            }
            tts.setSpeechRate(0.85f)
            tts.setPitch(1.0f)

            val languageResult = tts.setLanguage(
                Locale("en", "US")
            )

            val languageAvailable =
                languageResult == TextToSpeech.LANG_AVAILABLE ||
                        languageResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                        languageResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE

            initialization.complete(languageAvailable)
        }

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    utteranceResults[utteranceId]?.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceResults[utteranceId]?.complete(false)
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean
                ) {
                    utteranceResults[utteranceId]?.complete(false)
                }
            }
        )
    }

    private val utteranceResults =
        java.util.concurrent.ConcurrentHashMap<
                String,
                CompletableDeferred<Boolean>
                >()

    suspend fun speak(message: String): Boolean =
        speechMutex.withLock {

            if (closed || message.isBlank()) {
                return@withLock false
            }

            val ready = withTimeoutOrNull(5_000L.milliseconds) {
                initialization.await()
            } ?: false

            if (!ready || closed) {
                return@withLock false
            }

            if (!requestAudioFocus()) {
                return@withLock false
            }

            val utteranceId =
                "announcement_${utteranceCounter.incrementAndGet()}"

            activeUtteranceId = utteranceId

            val result = CompletableDeferred<Boolean>()
            utteranceResults[utteranceId] = result

            try {
                val status = tts.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    utteranceId
                )

                if (status == TextToSpeech.ERROR) {
                    return@withLock false
                }

                withTimeoutOrNull(30_000L.milliseconds) {
                    result.await()
                } ?: false

            } finally {
                utteranceResults.remove(utteranceId)
                activeUtteranceId = null
                abandonAudioFocus()
            }
        }

    private fun requestAudioFocus(): Boolean {

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val request = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                        )
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SPEECH
                        )
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()

            focusRequest = request
            audioManager.requestAudioFocus(request)

        } else {

            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    fun stopCurrent() {
        val utteranceId = activeUtteranceId

        if (::tts.isInitialized && !closed) {
            tts.stop()

            utteranceId?.let { id ->
                utteranceResults[id]?.complete(false)
            }
        }
    }

    fun shutdown() {
        closed = true

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        if (!initialization.isCompleted) {
            initialization.complete(false)
        }
    }
}
