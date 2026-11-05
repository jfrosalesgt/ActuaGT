package com.actuagt.shared.media

actual class VoiceInput actual constructor() {
    actual suspend fun transcribe(): String? = AndroidVoice.listen()

    actual fun stopListening() = AndroidVoice.stop()

    actual fun wasSilentEnd(): Boolean = AndroidVoice.lastEndWasSilence
}
