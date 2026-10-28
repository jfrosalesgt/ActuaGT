package com.actuagt.shared.media

actual class VoiceInput actual constructor() {
    actual suspend fun transcribe(): String? {
        TODO("Wire with the iOS app: SFSpeechRecognizer with es locale, then return the best transcript.")
    }
}
