package com.actuagt.shared.model

import kotlinx.serialization.Serializable

// Citizen corroboration ("I saw it too") attached to an incident report,
// stored in the parent report's `ratifications` subcollection.
@Serializable
data class Ratification(
    val id: String = "",
    val reportId: String = "",
    val userUid: String = "",
    val photoUrl: String? = null,
    val createdAtEpochMillis: Long = 0L,
    val comment: String? = null,
)
