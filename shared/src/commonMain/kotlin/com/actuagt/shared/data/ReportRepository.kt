package com.actuagt.shared.data

import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.media.NeutralizedText
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.OfficialRequest
import com.actuagt.shared.model.Ratification
import com.actuagt.shared.model.ReportUpdate
import kotlinx.coroutines.flow.Flow

// Feed and report-submission boundary used by the UI.
interface ReportRepository {
    // Public incidents, kept in sync from Firestore.
    val verifiedFeed: Flow<List<IncidentReport>>

    // Reports awaiting citizen ratification in a zone. Not public: the UI
    // exposes it only to registered citizens of that zone.
    fun observeRatificationQueue(zone: String): Flow<List<IncidentReport>>

    // Admin views across zones (temporary escape hatch until roles exist).
    fun observeAllPublishedReports(): Flow<List<IncidentReport>>
    fun observeAllProofReviews(): Flow<List<IncidentReport>>

    // Single incident by id, kept in sync from Firestore. Null when missing.
    fun observeReport(reportId: String): Flow<IncidentReport?>

    // Incidents of one zone, sorted by ratification count (highest first).
    fun observeZoneReports(zone: String): Flow<List<IncidentReport>>

    // Client-generated id for a pending report, so photos can be uploaded
    // before the document exists and the moderator sees them on creation.
    fun newPendingReportId(): String

    // Stores a citizen report for official review. Returns the new document id.
    suspend fun savePendingReport(report: IncidentReport): Result<String>

    // Records a citizen corroboration (photo + description) in the parent
    // report's `ratifications` subcollection. Returns the new document id.
    suspend fun addRatification(ratification: Ratification): Result<String>

    // Uploads the photo bytes to Firebase Storage. Returns the download URL.
    suspend fun uploadReportPhoto(reportId: String, photo: CapturedPhoto): Result<String>

    // Neutralises a raw voice transcript through the Meta transcription
    // assistant. The raw transcript is never persisted; only the confirmed
    // clean text reaches the report.
    suspend fun neutralizeText(transcript: String): Result<NeutralizedText>

    // Persists a refreshed FCM token under the current user's devices.
    suspend fun refreshPushToken(token: String): Result<Unit>

    // Public timeline of official responses for one report.
    fun observeReportUpdates(reportId: String): Flow<List<ReportUpdate>>

    // Reports whose finished-work proof the model could not verify, awaiting
    // human review in the official's zone.
    fun observeProofReviews(zone: String): Flow<List<IncidentReport>>

    // Official action verified server-side: the function checks the author's
    // OFFICIAL profile and matching zone. Message, status change (take case /
    // resolve) and resolution proof photo are optional but resolve requires
    // the proof photo. Manual review closes a `needs_review` proof.
    suspend fun officialRespond(
        reportId: String,
        message: String?,
        status: String?,
        proofPhoto: CapturedPhoto?,
        manualReview: String? = null,
        reviewReason: String? = null,
    ): Result<Unit>

    // Citizen application for official access. The credential photo is
    // optional; approval happens via setOfficialRole.
    suspend fun submitOfficialRequest(
        request: OfficialRequest,
        credentialPhoto: CapturedPhoto?,
    ): Result<Unit>

    // Pending official-access applications, visible only to admins.
    fun observeOfficialRequests(): Flow<List<OfficialRequest>>

    // Admin-only: approves (with zone and institution) or rejects an
    // application. Enforced server-side through verified Auth claims.
    suspend fun setOfficialRole(
        uid: String,
        zone: String?,
        institution: String?,
        approved: Boolean,
        reviewReason: String?,
    ): Result<Unit>

}
