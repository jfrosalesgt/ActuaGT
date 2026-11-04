package com.actuagt.shared.media

// Platform speech-to-text: the UI shells provide the recognizer (Android
// SpeechRecognizer, iOS system picker equivalent). Returns null when the
// user cancels, recognition is unavailable, or permission is denied.
// stopListening ends an in-flight capture early (the Detener button) so the
// user does not wait for the silence timeout.
expect class VoiceInput() {
    suspend fun transcribe(): String?

    fun stopListening()

    // True when the last capture ended because nobody spoke: the UI
    // cancels quietly instead of showing the failure message.
    fun wasSilentEnd(): Boolean
}
