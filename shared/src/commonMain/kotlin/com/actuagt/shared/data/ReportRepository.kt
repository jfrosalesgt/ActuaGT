package com.actuagt.shared.data

import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.Ratification
import kotlinx.coroutines.flow.Flow

// Feed and report-submission boundary used by the UI.
interface ReportRepository {
    // Public incidents, kept in sync from Firestore.
    val verifiedFeed: Flow<List<IncidentReport>>

    // Incidents of one zone, sorted by ratification count (highest first).
    fun observeZoneReports(zone: String): Flow<List<IncidentReport>>

    // Stores a citizen report for official review. Returns the new document id.
    suspend fun savePendingReport(report: IncidentReport): Result<String>

    // Records a citizen corroboration (photo + description) in the parent
    // report's `ratifications` subcollection. Returns the new document id.
    suspend fun addRatification(ratification: Ratification): Result<String>

    // Uploads the photo bytes to Firebase Storage. Returns the download URL.
    suspend fun uploadReportPhoto(reportId: String, photo: CapturedPhoto): Result<String>

    // Uploads the "finished work" proof photo and marks the report resolved.
    // Returns the proof photo download URL.
    suspend fun resolveReport(reportId: String, proofPhoto: CapturedPhoto): Result<String>
}
