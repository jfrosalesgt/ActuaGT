package com.actuagt.shared.push

actual class PushRegistrar actual constructor() {
    actual suspend fun register(uid: String, zone: String, role: String): Result<Unit> {
        TODO("Wire with the iOS app: APNs token via Firebase Messaging, then persist and subscribe.")
    }
}
