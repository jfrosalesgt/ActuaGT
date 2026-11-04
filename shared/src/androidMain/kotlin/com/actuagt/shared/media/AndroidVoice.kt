package com.actuagt.shared.media

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// Android speech bridge: MainActivity registers once. transcribe() asks for
// the microphone permission when needed, then listens with the system
// recognizer in Spanish and returns the best transcript, or null.
object AndroidVoice {
    // A full idea rarely fits one utterance: keep the mic open this long,
    // accumulating every partial result until Detener or the timeout.
    private const val LISTEN_TIMEOUT_MILLIS = 30_000L
    private const val MINIMUM_LENGTH_MILLIS = 2_000
    private const val POSSIBLY_COMPLETE_SILENCE_MILLIS = 5_000
    private const val COMPLETE_SILENCE_MILLIS = 10_000

    private var activity: ComponentActivity? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var permissionResult: CompletableDeferred<Boolean>? = null
    // System voice popup used as a last resort when the direct recognizer
    // sessions come back empty (wedged service on some ROMs).
    private var dictationLauncher: ActivityResultLauncher<Intent>? = null
    private var dictationResult: CompletableDeferred<String?>? = null
    private var fallbackCancelled = false
    private val mutex = Mutex()

    // Recognizer of the in-flight capture, if any. Written under the mutex,
    // read by stop() without locking so Detener never deadlocks.
    @Volatile
    private var currentRecognizer: SpeechRecognizer? = null

    // Ends the in-flight capture early: the recognizer delivers onResults
    // with what was captured so far, which completes listen().
    fun stop() {
        stopRequested = true
        currentRecognizer?.stopListening()
    }

    @Volatile
    private var stopRequested = false

    // True when the in-flight segment ended in silence (no match or speech
    // timeout) rather than another error. Sequential segments only, so a
    // plain field is enough.
    private var segmentSilentEnd = false

    // Cumulative across the whole capture (reset per listen()): whether the
    // service ever signalled speech, and whether any segment came back
    // empty without an error. A NO_MATCH on the final fragment must not
    // rewrite a capture where the user did speak into "silence".
    private var heardSpeech = false
    private var hadEmptyBlip = false

    // True when the in-flight segment delivered empty results without any
    // error: the system service sometimes drops a session (hotword race)
    // seconds after starting it. Retried, not fatal.
    private var segmentEmptyResult = false

    // One silent retry before giving up: a transient service blip recovers
    // without bothering the user, but a wedged service must reach the
    // system-popup fallback fast instead of stuttering through the mic.
    private const val MAX_EMPTY_RETRIES = 1
    private const val EMPTY_RETRY_DELAY_MILLIS = 300L

    // Remembers a wedged direct path per device: once the system popup
    // rescues a capture, later captures go straight to it instead of
    // stuttering through dead direct sessions first. A direct success
    // clears it again.
    private const val PREFS_NAME = "actuagt_voice"
    private const val KEY_PREFER_POPUP = "prefer_system_popup"

    // True when the last capture ended because nobody spoke (no match or
    // speech timeout) instead of an error: the UI cancels quietly then.
    @Volatile
    var lastEndWasSilence: Boolean = false
        private set

