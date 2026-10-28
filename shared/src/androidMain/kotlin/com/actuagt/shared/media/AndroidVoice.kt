package com.actuagt.shared.media

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// Android speech bridge: MainActivity registers once. transcribe() asks for
// the microphone permission when needed, then listens with the system
// recognizer in Spanish and returns the best transcript, or null.
object AndroidVoice {
    private const val LISTEN_TIMEOUT_MILLIS = 60_000L

    private var activity: ComponentActivity? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var permissionResult: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    fun register(activity: ComponentActivity) {
        if (permissionLauncher != null) return
        this.activity = activity
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionResult?.complete(granted)
            permissionResult = null
        }
    }

    suspend fun listen(): String? = mutex.withLock {
        val host = activity ?: return null
        if (!SpeechRecognizer.isRecognitionAvailable(host)) return null
        if (ContextCompat.checkSelfPermission(host, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val deferred = CompletableDeferred<Boolean>()
            permissionResult = deferred
            permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO) ?: return null
            if (!deferred.await()) return null
        }

        val result = CompletableDeferred<String?>()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(host)
        try {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    result.complete(matches?.firstOrNull()?.takeIf { it.isNotBlank() })
                }

                override fun onError(error: Int) {
                    result.complete(null)
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es").toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
            return withTimeoutOrNull(LISTEN_TIMEOUT_MILLIS) { result.await() }
        } finally {
            recognizer.destroy()
        }
    }
}
