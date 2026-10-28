package com.actuagt.shared.media

// Platform speech-to-text: the UI shells provide the recognizer (Android
// SpeechRecognizer, iOS system picker equivalent). Returns null when the
// user cancels, recognition is unavailable, or permission is denied.
expect class VoiceInput() {
    suspend fun transcribe(): String?
}
