package com.actuagt.shared.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.actuagt.shared.data.DataGraph
import com.actuagt.shared.data.ReportRepository
import com.actuagt.shared.model.IncidentReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

// Backs ReportDetailScreen: a single incident opened from a push deep link.
class ReportDetailScreenModel(
    reportId: String,
    repository: ReportRepository = DataGraph.reportRepository,
) : ScreenModel {

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    val report: StateFlow<IncidentReport?> = repository.observeReport(reportId)
        .onEach { _loaded.value = true }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
