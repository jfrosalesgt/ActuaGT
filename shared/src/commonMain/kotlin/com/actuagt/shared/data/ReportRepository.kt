package com.actuagt.shared.data

import com.actuagt.shared.model.IncidentReport
import kotlinx.coroutines.flow.Flow

// Feed and report-submission boundary used by the UI.
interface ReportRepository {
    // Public incidents, kept in sync from Firestore.
    val verifiedFeed: Flow<List<IncidentReport>>

    // Stores a citizen report for official review. Returns the new document id.
    suspend fun savePendingReport(report: IncidentReport): Result<String>

    // Records a citizen corroboration for a verified report.
    suspend fun addRatification(reportId: String, userUid: String): Result<Unit>
}
