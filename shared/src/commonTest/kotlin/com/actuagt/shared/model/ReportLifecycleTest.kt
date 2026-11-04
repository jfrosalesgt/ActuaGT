package com.actuagt.shared.model

import com.actuagt.shared.data.VERIFIED_FEED_PAGE_SIZE
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Contract tests for the report lifecycle models: Firestore stores the exact
// lowercase serial names, so a rename here would silently break rules,
// triggers, and the public feed query.
class ReportLifecycleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun incidentStatusUsesFirestoreSerialNames() {
        val expected = mapOf(
            IncidentStatus.PENDING to "\"pending\"",
            IncidentStatus.IN_RATIFICATION to "\"in_ratification\"",
            IncidentStatus.PUBLISHED to "\"published\"",
            IncidentStatus.IN_PROGRESS to "\"in_progress\"",
            IncidentStatus.REJECTED to "\"rejected\"",
            IncidentStatus.RESOLVED to "\"resolved\"",
        )
        for ((status, wire) in expected) {
            assertEquals(wire, json.encodeToString(status), "Wire name of $status")
            assertEquals(status, json.decodeFromString<IncidentStatus>(wire))
        }
    }

    @Test
    fun priorityAndProofReviewUseFirestoreSerialNames() {
        assertEquals("\"normal\"", json.encodeToString(ReportPriority.NORMAL))
        assertEquals("\"high\"", json.encodeToString(ReportPriority.HIGH))
        assertEquals("\"approved\"", json.encodeToString(ProofReview.APPROVED))
        assertEquals("\"needs_review\"", json.encodeToString(ProofReview.NEEDS_REVIEW))
    }

    @Test
    fun incidentReportRoundTripPreservesFeedFields() {
        val report = IncidentReport(
            id = "r1",
            title = "Bache",
            description = "Bache grande",
            category = "VIALIDAD",
            latitude = 14.6,
            longitude = -90.5,
            photoUrls = listOf("https://example.com/p.jpg"),
            status = IncidentStatus.PUBLISHED,
            priority = ReportPriority.HIGH,
            zone = "zona-1",
            ratificationsCount = 4,
            reportedByUid = "u1",
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        val decoded = json.decodeFromString<IncidentReport>(json.encodeToString(report))
        assertEquals(report, decoded)
    }

    @Test
    fun ratificationDefaultsKeepEvidenceOptionalInModel() {
        // The server and the security rules enforce photoUrl/comment; the
        // model stays nullable so older documents still decode.
        val ratification = Ratification(reportId = "r1", userUid = "u2")
        assertEquals(null, ratification.photoUrl)
        assertEquals(null, ratification.comment)
        val decoded = json.decodeFromString<Ratification>(json.encodeToString(ratification))
        assertEquals(ratification, decoded)
    }

    @Test
    fun feedPageSizeIsPositive() {
        assertTrue(VERIFIED_FEED_PAGE_SIZE > 0, "Page size must bound the feed query")
    }
}
