package com.actuagt.shared.push

import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Task
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

// Android push registration: FCM token persisted per device plus zone topic
// subscriptions (ratification calls for citizens, alerts for officials).
actual class PushRegistrar actual constructor() {
    actual suspend fun register(uid: String, zone: String, role: String): Result<Unit> =
        runCatching {
            val token = FirebaseMessaging.getInstance().token.awaitTask()
            val devices = Firebase.firestore
                .collection("users").document(uid).collection("devices")
            devices.document(token).set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAtEpochMillis" to System.currentTimeMillis(),
                )
            )
            val topicZone = zone.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
            if (topicZone.isNotBlank()) {
                val messaging = FirebaseMessaging.getInstance()
                if (role == "OFFICIAL") {
                    messaging.subscribeToTopic("zone_${topicZone}_officials").awaitTask()
                } else {
                    messaging.subscribeToTopic("zone_${topicZone}_ratify").awaitTask()
                }
            }
        }
}