    fun register(activity: ComponentActivity) {
        if (permissionLauncher != null && this.activity === activity) return
        this.activity = activity
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionResult?.complete(granted)
            permissionResult = null
        }
        dictationLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val text = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            // Backing out of the popup is a quiet cancel, not an error.
            if (text == null) fallbackCancelled = true
            dictationResult?.complete(text)
            dictationResult = null
        }
    }

    suspend fun listen(): String? = mutex.withLock {
        val host = activity ?: return null.also {
            Log.w(TAG, "listen: no activity registered")
        }
        if (!SpeechRecognizer.isRecognitionAvailable(host)) return null.also {
            Log.w(TAG, "listen: recognition not available")
        }
        if (ContextCompat.checkSelfPermission(host, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val deferred = CompletableDeferred<Boolean>()
            permissionResult = deferred
            permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO) ?: return null.also {
                Log.w(TAG, "listen: no permission launcher")
            }
            if (!deferred.await()) return null.also {
                Log.w(TAG, "listen: microphone permission denied")
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Let pauses breathe so an idea survives them; the overall
            // cap still ends the capture.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MINIMUM_LENGTH_MILLIS
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLY_COMPLETE_SILENCE_MILLIS
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                COMPLETE_SILENCE_MILLIS
            )
        }
        stopRequested = false
        lastEndWasSilence = false
        segmentSilentEnd = false
        segmentEmptyResult = false
        heardSpeech = false
        hadEmptyBlip = false
        val startedAt = SystemClock.elapsedRealtime()
        val transcript = StringBuilder()
        var emptyRetries = 0
        var usedFallback = false
        val prefs = host.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // On devices where the direct sessions never deliver (wedged
        // service), skip them entirely after the first rescue: the popup
        // is reliable there and opens without the mic stutter.
        val startWithPopup = prefs.getBoolean(KEY_PREFER_POPUP, false)
        try {
            // One recognizer session ends at the first pause, so chain
            // sessions: every chunk is appended until Detener, the cap, or
            // an empty segment (silence) ends the capture. A segment that
            // returns nothing without an error is a service blip: start a
            // fresh session instead of failing the whole capture.
            if (!startWithPopup) {
                withTimeoutOrNull(LISTEN_TIMEOUT_MILLIS) {
                    while (!stopRequested) {
                        val chunk = listenOnce(host, intent)
                        if (chunk != null) {
                            emptyRetries = 0
                            if (transcript.isNotEmpty()) transcript.append(' ')
                            transcript.append(chunk)
                        } else if (segmentEmptyResult && emptyRetries < MAX_EMPTY_RETRIES) {
                            emptyRetries++
                            Log.i(TAG, "empty segment, retrying ($emptyRetries/$MAX_EMPTY_RETRIES)")
                            delay(EMPTY_RETRY_DELAY_MILLIS)
                        } else {
                            break
                        }
                    }
                }
            }
        } finally {
            currentRecognizer?.let {
                currentRecognizer = null
                it.destroy()
            }
        }
        // Quiet cancel only for a clean silence: every segment silent, no
        // speech ever signalled, no empty-result blip. A NO_MATCH on the
        // final fragment after real speech (or empty blips) is a failure,
        // not silence.
        val cleanSilence = segmentSilentEnd && !heardSpeech && !hadEmptyBlip
        if (transcript.isEmpty() && cleanSilence) lastEndWasSilence = true
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        // Direct sessions came back with nothing and it was not a clean
        // silence: the service is wedged on this device, so offer one round
        // through the system voice popup before reporting failure. Skipped
        // for an accidental instant tap (Detener within 2 s with nothing said).
        if (transcript.isEmpty() && !cleanSilence && !(stopRequested && elapsed < 2_000L)) {
            Log.i(TAG, "direct path empty, trying system voice popup")
            fallbackDictation(host, intent)?.let {
                usedFallback = true
                if (transcript.isNotEmpty()) transcript.append(' ')
                transcript.append(it)
            }
            if (fallbackCancelled) lastEndWasSilence = true
        }
        if (transcript.isNotBlank()) {
            // Persist which path works on this device: a rescue switches
            // future captures straight to the popup, a direct success
            // switches back.
            prefs.edit().putBoolean(KEY_PREFER_POPUP, usedFallback).apply()
        }
        Log.i(
            TAG,
            "listen done: chars=${transcript.length} silentEnd=$segmentSilentEnd " +
                "heardSpeech=$heardSpeech hadEmptyBlip=$hadEmptyBlip " +
                "stopRequested=$stopRequested",
        )
        return transcript.toString().takeIf { it.isNotBlank() }
    }

    // One round through the system voice UI. Null when unavailable,
    // refused, or dismissed (tracked as a quiet cancel).
    private suspend fun fallbackDictation(host: ComponentActivity, intent: Intent): String? {
        val launcher = dictationLauncher ?: return null.also {
            Log.w(TAG, "fallback: no dictation launcher")
        }
        val deferred = CompletableDeferred<String?>()
        dictationResult = deferred
        fallbackCancelled = false
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "fallback: voice popup unavailable", e)
            dictationResult = null
            return null
        }
        return deferred.await()
    }

    private const val TAG = "ActuaGT"

    // Null when the segment heard nothing: silence (tracked), an error, or
    // an empty result (tracked separately: worth one fresh session).
    private suspend fun listenOnce(host: ComponentActivity, intent: Intent): String? {
        val result = CompletableDeferred<String?>()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(host)
        currentRecognizer = recognizer
        segmentEmptyResult = false
        try {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() {
                    heardSpeech = true
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    val chunk = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.takeIf { it.isNotBlank() }
                    if (chunk == null) {
                        segmentEmptyResult = true
                        hadEmptyBlip = true
                    }
                    if (!result.isCompleted) result.complete(chunk)
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "segment error=$error stopRequested=$stopRequested")
                    segmentSilentEnd = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (!result.isCompleted) result.complete(null)
                }
            })
            recognizer.startListening(intent)
            return result.await()
        } finally {
            if (currentRecognizer === recognizer) currentRecognizer = null
            recognizer.destroy()
        }
    }
}
