package com.actuagt.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Lifecycle of a citizen report. Serial names match the Firestore values.
// Nothing is public before PUBLISHED: moderated reports wait in
// IN_RATIFICATION, visible only to registered citizens of the same zone.
@Serializable
enum class IncidentStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("in_ratification")
    IN_RATIFICATION,

    @SerialName("published")
    PUBLISHED,

    @SerialName("in_progress")
    IN_PROGRESS,

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

// Resolution-proof review state. Only set on reports with a finished-work
// photo: APPROVED clears, NEEDS_REVIEW sends it to the official queue.
@Serializable
enum class ProofReview {
    @SerialName("approved")
    APPROVED,

    @SerialName("needs_review")
    NEEDS_REVIEW,
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
    val proofReview: ProofReview? = null,
    val proofReviewReason: String? = null,
    val reportedByUid: String = "",
    val createdAtEpochMillis: Long = 0L,
)
