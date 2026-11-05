package com.actuagt.shared.model

import kotlinx.serialization.Serializable

// Pre-submit photo/text coherence verdict from the Meta assistant: match
// lets the submit continue, mismatch blocks it so the citizen can fix the
// photos or the text before reaching moderation.
data class CoherenceVerdict(
    val match: Boolean,
    val reason: String,
)

// Exact {match, reason} shape the coherence function returns; decoded as
// a data class for the same GitLive reason as CleanupPayload.
@Serializable
data class CoherencePayload(
    val match: Boolean? = null,
    val reason: String = "",
)

fun CoherencePayload.toCoherenceVerdict(): CoherenceVerdict =
    CoherenceVerdict(
        match = match ?: throw IllegalStateException("Respuesta de verificación vacía"),
        reason = reason.takeIf { it.isNotBlank() } ?: "",
    )
