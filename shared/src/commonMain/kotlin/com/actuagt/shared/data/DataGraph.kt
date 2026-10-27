package com.actuagt.shared.data

// Manual dependency graph for Firestore-backed repositories.
object DataGraph {
    val reportRepository: ReportRepository by lazy {
        FirebaseReportRepository()
    }
}
