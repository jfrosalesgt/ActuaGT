package com.actuagt.shared.data

import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.media.NeutralizedText
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.IncidentStatus
import com.actuagt.shared.model.OfficialRequest
import com.actuagt.shared.model.OfficialRequestStatus
import com.actuagt.shared.model.ReportUpdate
import com.actuagt.shared.model.Ratification
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.firestore.where
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.storageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

// Firestore-backed reports, callable from commonMain.
class FirebaseReportRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val storage: FirebaseStorage = Firebase.storage,
    private val functions: FirebaseFunctions = Firebase.functions,
) : ReportRepository {

    override fun observeReport(reportId: String): Flow<IncidentReport?> =
        firestore.collection(INCIDENTS_COLLECTION)
            .document(reportId)
            .snapshots
            .map { snapshot ->
                if (!snapshot.exists) null
                else snapshot.data<IncidentReport>().copy(id = snapshot.id)
            }

    override val verifiedFeed: Flow<List<IncidentReport>> =
        firestore.collection(INCIDENTS_COLLECTION)
            .where(STATUS_FIELD, equalTo = PUBLISHED_STATUS)
            .snapshots
            .map { snapshot ->
                snapshot.documents.map { it.data<IncidentReport>().copy(id = it.id) }
            }

    override fun newPendingReportId(): String {
        // No auto-id getter on the reference: timestamp plus random suffix is
        // unique enough for a client-generated pending id.
        val suffix = (0..999999).random().toString().padStart(6, '0')
        return "pending_${Clock.System.now().toEpochMilliseconds()}_$suffix"
    }

    override suspend fun savePendingReport(report: IncidentReport): Result<String> = runCatching {
        val reference = firestore.collection(PENDING_REPORTS_COLLECTION).document(report.id)
        reference.set(report.copy(id = reference.id, status = IncidentStatus.PENDING))
        reference.id
    }

    override suspend fun addRatification(ratification: Ratification): Result<String> = runCatching {
        // Document id is the ratifier uid: one ratification per user, and a
        // second submit cannot overwrite the first client-side (see rules).
        firestore.collection(INCIDENTS_COLLECTION)
            .document(ratification.reportId)
            .collection(RATIFICATIONS_COLLECTION)
            .document(ratification.userUid)
            .set(ratification.copy(id = ratification.userUid))
        ratification.userUid
    }

    override fun observeZoneReports(zone: String): Flow<List<IncidentReport>> =
        observeZoneReportsWithStatus(zone, PUBLISHED_STATUS)

    // Queue of reports awaiting citizen ratification. Not public: the UI
    // exposes it only to registered citizens of the same zone.
    override fun observeRatificationQueue(zone: String): Flow<List<IncidentReport>> =
        observeZoneReportsWithStatus(zone, IN_RATIFICATION_STATUS)

    private fun observeZoneReportsWithStatus(
        zone: String,
        status: String,
    ): Flow<List<IncidentReport>> =
        firestore.collection(INCIDENTS_COLLECTION)
            .where(ZONE_FIELD, equalTo = zone)
            .where(STATUS_FIELD, equalTo = status)
            .snapshots
            .map { snapshot ->
                snapshot.documents.map { it.data<IncidentReport>().copy(id = it.id) }
                    .sortedByDescending { it.ratificationsCount }
            }

    override suspend fun uploadReportPhoto(reportId: String, photo: CapturedPhoto): Result<String> =
        runCatching {
            val fileName = "${photo.metadata.capturedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds()}.jpg"
            val reference = storage.reference("$REPORT_PHOTOS_PATH/$reportId/$fileName")
            reference.putData(
                photo.bytes.toStorageData(),
                storageMetadata { contentType = JPEG_CONTENT_TYPE },
            )
            reference.getDownloadUrl()
        }

    override suspend fun neutralizeText(transcript: String): Result<NeutralizedText> = runCatching {
        val data: Map<*, *> = functions.httpsCallable("enhanceReportText")(
            mapOf("transcript" to transcript)
        ).data()
        when (data["kind"]) {
            "clean" -> NeutralizedText.Clean(
                (data["text"] as? String)?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Respuesta de limpieza vacía")
            )
            "security_alert" -> NeutralizedText.SecurityAlert
            else -> throw IllegalStateException("Respuesta de limpieza desconocida")
        }
    }

    override suspend fun refreshPushToken(token: String): Result<Unit> = runCatching {
        val uid = Firebase.auth.currentUser?.uid ?: return@runCatching
        Firebase.firestore.collection(USERS_COLLECTION).document(uid)
            .collection(DEVICES_COLLECTION).document(token)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAtEpochMillis" to Clock.System.now().toEpochMilliseconds(),
                )
            )
    }

    override fun observeProofReviews(zone: String): Flow<List<IncidentReport>> =
        firestore.collection(INCIDENTS_COLLECTION)
            .where(ZONE_FIELD, equalTo = zone)
            .where(PROOF_REVIEW_FIELD, equalTo = NEEDS_REVIEW_VALUE)
            .snapshots
            .map { snapshot ->
                snapshot.documents.map { it.data<IncidentReport>().copy(id = it.id) }
            }

    override fun observeReportUpdates(reportId: String): Flow<List<ReportUpdate>> =
        firestore.collection(INCIDENTS_COLLECTION)
            .document(reportId)
            .collection(REPORT_UPDATES_COLLECTION)
            .snapshots
            .map { snapshot ->
                snapshot.documents.map { it.data<ReportUpdate>().copy(id = it.id) }
                    .sortedBy { it.createdAtEpochMillis }
            }

    override suspend fun officialRespond(
        reportId: String,
        message: String?,
        status: String?,
        proofPhoto: CapturedPhoto?,
        manualReview: String?,
        reviewReason: String?,
    ): Result<Unit> = runCatching {
        val proofPhotoUrl = proofPhoto?.let {
            uploadReportPhoto(reportId, it).getOrThrow()
        }
        val payload = mutableMapOf<String, Any?>(REPORT_ID_FIELD to reportId)
        message?.takeIf { it.isNotBlank() }?.let { payload[MESSAGE_FIELD] = it }
        status?.let { payload[STATUS_FIELD] = it }
        proofPhotoUrl?.let { payload[PROOF_PHOTO_FIELD] = it }
        manualReview?.let { payload[MANUAL_REVIEW_FIELD] = it }
        reviewReason?.takeIf { it.isNotBlank() }?.let { payload[REVIEW_REASON_FIELD] = it }
        val ignored: Map<*, *> = functions.httpsCallable(OFFICIAL_RESPOND_FUNCTION)(payload).data()
    }

    override suspend fun submitOfficialRequest(
        request: OfficialRequest,
        credentialPhoto: CapturedPhoto?,
    ): Result<Unit> = runCatching {
        val credentialPhotoUrl = credentialPhoto?.let { photo ->
            val reference = storage.reference("$OFFICIAL_REQUESTS_PATH/${request.uid}/credencial.jpg")
            reference.putData(
                photo.bytes.toStorageData(),
                storageMetadata { contentType = JPEG_CONTENT_TYPE },
            )
            reference.getDownloadUrl()
        }
        firestore.collection(OFFICIAL_REQUESTS_COLLECTION)
            .document(request.uid)
            .set(
                request.copy(
                    credentialPhotoUrl = credentialPhotoUrl,
                    status = OfficialRequestStatus.PENDING,
                )
            )
    }

    override suspend fun resolveReport(reportId: String, proofPhoto: CapturedPhoto): Result<String> =
        runCatching {
            val photoUrl = uploadReportPhoto(reportId, proofPhoto).getOrThrow()
            firestore.collection(INCIDENTS_COLLECTION)
                .document(reportId)
                .update(STATUS_FIELD to RESOLVED_STATUS, RESOLVED_PHOTO_FIELD to photoUrl)
            photoUrl
        }

    companion object {
        const val USERS_COLLECTION = "users"
        const val DEVICES_COLLECTION = "devices"
        const val INCIDENTS_COLLECTION = "incidents"
        const val PENDING_REPORTS_COLLECTION = "pending_reports"
        const val RATIFICATIONS_COLLECTION = "ratifications"
        const val REPORT_UPDATES_COLLECTION = "report_updates"
        const val OFFICIAL_REQUESTS_COLLECTION = "official_requests"
        const val REPORT_PHOTOS_PATH = "report_photos"
        const val OFFICIAL_REQUESTS_PATH = "official_requests"
        const val OFFICIAL_RESPOND_FUNCTION = "officialRespond"
        const val REPORT_ID_FIELD = "reportId"
        const val MESSAGE_FIELD = "message"
        const val PROOF_PHOTO_FIELD = "proofPhotoUrl"
        const val MANUAL_REVIEW_FIELD = "manualReview"
        const val REVIEW_REASON_FIELD = "reviewReason"
        const val PROOF_REVIEW_FIELD = "proofReview"
        const val NEEDS_REVIEW_VALUE = "needs_review"
        const val STATUS_FIELD = "status"
        const val IN_RATIFICATION_STATUS = "in_ratification"
        const val ZONE_FIELD = "zone"
        const val RESOLVED_PHOTO_FIELD = "resolvedPhotoUrl"
        const val JPEG_CONTENT_TYPE = "image/jpeg"

        // Matches the IncidentStatus.PUBLISHED serial name.
        const val PUBLISHED_STATUS = "published"

        // Matches the IncidentStatus.RESOLVED serial name.
        const val RESOLVED_STATUS = "resolved"
    }
}
