package com.actuagt.shared.media

actual class VoiceInput actual constructor() {
    actual suspend fun transcribe(): String? = AndroidVoice.listen()
}
