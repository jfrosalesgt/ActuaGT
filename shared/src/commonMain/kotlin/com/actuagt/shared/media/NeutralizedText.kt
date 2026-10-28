package com.actuagt.shared.media

// Outcome of the neutral-transcription assistant: clean text the user must
// confirm before submitting, or a security topic that must not be reported
// as infrastructure. The raw transcript is never persisted.
sealed interface NeutralizedText {
    data class Clean(val text: String) : NeutralizedText

    data object SecurityAlert : NeutralizedText
}
