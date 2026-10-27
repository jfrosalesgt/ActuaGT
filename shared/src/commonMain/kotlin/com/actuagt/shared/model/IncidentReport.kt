package com.actuagt.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Lifecycle of a citizen report. Serial names match the Firestore values.
@Serializable
enum class IncidentStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("verified")
    VERIFIED,

    @SerialName("rejected")
    REJECTED,
}

// Citizen infrastructure report stored in Firestore.
@Serializable
data class IncidentReport(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoUrls: List<String> = emptyList(),
    val status: IncidentStatus = IncidentStatus.PENDING,
    val reportedByUid: String = "",
    val createdAtEpochMillis: Long = 0L,
)
