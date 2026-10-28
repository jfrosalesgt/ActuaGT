package com.actuagt.shared.model

import kotlinx.serialization.Serializable

// Official response in a report's public timeline. Written only through the
// officialRespond function, which verifies the author's OFFICIAL profile.
@Serializable
data class ReportUpdate(
    val id: String = "",
    val reportId: String = "",
    val authorUid: String = "",
    val message: String = "",
    val status: IncidentStatus? = null,
    val proofPhotoUrl: String? = null,
    val createdAtEpochMillis: Long = 0L,
)
