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

    @SerialName("resolved")
    RESOLVED,
}

// Report priority, raised to HIGH once a report gathers enough ratifications.
@Serializable
enum class ReportPriority {
    @SerialName("normal")
    NORMAL,

    @SerialName("high")
    HIGH,
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
    val priority: ReportPriority = ReportPriority.NORMAL,
    val zone: String = "",
    val ratificationsCount: Int = 0,
    val resolvedPhotoUrl: String? = null,
    val reportedByUid: String = "",
    val createdAtEpochMillis: Long = 0L,
)
