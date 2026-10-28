package com.actuagt.shared.ui.navigation

// Push-notification deep link handoff: MainActivity delivers a report id,
// App pushes it immediately when a main tab is visible, otherwise LoginScreen
// consumes it right after signing in (its replaceAll would wipe a push).
object DeepLinkRequest {
    var pendingReportId: String? = null

    private val consumed = mutableSetOf<String>()

    // True only the first time an id is pushed directly.
    fun markPushed(id: String): Boolean = consumed.add(id)
}
