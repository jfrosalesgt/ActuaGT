package com.actuagt.shared.data

import com.actuagt.shared.model.IncidentReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

// A rejected Firestore listen (missing composite index, denied read) used to
// crash the app: the failure propagated through the snapshots Flow into the
// UI collector. Every repository observer now ends with orEmptyOnError and
// degrades to an empty view instead.
class QueryResilienceTest {

    @Test
    fun listenerFailureFallsBackToEmptyList() = runBlocking {
        val failing: Flow<List<IncidentReport>> = flow {
            throw IllegalStateException("FAILED_PRECONDITION: The query requires an index")
        }
        assertEquals(emptyList(), failing.orEmptyOnError().first())
    }

    @Test
    fun healthyFlowPassesThroughUnchanged() = runBlocking {
        val reports = listOf(IncidentReport(id = "r1"), IncidentReport(id = "r2"))
        assertEquals(reports, flowOf(reports).orEmptyOnError().first())
    }
}
