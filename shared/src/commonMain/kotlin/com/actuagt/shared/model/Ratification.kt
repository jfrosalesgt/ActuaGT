package com.actuagt.shared.model

import kotlinx.serialization.Serializable

// Citizen corroboration ("I saw it too") attached to an incident report.
@Serializable
data class Ratification(
    val id: String = "",
    val reportId: String = "",
    val userUid: String = "",
    val createdAtEpochMillis: Long = 0L,
    val comment: String? = null,
)
