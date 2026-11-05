package com.actuagt.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Lifecycle of an official-access request. Approval happens outside the
// client (admin console); the client only creates `pending` requests.
@Serializable
enum class OfficialRequestStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("approved")
    APPROVED,

    @SerialName("rejected")
    REJECTED,
}

@Serializable
data class OfficialRequest(
    val uid: String = "",
    val fullName: String = "",
    val institution: String = "",
    val position: String = "",
    val institutionalEmail: String = "",
    val zone: String = "",
    val credentialPhotoUrl: String? = null,
    val status: OfficialRequestStatus = OfficialRequestStatus.PENDING,
    val createdAtEpochMillis: Long = 0L,
)
