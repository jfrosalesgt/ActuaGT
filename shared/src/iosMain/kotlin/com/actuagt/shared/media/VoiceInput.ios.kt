package com.actuagt.shared.media

actual class VoiceInput actual constructor() {
    actual suspend fun transcribe(): String? {
        TODO("Wire with the iOS app: SFSpeechRecognizer with es locale, then return the best transcript.")
    }

    actual fun stopListening() {
        // No-op until the iOS recognizer is wired (see transcribe TODO).
    }

    actual fun wasSilentEnd(): Boolean = false
}
