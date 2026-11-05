package com.actuagt.shared.push

// Platform push registration: stores the device token under the user profile
// and subscribes to the zone topics for ratification and official alerts.
expect class PushRegistrar() {
    suspend fun register(uid: String, zone: String, role: String): Result<Unit>
}
