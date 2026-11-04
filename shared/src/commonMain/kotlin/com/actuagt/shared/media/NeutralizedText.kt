package com.actuagt.shared.media

import kotlinx.serialization.Serializable

// Outcome of the neutral-transcription assistant: the ordered idea as a
// short title plus description the user must confirm before submitting,
// or a security topic that must not be reported as infrastructure.
// The raw transcript is never persisted.
sealed interface NeutralizedText {
    data class Clean(val title: String, val text: String) : NeutralizedText

    data object SecurityAlert : NeutralizedText
}

// Exact {kind, title, text} shape the cleanup function returns. Callables
// must decode into this data class: GitLive cannot materialise maps with
// JsonElement values on-device, so untyped Map decoding crashes there.
@Serializable
data class CleanupPayload(
    val kind: String = "",
    val title: String = "",
    val text: String = "",
)

fun CleanupPayload.toNeutralizedText(): NeutralizedText {
    if (kind == "security_alert") return NeutralizedText.SecurityAlert
    if (kind != "clean") throw IllegalStateException("Respuesta de limpieza desconocida")
    if (title.isBlank() || text.isBlank()) throw IllegalStateException("Respuesta de limpieza vacía")
    return NeutralizedText.Clean(title = title.trim(), text = text.trim())
}
