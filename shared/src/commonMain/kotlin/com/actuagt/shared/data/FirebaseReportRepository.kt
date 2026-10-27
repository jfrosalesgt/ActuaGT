package com.actuagt.shared.data

import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.IncidentStatus
import com.actuagt.shared.model.Ratification
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

// Firestore-backed reports, callable from commonMain.
class FirebaseReportRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : ReportRepository {

    override val verifiedFeed: Flow<List<IncidentReport>> =
        firestore.collection(INCIDENTS_COLLECTION)
            .whereEqualTo(STATUS_FIELD, VERIFIED_STATUS)
            .snapshots
            .map { snapshot ->
                snapshot.documents.map { it.data<IncidentReport>().copy(id = it.id) }
            }

    override suspend fun savePendingReport(report: IncidentReport): Result<String> = runCatching {
        firestore.collection(PENDING_REPORTS_COLLECTION)
            .add(report.copy(id = "", status = IncidentStatus.PENDING))
            .id
    }

    override suspend fun addRatification(reportId: String, userUid: String): Result<Unit> = runCatching {
        val ratification = Ratification(
            reportId = reportId,
            userUid = userUid,
            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        firestore.collection(RATIFICATIONS_COLLECTION).add(ratification)
        Unit
    }

    companion object {
        const val INCIDENTS_COLLECTION = "incidents"
        const val PENDING_REPORTS_COLLECTION = "pending_reports"
        const val RATIFICATIONS_COLLECTION = "ratifications"
        const val STATUS_FIELD = "status"

        // Matches the IncidentStatus.VERIFIED serial name.
        const val VERIFIED_STATUS = "verified"
    }
}
