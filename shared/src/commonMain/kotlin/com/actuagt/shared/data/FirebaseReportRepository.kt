package com.actuagt.shared.data

import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.IncidentStatus
import com.actuagt.shared.model.Ratification
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
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
) : ReportRepository {

    override val verifiedFeed: Flow<List<IncidentReport>> =
        firestore.collection(INCIDENTS_COLLECTION)
            .where(STATUS_FIELD, equalTo = VERIFIED_STATUS)
            .snapshots
            .map { snapshot ->
                snapshot.documents.map { it.data<IncidentReport>().copy(id = it.id) }
            }

    override suspend fun savePendingReport(report: IncidentReport): Result<String> = runCatching {
        firestore.collection(PENDING_REPORTS_COLLECTION)
            .add(report.copy(id = "", status = IncidentStatus.PENDING))
            .id
    }

    override suspend fun addRatification(ratification: Ratification): Result<String> = runCatching {
        firestore.collection(INCIDENTS_COLLECTION)
            .document(ratification.reportId)
            .collection(RATIFICATIONS_COLLECTION)
            .add(ratification.copy(id = ""))
            .id
    }

    override fun observeZoneReports(zone: String): Flow<List<IncidentReport>> =
        firestore.collection(INCIDENTS_COLLECTION)
            .where(ZONE_FIELD, equalTo = zone)
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

    override suspend fun resolveReport(reportId: String, proofPhoto: CapturedPhoto): Result<String> =
        runCatching {
            val photoUrl = uploadReportPhoto(reportId, proofPhoto).getOrThrow()
            firestore.collection(INCIDENTS_COLLECTION)
                .document(reportId)
                .update(STATUS_FIELD to RESOLVED_STATUS, RESOLVED_PHOTO_FIELD to photoUrl)
            photoUrl
        }

    companion object {
        const val INCIDENTS_COLLECTION = "incidents"
        const val PENDING_REPORTS_COLLECTION = "pending_reports"
        const val RATIFICATIONS_COLLECTION = "ratifications"
        const val REPORT_PHOTOS_PATH = "report_photos"
        const val STATUS_FIELD = "status"
        const val ZONE_FIELD = "zone"
        const val RESOLVED_PHOTO_FIELD = "resolvedPhotoUrl"
        const val JPEG_CONTENT_TYPE = "image/jpeg"

        // Matches the IncidentStatus.VERIFIED serial name.
        const val VERIFIED_STATUS = "verified"

        // Matches the IncidentStatus.RESOLVED serial name.
        const val RESOLVED_STATUS = "resolved"
    }
}
